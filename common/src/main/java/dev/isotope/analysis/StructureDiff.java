package dev.isotope.analysis;

import dev.isotope.data.loot.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes differences between original and edited loot table structures.
 */
public final class StructureDiff {

    private StructureDiff() {}

    /**
     * Types of differences.
     */
    public sealed interface DiffEntry {
        record AddedPool(int poolIndex, LootPool pool) implements DiffEntry {}
        record RemovedPool(int poolIndex, LootPool pool) implements DiffEntry {}
        record AddedEntry(int poolIndex, int entryIndex, LootEntry entry) implements DiffEntry {}
        record RemovedEntry(int poolIndex, int entryIndex, LootEntry entry) implements DiffEntry {}
        record ModifiedWeight(int poolIndex, int entryIndex, ResourceLocation item, int oldWeight, int newWeight) implements DiffEntry {}
        record ModifiedCount(int poolIndex, int entryIndex, ResourceLocation item, String oldCount, String newCount) implements DiffEntry {}
        record ModifiedItem(int poolIndex, int entryIndex, ResourceLocation oldItem, ResourceLocation newItem) implements DiffEntry {}
    }

    /**
     * Result of a diff comparison.
     */
    public record DiffResult(
        List<DiffEntry> additions,
        List<DiffEntry> removals,
        List<DiffEntry> modifications,
        int totalChanges
    ) {
        public boolean hasChanges() {
            return totalChanges > 0;
        }
    }

    /**
     * Compare original and edited structures.
     */
    public static DiffResult compare(LootTableStructure original, LootTableStructure edited) {
        List<DiffEntry> additions = new ArrayList<>();
        List<DiffEntry> removals = new ArrayList<>();
        List<DiffEntry> modifications = new ArrayList<>();

        int origPoolCount = original.pools().size();
        int editPoolCount = edited.pools().size();

        // Compare pools that exist in both
        int commonPools = Math.min(origPoolCount, editPoolCount);
        for (int i = 0; i < commonPools; i++) {
            LootPool origPool = original.pools().get(i);
            LootPool editPool = edited.pools().get(i);
            comparePool(i, origPool, editPool, additions, removals, modifications);
        }

        // Added pools
        for (int i = commonPools; i < editPoolCount; i++) {
            additions.add(new DiffEntry.AddedPool(i, edited.pools().get(i)));
        }

        // Removed pools
        for (int i = commonPools; i < origPoolCount; i++) {
            removals.add(new DiffEntry.RemovedPool(i, original.pools().get(i)));
        }

        return new DiffResult(
            additions,
            removals,
            modifications,
            additions.size() + removals.size() + modifications.size()
        );
    }

    private static void comparePool(int poolIndex, LootPool original, LootPool edited,
                                    List<DiffEntry> additions, List<DiffEntry> removals,
                                    List<DiffEntry> modifications) {

        int origEntryCount = original.entries().size();
        int editEntryCount = edited.entries().size();

        // Compare entries that exist in both
        int commonEntries = Math.min(origEntryCount, editEntryCount);
        for (int i = 0; i < commonEntries; i++) {
            LootEntry origEntry = original.entries().get(i);
            LootEntry editEntry = edited.entries().get(i);
            compareEntry(poolIndex, i, origEntry, editEntry, modifications);
        }

        // Added entries
        for (int i = commonEntries; i < editEntryCount; i++) {
            additions.add(new DiffEntry.AddedEntry(poolIndex, i, edited.entries().get(i)));
        }

        // Removed entries
        for (int i = commonEntries; i < origEntryCount; i++) {
            removals.add(new DiffEntry.RemovedEntry(poolIndex, i, original.entries().get(i)));
        }
    }

    private static void compareEntry(int poolIndex, int entryIndex, LootEntry original, LootEntry edited,
                                     List<DiffEntry> modifications) {

        // Check item change
        ResourceLocation origItem = original.name().orElse(null);
        ResourceLocation editItem = edited.name().orElse(null);

        if (!Objects.equals(origItem, editItem) && origItem != null && editItem != null) {
            modifications.add(new DiffEntry.ModifiedItem(poolIndex, entryIndex, origItem, editItem));
        }

        // Check weight change
        if (original.weight() != edited.weight()) {
            ResourceLocation item = editItem != null ? editItem : origItem;
            if (item != null) {
                modifications.add(new DiffEntry.ModifiedWeight(poolIndex, entryIndex, item,
                    original.weight(), edited.weight()));
            }
        }

        // Check count change (from set_count function)
        String origCount = getCountString(original);
        String editCount = getCountString(edited);
        if (!origCount.equals(editCount)) {
            ResourceLocation item = editItem != null ? editItem : origItem;
            if (item != null) {
                modifications.add(new DiffEntry.ModifiedCount(poolIndex, entryIndex, item, origCount, editCount));
            }
        }
    }

    private static String getCountString(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            String funcName = func.function();
            if (funcName.equals("minecraft:set_count") || funcName.equals("set_count")) {
                if (func.parameters().has("count")) {
                    return formatCountParam(func.parameters().get("count"));
                }
            }
        }
        return "1";
    }

    private static String formatCountParam(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            int val = element.getAsInt();
            return String.valueOf(val);
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                int min = obj.get("min").getAsInt();
                int max = obj.get("max").getAsInt();
                if (min == max) {
                    return String.valueOf(min);
                }
                return min + "-" + max;
            }
        }
        return "1";
    }

    /**
     * Get a human-readable description of a diff entry.
     */
    public static String describe(DiffEntry entry) {
        return switch (entry) {
            case DiffEntry.AddedPool e ->
                "+ Added Pool " + (e.poolIndex() + 1) + " (" + e.pool().entries().size() + " entries)";
            case DiffEntry.RemovedPool e ->
                "- Removed Pool " + (e.poolIndex() + 1);
            case DiffEntry.AddedEntry e ->
                "+ Added: " + e.entry().name().map(ResourceLocation::getPath).orElse("unknown");
            case DiffEntry.RemovedEntry e ->
                "- Removed: " + e.entry().name().map(ResourceLocation::getPath).orElse("unknown");
            case DiffEntry.ModifiedWeight e ->
                "~ " + e.item().getPath() + " weight: " + e.oldWeight() + " -> " + e.newWeight();
            case DiffEntry.ModifiedCount e ->
                "~ " + e.item().getPath() + " count: " + e.oldCount() + " -> " + e.newCount();
            case DiffEntry.ModifiedItem e ->
                "~ Changed: " + e.oldItem().getPath() + " -> " + e.newItem().getPath();
        };
    }

    /**
     * Get color for a diff entry.
     */
    public static int getColor(DiffEntry entry) {
        return switch (entry) {
            case DiffEntry.AddedPool ignored -> 0xFF55FF55;    // Green
            case DiffEntry.AddedEntry ignored -> 0xFF55FF55;
            case DiffEntry.RemovedPool ignored -> 0xFFFF5555;  // Red
            case DiffEntry.RemovedEntry ignored -> 0xFFFF5555;
            case DiffEntry.ModifiedWeight ignored -> 0xFFFFFF55; // Yellow
            case DiffEntry.ModifiedCount ignored -> 0xFFFFFF55;
            case DiffEntry.ModifiedItem ignored -> 0xFFFFAA00;   // Orange
        };
    }
}
