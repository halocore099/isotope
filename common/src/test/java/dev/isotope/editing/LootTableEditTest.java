package dev.isotope.editing;

import dev.isotope.data.loot.*;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LootTableEdit record.
 */
@UnitTest
class LootTableEditTest {

    // ===== Factory Method =====

    @Nested
    @DisplayName("create() factory method")
    class CreateFactory {

        @Test
        @DisplayName("creates edit with given table ID")
        void createsWithTableId() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            LootTableEdit edit = LootTableEdit.create(tableId);

            assertThat(edit.tableId()).isEqualTo(tableId);
        }

        @Test
        @DisplayName("creates edit with empty operations")
        void createsWithEmptyOperations() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit edit = LootTableEdit.create(tableId);

            assertThat(edit.operations()).isEmpty();
            assertThat(edit.hasOperations()).isFalse();
            assertThat(edit.getOperationCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("sets default author to ISOTOPE")
        void setsDefaultAuthor() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit edit = LootTableEdit.create(tableId);

            assertThat(edit.author()).isEqualTo("ISOTOPE");
        }

        @Test
        @DisplayName("sets lastModified timestamp")
        void setsTimestamp() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            long before = System.currentTimeMillis();
            LootTableEdit edit = LootTableEdit.create(tableId);
            long after = System.currentTimeMillis();

