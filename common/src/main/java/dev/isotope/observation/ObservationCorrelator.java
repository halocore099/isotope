package dev.isotope.observation;

import dev.isotope.Isotope;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Correlates structure placements with loot table invocations.
 *
 * This is the core logic that determines:
 * "When structure X was placed, loot tables Y and Z were invoked."
 *
 * This is NOT heuristic - it's based on actual observed runtime behavior.
 */
public final class ObservationCorrelator {

    private static final ObservationCorrelator INSTANCE = new ObservationCorrelator();

    // The final result: structure -> set of observed loot tables
    private final Map<ResourceLocation, Set<ResourceLocation>> structureToLootTables = new LinkedHashMap<>();

    // Reverse index: loot table -> set of structures that invoked it
    private final Map<ResourceLocation, Set<ResourceLocation>> lootTableToStructures = new LinkedHashMap<>();

    // Detailed observations per structure
    private final Map<ResourceLocation, StructureObservation> observations = new LinkedHashMap<>();

    private ObservationCorrelator() {}

    public static ObservationCorrelator getInstance() {
        return INSTANCE;
    }

    public void clear() {
        structureToLootTables.clear();
        lootTableToStructures.clear();
        observations.clear();
    }

    /**
     * Correlate all observed placements and loot invocations.
     * Call this after placement/observation phase is complete.
     */
    public CorrelationResult correlate() {
        clear();

        Collection<StructurePlacement> placements = StructureObserver.getInstance().getAllPlacements();
        Collection<LootInvocation> invocations = LootObserver.getInstance().getAllInvocations();

        Isotope.LOGGER.info("[Correlator] Correlating {} placements with {} loot invocations",
            placements.size(), invocations.size());

        int correlatedCount = 0;

        // For each structure placement, find loot invocations that occurred within its bounds
        for (StructurePlacement placement : placements) {
            Set<ResourceLocation> lootTables = new HashSet<>();
            List<LootInvocation> structureInvocations = new ArrayList<>();

            for (LootInvocation invocation : invocations) {
                // Check if this invocation occurred within the structure's bounds
                // Use a small buffer (16 blocks) to account for structure pieces
                if (placement.isNear(invocation.position(), 16)) {
                    lootTables.add(invocation.tableId());
                    structureInvocations.add(invocation);
                    correlatedCount++;
                }
            }

            if (!lootTables.isEmpty()) {
                ResourceLocation structureId = placement.structureId();

                // Store the correlation
                structureToLootTables.put(structureId, lootTables);

                // Update reverse index
                for (ResourceLocation tableId : lootTables) {
                    lootTableToStructures
                        .computeIfAbsent(tableId, k -> new HashSet<>())
                        .add(structureId);
                }

                // Store detailed observation
                observations.put(structureId, new StructureObservation(
                    placement,
                    lootTables,
                    structureInvocations
                ));
            }
        }

        Isotope.LOGGER.info("[Correlator] Correlated {} loot invocations to {} structures",
            correlatedCount, structureToLootTables.size());

        return new CorrelationResult(
            placements.size(),
            invocations.size(),
            correlatedCount,
            structureToLootTables.size()
        );
    }

    /**
     * Get the loot tables observed for a structure.
     * Returns empty set if structure wasn't observed to use any loot tables.
     */
    public Set<ResourceLocation> getLootTablesFor(ResourceLocation structureId) {
        return structureToLootTables.getOrDefault(structureId, Set.of());
    }

    /**
     * Get all structures that used a specific loot table.
     */
    public Set<ResourceLocation> getStructuresUsing(ResourceLocation lootTableId) {
        return lootTableToStructures.getOrDefault(lootTableId, Set.of());
    }

    /**
     * Get detailed observation data for a structure.
     */
    public Optional<StructureObservation> getObservation(ResourceLocation structureId) {
        return Optional.ofNullable(observations.get(structureId));
    }

    /**
     * Get all correlated structures.
     */
    public Set<ResourceLocation> getCorrelatedStructures() {
        return Collections.unmodifiableSet(structureToLootTables.keySet());
    }

    /**
     * Get all observed loot tables.
     */
    public Set<ResourceLocation> getObservedLootTables() {
        return Collections.unmodifiableSet(lootTableToStructures.keySet());
    }

    /**
     * Get all observations.
     */
    public Collection<StructureObservation> getAllObservations() {
        return Collections.unmodifiableCollection(observations.values());
    }

    /**
     * Detailed observation data for a single structure.
     */
    public record StructureObservation(
        StructurePlacement placement,
        Set<ResourceLocation> lootTables,
        List<LootInvocation> invocations
    ) {
        /**
         * Get unique items observed from this structure.
         */
        public Set<ResourceLocation> getObservedItems() {
            Set<ResourceLocation> items = new HashSet<>();
            for (LootInvocation inv : invocations) {
                items.addAll(inv.itemsGenerated());
            }
            return items;
        }

        /**
         * Get invocation count per loot table.
         */
        public Map<ResourceLocation, Integer> getInvocationCounts() {
            Map<ResourceLocation, Integer> counts = new HashMap<>();
            for (LootInvocation inv : invocations) {
                counts.merge(inv.tableId(), 1, Integer::sum);
            }
            return counts;
        }
    }

    /**
     * Summary result of correlation.
     */
    public record CorrelationResult(
        int totalPlacements,
        int totalInvocations,
        int correlatedInvocations,
        int structuresWithLoot
    ) {}
}
