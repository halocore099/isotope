package dev.isotope.registry;

import dev.isotope.Isotope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

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
    private final Map<ResourceLocation, Set<ResourceLocation>> structureToSpawnerEntities = new LinkedHashMap<>();

    // Entity -> structures containing spawners for that entity
    private final Map<ResourceLocation, Set<ResourceLocation>> entityToStructures = new LinkedHashMap<>();

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
    public Set<ResourceLocation> extractFromTemplate(ResourceLocation structureId, CompoundTag templateNbt) {
        Set<ResourceLocation> entities = new LinkedHashSet<>();
        extractSpawnerEntitiesRecursive(templateNbt, entities);

        if (!entities.isEmpty()) {
            // Update structure -> entities mapping
            structureToSpawnerEntities
                .computeIfAbsent(structureId, k -> new LinkedHashSet<>())
                .addAll(entities);

            // Update reverse index
            for (ResourceLocation entityId : entities) {
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
    private void extractSpawnerEntitiesRecursive(CompoundTag nbt, Set<ResourceLocation> entities) {
        // Check if this is a spawner block entity
        if (isSpawnerBlock(nbt)) {
            spawnersFound++;
            extractSpawnerEntityTypes(nbt, entities);
        }

        // Recursively check nested compound tags
        for (String key : nbt.getAllKeys()) {
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
        if (nbt.contains("id", Tag.TAG_STRING)) {
            String id = nbt.getString("id");
            if (SPAWNER_IDS.contains(id)) {
                return true;
            }
        }

        // Also check for nbt field within blocks array (structure template format)
        // The blocks array has elements with {state: {Name: "..."}, nbt: {...}}
        if (nbt.contains("nbt", Tag.TAG_COMPOUND)) {
            CompoundTag innerNbt = nbt.getCompound("nbt");
            if (innerNbt.contains("id", Tag.TAG_STRING)) {
                String id = innerNbt.getString("id");
                if (SPAWNER_IDS.contains(id)) {
                    return true;
                }
            }
        }

        // Check state.Name for the block type (structure template format)
        if (nbt.contains("state", Tag.TAG_COMPOUND)) {
            CompoundTag state = nbt.getCompound("state");
            if (state.contains("Name", Tag.TAG_STRING)) {
                String name = state.getString("Name");
                if (SPAWNER_IDS.contains(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extract entity types from a spawner's NBT data.
     */
    private void extractSpawnerEntityTypes(CompoundTag spawnerNbt, Set<ResourceLocation> entities) {
        // The actual spawner data might be in 'nbt' field (structure template format)
        CompoundTag dataTag = spawnerNbt;
        if (spawnerNbt.contains("nbt", Tag.TAG_COMPOUND)) {
            dataTag = spawnerNbt.getCompound("nbt");
        }

        // Extract from SpawnData (the default/current spawn entity)
        if (dataTag.contains("SpawnData", Tag.TAG_COMPOUND)) {
            CompoundTag spawnData = dataTag.getCompound("SpawnData");
            extractEntityFromSpawnData(spawnData, entities);
        }

        // Extract from SpawnPotentials (weighted list of possible entities)
        if (dataTag.contains("SpawnPotentials", Tag.TAG_LIST)) {
            ListTag potentials = dataTag.getList("SpawnPotentials", Tag.TAG_COMPOUND);
            for (int i = 0; i < potentials.size(); i++) {
                CompoundTag potential = potentials.getCompound(i);
                // SpawnPotentials format: { weight: N, data: { entity: { id: "..." } } }
                if (potential.contains("data", Tag.TAG_COMPOUND)) {
                    CompoundTag data = potential.getCompound("data");
                    extractEntityFromSpawnData(data, entities);
                }
                // Alternative format: { weight: N, Entity: { id: "..." } }
                if (potential.contains("Entity", Tag.TAG_COMPOUND)) {
                    CompoundTag entityTag = potential.getCompound("Entity");
                    extractEntityId(entityTag, entities);
                }
            }
        }

        // Legacy format: direct Entity tag
        if (dataTag.contains("Entity", Tag.TAG_COMPOUND)) {
            CompoundTag entityTag = dataTag.getCompound("Entity");
            extractEntityId(entityTag, entities);
        }
    }

    /**
     * Extract entity ID from SpawnData format: { entity: { id: "..." } }
     */
    private void extractEntityFromSpawnData(CompoundTag spawnData, Set<ResourceLocation> entities) {
        // Modern format: { entity: { id: "..." } }
        if (spawnData.contains("entity", Tag.TAG_COMPOUND)) {
            CompoundTag entityTag = spawnData.getCompound("entity");
            extractEntityId(entityTag, entities);
        }
        // Direct ID in SpawnData (some versions)
        if (spawnData.contains("id", Tag.TAG_STRING)) {
            String entityIdStr = spawnData.getString("id");
            addEntityId(entityIdStr, entities);
        }
    }

    /**
     * Extract entity ID from an entity compound tag.
     */
    private void extractEntityId(CompoundTag entityTag, Set<ResourceLocation> entities) {
        if (entityTag.contains("id", Tag.TAG_STRING)) {
            String entityIdStr = entityTag.getString("id");
            addEntityId(entityIdStr, entities);
        }
    }

    /**
     * Parse and add an entity ID string to the set.
     */
    private void addEntityId(String entityIdStr, Set<ResourceLocation> entities) {
        try {
            ResourceLocation entityId = ResourceLocation.parse(entityIdStr);
            entities.add(entityId);
        } catch (Exception e) {
            Isotope.LOGGER.debug("[SpawnerExtractor] Invalid entity ID: {}", entityIdStr);
        }
    }

    // --- Query API ---

    /**
     * Get all entities found in spawners for a structure.
     */
    public Set<ResourceLocation> getSpawnerEntitiesForStructure(ResourceLocation structureId) {
        return structureToSpawnerEntities.getOrDefault(structureId, Set.of());
    }

    /**
     * Get all structures that contain spawners for a specific entity.
     */
    public Set<ResourceLocation> getStructuresWithSpawnerEntity(ResourceLocation entityId) {
        return entityToStructures.getOrDefault(entityId, Set.of());
    }

    /**
     * Get all structures that have spawner entities.
     */
    public Set<ResourceLocation> getStructuresWithSpawners() {
        return Collections.unmodifiableSet(structureToSpawnerEntities.keySet());
    }

    /**
     * Get all unique entities found in spawners.
     */
    public Set<ResourceLocation> getAllSpawnerEntities() {
        return Collections.unmodifiableSet(entityToStructures.keySet());
    }

    /**
     * Get entity loot tables for spawner entities in a structure.
     * Maps entity IDs to their loot table IDs via EntityLootRegistry.
     */
    public Set<ResourceLocation> getSpawnerLootTablesForStructure(ResourceLocation structureId) {
        Set<ResourceLocation> lootTables = new LinkedHashSet<>();
        Set<ResourceLocation> entities = getSpawnerEntitiesForStructure(structureId);

        EntityLootRegistry entityRegistry = EntityLootRegistry.getInstance();
        for (ResourceLocation entityId : entities) {
            entityRegistry.get(entityId).ifPresent(info ->
                lootTables.add(info.lootTableId()));
        }

        return lootTables;
    }

    /**
     * Check if a structure has any spawners.
     */
    public boolean hasSpawners(ResourceLocation structureId) {
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
