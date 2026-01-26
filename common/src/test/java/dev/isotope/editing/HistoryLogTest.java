package dev.isotope.editing;

import dev.isotope.data.loot.*;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HistoryLog singleton.
 */
@UnitTest
class HistoryLogTest {

    private HistoryLog historyLog;

    @BeforeEach
    void setUp() {
        historyLog = HistoryLog.getInstance();
        historyLog.clear();
    }

    @AfterEach
    void tearDown() {
        historyLog.clear();
    }

    // ===== Singleton =====

    @Nested
    @DisplayName("Singleton pattern")
    class Singleton {

        @Test
        @DisplayName("getInstance() returns same instance")
        void returnsSameInstance() {
            HistoryLog instance1 = HistoryLog.getInstance();
            HistoryLog instance2 = HistoryLog.getInstance();

            assertThat(instance1).isSameAs(instance2);
        }
    }

    // ===== Logging Operations =====

    @Nested
    @DisplayName("log()")
    class LogOperations {

        @Test
        @DisplayName("adds entry to log")
        void addsEntry() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.ModifyEntryWeight(0, 0, 10);

            historyLog.log(tableId, op);

            assertThat(historyLog.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("creates LogEntry with correct data")
        void createsCorrectEntry() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.ModifyEntryWeight(0, 0, 10);

            historyLog.log(tableId, op);

            List<HistoryLog.LogEntry> entries = historyLog.getAll();
            assertThat(entries).hasSize(1);

            HistoryLog.LogEntry entry = entries.get(0);
            assertThat(entry.tableId()).isEqualTo(tableId);
            assertThat(entry.operationType()).isEqualTo("MODIFY_WEIGHT");
            assertThat(entry.description()).isEqualTo(op.getDescription());
            assertThat(entry.timestamp()).isPositive();
            assertThat(entry.formattedTime()).isNotBlank();
        }

        @Test
        @DisplayName("maps operation types correctly")
        void mapsOperationTypes() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.log(tableId, new LootEditOperation.AddPool(0, LootPool.simple(1, List.of())));
            historyLog.log(tableId, new LootEditOperation.RemovePool(0));
            historyLog.log(tableId, new LootEditOperation.AddEntry(0, 0, LootEntry.empty(1)));
            historyLog.log(tableId, new LootEditOperation.RemoveEntry(0, 0));

