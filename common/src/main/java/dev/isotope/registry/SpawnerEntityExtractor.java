package dev.isotope.registry;

import dev.isotope.Isotope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Extracts entity types from spawner block NBT data in structure templates.
 *
 * When a structure contains a spawner (mob_spawner block), this extractor
 * identifies what entities that spawner can spawn. These entities' loot tables
 * can then be associated with the structure since they represent potential
 * drops from that structure.
 *
 * For example, a dungeon with a zombie spawner should link to minecraft:entities/zombie
 * even though the spawner itself has no LootTable field.
 *
 * Spawner NBT structure:
 * <pre>
 * {
 *   "id": "minecraft:mob_spawner",
 *   "SpawnData": {
 *     "entity": {
 *       "id": "minecraft:zombie"
 *     }
 *   },
 *   "SpawnPotentials": [
 *     {
 *       "weight": 1,
 *       "data": {
 *         "entity": {
 *           "id": "minecraft:skeleton"
 *         }
 *       }
 *     }
 *   ]
 * }
 * </pre>
 */
public final class SpawnerEntityExtractor {

    private static final SpawnerEntityExtractor INSTANCE = new SpawnerEntityExtractor();

    // Known spawner block IDs
    private static final Set<String> SPAWNER_IDS = Set.of(
        "minecraft:mob_spawner",
        "minecraft:spawner"
    );

    // Structure -> entities found in spawners
    private final Map<Identifier, Set<Identifier>> structureToSpawnerEntities = new LinkedHashMap<>();

    // Entity -> structures containing spawners for that entity
    private final Map<Identifier, Set<Identifier>> entityToStructures = new LinkedHashMap<>();

    // Statistics
    private int spawnersFound = 0;
    private int entitiesExtracted = 0;

    private SpawnerEntityExtractor() {}

    public static SpawnerEntityExtractor getInstance() {
        return INSTANCE;
    }

    /**
     * Extract spawner entities from template NBT.
     * Call this from StructureTemplateParser for each template.
     *
     * @param structureId The structure containing this template
     * @param templateNbt The serialized template NBT
     * @return Set of entity IDs found in spawners
     */
    public Set<Identifier> extractFromTemplate(Identifier structureId, CompoundTag templateNbt) {
        Set<Identifier> entities = new LinkedHashSet<>();
        extractSpawnerEntitiesRecursive(templateNbt, entities);

        if (!entities.isEmpty()) {
            // Update structure -> entities mapping
            structureToSpawnerEntities
                .computeIfAbsent(structureId, k -> new LinkedHashSet<>())
                .addAll(entities);

            // Update reverse index
            for (Identifier entityId : entities) {
                entityToStructures
                    .computeIfAbsent(entityId, k -> new LinkedHashSet<>())
                    .add(structureId);
            }

            entitiesExtracted += entities.size();
            Isotope.LOGGER.debug("[SpawnerExtractor] Found {} entities in spawners for {}",
                entities.size(), structureId);
        }

        return entities;
    }

