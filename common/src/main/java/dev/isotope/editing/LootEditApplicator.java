package dev.isotope.editing;

import dev.isotope.data.loot.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies edit operations to loot table structures.
 *
 * Each operation produces a new immutable structure, preserving the original.
 */
public final class LootEditApplicator {

    private LootEditApplicator() {}

    /**
     * Apply a single operation to a structure.
     *
     * @param structure The original structure
     * @param op The operation to apply
     * @return A new structure with the operation applied
     */
    public static LootTableStructure apply(LootTableStructure structure, LootEditOperation op) {
        return switch (op) {
            case LootEditOperation.AddPool p -> applyAddPool(structure, p);
            case LootEditOperation.RemovePool p -> applyRemovePool(structure, p);
            case LootEditOperation.ModifyPoolRolls p -> applyModifyPoolRolls(structure, p);
            case LootEditOperation.AddEntry p -> applyAddEntry(structure, p);
            case LootEditOperation.RemoveEntry p -> applyRemoveEntry(structure, p);
            case LootEditOperation.ModifyEntryWeight p -> applyModifyEntryWeight(structure, p);
            case LootEditOperation.ModifyEntryItem p -> applyModifyEntryItem(structure, p);
            case LootEditOperation.SetItemCount p -> applySetItemCount(structure, p);
            case LootEditOperation.AddFunction p -> applyAddFunction(structure, p);
            case LootEditOperation.RemoveFunction p -> applyRemoveFunction(structure, p);
            case LootEditOperation.AddCondition p -> applyAddCondition(structure, p);
            case LootEditOperation.RemoveCondition p -> applyRemoveCondition(structure, p);
            case LootEditOperation.AddPoolFunction p -> applyAddPoolFunction(structure, p);
            case LootEditOperation.RemovePoolFunction p -> applyRemovePoolFunction(structure, p);
            case LootEditOperation.AddPoolCondition p -> applyAddPoolCondition(structure, p);
            case LootEditOperation.RemovePoolCondition p -> applyRemovePoolCondition(structure, p);
        };
    }

    /**
     * Apply all operations from an edit to a structure.
     *
     * @param structure The original structure
     * @param edit The edit containing operations
     * @return A new structure with all operations applied
     */
    public static LootTableStructure applyAll(LootTableStructure structure, LootTableEdit edit) {
        LootTableStructure result = structure;
        for (LootEditOperation op : edit.operations()) {
            result = apply(result, op);
        }
        return result;
    }

    // ===== Pool Operations =====

    private static LootTableStructure applyAddPool(LootTableStructure structure, LootEditOperation.AddPool op) {
        return structure.withPoolAdded(op.index(), op.pool());
    }

    private static LootTableStructure applyRemovePool(LootTableStructure structure, LootEditOperation.RemovePool op) {
        return structure.withPoolRemoved(op.poolIndex());
    }

    private static LootTableStructure applyModifyPoolRolls(LootTableStructure structure, LootEditOperation.ModifyPoolRolls op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        LootPool newPool = pool.withRolls(op.newRolls());
        return structure.withPoolReplaced(op.poolIndex(), newPool);
    }

    // ===== Entry Operations =====

    private static LootTableStructure applyAddEntry(LootTableStructure structure, LootEditOperation.AddEntry op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        LootPool newPool = pool.withEntryAdded(op.entryIndex(), op.entry());
        return structure.withPoolReplaced(op.poolIndex(), newPool);
    }

    private static LootTableStructure applyRemoveEntry(LootTableStructure structure, LootEditOperation.RemoveEntry op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        LootPool newPool = pool.withEntryRemoved(op.entryIndex());
        return structure.withPoolReplaced(op.poolIndex(), newPool);
    }

