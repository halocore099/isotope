package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.compat.RegistryHelper;
import dev.isotope.data.LootSource;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.loot.LootCondition;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootTableParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

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
    private MinecraftServer cachedServer = null;

    private EntityLootRegistry() {}

    public static EntityLootRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Kill requirement for entity loot drops.
     */
    public enum KillRequirement {
        /** At least one entry requires player kill (killed_by_player condition) */
        PLAYER_KILL,
        /** At least one entry benefits from looting enchant (random_chance_with_looting or looting_enchant) */
        LOOTING_DEPENDENT,
        /** Has both player kill requirements and looting bonuses */
        PLAYER_KILL_WITH_LOOTING,
        /** No special kill conditions detected */
        NONE,
        /** Could not analyze (table not parseable) */
        UNKNOWN
    }

    /**
     * Initialize by scanning entity loot tables from LootTableRegistry.
     * Call this after LootTableRegistry is scanned.
     */
    public void initialize() {
        if (initialized) return;
        entities.clear();
        lootTableToEntity.clear();

        // Get server for parsing loot tables
        cachedServer = RegistryScanner.getCurrentServer();

        LootTableRegistry lootTables = LootTableRegistry.getInstance();
        if (!lootTables.isScanned()) {
            Isotope.LOGGER.warn("EntityLootRegistry: LootTableRegistry not scanned yet - will initialize with limited data");
        }

        int analyzedCount = 0;
        int fallbackCount = 0;

        // Find all entity loot tables (path starts with "entities/")
        for (LootTableInfo tableInfo : lootTables.getAll()) {
            String path = tableInfo.path();
            if (path.startsWith("entities/") || path.startsWith("entity/")) {
                // Extract entity ID from path: "entities/zombie" -> "minecraft:zombie"
                String entityPath = path.startsWith("entities/")
                    ? path.substring("entities/".length())
                    : path.substring("entity/".length());

                ResourceLocation entityId = RegistryHelper.fromNamespaceAndPath(
                    tableInfo.namespace(), entityPath);
                ResourceLocation lootTableId = tableInfo.id();

                // Get display name from entity registry if available
                String displayName = getEntityDisplayName(entityId);

                // Analyze kill requirements by parsing actual loot table JSON
                KillRequirement killReq = analyzeKillRequirements(lootTableId);
                if (killReq != KillRequirement.UNKNOWN) {
                    analyzedCount++;
                } else {
                    fallbackCount++;
                }

                EntityLootInfo info = new EntityLootInfo(
                    entityId,
                    lootTableId,
                    displayName,
                    killReq
                );

                entities.put(entityId, info);
                lootTableToEntity.put(lootTableId, entityId);
            }
        }

        initialized = true;
        Isotope.LOGGER.info("EntityLootRegistry: initialized {} entity loot tables ({} analyzed, {} fallback)",
            entities.size(), analyzedCount, fallbackCount);

        // Log breakdown by kill requirement
        Map<KillRequirement, Long> byReq = entities.values().stream()
            .collect(Collectors.groupingBy(EntityLootInfo::killRequirement, Collectors.counting()));
        byReq.forEach((req, count) ->
            Isotope.LOGGER.debug("  {} entities with {}", count, req));

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
            var entityTypeOpt = RegistryHelper.getEntityType(entityId);
            if (entityTypeOpt.isPresent()) {
                // Get the translation key and format it nicely
                String key = entityTypeOpt.get().getDescriptionId();
                // Extract the entity name from "entity.minecraft.zombie" -> "Zombie"
                String[] parts = key.split("\\.");
                if (parts.length > 0) {
                    String name = parts[parts.length - 1];
                    return formatDisplayName(name);
                }
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to get display name for {}: {}", entityId, e.getMessage());
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
     * Analyze a loot table to determine kill requirements.
     * Parses the actual JSON to look for killed_by_player, random_chance_with_looting,
     * and looting_enchant conditions/functions.
     *
     * @param lootTableId The loot table to analyze
     * @return The determined kill requirement
     */
    private KillRequirement analyzeKillRequirements(ResourceLocation lootTableId) {
        if (cachedServer == null) {
            Isotope.LOGGER.debug("No server available, cannot analyze {}", lootTableId);
            return KillRequirement.UNKNOWN;
        }

        try {
            Optional<LootTableStructure> structureOpt = LootTableParser.parse(cachedServer, lootTableId);
            if (structureOpt.isEmpty()) {
                Isotope.LOGGER.debug("Could not parse loot table for analysis: {}", lootTableId);
                return KillRequirement.UNKNOWN;
            }

            LootTableStructure structure = structureOpt.get();
            boolean hasPlayerKill = false;
            boolean hasLootingBonus = false;

            // Check all pools and entries for conditions
            for (LootPool pool : structure.pools()) {
                // Check pool-level conditions
                for (LootCondition cond : pool.conditions()) {
                    if (isPlayerKillCondition(cond)) hasPlayerKill = true;
                    if (isLootingCondition(cond)) hasLootingBonus = true;
                }

                // Check entry-level conditions and functions
                for (LootEntry entry : pool.entries()) {
                    for (LootCondition cond : entry.conditions()) {
                        if (isPlayerKillCondition(cond)) hasPlayerKill = true;
                        if (isLootingCondition(cond)) hasLootingBonus = true;
                    }

                    // Check for looting_enchant function
                    for (var func : entry.functions()) {
                        if (isLootingFunction(func)) hasLootingBonus = true;
                    }

                    // Recursively check children for composite entries
                    if (entry.isComposite()) {
                        var childResult = analyzeEntryChildren(entry.children());
                        if (childResult.playerKill) hasPlayerKill = true;
                        if (childResult.looting) hasLootingBonus = true;
                    }
                }
            }

            // Determine the combined result
            if (hasPlayerKill && hasLootingBonus) {
                return KillRequirement.PLAYER_KILL_WITH_LOOTING;
            } else if (hasPlayerKill) {
                return KillRequirement.PLAYER_KILL;
            } else if (hasLootingBonus) {
                return KillRequirement.LOOTING_DEPENDENT;
            } else {
                return KillRequirement.NONE;
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("Error analyzing loot table {}: {}", lootTableId, e.getMessage());
            return KillRequirement.UNKNOWN;
        }
    }

    /**
     * Helper record for tracking analysis results.
     */
    private record AnalysisResult(boolean playerKill, boolean looting) {}

    /**
     * Recursively analyze children of composite entries.
     */
    private AnalysisResult analyzeEntryChildren(List<LootEntry> children) {
        boolean hasPlayerKill = false;
        boolean hasLooting = false;

        for (LootEntry child : children) {
            for (LootCondition cond : child.conditions()) {
                if (isPlayerKillCondition(cond)) hasPlayerKill = true;
                if (isLootingCondition(cond)) hasLooting = true;
            }
            for (var func : child.functions()) {
                if (isLootingFunction(func)) hasLooting = true;
            }
            if (child.isComposite()) {
                var nested = analyzeEntryChildren(child.children());
                if (nested.playerKill) hasPlayerKill = true;
                if (nested.looting) hasLooting = true;
            }
        }

        return new AnalysisResult(hasPlayerKill, hasLooting);
    }

    /**
     * Check if a condition is a player kill requirement.
     */
    private boolean isPlayerKillCondition(LootCondition cond) {
        String type = cond.condition();
        return "minecraft:killed_by_player".equals(type) || "killed_by_player".equals(type);
    }

    /**
     * Check if a condition provides looting bonuses.
     */
    private boolean isLootingCondition(LootCondition cond) {
        String type = cond.condition();
        return "minecraft:random_chance_with_looting".equals(type) ||
               "random_chance_with_looting".equals(type);
    }

    /**
     * Check if a function provides looting bonuses.
     */
    private boolean isLootingFunction(dev.isotope.data.loot.LootFunction func) {
        String type = func.function();
        return "minecraft:looting_enchant".equals(type) || "looting_enchant".equals(type);
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
     * Get entities by loot table ID prefix.
     * Useful for mod-specific filtering (e.g., "alexsmobs:" to find all Alex's Mobs entities).
     *
     * @param prefix The loot table ID prefix to match (e.g., "alexsmobs:", "minecraft:entities/")
     * @return List of matching EntityLootInfo
     */
    public List<EntityLootInfo> getByLootTableIdPrefix(String prefix) {
        return entities.values().stream()
            .filter(e -> e.lootTableId().toString().startsWith(prefix))
            .toList();
    }

    /**
     * Get entities filtered by kill requirement.
     */
    public List<EntityLootInfo> getByKillRequirement(KillRequirement requirement) {
        return entities.values().stream()
            .filter(e -> e.killRequirement() == requirement)
            .toList();
    }

    /**
     * Get all entities that require or benefit from player kills.
     * Includes PLAYER_KILL, LOOTING_DEPENDENT, and PLAYER_KILL_WITH_LOOTING.
     */
    public List<EntityLootInfo> getEntitiesWithSpecialDrops() {
        return entities.values().stream()
            .filter(e -> e.killRequirement() != KillRequirement.NONE &&
                         e.killRequirement() != KillRequirement.UNKNOWN)
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
        cachedServer = null;
        initialized = false;
    }

    /**
     * Entity loot information.
     */
    public record EntityLootInfo(
        ResourceLocation entityId,
        ResourceLocation lootTableId,
        String displayName,
        KillRequirement killRequirement
    ) {
        /**
         * Check if this entity requires player kill for some drops.
         */
        public boolean requiresPlayerKill() {
            return killRequirement == KillRequirement.PLAYER_KILL ||
                   killRequirement == KillRequirement.PLAYER_KILL_WITH_LOOTING;
        }

        /**
         * Check if this entity benefits from looting enchant.
         */
        public boolean benefitsFromLooting() {
            return killRequirement == KillRequirement.LOOTING_DEPENDENT ||
                   killRequirement == KillRequirement.PLAYER_KILL_WITH_LOOTING;
        }

        /**
         * Convert to a LootSource for unified handling.
         */
        public LootSource toLootSource() {
            String description = switch (killRequirement) {
                case PLAYER_KILL -> "Mob drops (player kill required for rare items)";
                case LOOTING_DEPENDENT -> "Mob drops (looting enchant increases drops)";
                case PLAYER_KILL_WITH_LOOTING -> "Mob drops (player kill + looting for best drops)";
                case NONE, UNKNOWN -> "Mob drops";
            };
            return LootSource.mob(entityId, displayName, description);
        }
    }
}
