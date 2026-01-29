package dev.isotope.analysis;

import dev.isotope.compat.EnchantmentCompat;
import dev.isotope.compat.Id;
import dev.isotope.data.loot.LootFunction;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates enchantment probabilities for loot functions.
 * Analyzes enchant_with_levels and enchant_randomly functions to determine
 * what enchantments can appear and their approximate chances.
 */
public class EnchantmentProbabilityCalculator {

    /**
     * Represents the chance of a specific enchantment appearing.
     */
    public record EnchantmentChance(
        Id enchantmentId,
        String displayName,
        int minLevel,
        int maxLevel,
        float probability,  // 0.0 - 1.0
        boolean isTreasure,
        int rarityWeight
    ) implements Comparable<EnchantmentChance> {
        @Override
        public int compareTo(EnchantmentChance other) {
            // Sort by probability descending, then by name
            int cmp = Float.compare(other.probability, this.probability);
            return cmp != 0 ? cmp : displayName.compareTo(other.displayName);
        }
    }

    /**
     * Analysis result for an enchantment function.
     */
    public record EnchantmentAnalysis(
        List<EnchantmentChance> enchantments,
        int levelMin,
        int levelMax,
        boolean includeTreasure,
        float avgEnchantmentsPerItem,
        String functionType
    ) {
        /**
         * Check if this analysis has any results.
         */
        public boolean hasResults() {
            return !enchantments.isEmpty();
        }

        /**
         * Get the top N enchantments by probability.
         */
        public List<EnchantmentChance> getTop(int n) {
            return enchantments.stream().limit(n).toList();
        }
    }

    /**
     * Analyze an enchant_with_levels function.
     * This simulates enchanting table behavior.
     */
    public static EnchantmentAnalysis analyzeEnchantWithLevels(
        LootFunction function,
        Id itemId,
        RegistryAccess registryAccess
    ) {
        if (!function.function().equals("minecraft:enchant_with_levels")) {
            return emptyAnalysis("Not an enchant_with_levels function");
        }

        var params = function.parameters();

        // Extract level range
        int minLevel = 1;
        int maxLevel = 30;
        if (params.has("levels")) {
            var levels = params.get("levels");
            if (levels.isJsonPrimitive()) {
                minLevel = levels.getAsInt();
                maxLevel = minLevel;
            } else if (levels.isJsonObject()) {
                var levelsObj = levels.getAsJsonObject();
                minLevel = levelsObj.has("min") ? levelsObj.get("min").getAsInt() : 1;
                maxLevel = levelsObj.has("max") ? levelsObj.get("max").getAsInt() : 30;
            }
        }

        // Extract treasure flag
        boolean treasure = params.has("treasure") && params.get("treasure").getAsBoolean();

        // Get applicable enchantments
        EnchantmentCompat compat = EnchantmentCompat.getInstance();
        List<Id> applicable = compat.getApplicableEnchantments(itemId, treasure, registryAccess);

        if (applicable.isEmpty()) {
            return new EnchantmentAnalysis(
                List.of(),
                minLevel,
                maxLevel,
                treasure,
                0,
                "enchant_with_levels"
            );
        }

        // Calculate total weight
        int totalWeight = 0;
        for (Id enchId : applicable) {
            // Only include discoverable enchantments for enchanting table
            if (compat.isDiscoverable(enchId, registryAccess)) {
                totalWeight += compat.getRarityWeight(enchId, registryAccess);
            }
        }

        // Calculate probabilities
        List<EnchantmentChance> chances = new ArrayList<>();
        for (Id enchId : applicable) {
            if (!compat.isDiscoverable(enchId, registryAccess)) {
                continue;
            }

            int weight = compat.getRarityWeight(enchId, registryAccess);
            float probability = totalWeight > 0 ? (float) weight / totalWeight : 0;

            // Estimate level range based on enchanting level
            int enchMinLevel = compat.getMinLevel(enchId, registryAccess);
            int enchMaxLevel = Math.min(
                compat.getMaxLevel(enchId, registryAccess),
                estimateMaxLevelFromEnchantingLevel(maxLevel, enchId, registryAccess)
            );

            chances.add(new EnchantmentChance(
                enchId,
                compat.getDisplayName(enchId, registryAccess),
                enchMinLevel,
                enchMaxLevel,
                probability,
                compat.isTreasure(enchId, registryAccess),
                weight
            ));
        }

        // Sort by probability
        chances.sort(Comparator.naturalOrder());

        // Estimate average enchantments per item (simplified)
        float avgEnchants = estimateAverageEnchantments(minLevel, maxLevel);

        return new EnchantmentAnalysis(
            chances,
            minLevel,
            maxLevel,
            treasure,
            avgEnchants,
            "enchant_with_levels"
        );
    }