    private static LootTableStructure applyModifyEntryWeight(LootTableStructure structure, LootEditOperation.ModifyEntryWeight op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry ->
            entry.withWeight(op.newWeight())
        );
    }

    private static LootTableStructure applyModifyEntryItem(LootTableStructure structure, LootEditOperation.ModifyEntryItem op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry ->
            entry.withItem(op.newItem())
        );
    }

    private static LootTableStructure applySetItemCount(LootTableStructure structure, LootEditOperation.SetItemCount op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            // Find and replace or add set_count function
            List<LootFunction> newFunctions = new ArrayList<>();
            boolean found = false;

            for (LootFunction func : entry.functions()) {
                if (func.isSetCount()) {
                    // Replace existing set_count
                    newFunctions.add(createSetCountFunction(op.count()));
                    found = true;
                } else {
                    newFunctions.add(func);
                }
            }

            if (!found) {
                // Add new set_count function
                newFunctions.add(createSetCountFunction(op.count()));
            }

            return entry.withFunctions(newFunctions);
        });
    }

    // ===== Entry Function Operations =====

    private static LootTableStructure applyAddFunction(LootTableStructure structure, LootEditOperation.AddFunction op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            List<LootFunction> newFunctions = new ArrayList<>(entry.functions());
            newFunctions.add(op.function());
            return entry.withFunctions(newFunctions);
        });
    }

    private static LootTableStructure applyRemoveFunction(LootTableStructure structure, LootEditOperation.RemoveFunction op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.functionIndex() < 0 || op.functionIndex() >= entry.functions().size()) {
                return entry;
            }
            List<LootFunction> newFunctions = new ArrayList<>(entry.functions());
            newFunctions.remove(op.functionIndex());
            return entry.withFunctions(newFunctions);
        });
    }

    // ===== Entry Condition Operations =====

    private static LootTableStructure applyAddCondition(LootTableStructure structure, LootEditOperation.AddCondition op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            List<LootCondition> newConditions = new ArrayList<>(entry.conditions());
            newConditions.add(op.condition());
            return entry.withConditions(newConditions);
        });
    }

    private static LootTableStructure applyRemoveCondition(LootTableStructure structure, LootEditOperation.RemoveCondition op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.conditionIndex() < 0 || op.conditionIndex() >= entry.conditions().size()) {
                return entry;
            }
            List<LootCondition> newConditions = new ArrayList<>(entry.conditions());
            newConditions.remove(op.conditionIndex());
            return entry.withConditions(newConditions);
        });
    }

    // ===== Pool Function Operations =====

    private static LootTableStructure applyAddPoolFunction(LootTableStructure structure, LootEditOperation.AddPoolFunction op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        List<LootFunction> newFunctions = new ArrayList<>(pool.functions());
        newFunctions.add(op.function());
        return structure.withPoolReplaced(op.poolIndex(), pool.withFunctions(newFunctions));
    }

    private static LootTableStructure applyRemovePoolFunction(LootTableStructure structure, LootEditOperation.RemovePoolFunction op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        if (op.functionIndex() < 0 || op.functionIndex() >= pool.functions().size()) {
            return structure;
        }
        List<LootFunction> newFunctions = new ArrayList<>(pool.functions());
        newFunctions.remove(op.functionIndex());
        return structure.withPoolReplaced(op.poolIndex(), pool.withFunctions(newFunctions));
    }

    // ===== Pool Condition Operations =====

    private static LootTableStructure applyAddPoolCondition(LootTableStructure structure, LootEditOperation.AddPoolCondition op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        List<LootCondition> newConditions = new ArrayList<>(pool.conditions());
        newConditions.add(op.condition());
        return structure.withPoolReplaced(op.poolIndex(), pool.withConditions(newConditions));
    }

    private static LootTableStructure applyRemovePoolCondition(LootTableStructure structure, LootEditOperation.RemovePoolCondition op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        if (op.conditionIndex() < 0 || op.conditionIndex() >= pool.conditions().size()) {
            return structure;
        }
        List<LootCondition> newConditions = new ArrayList<>(pool.conditions());
        newConditions.remove(op.conditionIndex());
        return structure.withPoolReplaced(op.poolIndex(), pool.withConditions(newConditions));
    }

    // ===== Helpers =====

    /**
     * Helper to modify an entry within the structure.
     */
    private static LootTableStructure modifyEntry(
            LootTableStructure structure,
            int poolIndex,
            int entryIndex,
            java.util.function.Function<LootEntry, LootEntry> modifier) {

        if (poolIndex < 0 || poolIndex >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(poolIndex);

        if (entryIndex < 0 || entryIndex >= pool.entries().size()) {
            return structure;
        }
        LootEntry entry = pool.entries().get(entryIndex);
        LootEntry newEntry = modifier.apply(entry);
        LootPool newPool = pool.withEntryReplaced(entryIndex, newEntry);

        return structure.withPoolReplaced(poolIndex, newPool);
    }

    /**
     * Create a set_count function from a NumberProvider.
     */
    private static LootFunction createSetCountFunction(NumberProvider count) {
        return switch (count) {
            case NumberProvider.Constant c -> LootFunction.setCount((int) c.value());
            case NumberProvider.Uniform u -> LootFunction.setCount((int) u.min(), (int) u.max());
            case NumberProvider.Binomial b -> {
                // Binomial is not directly supported by set_count, use range approximation
                yield LootFunction.setCount(0, b.n());
            }
        };
    }
}
