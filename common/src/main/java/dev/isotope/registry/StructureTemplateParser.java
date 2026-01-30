package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.compat.Id;
import dev.isotope.compat.RegistryCompat;
import dev.isotope.util.Regs;
import dev.isotope.data.StructureLootLink;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

/**
 * Parses structure templates (.nbt files) to extract loot table references.
 *
 * This is Layer 2 of the "No Silent Failure" model - deterministic parsing
 * of structure templates to find loot tables referenced in block entities.
 *
 * Handles:
 * - Single-piece structures (direct .nbt files)
 * - Jigsaw structures (template pools with multiple pieces)
 * - Recursive template pool resolution
 */
public final class StructureTemplateParser {

    private static final StructureTemplateParser INSTANCE = new StructureTemplateParser();

    // Results: structure ID -> set of loot table IDs found in templates
    private final Map<Id, Set<Id>> structureToLootTables = new LinkedHashMap<>();

    // Reverse index: loot table ID -> set of structures that reference it
    private final Map<Id, Set<Id>> lootTableToStructures = new LinkedHashMap<>();

    // Template -> loot tables mapping (for detailed view)
    private final Map<Id, Set<Id>> templateToLootTables = new LinkedHashMap<>();

    // Structure -> spawner entity loot tables (from SpawnerEntityExtractor)
    private final Map<Id, Set<Id>> structureToSpawnerLoot = new LinkedHashMap<>();

    // Parsing stats
    private int templatesScanned = 0;
    private int lootReferencesFound = 0;
    private int spawnerEntitiesFound = 0;
    private boolean parsed = false;

    private StructureTemplateParser() {}

    public static StructureTemplateParser getInstance() {
        return INSTANCE;
    }

    /**
     * Parse all structure templates to extract loot table references.
     */
    public void parse(MinecraftServer server) {
        clear();
        SpawnerEntityExtractor.getInstance().clear();

        StructureTemplateManager templateManager = server.getStructureManager();
        Registry<Structure> structureRegistry = RegistryCompat.lookupRegistry(
            server.registryAccess(), Registries.STRUCTURE);
        Registry<StructureTemplatePool> poolRegistry = RegistryCompat.lookupRegistry(
            server.registryAccess(), Registries.TEMPLATE_POOL);

        if (structureRegistry == null || poolRegistry == null) {
            Isotope.LOGGER.error("[TemplateParser] Could not access required registries");
            return;
        }

        Isotope.LOGGER.info("[TemplateParser] Starting structure template parsing (including spawner analysis)...");

        // Process each registered structure
        for (var entry : structureRegistry.entrySet()) {
            Id structureId = Id.fromKey(entry.getKey());
            Structure structure = entry.getValue();

            Set<Id> lootTables = new HashSet<>();

            try {
                // Check if this is a jigsaw structure (has template pools)
                if (structure instanceof JigsawStructure jigsawStructure) {
                    lootTables.addAll(parseJigsawStructure(structureId, jigsawStructure, templateManager, poolRegistry));
                } else {
                    // Try to find templates by naming convention
                    lootTables.addAll(parseByNamingConvention(structureId, templateManager));
                }

                if (!lootTables.isEmpty()) {
                    structureToLootTables.put(structureId, lootTables);

                    // Update reverse index
                    for (Id tableId : lootTables) {
                        lootTableToStructures
                            .computeIfAbsent(tableId, k -> new HashSet<>())
                            .add(structureId);
                    }

                    lootReferencesFound += lootTables.size();
                }

                // Get spawner entity loot tables for this structure
                Set<Id> spawnerLootTables =
                    SpawnerEntityExtractor.getInstance().getSpawnerLootTablesForStructure(structureId);
                if (!spawnerLootTables.isEmpty()) {
                    structureToSpawnerLoot.put(structureId, spawnerLootTables);
                    spawnerEntitiesFound += spawnerLootTables.size();
                }
            } catch (Exception e) {
                Isotope.LOGGER.debug("[TemplateParser] Error parsing structure {}: {}",
                    structureId, e.getMessage());
            }
        }

        parsed = true;
        SpawnerEntityExtractor.Stats spawnerStats = SpawnerEntityExtractor.getInstance().getStats();
        Isotope.LOGGER.info("[TemplateParser] Parsed {} templates, found {} loot refs, {} spawner entity loot tables across {} structures",
            templatesScanned, lootReferencesFound, spawnerEntitiesFound, structureToLootTables.size());
        if (spawnerStats.spawnersFound() > 0) {
            Isotope.LOGGER.info("[TemplateParser] Spawner analysis: {}", spawnerStats.summary());
        }
    }

