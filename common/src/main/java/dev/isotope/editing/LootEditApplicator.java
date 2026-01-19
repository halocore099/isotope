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
        if (op instanceof LootEditOperation.AddPool p) return applyAddPool(structure, p);
        if (op instanceof LootEditOperation.RemovePool p) return applyRemovePool(structure, p);
        if (op instanceof LootEditOperation.ModifyPoolRolls p) return applyModifyPoolRolls(structure, p);
        if (op instanceof LootEditOperation.ModifyBonusRolls p) return applyModifyBonusRolls(structure, p);
        if (op instanceof LootEditOperation.AddEntry p) return applyAddEntry(structure, p);
        if (op instanceof LootEditOperation.RemoveEntry p) return applyRemoveEntry(structure, p);
        if (op instanceof LootEditOperation.ModifyEntryWeight p) return applyModifyEntryWeight(structure, p);
        if (op instanceof LootEditOperation.ModifyEntryQuality p) return applyModifyEntryQuality(structure, p);
        if (op instanceof LootEditOperation.ModifyEntryItem p) return applyModifyEntryItem(structure, p);
        if (op instanceof LootEditOperation.ModifyEntryType p) return applyModifyEntryType(structure, p);
        if (op instanceof LootEditOperation.SetItemCount p) return applySetItemCount(structure, p);
        if (op instanceof LootEditOperation.AddFunction p) return applyAddFunction(structure, p);
        if (op instanceof LootEditOperation.RemoveFunction p) return applyRemoveFunction(structure, p);
        if (op instanceof LootEditOperation.AddCondition p) return applyAddCondition(structure, p);
        if (op instanceof LootEditOperation.RemoveCondition p) return applyRemoveCondition(structure, p);
        if (op instanceof LootEditOperation.AddPoolFunction p) return applyAddPoolFunction(structure, p);
        if (op instanceof LootEditOperation.RemovePoolFunction p) return applyRemovePoolFunction(structure, p);
        if (op instanceof LootEditOperation.AddPoolCondition p) return applyAddPoolCondition(structure, p);
        if (op instanceof LootEditOperation.RemovePoolCondition p) return applyRemovePoolCondition(structure, p);
        if (op instanceof LootEditOperation.AddTableFunction p) return applyAddTableFunction(structure, p);
        if (op instanceof LootEditOperation.RemoveTableFunction p) return applyRemoveTableFunction(structure, p);
        if (op instanceof LootEditOperation.SetRandomSequence p) return applySetRandomSequence(structure, p);
        if (op instanceof LootEditOperation.AddChild p) return applyAddChild(structure, p);
        if (op instanceof LootEditOperation.RemoveChild p) return applyRemoveChild(structure, p);
        if (op instanceof LootEditOperation.ModifyChild p) return applyModifyChild(structure, p);
        return structure;
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
        if (count instanceof NumberProvider.Constant c) {
            return LootFunction.setCount((int) c.value());
        } else if (count instanceof NumberProvider.Uniform u) {
            return LootFunction.setCount((int) u.min(), (int) u.max());
        } else if (count instanceof NumberProvider.Binomial b) {
            // Binomial is not directly supported by set_count, use range approximation
            return LootFunction.setCount(0, b.n());
        }
        return LootFunction.setCount(1);
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
}