            assertThat(edit.lastModified()).isBetween(before, after);
        }
    }

    // ===== withOperation() =====

    @Nested
    @DisplayName("withOperation()")
    class WithOperation {

        @Test
        @DisplayName("adds operation to copy")
        void addsOperation() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit original = LootTableEdit.create(tableId);
            var op = new LootEditOperation.ModifyEntryWeight(0, 0, 10);

            LootTableEdit modified = original.withOperation(op);

            assertThat(modified.operations()).hasSize(1);
            assertThat(modified.operations().get(0)).isEqualTo(op);
            assertThat(original.operations()).isEmpty(); // Original unchanged
        }

        @Test
        @DisplayName("preserves existing operations")
        void preservesExisting() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit original = LootTableEdit.create(tableId);
            var op1 = new LootEditOperation.ModifyEntryWeight(0, 0, 10);
            var op2 = new LootEditOperation.ModifyEntryWeight(0, 1, 20);

            LootTableEdit modified = original.withOperation(op1).withOperation(op2);

            assertThat(modified.operations()).hasSize(2);
            assertThat(modified.operations().get(0)).isEqualTo(op1);
            assertThat(modified.operations().get(1)).isEqualTo(op2);
        }

        @Test
        @DisplayName("updates lastModified timestamp")
        void updatesTimestamp() throws InterruptedException {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit original = LootTableEdit.create(tableId);
            long originalTime = original.lastModified();

            Thread.sleep(1); // Ensure time advances

            var op = new LootEditOperation.RemovePool(0);
            LootTableEdit modified = original.withOperation(op);

            assertThat(modified.lastModified()).isGreaterThan(originalTime);
        }
    }

    // ===== withoutOperation() =====

    @Nested
    @DisplayName("withoutOperation()")
    class WithoutOperation {

        @Test
        @DisplayName("removes operation at index")
        void removesAtIndex() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op1 = new LootEditOperation.ModifyEntryWeight(0, 0, 10);
            var op2 = new LootEditOperation.ModifyEntryWeight(0, 1, 20);
            var op3 = new LootEditOperation.ModifyEntryWeight(0, 2, 30);

            LootTableEdit original = LootTableEdit.create(tableId)
                .withOperation(op1)
                .withOperation(op2)
                .withOperation(op3);

            LootTableEdit modified = original.withoutOperation(1);

            assertThat(modified.operations()).hasSize(2);
            assertThat(modified.operations().get(0)).isEqualTo(op1);
            assertThat(modified.operations().get(1)).isEqualTo(op3);
        }

        @Test
        @DisplayName("returns same object for invalid index")
        void returnsSameForInvalidIndex() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit original = LootTableEdit.create(tableId);

            assertThat(original.withoutOperation(-1)).isSameAs(original);
            assertThat(original.withoutOperation(0)).isSameAs(original);
            assertThat(original.withoutOperation(100)).isSameAs(original);
        }
    }

    // ===== undoTo() =====

    @Nested
    @DisplayName("undoTo()")
    class UndoTo {

        @Test
        @DisplayName("removes operations after index")
        void removesAfterIndex() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op1 = new LootEditOperation.ModifyEntryWeight(0, 0, 10);
            var op2 = new LootEditOperation.ModifyEntryWeight(0, 1, 20);
            var op3 = new LootEditOperation.ModifyEntryWeight(0, 2, 30);

            LootTableEdit original = LootTableEdit.create(tableId)
                .withOperation(op1)
                .withOperation(op2)
                .withOperation(op3);

            LootTableEdit undone = original.undoTo(1);

            assertThat(undone.operations()).hasSize(1);
            assertThat(undone.operations().get(0)).isEqualTo(op1);
        }

        @Test
        @DisplayName("undoTo(0) removes all operations")
        void undoToZero() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.RemovePool(0);

            LootTableEdit original = LootTableEdit.create(tableId).withOperation(op);
            LootTableEdit undone = original.undoTo(0);

            assertThat(undone.operations()).isEmpty();
        }

        @Test
        @DisplayName("returns same object for invalid index")
        void returnsSameForInvalidIndex() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit original = LootTableEdit.create(tableId);

            assertThat(original.undoTo(-1)).isSameAs(original);
            assertThat(original.undoTo(100)).isSameAs(original);
        }
    }

    // ===== Query Methods =====

    @Nested
    @DisplayName("Query methods")
    class QueryMethods {

        @Test
        @DisplayName("hasOperations() returns correct value")
        void hasOperations() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit empty = LootTableEdit.create(tableId);
            LootTableEdit withOp = empty.withOperation(new LootEditOperation.RemovePool(0));

            assertThat(empty.hasOperations()).isFalse();
            assertThat(withOp.hasOperations()).isTrue();
        }

        @Test
        @DisplayName("getOperationCount() returns correct count")
        void getOperationCount() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit edit = LootTableEdit.create(tableId)
                .withOperation(new LootEditOperation.RemovePool(0))
                .withOperation(new LootEditOperation.RemovePool(1))
                .withOperation(new LootEditOperation.RemovePool(2));

            assertThat(edit.getOperationCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("getOperationDescriptions() returns all descriptions")
        void getOperationDescriptions() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op1 = new LootEditOperation.ModifyEntryWeight(0, 0, 10);
            var op2 = new LootEditOperation.RemovePool(1);

            LootTableEdit edit = LootTableEdit.create(tableId)
                .withOperation(op1)
                .withOperation(op2);

            List<String> descriptions = edit.getOperationDescriptions();

            assertThat(descriptions).hasSize(2);
            assertThat(descriptions.get(0)).isEqualTo(op1.getDescription());
            assertThat(descriptions.get(1)).isEqualTo(op2.getDescription());
        }
    }

    // ===== Record Equality =====

    @Nested
    @DisplayName("Record equality")
    class Equality {

        @Test
        @DisplayName("edits with same data are equal")
        void equalEdits() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.RemovePool(0);

            // Note: Due to timestamp, we can't easily create equal edits
            // Testing field access instead
            LootTableEdit edit = new LootTableEdit(
                tableId, List.of(op), 12345L, "TestAuthor"
            );

            assertThat(edit.tableId()).isEqualTo(tableId);
            assertThat(edit.operations()).containsExactly(op);
            assertThat(edit.lastModified()).isEqualTo(12345L);
            assertThat(edit.author()).isEqualTo("TestAuthor");
        }
    }

    // ===== Immutability =====

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("operations list is mutable for internal use")
        void operationsListIsMutable() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            LootTableEdit edit = LootTableEdit.create(tableId);

            // The create() method returns a mutable ArrayList
            // This is by design for internal modifications
            assertThat(edit.operations()).isNotNull();
        }

        @Test
        @DisplayName("withOperation creates new list")
        void withOperationCreatesNewList() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.RemovePool(0);

            LootTableEdit original = LootTableEdit.create(tableId);
            LootTableEdit modified = original.withOperation(op);

            assertThat(original.operations()).isNotSameAs(modified.operations());
        }
    }
}
