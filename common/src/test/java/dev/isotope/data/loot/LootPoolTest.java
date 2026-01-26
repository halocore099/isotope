package dev.isotope.data.loot;

import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LootPool record.
 */
@UnitTest
class LootPoolTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("simple() factory method")
    class SimpleFactory {

        @Test
        @DisplayName("creates pool with constant rolls")
        void createsSimplePool() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootPool pool = LootPool.simple(3, List.of(entry));

            assertThat(pool.rolls()).isInstanceOf(NumberProvider.Constant.class);
            assertThat(pool.rolls().getMin()).isEqualTo(3);
            assertThat(pool.rolls().getMax()).isEqualTo(3);
            assertThat(pool.bonusRolls().getMin()).isEqualTo(0);
            assertThat(pool.entries()).hasSize(1);
            assertThat(pool.conditions()).isEmpty();
            assertThat(pool.functions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("withRolls() factory method")
    class WithRollsFactory {

        @Test
        @DisplayName("creates pool with uniform roll range")
        void createsUniformPool() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootPool pool = LootPool.withRolls(1, 5, List.of(entry));

            assertThat(pool.rolls()).isInstanceOf(NumberProvider.Uniform.class);
            assertThat(pool.rolls().getMin()).isEqualTo(1);
            assertThat(pool.rolls().getMax()).isEqualTo(5);
        }
    }

    // ===== Helper Methods =====

    @Nested
    @DisplayName("Helper methods")
    class HelperMethods {

        @Test
        @DisplayName("getTotalWeight() sums all entry weights")
        void totalWeight() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 3);
            var entry3 = LootEntry.empty(10);
            LootPool pool = LootPool.simple(1, List.of(entry1, entry2, entry3));

            assertThat(pool.getTotalWeight()).isEqualTo(18);
        }

        @Test
        @DisplayName("getTotalWeight() returns 0 for empty pool")
        void totalWeightEmpty() {
            LootPool pool = LootPool.simple(1, List.of());
            assertThat(pool.getTotalWeight()).isEqualTo(0);
        }

        @Test
        @DisplayName("getRollsString() returns formatted string for constant")
        void rollsStringConstant() {
            LootPool pool = LootPool.simple(5, List.of());
            assertThat(pool.getRollsString()).isEqualTo("5");
        }

        @Test
        @DisplayName("getRollsString() returns range for uniform")
        void rollsStringUniform() {
            LootPool pool = LootPool.withRolls(2, 8, List.of());
            assertThat(pool.getRollsString()).isEqualTo("2-8");
        }

        @Test
        @DisplayName("getEntryCount() returns number of entries")
        void entryCount() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootPool pool = LootPool.simple(1, List.of(entry1, entry2));

            assertThat(pool.getEntryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("hasConditions() returns false when no conditions")
        void noConditions() {
            LootPool pool = LootPool.simple(1, List.of());
            assertThat(pool.hasConditions()).isFalse();
        }

        @Test
        @DisplayName("hasConditions() returns true when has conditions")
        void hasConditions() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var condition = new LootCondition("minecraft:killed_by_player", null);
            LootPool pool = new LootPool(
                "",
                NumberProvider.constant(1),
                NumberProvider.constant(0),
                List.of(entry),
                List.of(condition),
                List.of()
            );
            assertThat(pool.hasConditions()).isTrue();
        }

        @Test
        @DisplayName("hasFunctions() returns correct value")
        void hasFunctions() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootPool poolWithout = LootPool.simple(1, List.of(entry));
            assertThat(poolWithout.hasFunctions()).isFalse();

            LootPool poolWith = new LootPool(
                "",
                NumberProvider.constant(1),
                NumberProvider.constant(0),
                List.of(entry),
                List.of(),
                List.of(LootFunction.setCount(2))
            );
            assertThat(poolWith.hasFunctions()).isTrue();
        }
    }

    // ===== Immutable Copy Methods =====

    @Nested
    @DisplayName("withX() copy methods")
    class CopyMethods {

        @Test
        @DisplayName("withName() creates copy with new name")
        void withName() {
            LootPool original = LootPool.simple(1, List.of());
            LootPool modified = original.withName("main_pool");

            assertThat(modified.name()).isEqualTo("main_pool");
            assertThat(original.name()).isEmpty();
        }

        @Test
        @DisplayName("withRolls() creates copy with new rolls")
        void withRolls() {
            LootPool original = LootPool.simple(1, List.of());
            LootPool modified = original.withRolls(NumberProvider.uniform(2, 5));

            assertThat(modified.rolls().getMin()).isEqualTo(2);
            assertThat(modified.rolls().getMax()).isEqualTo(5);
            assertThat(original.rolls().getMin()).isEqualTo(1);
        }

        @Test
        @DisplayName("withBonusRolls() creates copy with new bonus rolls")
        void withBonusRolls() {
            LootPool original = LootPool.simple(1, List.of());
            LootPool modified = original.withBonusRolls(NumberProvider.uniform(0, 2));

            assertThat(modified.bonusRolls().getMax()).isEqualTo(2);
            assertThat(original.bonusRolls().getMax()).isEqualTo(0);
        }

        @Test
        @DisplayName("withEntries() creates copy with new entries")
        void withEntries() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootPool original = LootPool.simple(1, List.of(entry1));
            LootPool modified = original.withEntries(List.of(entry1, entry2));

            assertThat(modified.entries()).hasSize(2);
            assertThat(original.entries()).hasSize(1);
        }

        @Test
        @DisplayName("withEntryAdded() adds entry at index")
        void withEntryAdded() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootPool original = LootPool.simple(1, List.of(entry1));
            LootPool modified = original.withEntryAdded(0, entry2);

            assertThat(modified.entries()).hasSize(2);
            assertThat(modified.entries().get(0).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }

        @Test
        @DisplayName("withEntryAdded() with -1 adds at end")
        void withEntryAddedAtEnd() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootPool original = LootPool.simple(1, List.of(entry1));
            LootPool modified = original.withEntryAdded(-1, entry2);

            assertThat(modified.entries()).hasSize(2);
            assertThat(modified.entries().get(1).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }

        @Test
        @DisplayName("withEntryRemoved() removes entry at index")
        void withEntryRemoved() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootPool original = LootPool.simple(1, List.of(entry1, entry2));
            LootPool modified = original.withEntryRemoved(0);

            assertThat(modified.entries()).hasSize(1);
            assertThat(modified.entries().get(0).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }

        @Test
        @DisplayName("withEntryRemoved() with invalid index returns same object")
        void withEntryRemovedInvalid() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            LootPool original = LootPool.simple(1, List.of(entry));
            LootPool modified = original.withEntryRemoved(5);

            assertThat(modified).isSameAs(original);
        }

        @Test
        @DisplayName("withEntryReplaced() replaces entry at index")
        void withEntryReplaced() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"));
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"));
            LootPool original = LootPool.simple(1, List.of(entry1));
            LootPool modified = original.withEntryReplaced(0, entry2);

            assertThat(modified.entries()).hasSize(1);
            assertThat(modified.entries().get(0).name()).hasValueSatisfying(
                id -> assertThat(id.getPath()).isEqualTo("emerald")
            );
        }

        @Test
        @DisplayName("withConditions() creates copy with new conditions")
        void withConditions() {
            LootPool original = LootPool.simple(1, List.of());
            var condition = new LootCondition("minecraft:killed_by_player", null);
            LootPool modified = original.withConditions(List.of(condition));

            assertThat(modified.conditions()).hasSize(1);
            assertThat(original.conditions()).isEmpty();
        }

        @Test
        @DisplayName("withFunctions() creates copy with new functions")
        void withFunctions() {
            LootPool original = LootPool.simple(1, List.of());
            LootPool modified = original.withFunctions(List.of(LootFunction.setCount(2)));

            assertThat(modified.functions()).hasSize(1);
            assertThat(original.functions()).isEmpty();
        }
    }

    // ===== Constructor Variants =====

    @Nested
    @DisplayName("Constructor with String name")
    class ConstructorVariants {

        @Test
        @DisplayName("accepts empty string for name")
        void emptyStringName() {
            LootPool pool = new LootPool(
                "",
                NumberProvider.constant(1),
                NumberProvider.constant(0),
                List.of(),
                List.of(),
                List.of()
            );
            assertThat(pool.name()).isEmpty();
        }

        @Test
        @DisplayName("accepts non-empty string for name")
        void presentStringName() {
            LootPool pool = new LootPool(
                "test_pool",
                NumberProvider.constant(1),
                NumberProvider.constant(0),
                List.of(),
                List.of(),
                List.of()
            );
            assertThat(pool.name()).isEqualTo("test_pool");
        }
    }
}
