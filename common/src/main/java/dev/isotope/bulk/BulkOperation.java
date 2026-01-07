package dev.isotope.bulk;

import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.editing.LootTableParser;
import dev.isotope.registry.LootTableRegistry;
import net.minecraft.resources.ResourceLocation;
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
        SCALE_COUNTS("Scale Counts", "Multiply all item counts by a factor");

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
        Map<ResourceLocation, List<String>> changesByTable
    ) {}

    /**
     * Preview removing an item from all tables.
     */
    public static BulkResult previewRemoveItem(MinecraftServer server, ResourceLocation itemToRemove) {
        Map<ResourceLocation, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            ResourceLocation tableId = tableInfo.id();
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
    public static void applyRemoveItem(MinecraftServer server, ResourceLocation itemToRemove) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            ResourceLocation tableId = tableInfo.id();
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
    public static BulkResult previewReplaceItem(MinecraftServer server, ResourceLocation oldItem, ResourceLocation newItem) {
        Map<ResourceLocation, List<String>> changes = new LinkedHashMap<>();
        int totalChanges = 0;

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            ResourceLocation tableId = tableInfo.id();
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
    public static void applyReplaceItem(MinecraftServer server, ResourceLocation oldItem, ResourceLocation newItem) {
        LootEditManager manager = LootEditManager.getInstance();

        for (var tableInfo : LootTableRegistry.getInstance().getAll()) {
            ResourceLocation tableId = tableInfo.id();
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

    private static LootTableStructure getStructure(MinecraftServer server, ResourceLocation tableId) {
        return LootEditManager.getInstance().getEditedStructure(tableId)
            .or(() -> LootTableParser.parse(server, tableId))
            .orElse(null);
    }
}
