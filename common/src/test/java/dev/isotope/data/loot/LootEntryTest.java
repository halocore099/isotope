package dev.isotope.data.loot;

import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LootEntry record.
 */
@UnitTest
class LootEntryTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("item() factory method")
    class ItemFactory {

        @Test
        @DisplayName("creates item entry with default weight 1")
        void createsItemWithDefaultWeight() {
            var id = TestFixtures.mockId("minecraft", "diamond");
            LootEntry entry = LootEntry.item(id);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_ITEM);
            assertThat(entry.name()).hasValue(id);
            assertThat(entry.weight()).isEqualTo(1);
            assertThat(entry.quality()).isEqualTo(0);
            assertThat(entry.conditions()).isEmpty();
            assertThat(entry.functions()).isEmpty();
            assertThat(entry.children()).isEmpty();
        }

        @Test
        @DisplayName("creates item entry with specified weight")
        void createsItemWithWeight() {
            var id = TestFixtures.mockId("minecraft", "diamond");
            LootEntry entry = LootEntry.item(id, 5);

            assertThat(entry.weight()).isEqualTo(5);
        }

        @Test
        @DisplayName("creates item entry with count function")
        void createsItemWithCount() {
            var id = TestFixtures.mockId("minecraft", "gold_ingot");
            LootEntry entry = LootEntry.item(id, 3, 1, 4);

            assertThat(entry.weight()).isEqualTo(3);
            assertThat(entry.functions()).hasSize(1);
            assertThat(entry.functions().get(0).isSetCount()).isTrue();
        }

        @Test
        @DisplayName("does not add count function when min=max=1")
        void noCountFunctionForDefault() {
            var id = TestFixtures.mockId("minecraft", "stick");
            LootEntry entry = LootEntry.item(id, 1, 1, 1);

            assertThat(entry.functions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("empty() factory method")
    class EmptyFactory {

        @Test
        @DisplayName("creates empty entry with weight")
        void createsEmptyEntry() {
            LootEntry entry = LootEntry.empty(10);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_EMPTY);
            assertThat(entry.name()).isEmpty();
            assertThat(entry.weight()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("lootTable() factory method")
    class LootTableFactory {

        @Test
        @DisplayName("creates loot table reference entry")
        void createsLootTableEntry() {
            var id = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            LootEntry entry = LootEntry.lootTable(id, 2);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_LOOT_TABLE);
            assertThat(entry.name()).hasValue(id);
            assertThat(entry.weight()).isEqualTo(2);
        }
    }

    // ===== Type Checking =====

    @Nested
    @DisplayName("Type checking methods")
    class TypeChecking {

        @Test
        @DisplayName("isItem() returns true for item entries")
        void isItemForItemEntry() {
            LootEntry entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            assertThat(entry.isItem()).isTrue();
            assertThat(entry.isEmpty()).isFalse();
            assertThat(entry.isComposite()).isFalse();
        }

        @Test
        @DisplayName("isEmpty() returns true for empty entries")
        void isEmptyForEmptyEntry() {
            LootEntry entry = LootEntry.empty(5);
            assertThat(entry.isEmpty()).isTrue();
            assertThat(entry.isItem()).isFalse();
        }

        @Test
        @DisplayName("isComposite() returns true for alternatives")
        void isCompositeForAlternatives() {
            LootEntry entry = new LootEntry(
                LootEntry.TYPE_ALTERNATIVES,
                Optional.empty(),
                1, 0,
                List.of(), List.of(),
                List.of(LootEntry.item(TestFixtures.mockId("minecraft", "diamond")))
            );
            assertThat(entry.isComposite()).isTrue();
        }

        @Test
        @DisplayName("isComposite() returns true for group")
        void isCompositeForGroup() {
            LootEntry entry = new LootEntry(
                LootEntry.TYPE_GROUP,
                Optional.empty(),
                1, 0,
                List.of(), List.of(), List.of()
            );
            assertThat(entry.isComposite()).isTrue();
        }

        @Test
        @DisplayName("isComposite() returns true for sequence")
        void isCompositeForSequence() {
            LootEntry entry = new LootEntry(
                LootEntry.TYPE_SEQUENCE,
                Optional.empty(),
                1, 0,
                List.of(), List.of(), List.of()
            );
            assertThat(entry.isComposite()).isTrue();
        }
    }

    // ===== Display Methods =====

    @Nested
    @DisplayName("getDisplayName()")
    class GetDisplayNameMethod {

        @Test
        @DisplayName("returns formatted item name for item entries")
        void formattedItemName() {
            LootEntry entry = LootEntry.item(TestFixtures.mockId("minecraft", "golden_apple"));
            assertThat(entry.getDisplayName()).isEqualTo("golden apple");
        }

        @Test
        @DisplayName("returns (empty) for empty entries")
        void emptyEntryName() {
            LootEntry entry = LootEntry.empty(5);
            assertThat(entry.getDisplayName()).isEqualTo("(empty)");
        }

        @Test
        @DisplayName("returns Table: prefix for loot table entries")
        void lootTableName() {
            LootEntry entry = LootEntry.lootTable(TestFixtures.mockId("minecraft", "chests/village"), 1);
            assertThat(entry.getDisplayName()).startsWith("Table:");
        }

        @Test
        @DisplayName("returns type with child count for composites")
        void compositeName() {
            var child1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var child2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootEntry entry = new LootEntry(
                LootEntry.TYPE_ALTERNATIVES,
                Optional.empty(),
                1, 0,
                List.of(), List.of(),
                List.of(child1, child2)
            );
            assertThat(entry.getDisplayName()).isEqualTo("alternatives (2)");
        }
    }

    // ===== Count Methods =====

    @Nested
    @DisplayName("Count-related methods")
    class CountMethods {

        @Test
        @DisplayName("getCountString() returns '1' when no set_count function")
        void defaultCountString() {
            LootEntry entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            assertThat(entry.getCountString()).isEqualTo("1");
        }

        @Test
        @DisplayName("getSetCountFunction() returns null when no set_count")
        void noSetCountFunction() {
            LootEntry entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            assertThat(entry.getSetCountFunction()).isNull();
        }

        @Test
        @DisplayName("getSetCountFunction() returns the function when present")
        void hasSetCountFunction() {
            LootEntry entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1, 2, 5);
            assertThat(entry.getSetCountFunction()).isNotNull();
            assertThat(entry.getSetCountFunction().isSetCount()).isTrue();
        }
    }

    // ===== Immutable Copy Methods =====

    @Nested
    @DisplayName("withX() copy methods")
    class CopyMethods {

        @Test
        @DisplayName("withWeight() creates copy with new weight")
        void withWeight() {
            LootEntry original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            LootEntry modified = original.withWeight(10);

            assertThat(modified.weight()).isEqualTo(10);
            assertThat(original.weight()).isEqualTo(1); // Original unchanged
            assertThat(modified.name()).isEqualTo(original.name());
        }

        @Test
        @DisplayName("withQuality() creates copy with new quality")
        void withQuality() {
            LootEntry original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootEntry modified = original.withQuality(5);

            assertThat(modified.quality()).isEqualTo(5);
            assertThat(original.quality()).isEqualTo(0);
        }

        @Test
        @DisplayName("withItem() creates copy with different item")
        void withItem() {
            var diamond = TestFixtures.mockId("minecraft", "diamond");
            var emerald = TestFixtures.mockId("minecraft", "emerald");

            LootEntry original = LootEntry.item(diamond, 5);
            LootEntry modified = original.withItem(emerald);

            assertThat(modified.name()).hasValue(emerald);
            assertThat(modified.weight()).isEqualTo(5); // Weight preserved
            assertThat(original.name()).hasValue(diamond);
        }

        @Test
        @DisplayName("withFunctions() creates copy with new functions")
        void withFunctions() {
            LootEntry original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            List<LootFunction> funcs = List.of(LootFunction.setCount(2, 5));
            LootEntry modified = original.withFunctions(funcs);

            assertThat(modified.functions()).hasSize(1);
            assertThat(original.functions()).isEmpty();
        }

        @Test
        @DisplayName("withConditions() creates copy with new conditions")
        void withConditions() {
            LootEntry original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            List<LootCondition> conds = List.of(new LootCondition("minecraft:killed_by_player", null));
            LootEntry modified = original.withConditions(conds);

            assertThat(modified.conditions()).hasSize(1);
            assertThat(original.conditions()).isEmpty();
        }

        @Test
        @DisplayName("withType() converts to empty entry")
        void withTypeToEmpty() {
            LootEntry original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            LootEntry modified = original.withType(LootEntry.TYPE_EMPTY, Optional.empty());

            assertThat(modified.type()).isEqualTo(LootEntry.TYPE_EMPTY);
            assertThat(modified.name()).isEmpty();
            assertThat(modified.weight()).isEqualTo(5); // Weight preserved
            assertThat(modified.functions()).isEmpty(); // Functions cleared for empty
        }

        @Test
        @DisplayName("withChildren() creates copy with new children")
        void withChildren() {
            var child = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootEntry original = new LootEntry(
                LootEntry.TYPE_ALTERNATIVES, Optional.empty(),
                1, 0, List.of(), List.of(), List.of()
            );
            LootEntry modified = original.withChildren(List.of(child));

            assertThat(modified.children()).hasSize(1);
            assertThat(original.children()).isEmpty();
        }

        @Test
        @DisplayName("withChildAdded() adds child at index")
        void withChildAdded() {
            var child1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var child2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootEntry original = new LootEntry(
                LootEntry.TYPE_GROUP, Optional.empty(),
                1, 0, List.of(), List.of(), List.of(child1)
            );
            LootEntry modified = original.withChildAdded(0, child2);

            assertThat(modified.children()).hasSize(2);
            assertThat(modified.children().get(0).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }

        @Test
        @DisplayName("withChildRemoved() removes child at index")
        void withChildRemoved() {
            var child1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var child2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootEntry original = new LootEntry(
                LootEntry.TYPE_GROUP, Optional.empty(),
                1, 0, List.of(), List.of(), List.of(child1, child2)
            );
            LootEntry modified = original.withChildRemoved(0);

            assertThat(modified.children()).hasSize(1);
            assertThat(modified.children().get(0).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }

        @Test
        @DisplayName("withChildRemoved() with invalid index returns same object")
        void withChildRemovedInvalidIndex() {
            var child = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootEntry original = new LootEntry(
                LootEntry.TYPE_GROUP, Optional.empty(),
                1, 0, List.of(), List.of(), List.of(child)
            );
            LootEntry modified = original.withChildRemoved(5);

            assertThat(modified).isSameAs(original);
        }

        @Test
        @DisplayName("withChildReplaced() replaces child at index")
        void withChildReplaced() {
            var child1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var child2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootEntry original = new LootEntry(
                LootEntry.TYPE_GROUP, Optional.empty(),
                1, 0, List.of(), List.of(), List.of(child1)
            );
            LootEntry modified = original.withChildReplaced(0, child2);

            assertThat(modified.children()).hasSize(1);
            assertThat(modified.children().get(0).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }
    }

    // ===== Record Equality =====

    @Nested
    @DisplayName("Record equality")
    class Equality {

        @Test
        @DisplayName("entries with same values are equal")
        void equalEntries() {
            var id = TestFixtures.mockId("minecraft", "diamond");
            LootEntry entry1 = LootEntry.item(id, 5);
            LootEntry entry2 = LootEntry.item(id, 5);

            assertThat(entry1).isEqualTo(entry2);
            assertThat(entry1.hashCode()).isEqualTo(entry2.hashCode());
        }

        @Test
        @DisplayName("entries with different weights are not equal")
        void differentWeights() {
            var id = TestFixtures.mockId("minecraft", "diamond");
            LootEntry entry1 = LootEntry.item(id, 5);
            LootEntry entry2 = LootEntry.item(id, 10);

            assertThat(entry1).isNotEqualTo(entry2);
        }
    }
}
