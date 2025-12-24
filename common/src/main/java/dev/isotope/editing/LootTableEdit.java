package dev.isotope.editing;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents all edits made to a single loot table.
 *
 * Stores the original table ID and a list of operations that transform
 * the original into the edited version. Operations are applied in order.
 */
public record LootTableEdit(
    ResourceLocation tableId,
    List<LootEditOperation> operations,
    long lastModified,
    String author
) {
    /**
     * Create a new empty edit for a table.
     */
    public static LootTableEdit create(ResourceLocation tableId) {
        return new LootTableEdit(
            tableId,
            new ArrayList<>(),
            System.currentTimeMillis(),
            "ISOTOPE"
        );
    }

    /**
     * Create a copy with an additional operation.
     */
    public LootTableEdit withOperation(LootEditOperation operation) {
        List<LootEditOperation> newOps = new ArrayList<>(operations);
        newOps.add(operation);
        return new LootTableEdit(tableId, newOps, System.currentTimeMillis(), author);
    }

    /**
     * Create a copy with an operation removed.
     */
    public LootTableEdit withoutOperation(int index) {
        if (index < 0 || index >= operations.size()) {
            return this;
        }
        List<LootEditOperation> newOps = new ArrayList<>(operations);
        newOps.remove(index);
        return new LootTableEdit(tableId, newOps, System.currentTimeMillis(), author);
    }

    /**
     * Create a copy with all operations after the given index removed (undo).
     */
    public LootTableEdit undoTo(int index) {
        if (index < 0 || index >= operations.size()) {
            return this;
        }
        List<LootEditOperation> newOps = new ArrayList<>(operations.subList(0, index));
        return new LootTableEdit(tableId, newOps, System.currentTimeMillis(), author);
    }

    /**
     * Check if this edit has any operations.
     */
    public boolean hasOperations() {
        return !operations.isEmpty();
    }

    /**
     * Get the number of operations.
     */
    public int getOperationCount() {
        return operations.size();
    }

    /**
     * Get a summary of all operations.
     */
    public List<String> getOperationDescriptions() {
        return operations.stream()
            .map(LootEditOperation::getDescription)
            .toList();
    }
}
