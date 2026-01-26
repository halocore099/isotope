package dev.isotope.analysis;

import dev.isotope.data.loot.*;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StructureDiff.
 */
@UnitTest
class StructureDiffTest {

    // ===== No Changes =====

    @Nested
    @DisplayName("No changes")
    class NoChanges {

        @Test
        @DisplayName("identical tables have no differences")
        void identicalTables() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var pool = LootPool.simple(3, List.of(diamond));
            var original = TestFixtures.chestTable("test", pool);
            var edited = TestFixtures.chestTable("test", pool);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isFalse();
            assertThat(result.totalChanges()).isEqualTo(0);
            assertThat(result.additions()).isEmpty();
            assertThat(result.removals()).isEmpty();
            assertThat(result.modifications()).isEmpty();
        }

        @Test
        @DisplayName("empty tables have no differences")
        void emptyTables() {
            var original = LootTableStructure.empty(TestFixtures.mockId("minecraft", "test"));
            var edited = LootTableStructure.empty(TestFixtures.mockId("minecraft", "test"));

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isFalse();
        }
    }

    // ===== Pool Changes =====

    @Nested
    @DisplayName("Pool changes")
    class PoolChanges {

        @Test
        @DisplayName("detects added pool")
        void addedPool() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool1 = LootPool.simple(1, List.of(diamond));
            var pool2 = LootPool.simple(2, List.of(diamond));

            var original = TestFixtures.chestTable("test", pool1);
            var edited = TestFixtures.chestTable("test", pool1, pool2);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.additions()).hasSize(1);
            assertThat(result.additions().get(0)).isInstanceOf(StructureDiff.DiffEntry.AddedPool.class);
        }

        @Test
        @DisplayName("detects removed pool")
        void removedPool() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool1 = LootPool.simple(1, List.of(diamond));
            var pool2 = LootPool.simple(2, List.of(diamond));

            var original = TestFixtures.chestTable("test", pool1, pool2);
            var edited = TestFixtures.chestTable("test", pool1);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.removals()).hasSize(1);
            assertThat(result.removals().get(0)).isInstanceOf(StructureDiff.DiffEntry.RemovedPool.class);
        }
    }

    // ===== Entry Changes =====

    @Nested
    @DisplayName("Entry changes")
    class EntryChanges {

        @Test
        @DisplayName("detects added entry")
        void addedEntry() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);

            var originalPool = LootPool.simple(1, List.of(diamond));
            var editedPool = LootPool.simple(1, List.of(diamond, emerald));

            var original = TestFixtures.chestTable("test", originalPool);
            var edited = TestFixtures.chestTable("test", editedPool);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.additions()).hasSize(1);
            var added = (StructureDiff.DiffEntry.AddedEntry) result.additions().get(0);
            assertThat(added.poolIndex()).isEqualTo(0);
            assertThat(added.entryIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("detects removed entry")
        void removedEntry() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);

            var originalPool = LootPool.simple(1, List.of(diamond, emerald));
            var editedPool = LootPool.simple(1, List.of(diamond));

            var original = TestFixtures.chestTable("test", originalPool);
            var edited = TestFixtures.chestTable("test", editedPool);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.removals()).hasSize(1);
            var removed = (StructureDiff.DiffEntry.RemovedEntry) result.removals().get(0);
            assertThat(removed.poolIndex()).isEqualTo(0);
        }
    }

    // ===== Modifications =====

    @Nested
    @DisplayName("Entry modifications")
    class Modifications {

        @Test
        @DisplayName("detects weight change")
        void weightChange() {
            var original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var edited = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 10);

            var originalPool = LootPool.simple(1, List.of(original));
            var editedPool = LootPool.simple(1, List.of(edited));

            var originalTable = TestFixtures.chestTable("test", originalPool);
            var editedTable = TestFixtures.chestTable("test", editedPool);

            StructureDiff.DiffResult result = StructureDiff.compare(originalTable, editedTable);

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.modifications()).hasSize(1);
            var mod = (StructureDiff.DiffEntry.ModifiedWeight) result.modifications().get(0);
            assertThat(mod.oldWeight()).isEqualTo(5);
            assertThat(mod.newWeight()).isEqualTo(10);
        }

        @Test
        @DisplayName("detects item change")
        void itemChange() {
            var original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var edited = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 5);

            var originalPool = LootPool.simple(1, List.of(original));
            var editedPool = LootPool.simple(1, List.of(edited));

            var originalTable = TestFixtures.chestTable("test", originalPool);
            var editedTable = TestFixtures.chestTable("test", editedPool);

            StructureDiff.DiffResult result = StructureDiff.compare(originalTable, editedTable);

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.modifications()).hasSize(1);
            assertThat(result.modifications().get(0)).isInstanceOf(StructureDiff.DiffEntry.ModifiedItem.class);
        }

        @Test
        @DisplayName("detects count change")
        void countChange() {
            var original = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5, 1, 3);
            var edited = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5, 2, 8);

            var originalPool = LootPool.simple(1, List.of(original));
            var editedPool = LootPool.simple(1, List.of(edited));

            var originalTable = TestFixtures.chestTable("test", originalPool);
            var editedTable = TestFixtures.chestTable("test", editedPool);

            StructureDiff.DiffResult result = StructureDiff.compare(originalTable, editedTable);

            assertThat(result.hasChanges()).isTrue();
            // Should have count modification
            assertThat(result.modifications().stream()
                .anyMatch(m -> m instanceof StructureDiff.DiffEntry.ModifiedCount)).isTrue();
        }
    }

    // ===== describe() =====

    @Nested
    @DisplayName("describe()")
    class Describe {

        @Test
        @DisplayName("describes added pool")
        void describeAddedPool() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(1, List.of(entry));
            var diff = new StructureDiff.DiffEntry.AddedPool(0, pool);

            String desc = StructureDiff.describe(diff);

            assertThat(desc).contains("+").contains("Pool 1");
        }

        @Test
        @DisplayName("describes removed pool")
        void describeRemovedPool() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(1, List.of(entry));
            var diff = new StructureDiff.DiffEntry.RemovedPool(2, pool);

            String desc = StructureDiff.describe(diff);

            assertThat(desc).contains("-").contains("Pool 3");
        }

        @Test
        @DisplayName("describes added entry")
        void describeAddedEntry() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var diff = new StructureDiff.DiffEntry.AddedEntry(0, 0, entry);

            String desc = StructureDiff.describe(diff);

            assertThat(desc).contains("+").contains("diamond");
        }

        @Test
        @DisplayName("describes weight modification")
        void describeWeightMod() {
            var diff = new StructureDiff.DiffEntry.ModifiedWeight(
                0, 0, TestFixtures.mockId("minecraft", "diamond"), 5, 10);

            String desc = StructureDiff.describe(diff);

            assertThat(desc).contains("weight").contains("5").contains("10");
        }

        @Test
        @DisplayName("describes item modification")
        void describeItemMod() {
            var diff = new StructureDiff.DiffEntry.ModifiedItem(
                0, 0,
                TestFixtures.mockId("minecraft", "diamond"),
                TestFixtures.mockId("minecraft", "emerald"));

            String desc = StructureDiff.describe(diff);

            assertThat(desc).contains("diamond").contains("emerald");
        }
    }

    // ===== getColor() =====

    @Nested
    @DisplayName("getColor()")
    class GetColor {

        @Test
        @DisplayName("returns green for additions")
        void greenForAdditions() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(1, List.of(entry));

            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.AddedPool(0, pool)))
                .isEqualTo(0xFF55FF55);
            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.AddedEntry(0, 0, entry)))
                .isEqualTo(0xFF55FF55);
        }

        @Test
        @DisplayName("returns red for removals")
        void redForRemovals() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(1, List.of(entry));

            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.RemovedPool(0, pool)))
                .isEqualTo(0xFFFF5555);
            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.RemovedEntry(0, 0, entry)))
                .isEqualTo(0xFFFF5555);
        }

        @Test
        @DisplayName("returns yellow for weight/count mods")
        void yellowForMods() {
            var id = TestFixtures.mockId("minecraft", "diamond");

            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.ModifiedWeight(0, 0, id, 5, 10)))
                .isEqualTo(0xFFFFFF55);
            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.ModifiedCount(0, 0, id, "1", "5")))
                .isEqualTo(0xFFFFFF55);
        }

        @Test
        @DisplayName("returns orange for item change")
        void orangeForItemChange() {
            var id1 = TestFixtures.mockId("minecraft", "diamond");
            var id2 = TestFixtures.mockId("minecraft", "emerald");

            assertThat(StructureDiff.getColor(new StructureDiff.DiffEntry.ModifiedItem(0, 0, id1, id2)))
                .isEqualTo(0xFFFFAA00);
        }
    }

    // ===== Complex Scenarios =====

    @Nested
    @DisplayName("Complex scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("handles multiple changes at once")
        void multipleChanges() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            var gold = LootEntry.item(TestFixtures.mockId("minecraft", "gold_ingot"), 1);

            // Original: [diamond, emerald]
            var originalPool = LootPool.simple(1, List.of(diamond, emerald));
            // Edited: [diamond, gold] (emerald replaced with gold)
            var editedPool = LootPool.simple(1, List.of(diamond, gold));

            var original = TestFixtures.chestTable("test", originalPool);
            var edited = TestFixtures.chestTable("test", editedPool);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.hasChanges()).isTrue();
            // Entry at index 1 changed from emerald to gold
            assertThat(result.modifications()).hasSize(1);
        }

        @Test
        @DisplayName("counts total changes correctly")
        void totalChanges() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            var pool1 = LootPool.simple(1, List.of(diamond));
            var pool2 = LootPool.simple(1, List.of(emerald));

            var original = TestFixtures.chestTable("test", pool1);
            var edited = TestFixtures.chestTable("test", pool1, pool2);

            StructureDiff.DiffResult result = StructureDiff.compare(original, edited);

            assertThat(result.totalChanges())
                .isEqualTo(result.additions().size() + result.removals().size() + result.modifications().size());
        }
    }
}
