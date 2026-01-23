package dev.isotope.bulk;

import dev.isotope.compat.Id;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.editing.LootTableParser;
import dev.isotope.registry.LootTableRegistry;
import net.minecraft.server.MinecraftServer;

import java.util.*;

/**
 * Bulk operations that apply changes across multiple loot tables.
 */
public class BulkOperation {

    /**
     * Types of bulk operations.
     */
    public enum Type {
        REMOVE_ITEM("Remove Item", "Remove a specific item from all loot tables"),
        REPLACE_ITEM("Replace Item", "Replace one item with another across all tables"),
        SCALE_WEIGHTS("Scale Weights", "Multiply all weights by a factor"),
        SCALE_COUNTS("Scale Counts", "Multiply all item counts by a factor"),
        REMOVE_EMPTY_POOLS("Remove Empty", "Remove all empty pools from all tables"),
        NORMALIZE_WEIGHTS("Normalize", "Set all weights to sum to 100 per pool");

        public final String name;
        public final String description;

        Type(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    /**
     * Result of a bulk operation preview.
     */
    public record BulkResult(
        Type type,
        int tablesAffected,
        int totalChanges,
        Map<Id, List<String>> changesByTable
    ) {}

    /**
     * Preview removing an item from all tables.
     */
    public static BulkResult previewRemoveItem(MinecraftServer server, Id itemToRemove) {
        Map<Id, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            List<String> tableChanges = new ArrayList<>();
            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    if (entry.name().isPresent() && entry.name().get().equals(itemToRemove)) {
                        tableChanges.add("Remove from pool " + poolIdx);
                        totalChanges++;
                    }
                }
            }

            if (!tableChanges.isEmpty()) {
                changes.put(tableId, tableChanges);
            }
        }

