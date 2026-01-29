package dev.isotope.editing;

import dev.isotope.compat.Id;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Session-wide history log of all edit operations.
 *
 * Provides a chronological view of all changes made during the session,
 * with timestamps and table context.
 */
public final class HistoryLog {

    private static final HistoryLog INSTANCE = new HistoryLog();
    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<LogEntry> entries = new CopyOnWriteArrayList<>();
    private final List<HistoryListener> listeners = new CopyOnWriteArrayList<>();

    private HistoryLog() {}

    public static HistoryLog getInstance() {
        return INSTANCE;
    }

    /**
     * A single log entry.
     */
    public record LogEntry(
        long timestamp,
        Id tableId,
        String operationType,
        String description,
        String formattedTime
    ) {
        public static LogEntry create(Id tableId, LootEditOperation operation) {
            long now = System.currentTimeMillis();
            String time = TIME_FORMAT.format(
                Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
            );
            return new LogEntry(
                now,
                tableId,
                getOperationType(operation),
                operation.getDescription(),
                time
            );
        }

        private static String getOperationType(LootEditOperation op) {
            return switch (op) {
                case LootEditOperation.AddPool ignored -> "ADD_POOL";
                case LootEditOperation.RemovePool ignored -> "REMOVE_POOL";
                case LootEditOperation.ModifyPoolRolls ignored -> "MODIFY_ROLLS";
                case LootEditOperation.ModifyBonusRolls ignored -> "MODIFY_BONUS_ROLLS";
                case LootEditOperation.AddEntry ignored -> "ADD_ENTRY";
                case LootEditOperation.RemoveEntry ignored -> "REMOVE_ENTRY";
                case LootEditOperation.ModifyEntryWeight ignored -> "MODIFY_WEIGHT";
                case LootEditOperation.ModifyEntryQuality ignored -> "MODIFY_QUALITY";
                case LootEditOperation.ModifyEntryItem ignored -> "MODIFY_ITEM";
                case LootEditOperation.ModifyEntryType ignored -> "MODIFY_TYPE";
                case LootEditOperation.SetItemCount ignored -> "SET_COUNT";
                case LootEditOperation.AddFunction ignored -> "ADD_FUNCTION";
                case LootEditOperation.RemoveFunction ignored -> "REMOVE_FUNCTION";
                case LootEditOperation.AddFunctionCondition ignored -> "ADD_FUNC_COND";
                case LootEditOperation.RemoveFunctionCondition ignored -> "REMOVE_FUNC_COND";
                case LootEditOperation.AddCondition ignored -> "ADD_CONDITION";
                case LootEditOperation.RemoveCondition ignored -> "REMOVE_CONDITION";
                case LootEditOperation.AddPoolFunction ignored -> "ADD_POOL_FUNC";
                case LootEditOperation.RemovePoolFunction ignored -> "REMOVE_POOL_FUNC";
                case LootEditOperation.AddPoolCondition ignored -> "ADD_POOL_COND";
                case LootEditOperation.RemovePoolCondition ignored -> "REMOVE_POOL_COND";
                case LootEditOperation.AddTableFunction ignored -> "ADD_TABLE_FUNC";
                case LootEditOperation.RemoveTableFunction ignored -> "REMOVE_TABLE_FUNC";
                case LootEditOperation.SetRandomSequence ignored -> "SET_RANDOM_SEQ";
                case LootEditOperation.AddChild ignored -> "ADD_CHILD";
                case LootEditOperation.RemoveChild ignored -> "REMOVE_CHILD";
                case LootEditOperation.ModifyChild ignored -> "MODIFY_CHILD";
                case LootEditOperation.ModifyFunction ignored -> "MODIFY_FUNCTION";
                case LootEditOperation.ModifyCondition ignored -> "MODIFY_CONDITION";
                case LootEditOperation.ModifyPoolFunction ignored -> "MODIFY_POOL_FUNC";
                case LootEditOperation.ModifyPoolCondition ignored -> "MODIFY_POOL_COND";
                case LootEditOperation.ModifyTableFunction ignored -> "MODIFY_TABLE_FUNC";
                case LootEditOperation.ModifyFunctionCondition ignored -> "MODIFY_FUNC_COND";
            };
        }
    }

    /**
     * Log an operation.
     */
    public void log(Id tableId, LootEditOperation operation) {
        LogEntry entry = LogEntry.create(tableId, operation);
        entries.add(entry);

        // Trim if too large
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }

        notifyListeners();
    }

    /**
     * Log an undo operation.
     */
    public void logUndo(Id tableId) {
        long now = System.currentTimeMillis();
        String time = TIME_FORMAT.format(
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
        entries.add(new LogEntry(now, tableId, "UNDO", "Undo", time));
        notifyListeners();
    }

    /**
     * Log a redo operation.
     */
    public void logRedo(Id tableId) {
        long now = System.currentTimeMillis();
        String time = TIME_FORMAT.format(
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
        entries.add(new LogEntry(now, tableId, "REDO", "Redo", time));
        notifyListeners();
    }

    /**
     * Log a batch operation (multiple operations applied at once).
     */
    public void logBatch(Id tableId, int count, String firstOpDescription) {
        long now = System.currentTimeMillis();
        String time = TIME_FORMAT.format(
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
        String description = "Batch (" + count + " ops): " + firstOpDescription;
        entries.add(new LogEntry(now, tableId, "BATCH", description, time));
        notifyListeners();
    }

    /**
     * Get all entries (most recent last).
     */
    public List<LogEntry> getAll() {
        return new ArrayList<>(entries);
    }

    /**
     * Get recent entries.
     */
    public List<LogEntry> getRecent(int count) {
        int start = Math.max(0, entries.size() - count);
        return new ArrayList<>(entries.subList(start, entries.size()));
    }

    /**
     * Get entries for a specific table.
     */
    public List<LogEntry> getForTable(Id tableId) {
        return entries.stream()
            .filter(e -> e.tableId().equals(tableId))
            .toList();
    }

    /**
     * Get entry count.
     */
    public int getCount() {
        return entries.size();
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        entries.clear();
        notifyListeners();
    }

    /**
     * Add a listener.
     */
    public void addListener(HistoryListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(HistoryListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (HistoryListener listener : listeners) {
            listener.onHistoryChanged();
        }
    }

    /**
     * Listener for history changes.
     */
    @FunctionalInterface
    public interface HistoryListener {
        void onHistoryChanged();
    }
}
