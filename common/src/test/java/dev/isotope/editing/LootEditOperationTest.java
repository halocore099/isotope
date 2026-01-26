package dev.isotope.editing;

import dev.isotope.data.loot.*;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LootEditOperation sealed interface and its implementations.
 */
@UnitTest
class LootEditOperationTest {

    // ===== Pool Operations =====

    @Nested
    @DisplayName("AddPool operation")
    class AddPoolOperation {

        @Test
        @DisplayName("stores pool and index")
        void storesPoolAndIndex() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(3, List.of(entry));
            var op = new LootEditOperation.AddPool(2, pool);

            assertThat(op.index()).isEqualTo(2);
            assertThat(op.pool()).isSameAs(pool);
        }

        @Test
        @DisplayName("description includes index")
        void descriptionIncludesIndex() {
            var pool = LootPool.simple(1, List.of());
            var op = new LootEditOperation.AddPool(0, pool);

            assertThat(op.getDescription()).contains("Add pool").contains("0");
        }
    }

    @Nested
    @DisplayName("RemovePool operation")
    class RemovePoolOperation {

        @Test
        @DisplayName("stores pool index")
        void storesPoolIndex() {
            var op = new LootEditOperation.RemovePool(3);

            assertThat(op.poolIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("description shows 1-based index")
        void descriptionShowsOneBasedIndex() {
            var op = new LootEditOperation.RemovePool(2);

            assertThat(op.getDescription()).contains("pool #3");
        }
    }

    @Nested
    @DisplayName("ModifyPoolRolls operation")
    class ModifyPoolRollsOperation {

        @Test
        @DisplayName("stores pool index and new rolls")
        void storesData() {
            var rolls = NumberProvider.uniform(1, 5);
            var op = new LootEditOperation.ModifyPoolRolls(0, rolls);

            assertThat(op.poolIndex()).isEqualTo(0);
            assertThat(op.newRolls()).isSameAs(rolls);
        }

        @Test
        @DisplayName("description includes rolls value")
        void descriptionIncludesRolls() {
            var rolls = NumberProvider.uniform(2, 8);
            var op = new LootEditOperation.ModifyPoolRolls(0, rolls);

            assertThat(op.getDescription()).contains("2-8");
        }
    }

    // ===== Entry Operations =====

    @Nested
    @DisplayName("AddEntry operation")
    class AddEntryOperation {

        @Test
        @DisplayName("stores all indices and entry")
        void storesData() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var op = new LootEditOperation.AddEntry(1, 2, entry);

            assertThat(op.poolIndex()).isEqualTo(1);
            assertThat(op.entryIndex()).isEqualTo(2);
            assertThat(op.entry()).isSameAs(entry);
        }

        @Test
        @DisplayName("description includes item name")
        void descriptionIncludesItemName() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            var op = new LootEditOperation.AddEntry(0, 0, entry);

            assertThat(op.getDescription()).contains("emerald");
        }
    }

    @Nested
    @DisplayName("RemoveEntry operation")
    class RemoveEntryOperation {

        @Test
        @DisplayName("stores indices")
        void storesIndices() {
            var op = new LootEditOperation.RemoveEntry(2, 5);

            assertThat(op.poolIndex()).isEqualTo(2);
            assertThat(op.entryIndex()).isEqualTo(5);
        }

        @Test
        @DisplayName("description shows 1-based indices")
        void descriptionShowsOneBasedIndices() {
            var op = new LootEditOperation.RemoveEntry(0, 2);

            assertThat(op.getDescription()).contains("entry #3").contains("pool #1");
        }
    }

    @Nested
    @DisplayName("ModifyEntryWeight operation")
    class ModifyEntryWeightOperation {

        @Test
        @DisplayName("stores all data")
        void storesData() {
            var op = new LootEditOperation.ModifyEntryWeight(1, 2, 15);

            assertThat(op.poolIndex()).isEqualTo(1);
            assertThat(op.entryIndex()).isEqualTo(2);
            assertThat(op.newWeight()).isEqualTo(15);
        }

        @Test
        @DisplayName("description includes new weight")
        void descriptionIncludesWeight() {
            var op = new LootEditOperation.ModifyEntryWeight(0, 0, 42);

            assertThat(op.getDescription()).contains("42");
        }
    }

    @Nested
    @DisplayName("ModifyEntryItem operation")
    class ModifyEntryItemOperation {

        @Test
        @DisplayName("stores item ID")
        void storesItemId() {
            var itemId = TestFixtures.mockId("minecraft", "netherite_ingot");
            var op = new LootEditOperation.ModifyEntryItem(0, 1, itemId);

            assertThat(op.newItem()).isEqualTo(itemId);
        }

        @Test
        @DisplayName("description includes item path")
        void descriptionIncludesPath() {
            var itemId = TestFixtures.mockId("minecraft", "golden_apple");
            var op = new LootEditOperation.ModifyEntryItem(0, 0, itemId);

            assertThat(op.getDescription()).contains("golden_apple");
        }
    }

    @Nested
    @DisplayName("ModifyEntryType operation")
    class ModifyEntryTypeOperation {

        @Test
        @DisplayName("stores type and name")
        void storesTypeAndName() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            var op = new LootEditOperation.ModifyEntryType(0, 0, LootEntry.TYPE_LOOT_TABLE, Optional.of(tableId));

            assertThat(op.newType()).isEqualTo(LootEntry.TYPE_LOOT_TABLE);
            assertThat(op.newName()).hasValue(tableId);
        }

        @Test
        @DisplayName("handles empty type correctly")
        void handlesEmptyType() {
            var op = new LootEditOperation.ModifyEntryType(0, 0, LootEntry.TYPE_EMPTY, Optional.empty());

            assertThat(op.newName()).isEmpty();
            assertThat(op.getDescription()).contains("empty");
        }
    }

    @Nested
    @DisplayName("SetItemCount operation")
    class SetItemCountOperation {

        @Test
        @DisplayName("stores count")
        void storesCount() {
            var count = NumberProvider.uniform(2, 5);
            var op = new LootEditOperation.SetItemCount(0, 1, count);

            assertThat(op.count()).isSameAs(count);
        }

        @Test
        @DisplayName("description includes count range")
        void descriptionIncludesRange() {
            var count = NumberProvider.uniform(1, 8);
            var op = new LootEditOperation.SetItemCount(0, 0, count);

            assertThat(op.getDescription()).contains("1-8");
        }
    }

    // ===== Function Operations =====

    @Nested
    @DisplayName("AddFunction operation")
    class AddFunctionOperation {

        @Test
        @DisplayName("stores function")
        void storesFunction() {
            var func = LootFunction.setCount(5);
            var op = new LootEditOperation.AddFunction(0, 1, func);

            assertThat(op.function()).isSameAs(func);
        }

        @Test
        @DisplayName("description includes function name")
        void descriptionIncludesFunctionName() {
            var func = LootFunction.enchantRandomly();
            var op = new LootEditOperation.AddFunction(0, 0, func);

            assertThat(op.getDescription()).containsIgnoringCase("enchant");
        }
    }

    @Nested
    @DisplayName("RemoveFunction operation")
    class RemoveFunctionOperation {

        @Test
        @DisplayName("stores all indices")
        void storesIndices() {
            var op = new LootEditOperation.RemoveFunction(1, 2, 3);

            assertThat(op.poolIndex()).isEqualTo(1);
            assertThat(op.entryIndex()).isEqualTo(2);
            assertThat(op.functionIndex()).isEqualTo(3);
        }
    }

    // ===== Condition Operations =====

    @Nested
    @DisplayName("AddCondition operation")
    class AddConditionOperation {

        @Test
        @DisplayName("stores condition")
        void storesCondition() {
            var cond = new LootCondition("minecraft:killed_by_player", null);
            var op = new LootEditOperation.AddCondition(0, 1, cond);

            assertThat(op.condition()).isSameAs(cond);
        }
    }

    @Nested
    @DisplayName("RemoveCondition operation")
    class RemoveConditionOperation {

        @Test
        @DisplayName("stores all indices")
        void storesIndices() {
            var op = new LootEditOperation.RemoveCondition(2, 3, 4);

            assertThat(op.poolIndex()).isEqualTo(2);
            assertThat(op.entryIndex()).isEqualTo(3);
            assertThat(op.conditionIndex()).isEqualTo(4);
        }
    }

    // ===== Pool-Level Operations =====

    @Nested
    @DisplayName("AddPoolFunction operation")
    class AddPoolFunctionOperation {

        @Test
        @DisplayName("stores pool index and function")
        void storesData() {
            var func = LootFunction.setCount(3);
            var op = new LootEditOperation.AddPoolFunction(2, func);

            assertThat(op.poolIndex()).isEqualTo(2);
            assertThat(op.function()).isSameAs(func);
        }
    }

    @Nested
    @DisplayName("AddPoolCondition operation")
    class AddPoolConditionOperation {

        @Test
        @DisplayName("stores pool index and condition")
        void storesData() {
            var cond = new LootCondition("minecraft:survives_explosion", null);
            var op = new LootEditOperation.AddPoolCondition(1, cond);

            assertThat(op.poolIndex()).isEqualTo(1);
            assertThat(op.condition()).isSameAs(cond);
        }
    }

    // ===== Table-Level Operations =====

    @Nested
    @DisplayName("AddTableFunction operation")
    class AddTableFunctionOperation {

        @Test
        @DisplayName("stores function")
        void storesFunction() {
            var func = LootFunction.setCount(2);
            var op = new LootEditOperation.AddTableFunction(func);

            assertThat(op.function()).isSameAs(func);
        }

        @Test
        @DisplayName("description mentions table")
        void descriptionMentionsTable() {
            var func = LootFunction.setCount(1);
            var op = new LootEditOperation.AddTableFunction(func);

            assertThat(op.getDescription()).containsIgnoringCase("table");
        }
    }

    @Nested
    @DisplayName("RemoveTableFunction operation")
    class RemoveTableFunctionOperation {

        @Test
        @DisplayName("stores function index")
        void storesFunctionIndex() {
            var op = new LootEditOperation.RemoveTableFunction(5);

            assertThat(op.functionIndex()).isEqualTo(5);
        }
    }

    // ===== Random Sequence Operations =====

    @Nested
    @DisplayName("SetRandomSequence operation")
    class SetRandomSequenceOperation {

        @Test
        @DisplayName("stores sequence ID")
        void storesSequenceId() {
            var seqId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            var op = new LootEditOperation.SetRandomSequence(Optional.of(seqId));

            assertThat(op.randomSequence()).hasValue(seqId);
        }

        @Test
        @DisplayName("description for setting sequence")
        void descriptionForSet() {
            var seqId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.SetRandomSequence(Optional.of(seqId));

            assertThat(op.getDescription()).contains("Set random sequence");
        }

        @Test
        @DisplayName("description for removing sequence")
        void descriptionForRemove() {
            var op = new LootEditOperation.SetRandomSequence(Optional.empty());

            assertThat(op.getDescription()).contains("Remove random sequence");
        }
    }

    // ===== Composite Entry Operations =====

    @Nested
    @DisplayName("AddChild operation")
    class AddChildOperation {

        @Test
        @DisplayName("stores all data")
        void storesData() {
            var child = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var op = new LootEditOperation.AddChild(0, 1, 2, child);

            assertThat(op.poolIndex()).isEqualTo(0);
            assertThat(op.entryIndex()).isEqualTo(1);
            assertThat(op.childIndex()).isEqualTo(2);
            assertThat(op.child()).isSameAs(child);
        }
    }

    @Nested
    @DisplayName("RemoveChild operation")
    class RemoveChildOperation {

        @Test
        @DisplayName("stores all indices")
        void storesIndices() {
            var op = new LootEditOperation.RemoveChild(1, 2, 3);

            assertThat(op.poolIndex()).isEqualTo(1);
            assertThat(op.entryIndex()).isEqualTo(2);
            assertThat(op.childIndex()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("ModifyChild operation")
    class ModifyChildOperation {

        @Test
        @DisplayName("stores all data")
        void storesData() {
            var newChild = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 5);
            var op = new LootEditOperation.ModifyChild(0, 1, 2, newChild);

            assertThat(op.newChild()).isSameAs(newChild);
        }
    }

    // ===== Sealed Interface =====

    @Nested
    @DisplayName("Sealed interface")
    class SealedInterface {

        @Test
        @DisplayName("all operation types implement LootEditOperation")
        void allTypesImplementInterface() {
            assertThat(new LootEditOperation.AddPool(0, LootPool.simple(1, List.of()))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.RemovePool(0)).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.ModifyPoolRolls(0, NumberProvider.constant(1))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.ModifyBonusRolls(0, NumberProvider.constant(0))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.AddEntry(0, 0, LootEntry.empty(1))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.RemoveEntry(0, 0)).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.ModifyEntryWeight(0, 0, 1)).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.ModifyEntryQuality(0, 0, 0)).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.ModifyEntryItem(0, 0, TestFixtures.mockId("minecraft", "diamond"))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.ModifyEntryType(0, 0, LootEntry.TYPE_EMPTY, Optional.empty())).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.SetItemCount(0, 0, NumberProvider.constant(1))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.AddFunction(0, 0, LootFunction.setCount(1))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.RemoveFunction(0, 0, 0)).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.AddCondition(0, 0, new LootCondition("test", null))).isInstanceOf(LootEditOperation.class);
            assertThat(new LootEditOperation.RemoveCondition(0, 0, 0)).isInstanceOf(LootEditOperation.class);
        }
    }
}