    /**
     * Analyze an enchant_randomly function.
     * This applies a single random enchantment.
     */
    public static EnchantmentAnalysis analyzeEnchantRandomly(
        LootFunction function,
        Id itemId,
        RegistryAccess registryAccess
    ) {
        if (!function.function().equals("minecraft:enchant_randomly")) {
            return emptyAnalysis("Not an enchant_randomly function");
        }

        var params = function.parameters();

        // Check if specific enchantment options are provided
        if (params.has("options")) {
            // Specific enchantment pool - analyze those
            var options = params.get("options");
            if (options.isJsonArray()) {
                var optionsArray = options.getAsJsonArray();
                List<EnchantmentChance> chances = new ArrayList<>();
                float probability = optionsArray.size() > 0 ? 1.0f / optionsArray.size() : 0;

                EnchantmentCompat compat = EnchantmentCompat.getInstance();
                for (var element : optionsArray) {
                    String enchStr = element.isJsonPrimitive() ? element.getAsString() : "";
                    if (!enchStr.isEmpty()) {
                        Id enchId = Id.parse(enchStr);
                        chances.add(new EnchantmentChance(
                            enchId,
                            compat.getDisplayName(enchId, registryAccess),
                            compat.getMinLevel(enchId, registryAccess),
                            compat.getMaxLevel(enchId, registryAccess),
                            probability,
                            compat.isTreasure(enchId, registryAccess),
                            compat.getRarityWeight(enchId, registryAccess)
                        ));
                    }
                }

                chances.sort(Comparator.naturalOrder());
                return new EnchantmentAnalysis(
                    chances,
                    1,
                    1,
                    true, // Options can include treasure
                    1.0f,
                    "enchant_randomly"
                );
            }
        }

        // No specific options - use all applicable enchantments
        EnchantmentCompat compat = EnchantmentCompat.getInstance();
        List<Id> applicable = compat.getApplicableEnchantments(itemId, true, registryAccess);

        if (applicable.isEmpty()) {
            return new EnchantmentAnalysis(
                List.of(),
                1,
                1,
                true,
                1.0f,
                "enchant_randomly"
            );
        }

        // Equal probability for each enchantment
        float probability = 1.0f / applicable.size();

        List<EnchantmentChance> chances = new ArrayList<>();
        for (Id enchId : applicable) {
            chances.add(new EnchantmentChance(
                enchId,
                compat.getDisplayName(enchId, registryAccess),
                compat.getMinLevel(enchId, registryAccess),
                compat.getMaxLevel(enchId, registryAccess),
                probability,
                compat.isTreasure(enchId, registryAccess),
                compat.getRarityWeight(enchId, registryAccess)
            ));
        }

        chances.sort(Comparator.naturalOrder());

        return new EnchantmentAnalysis(
            chances,
            1,
            1,
            true,
            1.0f,
            "enchant_randomly"
        );
    }

    /**
     * Analyze any enchantment function.
     */
    public static EnchantmentAnalysis analyze(
        LootFunction function,
        Id itemId,
        RegistryAccess registryAccess
    ) {
        return switch (function.function()) {
            case "minecraft:enchant_with_levels" -> analyzeEnchantWithLevels(function, itemId, registryAccess);
            case "minecraft:enchant_randomly" -> analyzeEnchantRandomly(function, itemId, registryAccess);
            default -> emptyAnalysis("Not an enchantment function");
        };
    }

    /**
     * Estimate the max enchantment level achievable at a given enchanting level.
     */
    private static int estimateMaxLevelFromEnchantingLevel(int enchantingLevel, Id enchId, RegistryAccess access) {
        // This is a simplified approximation
        // Actual calculation depends on enchantment's min/max cost formulas
        EnchantmentCompat compat = EnchantmentCompat.getInstance();
        int maxLevel = compat.getMaxLevel(enchId, access);

        // Higher enchanting levels can get higher enchantment levels
        // Level 30 can typically get max level enchantments
        // Level 15 is roughly half
        // Level 5 usually gets level 1-2
        if (enchantingLevel >= 30) {
            return maxLevel;
        } else if (enchantingLevel >= 20) {
            return Math.min(maxLevel, (maxLevel * 3) / 4 + 1);
        } else if (enchantingLevel >= 10) {
            return Math.min(maxLevel, maxLevel / 2 + 1);
        } else {
            return Math.min(maxLevel, Math.max(1, maxLevel / 3));
        }
    }

    /**
     * Estimate average number of enchantments per item.
     */
    private static float estimateAverageEnchantments(int minLevel, int maxLevel) {
        // This is a rough approximation based on vanilla behavior
        int avgLevel = (minLevel + maxLevel) / 2;
        if (avgLevel >= 30) {
            return 3.5f;
        } else if (avgLevel >= 20) {
            return 2.5f;
        } else if (avgLevel >= 10) {
            return 1.5f;
        } else {
            return 1.0f;
        }
    }

    /**
     * Create an empty analysis with a reason.
     */
    private static EnchantmentAnalysis emptyAnalysis(String reason) {
        return new EnchantmentAnalysis(
            List.of(),
            0,
            0,
            false,
            0,
            reason
        );
    }
}
