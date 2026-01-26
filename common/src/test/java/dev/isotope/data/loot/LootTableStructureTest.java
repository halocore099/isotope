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
 * Unit tests for LootTableStructure record.
 */
@UnitTest
class LootTableStructureTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("empty() factory method")
    class EmptyFactory {

        @Test
        @DisplayName("creates empty loot table")
        void createsEmptyTable() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure table = LootTableStructure.empty(id);

            assertThat(table.id()).isEqualTo(id);
            assertThat(table.type()).isEqualTo(LootTableStructure.TYPE_EMPTY);
            assertThat(table.pools()).isEmpty();
            assertThat(table.functions()).isEmpty();
            assertThat(table.randomSequence()).isEmpty();
        }
    }

    @Nested
    @DisplayName("chest() factory method")
    class ChestFactory {

        @Test
        @DisplayName("creates chest loot table with pools")
        void createsChestTable() {
            var id = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            var pool = LootPool.simple(3, List.of(
                LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1)
            ));
            LootTableStructure table = LootTableStructure.chest(id, List.of(pool));

            assertThat(table.type()).isEqualTo(LootTableStructure.TYPE_CHEST);
            assertThat(table.pools()).hasSize(1);
            assertThat(table.functions()).isEmpty();
        }
    }

    // ===== Helper Methods =====

    @Nested
    @DisplayName("Helper methods")
    class HelperMethods {

        @Test
        @DisplayName("getTotalEntryCount() sums entries across all pools")
        void totalEntryCount() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool1 = LootPool.simple(1, List.of(
                LootEntry.item(TestFixtures.mockId("minecraft", "diamond")),
                LootEntry.item(TestFixtures.mockId("minecraft", "emerald"))
            ));
            var pool2 = LootPool.simple(1, List.of(
                LootEntry.item(TestFixtures.mockId("minecraft", "gold_ingot"))
            ));
            LootTableStructure table = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(pool1, pool2),
                List.of(),
                Optional.empty()
            );

            assertThat(table.getTotalEntryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("getPoolCount() returns number of pools")
        void poolCount() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool1 = LootPool.simple(1, List.of());
            var pool2 = LootPool.simple(1, List.of());
            LootTableStructure table = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(pool1, pool2),
                List.of(),
                Optional.empty()
            );

            assertThat(table.getPoolCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("isEmpty() returns true for empty type")
        void isEmptyForEmptyType() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure table = LootTableStructure.empty(id);

            assertThat(table.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty() returns true for no pools")
        void isEmptyForNoPools() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure table = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(),
                List.of(),
                Optional.empty()
            );

            assertThat(table.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty() returns false for table with pools")
        void notEmptyWithPools() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool = LootPool.simple(1, List.of());
            LootTableStructure table = LootTableStructure.chest(id, List.of(pool));

            assertThat(table.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("getTypeDisplayName() formats type correctly")
        void typeDisplayName() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure chest = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(), List.of(), Optional.empty()
            );
            LootTableStructure entity = new LootTableStructure(
                id, LootTableStructure.TYPE_ENTITY,
                List.of(), List.of(), Optional.empty()
            );

            assertThat(chest.getTypeDisplayName()).isEqualTo("Chest");
            assertThat(entity.getTypeDisplayName()).isEqualTo("Entity");
        }

        @Test
        @DisplayName("getShortId() returns just the path")
        void shortId() {
            var id = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            LootTableStructure table = LootTableStructure.empty(id);

            assertThat(table.getShortId()).isEqualTo("chests/simple_dungeon");
        }

        @Test
        @DisplayName("getNamespace() returns the namespace")
        void namespace() {
            var id = TestFixtures.mockId("mymod", "chests/custom");
            LootTableStructure table = LootTableStructure.empty(id);

            assertThat(table.getNamespace()).isEqualTo("mymod");
        }

        @Test
        @DisplayName("hasGlobalFunctions() returns correct value")
        void hasGlobalFunctions() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure tableWithout = LootTableStructure.empty(id);
            assertThat(tableWithout.hasGlobalFunctions()).isFalse();

            LootTableStructure tableWith = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(),
                List.of(LootFunction.setCount(2)),
                Optional.empty()
            );
            assertThat(tableWith.hasGlobalFunctions()).isTrue();
        }
    }

    // ===== Immutable Copy Methods =====

    @Nested
    @DisplayName("withX() copy methods")
    class CopyMethods {

        @Test
        @DisplayName("withPools() creates copy with new pools")
        void withPools() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure original = LootTableStructure.empty(id);
            var pool = LootPool.simple(1, List.of());
            LootTableStructure modified = original.withPools(List.of(pool));

            assertThat(modified.pools()).hasSize(1);
            assertThat(original.pools()).isEmpty();
        }

        @Test
        @DisplayName("withPoolAdded() adds pool at index")
        void withPoolAdded() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool1 = LootPool.simple(1, List.of()).withName("pool1");
            var pool2 = LootPool.simple(2, List.of()).withName("pool2");
            LootTableStructure original = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(pool1),
                List.of(),
                Optional.empty()
            );
            LootTableStructure modified = original.withPoolAdded(0, pool2);

            assertThat(modified.pools()).hasSize(2);
            assertThat(modified.pools().get(0).rolls().getMin()).isEqualTo(2);
        }

        @Test
        @DisplayName("withPoolAdded() with -1 adds at end")
        void withPoolAddedAtEnd() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool1 = LootPool.simple(1, List.of());
            var pool2 = LootPool.simple(2, List.of());
            LootTableStructure original = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(pool1),
                List.of(),
                Optional.empty()
            );
            LootTableStructure modified = original.withPoolAdded(-1, pool2);

            assertThat(modified.pools()).hasSize(2);
            assertThat(modified.pools().get(1).rolls().getMin()).isEqualTo(2);
        }

        @Test
        @DisplayName("withPoolRemoved() removes pool at index")
        void withPoolRemoved() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool1 = LootPool.simple(1, List.of());
            var pool2 = LootPool.simple(2, List.of());
            LootTableStructure original = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(pool1, pool2),
                List.of(),
                Optional.empty()
            );
            LootTableStructure modified = original.withPoolRemoved(0);

            assertThat(modified.pools()).hasSize(1);
            assertThat(modified.pools().get(0).rolls().getMin()).isEqualTo(2);
        }

        @Test
        @DisplayName("withPoolRemoved() with invalid index returns same object")
        void withPoolRemovedInvalid() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure original = LootTableStructure.empty(id);
            LootTableStructure modified = original.withPoolRemoved(0);

            assertThat(modified).isSameAs(original);
        }

        @Test
        @DisplayName("withPoolReplaced() replaces pool at index")
        void withPoolReplaced() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var pool1 = LootPool.simple(1, List.of());
            var pool2 = LootPool.simple(5, List.of());
            LootTableStructure original = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(pool1),
                List.of(),
                Optional.empty()
            );
            LootTableStructure modified = original.withPoolReplaced(0, pool2);

            assertThat(modified.pools()).hasSize(1);
            assertThat(modified.pools().get(0).rolls().getMin()).isEqualTo(5);
        }

        @Test
        @DisplayName("withFunctions() creates copy with new functions")
        void withFunctions() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            LootTableStructure original = LootTableStructure.empty(id);
            LootTableStructure modified = original.withFunctions(List.of(LootFunction.setCount(2)));

            assertThat(modified.functions()).hasSize(1);
            assertThat(original.functions()).isEmpty();
        }

        @Test
        @DisplayName("withType() creates copy with new type")
        void withType() {
            var id = TestFixtures.mockId("minecraft", "test");
            LootTableStructure original = new LootTableStructure(
                id, LootTableStructure.TYPE_CHEST,
                List.of(), List.of(), Optional.empty()
            );
            LootTableStructure modified = original.withType(LootTableStructure.TYPE_ENTITY);

            assertThat(modified.type()).isEqualTo(LootTableStructure.TYPE_ENTITY);
            assertThat(original.type()).isEqualTo(LootTableStructure.TYPE_CHEST);
        }

        @Test
        @DisplayName("withId() creates copy with new id")
        void withId() {
            var id1 = TestFixtures.mockId("minecraft", "chests/old");
            var id2 = TestFixtures.mockId("minecraft", "chests/new");
            LootTableStructure original = LootTableStructure.empty(id1);
            LootTableStructure modified = original.withId(id2);

            assertThat(modified.id()).isEqualTo(id2);
            assertThat(original.id()).isEqualTo(id1);
        }

        @Test
        @DisplayName("withRandomSequence() creates copy with new random sequence")
        void withRandomSequence() {
            var id = TestFixtures.mockId("minecraft", "chests/test");
            var seqId = TestFixtures.mockId("minecraft", "sequences/test");
            LootTableStructure original = LootTableStructure.empty(id);
            LootTableStructure modified = original.withRandomSequence(Optional.of(seqId));

            assertThat(modified.randomSequence()).hasValue(seqId);
            assertThat(original.randomSequence()).isEmpty();
        }
    }

    // ===== Type Constants =====

    @Nested
    @DisplayName("Type constants")
    class TypeConstants {

        @Test
        @DisplayName("all type constants are properly namespaced")
        void typeConstantsAreNamespaced() {
            assertThat(LootTableStructure.TYPE_EMPTY).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_CHEST).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_ENTITY).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_BLOCK).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_FISHING).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_GIFT).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_BARTER).startsWith("minecraft:");
            assertThat(LootTableStructure.TYPE_ARCHAEOLOGY).startsWith("minecraft:");
        }
    }
}
