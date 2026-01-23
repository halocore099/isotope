package dev.isotope.analysis;

import dev.isotope.compat.Id;
import dev.isotope.data.loot.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates loot table rolls to generate empirical drop distributions.
 *
 * Unlike DropRateCalculator which computes theoretical probabilities,
 * this class actually "rolls" the loot table many times to see real
 * distributions, accounting for edge cases and random variance.
 */
public class DropSimulator {

    /**
     * Result of a single item in the simulation.
     */
    public record ItemResult(
        Id item,
        int timesDropped,      // How many simulations this item appeared in
        int totalCount,        // Total items across all simulations
        float dropRate,        // timesDropped / totalSimulations
        float avgCountWhenDropped, // totalCount / timesDropped
        float theoreticalRate  // From DropRateCalculator for comparison
    ) {
        public float variance() {
            return Math.abs(dropRate - theoreticalRate);
        }
    }

    /**
     * Full simulation result.
     */
    public record SimulationResult(
        Id tableId,
        int simulationCount,
        long durationMs,
        Map<Id, ItemResult> items,
        float avgItemsPerRoll,
        int totalItemsGenerated
    ) {
        public List<ItemResult> sortedByDropRate() {
            List<ItemResult> sorted = new ArrayList<>(items.values());
            sorted.sort(Comparator.comparing(ItemResult::dropRate).reversed());
            return sorted;
        }

        public List<ItemResult> sortedByVariance() {
            List<ItemResult> sorted = new ArrayList<>(items.values());
            sorted.sort(Comparator.comparing(ItemResult::variance).reversed());
            return sorted;
        }
    }

    /**
     * Callback for progress updates during simulation.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int completed, int total);
    }

    /**
     * Run a simulation on a loot table.
     *
     * @param table The loot table structure to simulate
     * @param tableId The table ID for result tracking
     * @param simulationCount Number of times to roll the table
     * @param callback Optional progress callback
     * @return Simulation results
     */
    public static SimulationResult simulate(
            LootTableStructure table,
            Id tableId,
            int simulationCount,
            ProgressCallback callback) {

        long startTime = System.currentTimeMillis();

        // Get theoretical rates for comparison
        Map<Id, Float> theoreticalRates = new HashMap<>();
        for (DropRateCalculator.DropRate rate : DropRateCalculator.calculate(table)) {
            theoreticalRates.put(rate.item(), rate.probability());
        }

        // Track drops
        Map<Id, int[]> dropCounts = new HashMap<>(); // [timesDropped, totalCount]
        int totalItems = 0;

        // Run simulations
        for (int sim = 0; sim < simulationCount; sim++) {
            Set<Id> droppedThisRoll = new HashSet<>();

            // Roll each pool
            for (LootPool pool : table.pools()) {
                int rolls = rollNumberProvider(pool.rolls());

                for (int roll = 0; roll < rolls; roll++) {
                    LootEntry selected = selectEntry(pool);
                    if (selected != null && selected.name().isPresent()) {
                        Id item = selected.name().get();
                        int count = getItemCount(selected);

                        dropCounts.computeIfAbsent(item, k -> new int[2]);
                        if (!droppedThisRoll.contains(item)) {
                            dropCounts.get(item)[0]++; // timesDropped
                            droppedThisRoll.add(item);
                        }
                        dropCounts.get(item)[1] += count; // totalCount
                        totalItems += count;
                    }
                }
            }

            // Progress callback
            if (callback != null && sim % 100 == 0) {
                callback.onProgress(sim, simulationCount);
            }
        }

        // Build results
        Map<Id, ItemResult> items = new HashMap<>();
        for (var entry : dropCounts.entrySet()) {
            Id item = entry.getKey();
            int timesDropped = entry.getValue()[0];
            int totalCount = entry.getValue()[1];

            items.put(item, new ItemResult(
                item,
                timesDropped,
                totalCount,
                (float) timesDropped / simulationCount,
                timesDropped > 0 ? (float) totalCount / timesDropped : 0,
                theoreticalRates.getOrDefault(item, 0f)
            ));
        }

        long duration = System.currentTimeMillis() - startTime;

        return new SimulationResult(
            tableId,
            simulationCount,
            duration,
            items,
            (float) totalItems / simulationCount,
            totalItems
        );
    }

    /**
     * Roll a NumberProvider to get an integer value.
     */
    private static int rollNumberProvider(NumberProvider provider) {
        return switch (provider) {
            case NumberProvider.Constant c -> (int) c.value();
            case NumberProvider.Uniform u -> {
                int min = (int) u.min();
                int max = (int) u.max();
                yield min + ThreadLocalRandom.current().nextInt(max - min + 1);
            }
            case NumberProvider.Binomial b -> {
                int successes = 0;
                int n = (int) b.n();
                for (int i = 0; i < n; i++) {
                    if (ThreadLocalRandom.current().nextFloat() < b.p()) {
                        successes++;
                    }
                }
                yield successes;
            }
        };
    }

    /**
     * Select a random entry from a pool based on weights.
     */
    private static LootEntry selectEntry(LootPool pool) {
        List<LootEntry> entries = pool.entries();
        if (entries.isEmpty()) return null;

        int totalWeight = 0;
        for (LootEntry entry : entries) {
            totalWeight += entry.weight();
        }

        if (totalWeight <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (LootEntry entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry;
            }
        }

        return entries.get(entries.size() - 1);
    }

    /**
     * Get the item count from an entry's set_count function.
     */
    private static int getItemCount(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            String funcName = func.function();
            if (funcName.equals("minecraft:set_count") || funcName.equals("set_count")) {
                if (func.parameters().has("count")) {
                    return rollJsonNumberProvider(func.parameters().get("count"));
                }
            }
        }
        return 1;
    }

    /**
     * Roll a number provider from JSON.
     */
    private static int rollJsonNumberProvider(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsInt();
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                int min = obj.get("min").getAsInt();
                int max = obj.get("max").getAsInt();
                return min + ThreadLocalRandom.current().nextInt(max - min + 1);
            }
            if (obj.has("n") && obj.has("p")) {
                int n = obj.get("n").getAsInt();
                float p = obj.get("p").getAsFloat();
                int successes = 0;
                for (int i = 0; i < n; i++) {
                    if (ThreadLocalRandom.current().nextFloat() < p) {
                        successes++;
                    }
                }
                return successes;
            }
        }
        return 1;
    }
}
