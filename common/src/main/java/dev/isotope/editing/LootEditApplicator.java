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
            case LootEditOperation.ModifyBonusRolls p -> applyModifyBonusRolls(structure, p);
            case LootEditOperation.AddEntry p -> applyAddEntry(structure, p);
            case LootEditOperation.RemoveEntry p -> applyRemoveEntry(structure, p);
            case LootEditOperation.ModifyEntryWeight p -> applyModifyEntryWeight(structure, p);
            case LootEditOperation.ModifyEntryQuality p -> applyModifyEntryQuality(structure, p);
            case LootEditOperation.ModifyEntryItem p -> applyModifyEntryItem(structure, p);
            case LootEditOperation.ModifyEntryType p -> applyModifyEntryType(structure, p);
            case LootEditOperation.SetItemCount p -> applySetItemCount(structure, p);
            case LootEditOperation.AddFunction p -> applyAddFunction(structure, p);
            case LootEditOperation.RemoveFunction p -> applyRemoveFunction(structure, p);
            case LootEditOperation.AddFunctionCondition p -> applyAddFunctionCondition(structure, p);
            case LootEditOperation.RemoveFunctionCondition p -> applyRemoveFunctionCondition(structure, p);
            case LootEditOperation.AddCondition p -> applyAddCondition(structure, p);
            case LootEditOperation.RemoveCondition p -> applyRemoveCondition(structure, p);
            case LootEditOperation.AddPoolFunction p -> applyAddPoolFunction(structure, p);
            case LootEditOperation.RemovePoolFunction p -> applyRemovePoolFunction(structure, p);
            case LootEditOperation.AddPoolCondition p -> applyAddPoolCondition(structure, p);
            case LootEditOperation.RemovePoolCondition p -> applyRemovePoolCondition(structure, p);
            case LootEditOperation.AddTableFunction p -> applyAddTableFunction(structure, p);
            case LootEditOperation.RemoveTableFunction p -> applyRemoveTableFunction(structure, p);
            case LootEditOperation.SetRandomSequence p -> applySetRandomSequence(structure, p);
            case LootEditOperation.AddChild p -> applyAddChild(structure, p);
            case LootEditOperation.RemoveChild p -> applyRemoveChild(structure, p);
            case LootEditOperation.ModifyChild p -> applyModifyChild(structure, p);
            case LootEditOperation.ModifyFunction p -> applyModifyFunction(structure, p);
            case LootEditOperation.ModifyCondition p -> applyModifyCondition(structure, p);
            case LootEditOperation.ModifyPoolFunction p -> applyModifyPoolFunction(structure, p);
            case LootEditOperation.ModifyPoolCondition p -> applyModifyPoolCondition(structure, p);
            case LootEditOperation.ModifyTableFunction p -> applyModifyTableFunction(structure, p);
            case LootEditOperation.ModifyFunctionCondition p -> applyModifyFunctionCondition(structure, p);
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

    private static LootTableStructure applyModifyBonusRolls(LootTableStructure structure, LootEditOperation.ModifyBonusRolls op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        LootPool newPool = pool.withBonusRolls(op.newBonusRolls());
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

    private static LootTableStructure applyModifyEntryQuality(LootTableStructure structure, LootEditOperation.ModifyEntryQuality op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry ->
            entry.withQuality(op.newQuality())
        );
    }

    private static LootTableStructure applyModifyEntryItem(LootTableStructure structure, LootEditOperation.ModifyEntryItem op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry ->
            entry.withItem(op.newItem())
        );
    }

    private static LootTableStructure applyModifyEntryType(LootTableStructure structure, LootEditOperation.ModifyEntryType op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry ->
            entry.withType(op.newType(), op.newName())
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

    // ===== Function Condition Operations =====

    private static LootTableStructure applyAddFunctionCondition(LootTableStructure structure, LootEditOperation.AddFunctionCondition op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.functionIndex() < 0 || op.functionIndex() >= entry.functions().size()) {
                return entry;
            }
            List<LootFunction> newFunctions = new ArrayList<>(entry.functions());
            LootFunction oldFunc = newFunctions.get(op.functionIndex());

            // Create new function with added condition
            List<LootCondition> newConditions = new ArrayList<>(oldFunc.conditions());
            newConditions.add(op.condition());
            LootFunction newFunc = new LootFunction(oldFunc.function(), oldFunc.parameters(), newConditions);
            newFunctions.set(op.functionIndex(), newFunc);

            return entry.withFunctions(newFunctions);
        });
    }

    private static LootTableStructure applyRemoveFunctionCondition(LootTableStructure structure, LootEditOperation.RemoveFunctionCondition op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.functionIndex() < 0 || op.functionIndex() >= entry.functions().size()) {
                return entry;
            }
            LootFunction oldFunc = entry.functions().get(op.functionIndex());
            if (op.conditionIndex() < 0 || op.conditionIndex() >= oldFunc.conditions().size()) {
                return entry;
            }

            List<LootFunction> newFunctions = new ArrayList<>(entry.functions());
            List<LootCondition> newConditions = new ArrayList<>(oldFunc.conditions());
            newConditions.remove(op.conditionIndex());
            LootFunction newFunc = new LootFunction(oldFunc.function(), oldFunc.parameters(), newConditions);
            newFunctions.set(op.functionIndex(), newFunc);

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

    // ===== Table Function Operations =====

    private static LootTableStructure applyAddTableFunction(LootTableStructure structure, LootEditOperation.AddTableFunction op) {
        List<LootFunction> newFunctions = new ArrayList<>(structure.functions());
        newFunctions.add(op.function());
        return structure.withFunctions(newFunctions);
    }

    private static LootTableStructure applyRemoveTableFunction(LootTableStructure structure, LootEditOperation.RemoveTableFunction op) {
        if (op.functionIndex() < 0 || op.functionIndex() >= structure.functions().size()) {
            return structure;
        }
        List<LootFunction> newFunctions = new ArrayList<>(structure.functions());
        newFunctions.remove(op.functionIndex());
        return structure.withFunctions(newFunctions);
    }

    // ===== Random Sequence Operations =====

    private static LootTableStructure applySetRandomSequence(LootTableStructure structure, LootEditOperation.SetRandomSequence op) {
        return structure.withRandomSequence(op.randomSequence());
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

    // ===== Composite Entry Child Operations =====

    private static LootTableStructure applyAddChild(LootTableStructure structure, LootEditOperation.AddChild op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (!entry.isComposite()) {
                return entry; // Not a composite entry, no change
            }
            return entry.withChildAdded(op.childIndex(), op.child());
        });
    }

    private static LootTableStructure applyRemoveChild(LootTableStructure structure, LootEditOperation.RemoveChild op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (!entry.isComposite()) {
                return entry; // Not a composite entry, no change
            }
            return entry.withChildRemoved(op.childIndex());
        });
    }

    private static LootTableStructure applyModifyChild(LootTableStructure structure, LootEditOperation.ModifyChild op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (!entry.isComposite()) {
                return entry; // Not a composite entry, no change
            }
            return entry.withChildReplaced(op.childIndex(), op.newChild());
        });
    }

    // ===== Modify Operations (for editing existing functions/conditions) =====

    private static LootTableStructure applyModifyFunction(LootTableStructure structure, LootEditOperation.ModifyFunction op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.functionIndex() < 0 || op.functionIndex() >= entry.functions().size()) {
                return entry;
            }
            List<LootFunction> newFunctions = new ArrayList<>(entry.functions());
            newFunctions.set(op.functionIndex(), op.newFunction());
            return entry.withFunctions(newFunctions);
        });
    }

    private static LootTableStructure applyModifyCondition(LootTableStructure structure, LootEditOperation.ModifyCondition op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.conditionIndex() < 0 || op.conditionIndex() >= entry.conditions().size()) {
                return entry;
            }
            List<LootCondition> newConditions = new ArrayList<>(entry.conditions());
            newConditions.set(op.conditionIndex(), op.newCondition());
            return entry.withConditions(newConditions);
        });
    }

    private static LootTableStructure applyModifyPoolFunction(LootTableStructure structure, LootEditOperation.ModifyPoolFunction op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        if (op.functionIndex() < 0 || op.functionIndex() >= pool.functions().size()) {
            return structure;
        }
        List<LootFunction> newFunctions = new ArrayList<>(pool.functions());
        newFunctions.set(op.functionIndex(), op.newFunction());
        return structure.withPoolReplaced(op.poolIndex(), pool.withFunctions(newFunctions));
    }

    private static LootTableStructure applyModifyPoolCondition(LootTableStructure structure, LootEditOperation.ModifyPoolCondition op) {
        if (op.poolIndex() < 0 || op.poolIndex() >= structure.pools().size()) {
            return structure;
        }
        LootPool pool = structure.pools().get(op.poolIndex());
        if (op.conditionIndex() < 0 || op.conditionIndex() >= pool.conditions().size()) {
            return structure;
        }
        List<LootCondition> newConditions = new ArrayList<>(pool.conditions());
        newConditions.set(op.conditionIndex(), op.newCondition());
        return structure.withPoolReplaced(op.poolIndex(), pool.withConditions(newConditions));
    }

    private static LootTableStructure applyModifyTableFunction(LootTableStructure structure, LootEditOperation.ModifyTableFunction op) {
        if (op.functionIndex() < 0 || op.functionIndex() >= structure.functions().size()) {
            return structure;
        }
        List<LootFunction> newFunctions = new ArrayList<>(structure.functions());
        newFunctions.set(op.functionIndex(), op.newFunction());
        return structure.withFunctions(newFunctions);
    }

    private static LootTableStructure applyModifyFunctionCondition(LootTableStructure structure, LootEditOperation.ModifyFunctionCondition op) {
        return modifyEntry(structure, op.poolIndex(), op.entryIndex(), entry -> {
            if (op.functionIndex() < 0 || op.functionIndex() >= entry.functions().size()) {
                return entry;
            }
            LootFunction oldFunc = entry.functions().get(op.functionIndex());
            if (op.conditionIndex() < 0 || op.conditionIndex() >= oldFunc.conditions().size()) {
                return entry;
            }

            List<LootFunction> newFunctions = new ArrayList<>(entry.functions());
            List<LootCondition> newConditions = new ArrayList<>(oldFunc.conditions());
            newConditions.set(op.conditionIndex(), op.newCondition());
            LootFunction newFunc = new LootFunction(oldFunc.function(), oldFunc.parameters(), newConditions);
            newFunctions.set(op.functionIndex(), newFunc);

            return entry.withFunctions(newFunctions);
        });
    }
}
