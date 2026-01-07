package dev.isotope.wizards;

import dev.isotope.data.loot.*;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick fix wizards for common loot table balancing operations.
 *
 * Each fix can be previewed before applying.
 */
public class QuickFix {

    /**
     * Result of a quick fix operation.
     */
    public record FixResult(
        String description,
        int changesCount,
        List<LootEditOperation> operations
    ) {}

    /**
     * Available quick fix types.
     */
    public enum FixType {
        BALANCE_WEIGHTS("Balance Weights", "Normalize all entry weights to sum to 100"),
        NERF_RARE("Nerf Rare Items", "Halve weight of items with <5% drop rate"),
        BUFF_RARE("Buff Rare Items", "Double weight of items with <5% drop rate"),
        NERF_COMMON("Nerf Common Items", "Halve weight of items with >50% drop rate"),
        BUFF_COMMON("Buff Common Items", "Double weight of items with >50% drop rate"),
        CAP_QUANTITY("Cap Quantity", "Set max item count to 3 for all entries"),
        DOUBLE_QUANTITY("Double Quantity", "Double all item counts"),
        HALVE_QUANTITY("Halve Quantity", "Halve all item counts"),
        REMOVE_ENCHANTED("Remove Enchanted", "Remove enchantment functions from all entries"),
        REMOVE_DUPLICATES("Remove Duplicates", "Remove duplicate item entries per pool");

        public final String name;
        public final String description;

