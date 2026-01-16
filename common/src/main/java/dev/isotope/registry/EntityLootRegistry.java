package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.LootSource;
import dev.isotope.data.LootTableInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of entity/mob loot tables.
 *
 * Scans loot tables with "entities/" prefix and maps them to entity types.
 * Entity loot is different from chest loot:
 * - Triggered on entity death (not container interaction)
 * - Killer-dependent (Looting enchant, player kill requirements)
 * - Conditions like killed_by_player, random_chance_with_looting
 *
 * Examples:
 * - minecraft:entities/zombie -> zombie (rotten flesh, rare iron/carrot/potato)
 * - minecraft:entities/creeper -> creeper (gunpowder, music discs from skeleton kill)
 * - minecraft:entities/ender_dragon -> ender_dragon (XP only, but still has table)
 */
public final class EntityLootRegistry {

    private static final EntityLootRegistry INSTANCE = new EntityLootRegistry();

    // Entity ID -> Entity loot info
    private final Map<ResourceLocation, EntityLootInfo> entities = new LinkedHashMap<>();

    // Loot table ID -> Entity ID (reverse lookup)
    private final Map<ResourceLocation, ResourceLocation> lootTableToEntity = new LinkedHashMap<>();

    private boolean initialized = false;

    private EntityLootRegistry() {}

    public static EntityLootRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize by scanning entity loot tables from LootTableRegistry.
     * Call this after LootTableRegistry is scanned.
     */
    public void initialize() {
        if (initialized) return;
        entities.clear();
        lootTableToEntity.clear();

        LootTableRegistry lootTables = LootTableRegistry.getInstance();
        if (!lootTables.isScanned()) {
            Isotope.LOGGER.warn("EntityLootRegistry: LootTableRegistry not scanned yet");
            return;
        }

        // Find all entity loot tables (path starts with "entities/")
        for (LootTableInfo tableInfo : lootTables.getAll()) {
            String path = tableInfo.path();
            if (path.startsWith("entities/") || path.startsWith("entity/")) {
                // Extract entity ID from path: "entities/zombie" -> "minecraft:zombie"
                String entityPath = path.startsWith("entities/")
                    ? path.substring("entities/".length())
                    : path.substring("entity/".length());

                ResourceLocation entityId = ResourceLocation.fromNamespaceAndPath(
                    tableInfo.namespace(), entityPath);
                ResourceLocation lootTableId = tableInfo.id();

                // Get display name from entity registry if available
                String displayName = getEntityDisplayName(entityId);

                // Determine if this entity requires player kill for rare drops
                boolean requiresPlayerKill = checkRequiresPlayerKill(tableInfo);

                EntityLootInfo info = new EntityLootInfo(
                    entityId,
                    lootTableId,
                    displayName,
                    requiresPlayerKill
                );

                entities.put(entityId, info);
                lootTableToEntity.put(lootTableId, entityId);
            }
        }

        initialized = true;
        Isotope.LOGGER.info("EntityLootRegistry: initialized {} entity loot tables", entities.size());

        // Log breakdown by namespace
        Map<String, Long> byNamespace = entities.values().stream()
            .collect(Collectors.groupingBy(e -> e.entityId().getNamespace(), Collectors.counting()));
        byNamespace.forEach((ns, count) ->
            Isotope.LOGGER.debug("  {} entities from {}", count, ns));
    }

    /**
     * Get display name for an entity from the registry.
     */
    private String getEntityDisplayName(ResourceLocation entityId) {
        try {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
            if (entityType != null) {
                // Get the translation key and format it nicely
                String key = entityType.getDescriptionId();
                // Extract the entity name from "entity.minecraft.zombie" -> "Zombie"
                String[] parts = key.split("\\.");
                if (parts.length > 0) {
                    String name = parts[parts.length - 1];
                    return formatDisplayName(name);
                }
            }
        } catch (Exception e) {
            // Fallback to path-based name
        }
        return formatDisplayName(entityId.getPath());
    }

    /**
     * Format snake_case to Title Case.
     */
    private String formatDisplayName(String path) {
        if (path == null || path.isEmpty()) return "";
        return Arrays.stream(path.split("_"))
            .map(word -> word.isEmpty() ? "" :
                Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    /**
     * Check if a loot table has killed_by_player conditions.
     * This is a heuristic - actual analysis would require parsing the JSON.
     */
    private boolean checkRequiresPlayerKill(LootTableInfo tableInfo) {
        // For now, assume common mobs with rare drops need player kills
        // This could be enhanced with actual loot table JSON parsing
        String path = tableInfo.path();
        return path.contains("zombie") ||
               path.contains("skeleton") ||
               path.contains("creeper") ||
               path.contains("witch") ||
               path.contains("wither");
    }

    /**
     * Get all registered entity loot info.
     */
    public Collection<EntityLootInfo> getAll() {
        return Collections.unmodifiableCollection(entities.values());
    }

    /**
     * Get entity loot info by entity ID.
     */
    public Optional<EntityLootInfo> get(ResourceLocation entityId) {
        return Optional.ofNullable(entities.get(entityId));
    }

    /**
     * Get entity loot info by loot table ID.
     */
    public Optional<EntityLootInfo> getByLootTable(ResourceLocation lootTableId) {
        ResourceLocation entityId = lootTableToEntity.get(lootTableId);
        if (entityId != null) {
            return Optional.ofNullable(entities.get(entityId));
        }
        return Optional.empty();
    }

    /**
     * Check if a loot table is an entity loot table.
     */
    public boolean isEntityLootTable(ResourceLocation lootTableId) {
        return lootTableToEntity.containsKey(lootTableId);
    }

    /**
     * Get entity ID for a loot table.
     */
    public Optional<ResourceLocation> getEntityForLootTable(ResourceLocation lootTableId) {
        return Optional.ofNullable(lootTableToEntity.get(lootTableId));
    }

    /**
     * Get entities filtered by namespace.
     */
    public List<EntityLootInfo> getByNamespace(String namespace) {
        if ("*".equals(namespace)) {
            return new ArrayList<>(entities.values());
        }
        return entities.values().stream()
            .filter(e -> e.entityId().getNamespace().equals(namespace))
            .toList();
    }

    /**
     * Convert all entities to LootSource objects.
     */
    public List<LootSource> getAllAsLootSources() {
        return entities.values().stream()
            .map(EntityLootInfo::toLootSource)
            .toList();
    }

    /**
     * Get all unique namespaces.
     */
    public Set<String> getNamespaces() {
        return entities.values().stream()
            .map(e -> e.entityId().getNamespace())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Total entity count.
     */
    public int size() {
        return entities.size();
    }

    /**
     * Check if registry has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Reset the registry.
     */
    public void reset() {
        entities.clear();
        lootTableToEntity.clear();
        initialized = false;
    }

    /**
     * Entity loot information.
     */
    public record EntityLootInfo(
        ResourceLocation entityId,
        ResourceLocation lootTableId,
        String displayName,
        boolean requiresPlayerKill
    ) {
        /**
         * Convert to a LootSource for unified handling.
         */
        public LootSource toLootSource() {
            String description = requiresPlayerKill
                ? "Mob drops (player kill may be required for rare items)"
                : "Mob drops";
            return LootSource.mob(entityId, displayName, description);
        }
    }
}
