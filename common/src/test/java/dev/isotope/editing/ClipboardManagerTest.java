package dev.isotope.editing;

import dev.isotope.data.loot.*;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ClipboardManager singleton.
 */
@UnitTest
class ClipboardManagerTest {

    private ClipboardManager clipboard;

    @BeforeEach
    void setUp() {
        clipboard = ClipboardManager.getInstance();
        clipboard.clear();
    }

    @AfterEach
    void tearDown() {
        clipboard.clear();
    }

    // ===== Singleton =====

    @Nested
    @DisplayName("Singleton pattern")
    class Singleton {

        @Test
        @DisplayName("getInstance() returns same instance")
        void returnsSameInstance() {
            ClipboardManager instance1 = ClipboardManager.getInstance();
            ClipboardManager instance2 = ClipboardManager.getInstance();

            assertThat(instance1).isSameAs(instance2);
        }
    }

    // ===== Entry Operations =====

    @Nested
    @DisplayName("Entry operations")
    class EntryOperations {

        @Test
        @DisplayName("copyEntry() stores entry and source")
        void copyEntryStoresData() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            clipboard.copyEntry(entry, tableId);

            assertThat(clipboard.hasEntry()).isTrue();
            assertThat(clipboard.getEntry()).isPresent();
            assertThat(clipboard.getSourceTable()).hasValue(tableId);
        }

        @Test
        @DisplayName("copyEntry() creates deep copy")
        void copyEntryCreatesDeepCopy() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 5);
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            clipboard.copyEntry(entry, tableId);

            var copied = clipboard.getEntry().orElseThrow();
            assertThat(copied).isNotSameAs(entry);
            assertThat(copied.weight()).isEqualTo(entry.weight());
        }

        @Test
        @DisplayName("copyEntry() clears pool if present")
        void copyEntryClearsPool() {
            var pool = LootPool.simple(3, List.of());
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            clipboard.copyPool(pool, tableId);
            assertThat(clipboard.hasPool()).isTrue();

            clipboard.copyEntry(entry, tableId);
            assertThat(clipboard.hasPool()).isFalse();
            assertThat(clipboard.hasEntry()).isTrue();
        }

        @Test
        @DisplayName("getEntry() returns empty when no entry copied")
        void getEntryEmptyWhenNone() {
            assertThat(clipboard.getEntry()).isEmpty();
            assertThat(clipboard.hasEntry()).isFalse();
        }
    }

    // ===== Pool Operations =====

    @Nested
    @DisplayName("Pool operations")
    class PoolOperations {

        @Test
        @DisplayName("copyPool() stores pool and source")
        void copyPoolStoresData() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(3, List.of(entry));
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            clipboard.copyPool(pool, tableId);

            assertThat(clipboard.hasPool()).isTrue();
            assertThat(clipboard.getPool()).isPresent();
            assertThat(clipboard.getSourceTable()).hasValue(tableId);
        }

        @Test
        @DisplayName("copyPool() creates deep copy")
        void copyPoolCreatesDeepCopy() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(3, List.of(entry));
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            clipboard.copyPool(pool, tableId);

            var copied = clipboard.getPool().orElseThrow();
            assertThat(copied).isNotSameAs(pool);
            assertThat(copied.entries()).isNotSameAs(pool.entries());
        }

        @Test
        @DisplayName("copyPool() clears entry if present")
        void copyPoolClearsEntry() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var pool = LootPool.simple(3, List.of(entry));
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            clipboard.copyEntry(entry, tableId);
            assertThat(clipboard.hasEntry()).isTrue();

            clipboard.copyPool(pool, tableId);
            assertThat(clipboard.hasEntry()).isFalse();
            assertThat(clipboard.hasPool()).isTrue();
        }

        @Test
        @DisplayName("getPool() returns empty when no pool copied")
        void getPoolEmptyWhenNone() {
            assertThat(clipboard.getPool()).isEmpty();
            assertThat(clipboard.hasPool()).isFalse();
        }
    }

    // ===== General Methods =====

    @Nested
    @DisplayName("General methods")
    class GeneralMethods {

        @Test
        @DisplayName("getSourceTable() returns empty when nothing copied")
        void sourceTableEmptyWhenNothing() {
            assertThat(clipboard.getSourceTable()).isEmpty();
        }

        @Test
        @DisplayName("hasContent() returns false when empty")
        void hasContentFalseWhenEmpty() {
            assertThat(clipboard.hasContent()).isFalse();
        }

        @Test
        @DisplayName("hasContent() returns true when entry copied")
        void hasContentTrueWithEntry() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            clipboard.copyEntry(entry, TestFixtures.mockId("minecraft", "chests/test"));

            assertThat(clipboard.hasContent()).isTrue();
        }

        @Test
        @DisplayName("hasContent() returns true when pool copied")
        void hasContentTrueWithPool() {
            var pool = LootPool.simple(1, List.of());
            clipboard.copyPool(pool, TestFixtures.mockId("minecraft", "chests/test"));

            assertThat(clipboard.hasContent()).isTrue();
        }
    }

    // ===== Content Description =====

    @Nested
    @DisplayName("getContentDescription()")
    class ContentDescription {

        @Test
        @DisplayName("returns 'Empty' when nothing copied")
        void emptyDescription() {
            assertThat(clipboard.getContentDescription()).isEqualTo("Empty");
        }

        @Test
        @DisplayName("describes entry with item name")
        void describesEntryWithItemName() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            clipboard.copyEntry(entry, TestFixtures.mockId("minecraft", "chests/test"));

            assertThat(clipboard.getContentDescription()).contains("Entry").contains("diamond");
        }

        @Test
        @DisplayName("describes entry with type for non-item entries")
        void describesEntryWithTypeForEmpty() {
            var entry = LootEntry.empty(5);
            clipboard.copyEntry(entry, TestFixtures.mockId("minecraft", "chests/test"));

            assertThat(clipboard.getContentDescription()).contains("Entry").contains("empty");
        }

        @Test
        @DisplayName("describes pool with entry count")
        void describesPoolWithEntryCount() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            var pool = LootPool.simple(1, List.of(entry1, entry2));
            clipboard.copyPool(pool, TestFixtures.mockId("minecraft", "chests/test"));

            assertThat(clipboard.getContentDescription()).contains("Pool").contains("2");
        }
    }

    // ===== Clear =====

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("clears entry")
        void clearsEntry() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            clipboard.copyEntry(entry, TestFixtures.mockId("minecraft", "chests/test"));

            clipboard.clear();

            assertThat(clipboard.hasEntry()).isFalse();
        }

        @Test
        @DisplayName("clears pool")
        void clearsPool() {
            var pool = LootPool.simple(1, List.of());
            clipboard.copyPool(pool, TestFixtures.mockId("minecraft", "chests/test"));

            clipboard.clear();

            assertThat(clipboard.hasPool()).isFalse();
        }

        @Test
        @DisplayName("clears source table")
        void clearsSourceTable() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            clipboard.copyEntry(entry, TestFixtures.mockId("minecraft", "chests/test"));

            clipboard.clear();

            assertThat(clipboard.getSourceTable()).isEmpty();
        }

        @Test
        @DisplayName("hasContent() returns false after clear")
        void hasContentFalseAfterClear() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            clipboard.copyEntry(entry, TestFixtures.mockId("minecraft", "chests/test"));

            clipboard.clear();

            assertThat(clipboard.hasContent()).isFalse();
        }
    }
}
