package dev.isotope.analysis;

import dev.isotope.data.loot.*;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Calculates drop rates and probabilities for loot table entries.
 */
public final class DropRateCalculator {

    private DropRateCalculator() {}

    /**
     * Calculated drop rate for an item.
     */
    public record DropRate(
        ResourceLocation item,
        float probability,    // 0.0 to 1.0
        float avgCount,       // Average items per roll
        int weight,
        int poolIndex,
        int entryIndex
    ) {
        public float percentChance() {
            return probability * 100f;
        }
    }

    /**
     * Pool statistics.
     */
    public record PoolStats(
        int poolIndex,
        float avgRolls,
        int totalWeight,
        float expectedItemsPerRoll,
        List<DropRate> rates
    ) {}

    /**
     * Calculate drop rates for all entries in a loot table.
     */
    public static List<DropRate> calculate(LootTableStructure table) {
        List<DropRate> allRates = new ArrayList<>();

        for (int poolIdx = 0; poolIdx < table.pools().size(); poolIdx++) {
            LootPool pool = table.pools().get(poolIdx);
            allRates.addAll(calculatePool(pool, poolIdx));
        }

        // Sort by probability (highest first)
        allRates.sort(Comparator.comparing(DropRate::probability).reversed());

        return allRates;
    }

    /**
     * Calculate drop rates for a single pool.
     */
    public static List<DropRate> calculatePool(LootPool pool, int poolIndex) {
        List<DropRate> rates = new ArrayList<>();

        // Calculate total weight
        int totalWeight = 0;
        for (LootEntry entry : pool.entries()) {
            totalWeight += entry.weight();
        }

        if (totalWeight == 0) {
            return rates;
        }

        // Calculate probability for each entry
        for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
            LootEntry entry = pool.entries().get(entryIdx);

            if (entry.name().isEmpty()) {
                continue; // Skip non-item entries
            }

            float probability = (float) entry.weight() / totalWeight;
            float avgCount = calculateAverageCount(entry);

            rates.add(new DropRate(
                entry.name().get(),
                probability,
                avgCount,
                entry.weight(),
                poolIndex,
                entryIdx
            ));
        }

        return rates;
    }

    /**
     * Calculate pool statistics.
     */
    public static PoolStats calculatePoolStats(LootPool pool, int poolIndex) {
        float avgRolls = getAverageRolls(pool.rolls());

        int totalWeight = 0;
        for (LootEntry entry : pool.entries()) {
            totalWeight += entry.weight();
        }

        List<DropRate> rates = calculatePool(pool, poolIndex);

        float expectedItems = 0;
        for (DropRate rate : rates) {
            expectedItems += rate.probability() * rate.avgCount();
        }
        expectedItems *= avgRolls;

        return new PoolStats(poolIndex, avgRolls, totalWeight, expectedItems, rates);
    }

    /**
     * Calculate the average count from set_count functions.
     */
    private static float calculateAverageCount(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            String funcName = func.function();
            if (funcName.equals("minecraft:set_count") || funcName.equals("set_count")) {
                if (func.parameters().has("count")) {
                    return parseAverageFromNumberProvider(func.parameters().get("count"));
                }
            }
        }
        return 1.0f; // Default count
    }

    /**
     * Parse average value from a number provider JSON element.
     */
    private static float parseAverageFromNumberProvider(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsFloat();
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                float min = obj.get("min").getAsFloat();
                float max = obj.get("max").getAsFloat();
                return (min + max) / 2f;
            }
            if (obj.has("n") && obj.has("p")) {
                // Binomial: average = n * p
                float n = obj.get("n").getAsFloat();
                float p = obj.get("p").getAsFloat();
                return n * p;
            }
        }
        return 1.0f;
    }

    /**
     * Get average rolls from a NumberProvider.
     */
    private static float getAverageRolls(NumberProvider rolls) {
        return switch (rolls) {
            case NumberProvider.Constant c -> c.value();
            case NumberProvider.Uniform u -> (u.min() + u.max()) / 2f;
            case NumberProvider.Binomial b -> b.n() * b.p();
        };
    }

    /**
     * Get rarity color for a drop rate.
     */
    public static int getRarityColor(float probability) {
        if (probability >= 0.5f) {
            return 0xFF888888; // Common (gray)
        } else if (probability >= 0.2f) {
            return 0xFF55FF55; // Uncommon (green)
        } else if (probability >= 0.05f) {
            return 0xFF5555FF; // Rare (blue)
        } else if (probability >= 0.01f) {
            return 0xFFAA00AA; // Epic (purple)
        } else {
            return 0xFFFFAA00; // Legendary (gold)
        }
    }
}
