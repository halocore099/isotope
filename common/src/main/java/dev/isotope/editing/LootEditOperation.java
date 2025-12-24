package dev.isotope.editing;

import dev.isotope.data.loot.*;
import net.minecraft.resources.ResourceLocation;

/**
 * Sealed interface representing all possible loot table edit operations.
 *
 * Each operation is immutable and can be applied to a LootTableStructure
 * to produce a modified copy. Operations are designed to be serializable
 * for persistence in analysis saves.
 */
public sealed interface LootEditOperation permits
        LootEditOperation.AddPool,
        LootEditOperation.RemovePool,
        LootEditOperation.ModifyPoolRolls,
        LootEditOperation.AddEntry,
        LootEditOperation.RemoveEntry,
        LootEditOperation.ModifyEntryWeight,
        LootEditOperation.ModifyEntryItem,
        LootEditOperation.SetItemCount,
        LootEditOperation.AddFunction,
        LootEditOperation.RemoveFunction,
        LootEditOperation.AddCondition,
        LootEditOperation.RemoveCondition,
        LootEditOperation.AddPoolFunction,
        LootEditOperation.RemovePoolFunction,
        LootEditOperation.AddPoolCondition,
        LootEditOperation.RemovePoolCondition {

    /**
     * Get a human-readable description of this operation.
     */
    String getDescription();

    // ===== Pool Operations =====

    /**
     * Add a new pool at the specified index.
     */
    record AddPool(int index, LootPool pool) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Add pool at index " + index;
        }
    }

    /**
     * Remove the pool at the specified index.
     */
    record RemovePool(int poolIndex) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Remove pool #" + (poolIndex + 1);
        }
    }

    /**
     * Modify the rolls of a pool.
     */
    record ModifyPoolRolls(int poolIndex, NumberProvider newRolls) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Set pool #" + (poolIndex + 1) + " rolls to " + newRolls;
        }
    }

    // ===== Entry Operations =====

    /**
     * Add a new entry to a pool at the specified index.
     */
    record AddEntry(int poolIndex, int entryIndex, LootEntry entry) implements LootEditOperation {
        @Override
        public String getDescription() {
            String entryName = entry.name().map(ResourceLocation::getPath).orElse("entry");
            return "Add " + entryName + " to pool #" + (poolIndex + 1);
        }
    }

    /**
     * Remove an entry from a pool.
     */
    record RemoveEntry(int poolIndex, int entryIndex) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Remove entry #" + (entryIndex + 1) + " from pool #" + (poolIndex + 1);
        }
    }

    /**
     * Modify the weight of an entry.
     */
    record ModifyEntryWeight(int poolIndex, int entryIndex, int newWeight) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Set weight of entry #" + (entryIndex + 1) + " in pool #" + (poolIndex + 1) + " to " + newWeight;
        }
    }

    /**
     * Change the item of an item entry.
     */
    record ModifyEntryItem(int poolIndex, int entryIndex, ResourceLocation newItem) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Change item in pool #" + (poolIndex + 1) + " entry #" + (entryIndex + 1) + " to " + newItem.getPath();
        }
    }

    /**
     * Set the count function on an entry.
     */
    record SetItemCount(int poolIndex, int entryIndex, NumberProvider count) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Set count of entry #" + (entryIndex + 1) + " in pool #" + (poolIndex + 1) + " to " + count;
        }
    }

    // ===== Entry Function Operations =====

    /**
     * Add a function to an entry.
     */
    record AddFunction(int poolIndex, int entryIndex, LootFunction function) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Add " + function.getDisplayName() + " to entry #" + (entryIndex + 1) + " in pool #" + (poolIndex + 1);
        }
    }

    /**
     * Remove a function from an entry.
     */
    record RemoveFunction(int poolIndex, int entryIndex, int functionIndex) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Remove function #" + (functionIndex + 1) + " from entry #" + (entryIndex + 1) + " in pool #" + (poolIndex + 1);
        }
    }

    // ===== Entry Condition Operations =====

    /**
     * Add a condition to an entry.
     */
    record AddCondition(int poolIndex, int entryIndex, LootCondition condition) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Add " + condition.getDisplayName() + " to entry #" + (entryIndex + 1) + " in pool #" + (poolIndex + 1);
        }
    }

    /**
     * Remove a condition from an entry.
     */
    record RemoveCondition(int poolIndex, int entryIndex, int conditionIndex) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Remove condition #" + (conditionIndex + 1) + " from entry #" + (entryIndex + 1) + " in pool #" + (poolIndex + 1);
        }
    }

    // ===== Pool Function Operations =====

    /**
     * Add a function to a pool (applies to all entries).
     */
    record AddPoolFunction(int poolIndex, LootFunction function) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Add " + function.getDisplayName() + " to pool #" + (poolIndex + 1);
        }
    }

    /**
     * Remove a function from a pool.
     */
    record RemovePoolFunction(int poolIndex, int functionIndex) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Remove function #" + (functionIndex + 1) + " from pool #" + (poolIndex + 1);
        }
    }

    // ===== Pool Condition Operations =====

    /**
     * Add a condition to a pool.
     */
    record AddPoolCondition(int poolIndex, LootCondition condition) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Add " + condition.getDisplayName() + " condition to pool #" + (poolIndex + 1);
        }
    }

    /**
     * Remove a condition from a pool.
     */
    record RemovePoolCondition(int poolIndex, int conditionIndex) implements LootEditOperation {
        @Override
        public String getDescription() {
            return "Remove condition #" + (conditionIndex + 1) + " from pool #" + (poolIndex + 1);
        }
    }
}
