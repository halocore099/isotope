package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.observation.ObservationCorrelator;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.registry.StructureRegistry;
import dev.isotope.registry.StructureTemplateParser;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Detects orphaned loot tables and structures.
 *
 * Orphans are flagged, not hidden - this surfaces potential issues:
 * - Typos in loot table references
 * - Outdated datapacks
 * - Broken mod configurations
 * - Decorative structures (intentionally no loot)
 *
 * Part of the "No Silent Failure" model.
 */
public final class OrphanDetector {

    private static final OrphanDetector INSTANCE = new OrphanDetector();

    // Orphan results
    private final Set<Identifier> orphanLootTables = new LinkedHashSet<>();
    private final Set<Identifier> orphanStructures = new LinkedHashSet<>();

    // Runtime-only discoveries (loot tables seen at runtime but not in templates/heuristics)
    private final Set<Identifier> runtimeOnlyTables = new LinkedHashSet<>();

    // Detection stats
    private boolean detected = false;

    private OrphanDetector() {}

    public static OrphanDetector getInstance() {
        return INSTANCE;
    }

    /**
     * Run orphan detection.
     * Call this after linking is complete.
     */
    public OrphanReport detect() {
        clear();

        LootTableRegistry lootTables = LootTableRegistry.getInstance();
        StructureRegistry structures = StructureRegistry.getInstance();
        StructureLootLinker linker = StructureLootLinker.getInstance();
        StructureTemplateParser templateParser = StructureTemplateParser.getInstance();
        ObservationCorrelator correlator = ObservationCorrelator.getInstance();

        if (!lootTables.isScanned() || !structures.isScanned()) {
            Isotope.LOGGER.warn("[OrphanDetector] Cannot detect: registries not scanned");
            return OrphanReport.empty();
        }

        // Get all linked loot tables and structures
        Set<Identifier> linkedLootTables = linker.getLinkedLootTables();
        Set<Identifier> linkedStructures = linker.getLinkedStructures();

        // Get template-referenced loot tables
        Set<Identifier> templateLootTables = templateParser.isParsed()
            ? templateParser.getLootTablesInTemplates()
            : Set.of();

        // Get runtime-observed loot tables
        Set<Identifier> runtimeLootTables = correlator.getObservedLootTables();

        // Detect orphan loot tables
        // Focus on chest loot tables (most relevant for structure linking)
        for (LootTableInfo info : lootTables.getChestLootTables()) {
            Identifier tableId = info.id();

            boolean isLinked = linkedLootTables.contains(tableId);
            boolean isInTemplate = templateLootTables.contains(tableId);
            boolean isObservedRuntime = runtimeLootTables.contains(tableId);

            if (!isLinked && !isInTemplate && !isObservedRuntime) {
                orphanLootTables.add(tableId);
            }

            // Track runtime-only discoveries (not in templates or heuristics, but seen at runtime)
            if (isObservedRuntime && !isInTemplate && !isLinked) {
                runtimeOnlyTables.add(tableId);
            }
        }

        // Detect orphan structures (structures with no loot links)
        for (StructureInfo info : structures.getAll()) {
            Identifier structureId = info.id();

            if (!linkedStructures.contains(structureId)) {
                // Check if it's a known decorative structure (no chests expected)
                if (!isKnownDecorativeStructure(structureId)) {
                    orphanStructures.add(structureId);
                }
            }
        }

        detected = true;

        OrphanReport report = new OrphanReport(
            Collections.unmodifiableSet(new LinkedHashSet<>(orphanLootTables)),
            Collections.unmodifiableSet(new LinkedHashSet<>(orphanStructures)),
            Collections.unmodifiableSet(new LinkedHashSet<>(runtimeOnlyTables))
        );

        Isotope.LOGGER.info("[OrphanDetector] Detection complete: {} orphan loot tables, {} orphan structures, {} runtime-only discoveries",
            orphanLootTables.size(), orphanStructures.size(), runtimeOnlyTables.size());

        return report;
    }

    /**
     * Check if a structure is known to be decorative (no loot expected).
     */
    private boolean isKnownDecorativeStructure(Identifier structureId) {
        if (!"minecraft".equals(structureId.getNamespace())) {
            return false;
        }

        // These vanilla structures typically don't have loot chests
        Set<String> decorativeStructures = Set.of(
            "nether_fossil",
            "desert_well",
            "swamp_hut",  // Has witch, but no chest
            "fossil",
            "amethyst_geode"
        );

        return decorativeStructures.contains(structureId.getPath());
    }

    // --- Query API ---

    /**
     * Get all orphan loot tables (not linked to any structure).
     */
    public Set<Identifier> getOrphanLootTables() {
        return Collections.unmodifiableSet(orphanLootTables);
    }