            List<HistoryLog.LogEntry> entries = historyLog.getAll();
            assertThat(entries.get(0).operationType()).isEqualTo("ADD_POOL");
            assertThat(entries.get(1).operationType()).isEqualTo("REMOVE_POOL");
            assertThat(entries.get(2).operationType()).isEqualTo("ADD_ENTRY");
            assertThat(entries.get(3).operationType()).isEqualTo("REMOVE_ENTRY");
        }
    }

    @Nested
    @DisplayName("logUndo()")
    class LogUndo {

        @Test
        @DisplayName("logs undo operation")
        void logsUndo() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.logUndo(tableId);

            List<HistoryLog.LogEntry> entries = historyLog.getAll();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).operationType()).isEqualTo("UNDO");
        }
    }

    @Nested
    @DisplayName("logRedo()")
    class LogRedo {

        @Test
        @DisplayName("logs redo operation")
        void logsRedo() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.logRedo(tableId);

            List<HistoryLog.LogEntry> entries = historyLog.getAll();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).operationType()).isEqualTo("REDO");
        }
    }

    @Nested
    @DisplayName("logBatch()")
    class LogBatch {

        @Test
        @DisplayName("logs batch operation")
        void logsBatch() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.logBatch(tableId, 5, "First operation description");

            List<HistoryLog.LogEntry> entries = historyLog.getAll();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).operationType()).isEqualTo("BATCH");
            assertThat(entries.get(0).description()).contains("5 ops");
        }
    }

    // ===== Query Methods =====

    @Nested
    @DisplayName("Query methods")
    class QueryMethods {

        @Test
        @DisplayName("getAll() returns all entries in order")
        void getAllReturnsInOrder() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, 1));
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 1, 2));
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 2, 3));

            List<HistoryLog.LogEntry> entries = historyLog.getAll();

            assertThat(entries).hasSize(3);
            assertThat(entries.get(0).timestamp())
                .isLessThanOrEqualTo(entries.get(1).timestamp());
            assertThat(entries.get(1).timestamp())
                .isLessThanOrEqualTo(entries.get(2).timestamp());
        }

        @Test
        @DisplayName("getRecent() returns last N entries")
        void getRecentReturnsLastN() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            for (int i = 0; i < 10; i++) {
                historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, i));
            }

            List<HistoryLog.LogEntry> recent = historyLog.getRecent(3);

            assertThat(recent).hasSize(3);
        }

        @Test
        @DisplayName("getForTable() filters by table ID")
        void getForTableFilters() {
            var tableId1 = TestFixtures.mockId("minecraft", "chests/test1");
            var tableId2 = TestFixtures.mockId("minecraft", "chests/test2");

            historyLog.log(tableId1, new LootEditOperation.ModifyEntryWeight(0, 0, 1));
            historyLog.log(tableId2, new LootEditOperation.ModifyEntryWeight(0, 0, 2));
            historyLog.log(tableId1, new LootEditOperation.ModifyEntryWeight(0, 0, 3));

            List<HistoryLog.LogEntry> table1Entries = historyLog.getForTable(tableId1);

            assertThat(table1Entries).hasSize(2);
            assertThat(table1Entries).allMatch(e -> e.tableId().equals(tableId1));
        }

        @Test
        @DisplayName("getCount() returns total entries")
        void getCountReturnsTotal() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, 1));
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 1, 2));

            assertThat(historyLog.getCount()).isEqualTo(2);
        }
    }

    // ===== Listeners =====

    @Nested
    @DisplayName("Listeners")
    class Listeners {

        @Test
        @DisplayName("notifies listeners on log")
        void notifiesOnLog() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            AtomicInteger notifyCount = new AtomicInteger(0);
            HistoryLog.HistoryListener listener = notifyCount::incrementAndGet;

            historyLog.addListener(listener);
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, 1));

            assertThat(notifyCount.get()).isEqualTo(1);

            historyLog.removeListener(listener);
        }

        @Test
        @DisplayName("notifies listeners on clear")
        void notifiesOnClear() {
            AtomicInteger notifyCount = new AtomicInteger(0);
            HistoryLog.HistoryListener listener = notifyCount::incrementAndGet;

            historyLog.addListener(listener);
            historyLog.clear();

            assertThat(notifyCount.get()).isEqualTo(1);

            historyLog.removeListener(listener);
        }

        @Test
        @DisplayName("removeListener stops notifications")
        void removeListenerStopsNotifications() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            AtomicInteger notifyCount = new AtomicInteger(0);
            HistoryLog.HistoryListener listener = notifyCount::incrementAndGet;

            historyLog.addListener(listener);
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, 1));
            historyLog.removeListener(listener);
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, 2));

            assertThat(notifyCount.get()).isEqualTo(1); // Only first notification
        }
    }

    // ===== Clear =====

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("removes all entries")
        void removesAllEntries() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");

            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 0, 1));
            historyLog.log(tableId, new LootEditOperation.ModifyEntryWeight(0, 1, 2));
            historyLog.clear();

            assertThat(historyLog.getCount()).isEqualTo(0);
            assertThat(historyLog.getAll()).isEmpty();
        }
    }

    // ===== LogEntry Record =====

    @Nested
    @DisplayName("LogEntry record")
    class LogEntryRecord {

        @Test
        @DisplayName("create() factory method works correctly")
        void createFactoryMethod() {
            var tableId = TestFixtures.mockId("minecraft", "chests/test");
            var op = new LootEditOperation.ModifyEntryWeight(0, 0, 10);

            HistoryLog.LogEntry entry = HistoryLog.LogEntry.create(tableId, op);

            assertThat(entry.tableId()).isEqualTo(tableId);
            assertThat(entry.operationType()).isEqualTo("MODIFY_WEIGHT");
            assertThat(entry.description()).isEqualTo(op.getDescription());
            assertThat(entry.formattedTime()).matches("\\d{2}:\\d{2}:\\d{2}");
        }
    }
}