        return new BulkResult(Type.REMOVE_ITEM, changes.size(), totalChanges, changes);
    }

    /**
     * Apply removing an item from all tables.
     */
    public static void applyRemoveItem(MinecraftServer server, Id itemToRemove) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            // Process in reverse to keep indices valid
            for (int poolIdx = structure.pools().size() - 1; poolIdx >= 0; poolIdx--) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = pool.entries().size() - 1; entryIdx >= 0; entryIdx--) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    if (entry.name().isPresent() && entry.name().get().equals(itemToRemove)) {
                        manager.applyOperation(tableId, new LootEditOperation.RemoveEntry(poolIdx, entryIdx));
                    }
                }
            }
        }
    }

    /**
     * Preview replacing an item across all tables.
     */
    public static BulkResult previewReplaceItem(MinecraftServer server, Id oldItem, Id newItem) {
        Map<Id, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            List<String> tableChanges = new ArrayList<>();
            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    if (entry.name().isPresent() && entry.name().get().equals(oldItem)) {
                        tableChanges.add("Replace in pool " + poolIdx + " entry " + entryIdx);
                        totalChanges++;
                    }
                }
            }

            if (!tableChanges.isEmpty()) {
                changes.put(tableId, tableChanges);
            }
        }

        return new BulkResult(Type.REPLACE_ITEM, changes.size(), totalChanges, changes);
    }

    /**
     * Apply replacing an item across all tables.
     */
    public static void applyReplaceItem(MinecraftServer server, Id oldItem, Id newItem) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    if (entry.name().isPresent() && entry.name().get().equals(oldItem)) {
                        manager.applyOperation(tableId, new LootEditOperation.ModifyEntryItem(poolIdx, entryIdx, newItem));
                    }
                }
            }
        }
    }

    /**
     * Preview scaling weights across all tables.
     */
    public static BulkResult previewScaleWeights(MinecraftServer server, float scaleFactor) {
        Map<Id, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            List<String> tableChanges = new ArrayList<>();
            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    int oldWeight = entry.weight();
                    int newWeight = Math.max(1, Math.round(oldWeight * scaleFactor));
                    if (newWeight != oldWeight) {
                        tableChanges.add("Pool " + poolIdx + " entry " + entryIdx + ": " + oldWeight + " → " + newWeight);
                        totalChanges++;
                    }
                }
            }

            if (!tableChanges.isEmpty()) {
                changes.put(tableId, tableChanges);
            }
        }

        return new BulkResult(Type.SCALE_WEIGHTS, changes.size(), totalChanges, changes);
    }

    /**
     * Apply scaling weights across all tables.
     */
    public static void applyScaleWeights(MinecraftServer server, float scaleFactor) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    int oldWeight = entry.weight();
                    int newWeight = Math.max(1, Math.round(oldWeight * scaleFactor));
                    if (newWeight != oldWeight) {
                        manager.applyOperation(tableId, new LootEditOperation.ModifyEntryWeight(poolIdx, entryIdx, newWeight));
                    }
                }
            }
        }
    }

    /**
     * Preview scaling item counts across all tables.
     */
    public static BulkResult previewScaleCounts(MinecraftServer server, float scaleFactor) {
        Map<Id, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            List<String> tableChanges = new ArrayList<>();
            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);

                    // Check for set_count function
                    for (LootFunction func : entry.functions()) {
                        String funcName = func.function();
                        if (funcName.equals("minecraft:set_count") || funcName.equals("set_count")) {
                            if (func.parameters().has("count")) {
                                NumberProvider count = parseNumberProvider(func.parameters().get("count"));
                                if (count != null) {
                                    String oldCount = formatCount(count);
                                    NumberProvider newCount = scaleCount(count, scaleFactor);
                                    String newCountStr = formatCount(newCount);
                                    if (!oldCount.equals(newCountStr)) {
                                        tableChanges.add("Pool " + poolIdx + " entry " + entryIdx + ": " + oldCount + " → " + newCountStr);
                                        totalChanges++;
                                    }
                                }
                            }
                            break; // Only process first set_count
                        }
                    }
                }
            }

            if (!tableChanges.isEmpty()) {
                changes.put(tableId, tableChanges);
            }
        }

        return new BulkResult(Type.SCALE_COUNTS, changes.size(), totalChanges, changes);
    }

    /**
     * Apply scaling item counts across all tables.
     */
    public static void applyScaleCounts(MinecraftServer server, float scaleFactor) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);

                    // Check for set_count function
                    for (LootFunction func : entry.functions()) {
                        String funcName = func.function();
                        if (funcName.equals("minecraft:set_count") || funcName.equals("set_count")) {
                            if (func.parameters().has("count")) {
                                NumberProvider count = parseNumberProvider(func.parameters().get("count"));
                                if (count != null) {
                                    NumberProvider newCount = scaleCount(count, scaleFactor);
                                    manager.applyOperation(tableId, new LootEditOperation.SetItemCount(poolIdx, entryIdx, newCount));
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    // === Remove Empty Pools ===

    /**
     * Preview removing empty pools from all tables.
     */
    public static BulkResult previewRemoveEmptyPools(MinecraftServer server) {
        Map<Id, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            List<String> tableChanges = new ArrayList<>();
            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                if (pool.entries().isEmpty()) {
                    tableChanges.add("Remove empty pool #" + (poolIdx + 1));
                    totalChanges++;
                }
            }

            if (!tableChanges.isEmpty()) {
                changes.put(tableId, tableChanges);
            }
        }

        return new BulkResult(Type.REMOVE_EMPTY_POOLS, changes.size(), totalChanges, changes);
    }

    /**
     * Apply removing empty pools from all tables.
     */
    public static void applyRemoveEmptyPools(MinecraftServer server) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            // Process in reverse to keep indices valid
            for (int poolIdx = structure.pools().size() - 1; poolIdx >= 0; poolIdx--) {
                LootPool pool = structure.pools().get(poolIdx);
                if (pool.entries().isEmpty()) {
                    manager.applyOperation(tableId, new LootEditOperation.RemovePool(poolIdx));
                }
            }
        }
    }

    // === Normalize Weights ===

    /**
     * Preview normalizing weights to sum to 100 per pool.
     */
    public static BulkResult previewNormalizeWeights(MinecraftServer server) {
        Map<Id, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            List<String> tableChanges = new ArrayList<>();
            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                if (pool.entries().isEmpty()) continue;

                int totalWeight = pool.getTotalWeight();
                if (totalWeight == 0 || totalWeight == 100) continue;

                // Calculate normalized weights
                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    int oldWeight = entry.weight();
                    int newWeight = Math.max(1, Math.round((float) oldWeight * 100 / totalWeight));
                    if (newWeight != oldWeight) {
                        tableChanges.add("Pool #" + (poolIdx + 1) + " entry #" + (entryIdx + 1) + ": " + oldWeight + " → " + newWeight);
                        totalChanges++;
                    }
                }
            }

            if (!tableChanges.isEmpty()) {
                changes.put(tableId, tableChanges);
            }
        }

        return new BulkResult(Type.NORMALIZE_WEIGHTS, changes.size(), totalChanges, changes);
    }

    /**
     * Apply normalizing weights to sum to 100 per pool.
     */
    public static void applyNormalizeWeights(MinecraftServer server) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            Id tableId = tableInfo.id();
            LootTableStructure structure = getStructure(server, tableId);
            if (structure == null) continue;

            for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
                LootPool pool = structure.pools().get(poolIdx);
                if (pool.entries().isEmpty()) continue;

                int totalWeight = pool.getTotalWeight();
                if (totalWeight == 0 || totalWeight == 100) continue;

                for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                    LootEntry entry = pool.entries().get(entryIdx);
                    int oldWeight = entry.weight();
                    int newWeight = Math.max(1, Math.round((float) oldWeight * 100 / totalWeight));
                    if (newWeight != oldWeight) {
                        manager.applyOperation(tableId, new LootEditOperation.ModifyEntryWeight(poolIdx, entryIdx, newWeight));
                    }
                }
            }
        }
    }

    // === Helper methods ===

    private static NumberProvider parseNumberProvider(com.google.gson.JsonElement element) {
        if (element == null) return null;

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

    private static String formatCount(NumberProvider provider) {
        if (provider instanceof NumberProvider.Constant c) {
            return String.valueOf((int) c.value());
        } else if (provider instanceof NumberProvider.Uniform u) {
            return (int) u.min() + "-" + (int) u.max();
        } else if (provider instanceof NumberProvider.Binomial b) {
            return "binomial(" + b.n() + ")";
        }
        return "?";
    }

    private static LootTableStructure getStructure(MinecraftServer server, Id tableId) {
        return LootEditManager.getInstance().getEditedStructure(tableId)
            .or(() -> LootTableParser.parse(server, tableId))
            .orElse(null);
    }
}