    /**
     * Get all orphan structures (no loot table links).
     */
    public Set<Identifier> getOrphanStructures() {
        return Collections.unmodifiableSet(orphanStructures);
    }

    /**
     * Get loot tables discovered only at runtime.
     * These were not found via templates or heuristics.
     */
    public Set<Identifier> getRuntimeOnlyDiscoveries() {
        return Collections.unmodifiableSet(runtimeOnlyTables);
    }

    /**
     * Check if a loot table is orphaned.
     */
    public boolean isOrphanLootTable(Identifier tableId) {
        return orphanLootTables.contains(tableId);
    }

    /**
     * Check if a structure is orphaned.
     */
    public boolean isOrphanStructure(Identifier structureId) {
        return orphanStructures.contains(structureId);
    }

    /**
     * Check if detection has been run.
     */
    public boolean isDetected() {
        return detected;
    }

    /**
     * Clear all detection results.
     */
    public void clear() {
        orphanLootTables.clear();
        orphanStructures.clear();
        runtimeOnlyTables.clear();
        detected = false;
    }

    /**
     * Orphan detection report.
     */
    public record OrphanReport(
        Set<Identifier> orphanLootTables,
        Set<Identifier> orphanStructures,
        Set<Identifier> runtimeOnlyDiscoveries
    ) {
        public static OrphanReport empty() {
            return new OrphanReport(Set.of(), Set.of(), Set.of());
        }

        public boolean hasOrphans() {
            return !orphanLootTables.isEmpty() || !orphanStructures.isEmpty();
        }

        public boolean hasRuntimeDiscoveries() {
            return !runtimeOnlyDiscoveries.isEmpty();
        }

        public int totalOrphans() {
            return orphanLootTables.size() + orphanStructures.size();
        }

        /**
         * Get a summary string for logging/display.
         */
        public String summary() {
            StringBuilder sb = new StringBuilder();

            if (!orphanLootTables.isEmpty()) {
                sb.append(orphanLootTables.size()).append(" orphan loot table(s)");
            }

            if (!orphanStructures.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(orphanStructures.size()).append(" orphan structure(s)");
            }

            if (!runtimeOnlyDiscoveries.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(runtimeOnlyDiscoveries.size()).append(" runtime-only discovery(s)");
            }

            if (sb.length() == 0) {
                return "No orphans detected";
            }

            return sb.toString();
        }
    }

    /**
     * Reason why something might be orphaned.
     */
    public enum OrphanReason {
        NOT_LINKED("Not linked to any structure"),
        NOT_IN_TEMPLATE("Not found in any structure template"),
        NOT_OBSERVED("Not observed at runtime"),
        NO_LOOT_TABLES("Structure has no loot table links"),
        DECORATIVE("Structure is decorative (no loot expected)");

        private final String description;

        OrphanReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Get detailed orphan info for a loot table.
     */
    public OrphanInfo getLootTableOrphanInfo(Identifier tableId) {
        List<OrphanReason> reasons = new ArrayList<>();

        StructureLootLinker linker = StructureLootLinker.getInstance();
        StructureTemplateParser templateParser = StructureTemplateParser.getInstance();
        ObservationCorrelator correlator = ObservationCorrelator.getInstance();

        if (linker.getLinksForLootTable(tableId).isEmpty()) {
            reasons.add(OrphanReason.NOT_LINKED);
        }

        if (templateParser.isParsed() && !templateParser.getLootTablesInTemplates().contains(tableId)) {
            reasons.add(OrphanReason.NOT_IN_TEMPLATE);
        }

        if (!correlator.getObservedLootTables().contains(tableId)) {
            reasons.add(OrphanReason.NOT_OBSERVED);
        }

        return new OrphanInfo(tableId, reasons);
    }

    /**
     * Get detailed orphan info for a structure.
     */
    public OrphanInfo getStructureOrphanInfo(Identifier structureId) {
        List<OrphanReason> reasons = new ArrayList<>();

        StructureLootLinker linker = StructureLootLinker.getInstance();

        if (linker.getLinksForStructure(structureId).isEmpty()) {
            if (isKnownDecorativeStructure(structureId)) {
                reasons.add(OrphanReason.DECORATIVE);
            } else {
                reasons.add(OrphanReason.NO_LOOT_TABLES);
            }
        }

        return new OrphanInfo(structureId, reasons);
    }

    /**
     * Detailed orphan information.
     */
    public record OrphanInfo(
        Identifier id,
        List<OrphanReason> reasons
    ) {
        public boolean isOrphan() {
            return !reasons.isEmpty() && !reasons.contains(OrphanReason.DECORATIVE);
        }

        public boolean isDecorativeStructure() {
            return reasons.contains(OrphanReason.DECORATIVE);
        }
    }
}
