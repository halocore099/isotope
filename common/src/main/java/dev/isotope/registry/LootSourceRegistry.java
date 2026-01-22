package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.analysis.OrphanDetector;
import dev.isotope.data.LootSource;
import dev.isotope.data.LootSourceType;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified registry providing all loot sources (structures + features + mobs).
 *
 * This is the primary interface for UI components that need to display
 * all sources of loot tables, regardless of whether they are real structures,
 * fire-and-forget features like dungeons, or entity/mob drops.
 *
 * Call compile() after StructureRegistry, FeatureRegistry, and EntityLootRegistry are initialized.
 */
public final class LootSourceRegistry {

    private static final LootSourceRegistry INSTANCE = new LootSourceRegistry();

    private final Map<Identifier, LootSource> sources = new LinkedHashMap<>();
    private boolean compiled = false;

    private LootSourceRegistry() {}

    public static LootSourceRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Compile all loot sources from structures, features, and mobs.
     * Call this after StructureRegistry, FeatureRegistry, and EntityLootRegistry are initialized.
     */
    public void compile() {
        sources.clear();

        int structureCount = 0;
        int featureCount = 0;
        int mobCount = 0;

        // Add real structures first
        for (StructureInfo info : StructureRegistry.getInstance().getAll()) {
            LootSource source = LootSource.fromStructure(info);
            sources.put(source.id(), source);
            structureCount++;
        }

        // Add features (won't override since features have different IDs from structures)
        for (FeatureRegistry.FeatureDefinition feature : FeatureRegistry.getInstance().getAll()) {
            LootSource source = feature.toLootSource();
            // Only add if not already present (features shouldn't conflict with structures)
            if (!sources.containsKey(source.id())) {
                sources.put(source.id(), source);
                featureCount++;
            }
        }

        // Add mobs/entities
        for (EntityLootRegistry.EntityLootInfo entity : EntityLootRegistry.getInstance().getAll()) {
            LootSource source = entity.toLootSource();
            // Only add if not already present
            if (!sources.containsKey(source.id())) {
                sources.put(source.id(), source);
                mobCount++;
            }
        }

        compiled = true;
        Isotope.LOGGER.info("LootSourceRegistry: compiled {} total sources ({} structures, {} features, {} mobs)",
            sources.size(), structureCount, featureCount, mobCount);
    }

    /**
     * Get all loot sources.
     */
    public Collection<LootSource> getAll() {
        return Collections.unmodifiableCollection(sources.values());
    }

    /**
     * Get loot source by ID.
     */
    public Optional<LootSource> get(Identifier id) {
        return Optional.ofNullable(sources.get(id));
    }

    /**
     * Get loot sources filtered by namespace.
     */
    public List<LootSource> getByNamespace(String namespace) {
        if ("*".equals(namespace)) {
            return new ArrayList<>(sources.values());
        }
        return sources.values().stream()
            .filter(s -> s.namespace().equals(namespace))
            .toList();
    }

    /**
     * Get loot sources filtered by type.
     */
    public List<LootSource> getByType(LootSourceType type) {
        return sources.values().stream()
            .filter(s -> s.type() == type)
            .toList();
    }

    /**
     * Get all structures (excluding features).
     */
    public List<LootSource> getStructures() {
        return getByType(LootSourceType.STRUCTURE);
    }

    /**
     * Get all features (excluding structures).
     */
    public List<LootSource> getFeatures() {
        return getByType(LootSourceType.FEATURE);
    }

    /**
     * Get all mobs (entity loot sources).
     */
    public List<LootSource> getMobs() {
        return getByType(LootSourceType.MOB);
    }