        FixType(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    /**
     * Preview a quick fix without applying it.
     */
    public static FixResult preview(FixType type, ResourceLocation tableId, LootTableStructure structure) {
        List<LootEditOperation> operations = new ArrayList<>();

        switch (type) {
            case BALANCE_WEIGHTS -> balanceWeights(structure, operations);
            case NERF_RARE -> adjustRareWeights(structure, operations, 0.5f, 0.05f);
            case BUFF_RARE -> adjustRareWeights(structure, operations, 2.0f, 0.05f);
            case NERF_COMMON -> adjustCommonWeights(structure, operations, 0.5f, 0.5f);
            case BUFF_COMMON -> adjustCommonWeights(structure, operations, 2.0f, 0.5f);
            case CAP_QUANTITY -> capQuantity(structure, operations, 3);
            case DOUBLE_QUANTITY -> scaleQuantity(structure, operations, 2.0f);
            case HALVE_QUANTITY -> scaleQuantity(structure, operations, 0.5f);
            case REMOVE_ENCHANTED -> removeEnchantments(structure, operations);
            case REMOVE_DUPLICATES -> removeDuplicates(structure, operations);
        }

        return new FixResult(type.description, operations.size(), operations);
    }

    /**
     * Apply a quick fix.
     */
    public static void apply(ResourceLocation tableId, FixResult result) {
        LootEditManager manager = LootEditManager.getInstance();
        for (LootEditOperation op : result.operations) {
            manager.applyOperation(tableId, op);
        }
    }

    // === Fix implementations ===

    private static void balanceWeights(LootTableStructure structure, List<LootEditOperation> ops) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            if (pool.entries().isEmpty()) continue;

            int totalWeight = 0;
            for (LootEntry entry : pool.entries()) {
                totalWeight += entry.weight();
            }

            if (totalWeight == 0 || totalWeight == 100) continue;

            // Scale each entry's weight to make total = 100
            float scale = 100f / totalWeight;
            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);
                int newWeight = Math.max(1, Math.round(entry.weight() * scale));
                if (newWeight != entry.weight()) {
                    ops.add(new LootEditOperation.ModifyEntryWeight(poolIdx, entryIdx, newWeight));
                }
            }
        }
    }

    private static void adjustRareWeights(LootTableStructure structure,
                                          List<LootEditOperation> ops, float multiplier, float threshold) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            int totalWeight = 0;
            for (LootEntry entry : pool.entries()) {
                totalWeight += entry.weight();
            }
            if (totalWeight == 0) continue;

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);
                float dropRate = (float) entry.weight() / totalWeight;

                if (dropRate < threshold) {
                    int newWeight = Math.max(1, Math.round(entry.weight() * multiplier));
                    if (newWeight != entry.weight()) {
                        ops.add(new LootEditOperation.ModifyEntryWeight(poolIdx, entryIdx, newWeight));
                    }
                }
            }
        }
    }

    private static void adjustCommonWeights(LootTableStructure structure,
                                            List<LootEditOperation> ops, float multiplier, float threshold) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            int totalWeight = 0;
            for (LootEntry entry : pool.entries()) {
                totalWeight += entry.weight();
            }
            if (totalWeight == 0) continue;

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);
                float dropRate = (float) entry.weight() / totalWeight;

                if (dropRate > threshold) {
                    int newWeight = Math.max(1, Math.round(entry.weight() * multiplier));
                    if (newWeight != entry.weight()) {
                        ops.add(new LootEditOperation.ModifyEntryWeight(poolIdx, entryIdx, newWeight));
                    }
                }
            }
        }
    }

    private static void capQuantity(LootTableStructure structure,
                                    List<LootEditOperation> ops, int maxCount) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                for (LootFunction func : entry.functions()) {
                    if (func.function().equals("minecraft:set_count") || func.function().equals("set_count")) {
                        if (func.parameters().has("count")) {
                            NumberProvider currentCount = parseNumberProvider(func.parameters().get("count"));
                            if (currentCount != null) {
                                float maxVal = getMax(currentCount);
                                if (maxVal > maxCount) {
                                    NumberProvider newCount = capCount(currentCount, maxCount);
                                    ops.add(new LootEditOperation.SetItemCount(poolIdx, entryIdx, newCount));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void scaleQuantity(LootTableStructure structure,
                                      List<LootEditOperation> ops, float scale) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                for (LootFunction func : entry.functions()) {
                    if (func.function().equals("minecraft:set_count") || func.function().equals("set_count")) {
                        if (func.parameters().has("count")) {
                            NumberProvider currentCount = parseNumberProvider(func.parameters().get("count"));
                            if (currentCount != null) {
                                NumberProvider newCount = scaleCount(currentCount, scale);
                                ops.add(new LootEditOperation.SetItemCount(poolIdx, entryIdx, newCount));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void removeEnchantments(LootTableStructure structure,
                                           List<LootEditOperation> ops) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                for (int funcIdx = entry.functions().size() - 1; funcIdx >= 0; funcIdx--) {
                    LootFunction func = entry.functions().get(funcIdx);
                    String funcName = func.function();
                    if (funcName.contains("enchant") || funcName.contains("Enchant")) {
                        ops.add(new LootEditOperation.RemoveFunction(poolIdx, entryIdx, funcIdx));
                    }
                }
            }
        }
    }

    private static void removeDuplicates(LootTableStructure structure,
                                         List<LootEditOperation> ops) {
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();

            // Process in reverse so indices stay valid when removing
            for (int entryIdx = pool.entries().size() - 1; entryIdx >= 0; entryIdx--) {
                LootEntry entry = pool.entries().get(entryIdx);
                if (entry.name().isPresent()) {
                    ResourceLocation item = entry.name().get();
                    if (seen.contains(item)) {
                        ops.add(new LootEditOperation.RemoveEntry(poolIdx, entryIdx));
                    } else {
                        seen.add(item);
                    }
                }
            }
        }
    }

    // === Helper methods ===

    private static NumberProvider parseNumberProvider(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new NumberProvider.Constant(element.getAsFloat());
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                return new NumberProvider.Uniform(obj.get("min").getAsFloat(), obj.get("max").getAsFloat());
            }
            if (obj.has("n") && obj.has("p")) {
                return new NumberProvider.Binomial(obj.get("n").getAsInt(), obj.get("p").getAsFloat());
            }
        }
        return null;
    }

    private static float getMax(NumberProvider provider) {
        if (provider instanceof NumberProvider.Constant c) {
            return c.value();
        } else if (provider instanceof NumberProvider.Uniform u) {
            return u.max();
        } else if (provider instanceof NumberProvider.Binomial b) {
            return b.n();
        }
        return 1;
    }

    private static NumberProvider capCount(NumberProvider provider, int max) {
        if (provider instanceof NumberProvider.Constant c) {
            return new NumberProvider.Constant(Math.min(c.value(), max));
        } else if (provider instanceof NumberProvider.Uniform u) {
            return new NumberProvider.Uniform(Math.min(u.min(), max), Math.min(u.max(), max));
        } else if (provider instanceof NumberProvider.Binomial b) {
            return new NumberProvider.Binomial(Math.min(b.n(), max), b.p());
        }
        return provider;
    }

    private static NumberProvider scaleCount(NumberProvider provider, float scale) {
        if (provider instanceof NumberProvider.Constant c) {
            return new NumberProvider.Constant(Math.max(1, c.value() * scale));
        } else if (provider instanceof NumberProvider.Uniform u) {
            return new NumberProvider.Uniform(
                Math.max(1, u.min() * scale),
                Math.max(1, u.max() * scale)
            );
        } else if (provider instanceof NumberProvider.Binomial b) {
            return new NumberProvider.Binomial(Math.max(1, Math.round(b.n() * scale)), b.p());
        }
        return provider;
    }
}