    /**
     * Parse a jigsaw structure by traversing its template pools.
     */
    private Set<Id> parseJigsawStructure(
            Id structureId,
            JigsawStructure structure,
            StructureTemplateManager templateManager,
            Registry<StructureTemplatePool> poolRegistry) {

        Set<Id> lootTables = new HashSet<>();
        Set<Id> visitedPools = new HashSet<>();

        try {
            // Get the start pool - use reflection to handle API changes across versions
            Holder<StructureTemplatePool> startPoolHolder = null;
            try {
                // Try direct field access (1.21.4+)
                var field = JigsawStructure.class.getDeclaredField("startPool");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var holder = (Holder<StructureTemplatePool>) field.get(structure);
                startPoolHolder = holder;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // Field not accessible, skip
            }

            if (startPoolHolder != null && startPoolHolder.isBound()) {
                Id poolId = startPoolHolder.unwrapKey()
                    .map(Id::fromKey)
                    .orElse(null);

                if (poolId != null) {
                    lootTables.addAll(parseTemplatePoolRecursive(
                        structureId, poolId, templateManager, poolRegistry, visitedPools));
                }
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("[TemplateParser] Error parsing jigsaw structure: {}", e.getMessage());
        }

        return lootTables;
    }

    /**
     * Recursively parse a template pool and all pools it references.
     */
    private Set<Id> parseTemplatePoolRecursive(
            Id structureId,
            Id poolId,
            StructureTemplateManager templateManager,
            Registry<StructureTemplatePool> poolRegistry,
            Set<Id> visitedPools) {

        // Avoid infinite recursion
        if (visitedPools.contains(poolId)) {
            return Set.of();
        }
        visitedPools.add(poolId);

        Set<Id> lootTables = new HashSet<>();

        try {
            Optional<StructureTemplatePool> poolOpt = Regs.getOptional(poolRegistry, Registries.TEMPLATE_POOL, poolId);
            if (poolOpt.isEmpty()) {
                return lootTables;
            }

            StructureTemplatePool pool = poolOpt.get();

            // Get all templates from this pool using getShuffledTemplates
            // This returns the weighted random elements
            try {
                var elements = pool.getShuffledTemplates(net.minecraft.util.RandomSource.create());
                for (var element : elements) {
                    try {
                        // Try to get the template location from the element
                        Set<Id> templateLocations = extractTemplateLocations(element);

                        for (Id templateLoc : templateLocations) {
                            lootTables.addAll(parseTemplate(structureId, templateLoc, templateManager));
                        }
                    } catch (Exception e) {
                        // Skip elements that can't be parsed
                    }
                }
            } catch (Exception e) {
                Isotope.LOGGER.debug("[TemplateParser] Error getting pool elements: {}", e.getMessage());
            }

            // Check fallback pool
            if (pool.getFallback().isBound()) {
                Id fallbackId = pool.getFallback().unwrapKey()
                    .map(Id::fromKey)
                    .orElse(null);

                if (fallbackId != null && !fallbackId.equals(poolId)) {
                    lootTables.addAll(parseTemplatePoolRecursive(
                        structureId, fallbackId, templateManager, poolRegistry, visitedPools));
                }
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("[TemplateParser] Error parsing pool {}: {}", poolId, e.getMessage());
        }

        return lootTables;
    }

    /**
     * Extract template locations from a pool element.
     * Uses reflection as a fallback for accessing template references.
     */
    private Set<Id> extractTemplateLocations(
            net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement element) {

        Set<Id> locations = new HashSet<>();

        try {
            // Try to extract template location using reflection
            // This handles different element types across MC versions
            Class<?> clazz = element.getClass();

            // Look for 'template' field (SinglePoolElement)
            try {
                var templateField = findField(clazz, "template");
                if (templateField != null) {
                    templateField.setAccessible(true);
                    Object templateHolder = templateField.get(element);
                    if (templateHolder instanceof net.minecraft.core.Holder<?> holder) {
                        holder.unwrapKey()
                            .map(key -> Id.fromKey((ResourceKey<?>) key))
                            .ifPresent(locations::add);
                    }
                }
            } catch (Exception ignored) {}

            // Look for 'elements' field (ListPoolElement)
            try {
                var elementsField = findField(clazz, "elements");
                if (elementsField != null) {
                    elementsField.setAccessible(true);
                    Object elements = elementsField.get(element);
                    if (elements instanceof List<?> list) {
                        for (Object subElement : list) {
                            if (subElement instanceof net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement poolElement) {
                                locations.addAll(extractTemplateLocations(poolElement));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            // Element type not supported
        }

        return locations;
    }

    /**
     * Find a field by name, checking superclasses too.
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Parse a single structure template to extract loot table references.
     */
    private Set<Id> parseTemplate(
            Id structureId,
            Id templateLocation,
            StructureTemplateManager templateManager) {

        Set<Id> lootTables = new HashSet<>();

        try {
            Optional<StructureTemplate> templateOpt = templateManager.get(templateLocation.mc());
            if (templateOpt.isEmpty()) {
                return lootTables;
            }

            templatesScanned++;
            StructureTemplate template = templateOpt.get();

            // Get block entities from the template
            // StructureTemplate stores block entity NBT in palettes
            lootTables.addAll(extractLootTablesFromTemplate(structureId, template));

            if (!lootTables.isEmpty()) {
                templateToLootTables.put(templateLocation, lootTables);
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("[TemplateParser] Error parsing template {}: {}",
                templateLocation, e.getMessage());
        }

        return lootTables;
    }

    /**
     * Extract loot table references from a structure template.
     * Uses NBT serialization (public API) to access template data.
     * Also analyzes spawner blocks for entity loot tables.
     */
    private Set<Id> extractLootTablesFromTemplate(Id structureId, StructureTemplate template) {
        Set<Id> lootTables = new HashSet<>();

        try {
            // Serialize template to NBT - this is the public API
            CompoundTag templateNbt = template.save(new CompoundTag());

            // Extract loot tables from the serialized NBT
            // Structure: { blocks: [...], entities: [...], palettes: [...] }
            extractLootTablesFromNbt(templateNbt, lootTables);

            // Also analyze spawner blocks for entity types
            SpawnerEntityExtractor.getInstance().extractFromTemplate(structureId, templateNbt);

        } catch (Exception e) {
            Isotope.LOGGER.debug("[TemplateParser] Error extracting loot tables: {}", e.getMessage());
        }

        return lootTables;
    }

    /**
     * Recursively extract loot table references from NBT data.
     */
    private void extractLootTablesFromNbt(CompoundTag nbt, Set<Id> lootTables) {
        // Check for LootTable field (used by chests, barrels, etc.)
        dev.isotope.compat.NbtCompat.getString(nbt, "LootTable").ifPresent(lootTableStr -> {
            try {
                Id lootTableId = Id.parse(lootTableStr);
                lootTables.add(lootTableId);
            } catch (Exception e) {
                // Invalid resource location
            }
        });

        // Check for loot_table field (alternative naming)
        dev.isotope.compat.NbtCompat.getString(nbt, "loot_table").ifPresent(lootTableStr -> {
            try {
                Id lootTableId = Id.parse(lootTableStr);
                lootTables.add(lootTableId);
            } catch (Exception e) {
                // Invalid resource location
            }
        });

        // Recursively check nested compound tags
        for (String key : dev.isotope.compat.NbtCompat.keySet(nbt)) {
            Tag tag = nbt.get(key);
            if (tag instanceof CompoundTag compound) {
                extractLootTablesFromNbt(compound, lootTables);
            } else if (tag instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag compound) {
                        extractLootTablesFromNbt(compound, lootTables);
                    }
                }
            }
        }
    }

    /**
     * Try to find templates for non-jigsaw structures by naming convention.
     * e.g., minecraft:igloo -> data/minecraft/structures/igloo/*.nbt
     */
    private Set<Id> parseByNamingConvention(
            Id structureId,
            StructureTemplateManager templateManager) {

        Set<Id> lootTables = new HashSet<>();

        // Common patterns for structure template locations
        String[] patterns = {
            structureId.getPath(),
            structureId.getPath() + "/base",
            structureId.getPath() + "/main",
            structureId.getPath().replace("_", "/")
        };

        for (String pattern : patterns) {
            Id templateLoc = Id.of(structureId.getNamespace(), pattern);

            lootTables.addAll(parseTemplate(structureId, templateLoc, templateManager));
        }

        return lootTables;
    }

    // --- Query API ---

    /**
     * Get loot tables found in templates for a structure.
     */
    public Set<Id> getLootTablesForStructure(Id structureId) {
        return structureToLootTables.getOrDefault(structureId, Set.of());
    }

    /**
     * Get structures that reference a loot table in their templates.
     */
    public Set<Id> getStructuresForLootTable(Id lootTableId) {
        return lootTableToStructures.getOrDefault(lootTableId, Set.of());
    }

    /**
     * Get all structures that have template-based loot links.
     */
    public Set<Id> getStructuresWithTemplateLinks() {
        return Collections.unmodifiableSet(structureToLootTables.keySet());
    }

    /**
     * Get all loot tables found in templates.
     */
    public Set<Id> getLootTablesInTemplates() {
        return Collections.unmodifiableSet(lootTableToStructures.keySet());
    }

    /**
     * Get spawner entity loot tables for a structure.
     * These are entity loot tables linked to entities found in spawner blocks.
     */
    public Set<Id> getSpawnerLootTablesForStructure(Id structureId) {
        return structureToSpawnerLoot.getOrDefault(structureId, Set.of());
    }

    /**
     * Get all structures with spawner entity loot.
     */
    public Set<Id> getStructuresWithSpawnerLoot() {
        return Collections.unmodifiableSet(structureToSpawnerLoot.keySet());
    }

    /**
     * Get the SpawnerEntityExtractor for direct access to spawner data.
     */
    public SpawnerEntityExtractor getSpawnerExtractor() {
        return SpawnerEntityExtractor.getInstance();
    }

    /**
     * Create StructureLootLink objects from parsed data.
     */
    public List<StructureLootLink> createLinks() {
        List<StructureLootLink> links = new ArrayList<>();

        for (var entry : structureToLootTables.entrySet()) {
            Id structureId = entry.getKey();
            for (Id lootTableId : entry.getValue()) {
                links.add(StructureLootLink.fromTemplate(structureId, lootTableId));
            }
        }

        return links;
    }

    /**
     * Check if parsing has been completed.
     */
    public boolean isParsed() {
        return parsed;
    }

    /**
     * Get parsing statistics.
     */
    public ParseStats getStats() {
        SpawnerEntityExtractor.Stats spawnerStats = SpawnerEntityExtractor.getInstance().getStats();
        return new ParseStats(
            templatesScanned,
            lootReferencesFound,
            structureToLootTables.size(),
            lootTableToStructures.size(),
            spawnerStats.spawnersFound(),
            spawnerStats.entitiesExtracted(),
            structureToSpawnerLoot.size()
        );
    }

    /**
     * Clear all parsed data.
     */
    public void clear() {
        structureToLootTables.clear();
        lootTableToStructures.clear();
        templateToLootTables.clear();
        structureToSpawnerLoot.clear();
        templatesScanned = 0;
        lootReferencesFound = 0;
        spawnerEntitiesFound = 0;
        parsed = false;
    }

    /**
     * Parsing statistics.
     */
    public record ParseStats(
        int templatesScanned,
        int lootReferencesFound,
        int structuresWithLinks,
        int lootTablesReferenced,
        int spawnersFound,
        int spawnerEntitiesFound,
        int structuresWithSpawners
    ) {}
}