    /**
     * Get all unique namespaces.
     */
    public Set<String> getNamespaces() {
        return sources.values().stream()
            .map(LootSource::namespace)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get count of sources per namespace.
     */
    public Map<String, Integer> getNamespaceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LootSource source : sources.values()) {
            counts.merge(source.namespace(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Get count of sources per type.
     */
    public Map<LootSourceType, Integer> getTypeCounts() {
        Map<LootSourceType, Integer> counts = new EnumMap<>(LootSourceType.class);
        for (LootSource source : sources.values()) {
            counts.merge(source.type(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Total source count.
     */
    public int size() {
        return sources.size();
    }

    /**
     * Check if registry has been compiled.
     */
    public boolean isCompiled() {
        return compiled;
    }

    /**
     * Reset the registry.
     */
    public void reset() {
        sources.clear();
        compiled = false;
    }

    /**
     * Dump comprehensive debug information about all loot tables and their linked sources.
     * Call this after compile() to output full mapping info to logs.
     */
    public void dumpDebugInfo() {
        Isotope.LOGGER.info("╔══════════════════════════════════════════════════════════════════╗");
        Isotope.LOGGER.info("║              ISOTOPE DEBUG DUMP - LOOT TABLE MAPPINGS            ║");
        Isotope.LOGGER.info("╚══════════════════════════════════════════════════════════════════╝");

        LootTableRegistry lootTables = LootTableRegistry.getInstance();
        StructureLootLinker linker = StructureLootLinker.getInstance();
        OrphanDetector orphans = OrphanDetector.getInstance();

        // === SECTION 1: Structures and their loot tables ===
        Isotope.LOGGER.info("");
        Isotope.LOGGER.info("┌─────────────────────────────────────────────────────────────────┐");
        Isotope.LOGGER.info("│ STRUCTURES → LOOT TABLES                                        │");
        Isotope.LOGGER.info("└─────────────────────────────────────────────────────────────────┘");

        List<LootSource> structures = new ArrayList<>(getByType(LootSourceType.STRUCTURE));
        structures.sort(Comparator.comparing(s -> s.id().toString()));

        for (LootSource structure : structures) {
            List<StructureLootLink> links = linker.getLinksForStructure(structure.id());
            if (links.isEmpty()) {
                Isotope.LOGGER.info("  {} (NO LOOT TABLES)", structure.id());
            } else {
                Isotope.LOGGER.info("  {} ({} tables)", structure.id(), links.size());
                for (StructureLootLink link : links) {
                    Isotope.LOGGER.info("    └─ {} [{}]", link.lootTableId(), link.confidence());
                }
            }
        }

        // === SECTION 2: Features and their loot tables ===
        Isotope.LOGGER.info("");
        Isotope.LOGGER.info("┌─────────────────────────────────────────────────────────────────┐");
        Isotope.LOGGER.info("│ FEATURES → LOOT TABLES                                          │");
        Isotope.LOGGER.info("└─────────────────────────────────────────────────────────────────┘");

        List<LootSource> features = new ArrayList<>(getByType(LootSourceType.FEATURE));
        features.sort(Comparator.comparing(s -> s.id().toString()));

        for (LootSource feature : features) {
            List<StructureLootLink> links = linker.getLinksForStructure(feature.id());
            if (links.isEmpty()) {
                Isotope.LOGGER.info("  {} (NO LOOT TABLES)", feature.id());
            } else {
                Isotope.LOGGER.info("  {} ({} tables)", feature.id(), links.size());
                for (StructureLootLink link : links) {
                    Isotope.LOGGER.info("    └─ {} [{}]", link.lootTableId(), link.confidence());
                }
            }
        }

        // === SECTION 3: Mobs and their loot tables ===
        Isotope.LOGGER.info("");
        Isotope.LOGGER.info("┌─────────────────────────────────────────────────────────────────┐");
        Isotope.LOGGER.info("│ MOBS → LOOT TABLES                                              │");
        Isotope.LOGGER.info("└─────────────────────────────────────────────────────────────────┘");

        List<LootSource> mobs = new ArrayList<>(getByType(LootSourceType.MOB));
        mobs.sort(Comparator.comparing(s -> s.id().toString()));

        for (LootSource mob : mobs) {
            // Mobs link directly to their loot table via their ID
            Isotope.LOGGER.info("  {} → entities/{}", mob.id(), mob.id().getPath());
        }

        // === SECTION 4: Loot tables grouped by category ===
        Isotope.LOGGER.info("");
        Isotope.LOGGER.info("┌─────────────────────────────────────────────────────────────────┐");
        Isotope.LOGGER.info("│ LOOT TABLES BY CATEGORY                                         │");
        Isotope.LOGGER.info("└─────────────────────────────────────────────────────────────────┘");

        Map<LootTableInfo.LootTableCategory, List<LootTableInfo>> byCategory = lootTables.getAll().stream()
            .collect(Collectors.groupingBy(LootTableInfo::category, TreeMap::new, Collectors.toList()));

        for (var entry : byCategory.entrySet()) {
            Isotope.LOGGER.info("");
            Isotope.LOGGER.info("  ── {} ({} tables) ──", entry.getKey(), entry.getValue().size());
            List<LootTableInfo> tables = entry.getValue();
            tables.sort(Comparator.comparing(t -> t.id().toString()));

            for (LootTableInfo table : tables) {
                List<StructureLootLink> links = linker.getLinksForLootTable(table.id());
                boolean isOrphan = orphans.isOrphanLootTable(table.id());

                if (links.isEmpty()) {
                    String orphanMark = isOrphan ? " [ORPHAN]" : "";
                    Isotope.LOGGER.info("    {} → (no source){}", table.id(), orphanMark);
                } else {
                    StringBuilder sourcesStr = new StringBuilder();
                    for (int i = 0; i < links.size(); i++) {
                        if (i > 0) sourcesStr.append(", ");
                        sourcesStr.append(links.get(i).structureId());
                    }
                    Isotope.LOGGER.info("    {} → {}", table.id(), sourcesStr);
                }
            }
        }

        // === SECTION 5: Orphan summary ===
        Isotope.LOGGER.info("");
        Isotope.LOGGER.info("┌─────────────────────────────────────────────────────────────────┐");
        Isotope.LOGGER.info("│ ORPHANS (no linked source)                                      │");
        Isotope.LOGGER.info("└─────────────────────────────────────────────────────────────────┘");

        Set<Identifier> orphanTables = orphans.getOrphanLootTables();
        Set<Identifier> orphanStructs = orphans.getOrphanStructures();

        if (orphanTables.isEmpty() && orphanStructs.isEmpty()) {
            Isotope.LOGGER.info("  No orphans detected!");
        } else {
            if (!orphanTables.isEmpty()) {
                Isotope.LOGGER.info("  Orphan Loot Tables ({}):", orphanTables.size());
                List<Identifier> sortedOrphanTables = new ArrayList<>(orphanTables);
                sortedOrphanTables.sort(Comparator.comparing(Identifier::toString));
                for (Identifier table : sortedOrphanTables) {
                    LootTableInfo info = lootTables.get(table).orElse(null);
                    String category = info != null ? info.category().toString() : "UNKNOWN";
                    Isotope.LOGGER.info("    └─ {} [{}]", table, category);
                }
            }

            if (!orphanStructs.isEmpty()) {
                Isotope.LOGGER.info("  Orphan Structures ({}):", orphanStructs.size());
                List<Identifier> sortedOrphanStructs = new ArrayList<>(orphanStructs);
                sortedOrphanStructs.sort(Comparator.comparing(Identifier::toString));
                for (Identifier struct : sortedOrphanStructs) {
                    Isotope.LOGGER.info("    └─ {}", struct);
                }
            }
        }

        Isotope.LOGGER.info("");
        Isotope.LOGGER.info("══════════════════════════════════════════════════════════════════");
        Isotope.LOGGER.info("  END OF DEBUG DUMP");
        Isotope.LOGGER.info("══════════════════════════════════════════════════════════════════");
    }
}