    /**
     * Recursively search NBT for spawner blocks and extract entity types.
     */
    private void extractSpawnerEntitiesRecursive(CompoundTag nbt, Set<Identifier> entities) {
        // Check if this is a spawner block entity
        if (isSpawnerBlock(nbt)) {
            spawnersFound++;
            extractSpawnerEntityTypes(nbt, entities);
        }

        // Recursively check nested compound tags
        for (String key : nbt.keySet()) {
            Tag tag = nbt.get(key);
            if (tag instanceof CompoundTag compound) {
                extractSpawnerEntitiesRecursive(compound, entities);
            } else if (tag instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag compound) {
                        extractSpawnerEntitiesRecursive(compound, entities);
                    }
                }
            }
        }
    }

    /**
     * Check if a compound tag represents a spawner block entity.
     */
    private boolean isSpawnerBlock(CompoundTag nbt) {
        // Check for block entity ID field
        Optional<String> idOpt = nbt.getString("id");
        if (idOpt.isPresent() && SPAWNER_IDS.contains(idOpt.get())) {
            return true;
        }

        // Also check for nbt field within blocks array (structure template format)
        // The blocks array has elements with {state: {Name: "..."}, nbt: {...}}
        Optional<CompoundTag> innerNbtOpt = nbt.getCompound("nbt");
        if (innerNbtOpt.isPresent()) {
            Optional<String> innerId = innerNbtOpt.get().getString("id");
            if (innerId.isPresent() && SPAWNER_IDS.contains(innerId.get())) {
                return true;
            }
        }

        // Check state.Name for the block type (structure template format)
        Optional<CompoundTag> stateOpt = nbt.getCompound("state");
        if (stateOpt.isPresent()) {
            Optional<String> nameOpt = stateOpt.get().getString("Name");
            if (nameOpt.isPresent() && SPAWNER_IDS.contains(nameOpt.get())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract entity types from a spawner's NBT data.
     */
    private void extractSpawnerEntityTypes(CompoundTag spawnerNbt, Set<Identifier> entities) {
        // The actual spawner data might be in 'nbt' field (structure template format)
        CompoundTag dataTag = spawnerNbt.getCompound("nbt").orElse(spawnerNbt);

        // Extract from SpawnData (the default/current spawn entity)
        dataTag.getCompound("SpawnData").ifPresent(spawnData ->
            extractEntityFromSpawnData(spawnData, entities));

        // Extract from SpawnPotentials (weighted list of possible entities)
        dataTag.getList("SpawnPotentials").ifPresent(potentials -> {
            for (int i = 0; i < potentials.size(); i++) {
                if (potentials.get(i) instanceof CompoundTag potential) {
                    // SpawnPotentials format: { weight: N, data: { entity: { id: "..." } } }
                    potential.getCompound("data").ifPresent(data ->
                        extractEntityFromSpawnData(data, entities));
                    // Alternative format: { weight: N, Entity: { id: "..." } }
                    potential.getCompound("Entity").ifPresent(entityTag ->
                        extractEntityId(entityTag, entities));
                }
            }
        });

        // Legacy format: direct Entity tag
        dataTag.getCompound("Entity").ifPresent(entityTag ->
            extractEntityId(entityTag, entities));
    }

    /**
     * Extract entity ID from SpawnData format: { entity: { id: "..." } }
     */
    private void extractEntityFromSpawnData(CompoundTag spawnData, Set<Identifier> entities) {
        // Modern format: { entity: { id: "..." } }
        spawnData.getCompound("entity").ifPresent(entityTag ->
            extractEntityId(entityTag, entities));
        // Direct ID in SpawnData (some versions)
        spawnData.getString("id").ifPresent(entityIdStr ->
            addEntityId(entityIdStr, entities));
    }

    /**
     * Extract entity ID from an entity compound tag.
     */
    private void extractEntityId(CompoundTag entityTag, Set<Identifier> entities) {
        entityTag.getString("id").ifPresent(entityIdStr ->
            addEntityId(entityIdStr, entities));
    }

    /**
     * Parse and add an entity ID string to the set.
     */
    private void addEntityId(String entityIdStr, Set<Identifier> entities) {
        try {
            Identifier entityId = Identifier.parse(entityIdStr);
            entities.add(entityId);
        } catch (Exception e) {
            Isotope.LOGGER.debug("[SpawnerExtractor] Invalid entity ID: {}", entityIdStr);
        }
    }

    // --- Query API ---

    /**
     * Get all entities found in spawners for a structure.
     */
    public Set<Identifier> getSpawnerEntitiesForStructure(Identifier structureId) {
        return structureToSpawnerEntities.getOrDefault(structureId, Set.of());
    }

    /**
     * Get all structures that contain spawners for a specific entity.
     */
    public Set<Identifier> getStructuresWithSpawnerEntity(Identifier entityId) {
        return entityToStructures.getOrDefault(entityId, Set.of());
    }

    /**
     * Get all structures that have spawner entities.
     */
    public Set<Identifier> getStructuresWithSpawners() {
        return Collections.unmodifiableSet(structureToSpawnerEntities.keySet());
    }

    /**
     * Get all unique entities found in spawners.
     */
    public Set<Identifier> getAllSpawnerEntities() {
        return Collections.unmodifiableSet(entityToStructures.keySet());
    }

    /**
     * Get entity loot tables for spawner entities in a structure.
     * Maps entity IDs to their loot table IDs via EntityLootRegistry.
     */
    public Set<Identifier> getSpawnerLootTablesForStructure(Identifier structureId) {
        Set<Identifier> lootTables = new LinkedHashSet<>();
        Set<Identifier> entities = getSpawnerEntitiesForStructure(structureId);

        EntityLootRegistry entityRegistry = EntityLootRegistry.getInstance();
        for (Identifier entityId : entities) {
            entityRegistry.get(entityId).ifPresent(info ->
                lootTables.add(info.lootTableId()));
        }

        return lootTables;
    }

    /**
     * Check if a structure has any spawners.
     */
    public boolean hasSpawners(Identifier structureId) {
        return structureToSpawnerEntities.containsKey(structureId);
    }

    /**
     * Get statistics.
     */
    public Stats getStats() {
        return new Stats(
            spawnersFound,
            entitiesExtracted,
            structureToSpawnerEntities.size(),
            entityToStructures.size()
        );
    }

    /**
     * Clear all data.
     */
    public void clear() {
        structureToSpawnerEntities.clear();
        entityToStructures.clear();
        spawnersFound = 0;
        entitiesExtracted = 0;
    }

    /**
     * Statistics record.
     */
    public record Stats(
        int spawnersFound,
        int entitiesExtracted,
        int structuresWithSpawners,
        int uniqueEntities
    ) {
        public String summary() {
            return String.format("%d spawners, %d entities, %d structures",
                spawnersFound, entitiesExtracted, structuresWithSpawners);
        }
    }
}
