package dev.isotope.editing;

import net.minecraft.resources.ResourceLocation;

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
        ResourceLocation tableId,
        String operationType,
        String description,
        String formattedTime
    ) {
        public static LogEntry create(ResourceLocation tableId, LootEditOperation operation) {
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
            if (op instanceof LootEditOperation.AddPool) return "ADD_POOL";
            if (op instanceof LootEditOperation.RemovePool) return "REMOVE_POOL";
            if (op instanceof LootEditOperation.ModifyPoolRolls) return "MODIFY_ROLLS";
            if (op instanceof LootEditOperation.ModifyBonusRolls) return "MODIFY_BONUS_ROLLS";
            if (op instanceof LootEditOperation.AddEntry) return "ADD_ENTRY";
            if (op instanceof LootEditOperation.RemoveEntry) return "REMOVE_ENTRY";
            if (op instanceof LootEditOperation.ModifyEntryWeight) return "MODIFY_WEIGHT";
            if (op instanceof LootEditOperation.ModifyEntryQuality) return "MODIFY_QUALITY";
            if (op instanceof LootEditOperation.ModifyEntryItem) return "MODIFY_ITEM";
            if (op instanceof LootEditOperation.ModifyEntryType) return "MODIFY_TYPE";
            if (op instanceof LootEditOperation.SetItemCount) return "SET_COUNT";
            if (op instanceof LootEditOperation.AddFunction) return "ADD_FUNCTION";
            if (op instanceof LootEditOperation.RemoveFunction) return "REMOVE_FUNCTION";
            if (op instanceof LootEditOperation.AddCondition) return "ADD_CONDITION";
            if (op instanceof LootEditOperation.RemoveCondition) return "REMOVE_CONDITION";
            if (op instanceof LootEditOperation.AddPoolFunction) return "ADD_POOL_FUNC";
            if (op instanceof LootEditOperation.RemovePoolFunction) return "REMOVE_POOL_FUNC";
            if (op instanceof LootEditOperation.AddPoolCondition) return "ADD_POOL_COND";
            if (op instanceof LootEditOperation.RemovePoolCondition) return "REMOVE_POOL_COND";
            if (op instanceof LootEditOperation.AddTableFunction) return "ADD_TABLE_FUNC";
            if (op instanceof LootEditOperation.RemoveTableFunction) return "REMOVE_TABLE_FUNC";
            if (op instanceof LootEditOperation.SetRandomSequence) return "SET_RANDOM_SEQ";
            if (op instanceof LootEditOperation.AddChild) return "ADD_CHILD";
            if (op instanceof LootEditOperation.RemoveChild) return "REMOVE_CHILD";
            if (op instanceof LootEditOperation.ModifyChild) return "MODIFY_CHILD";
            return "UNKNOWN";
        }
    }

    /**
     * Log an operation.
     */
    public void log(ResourceLocation tableId, LootEditOperation operation) {
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
    public void logUndo(ResourceLocation tableId) {
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
    public void logRedo(ResourceLocation tableId) {
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
    public void logBatch(ResourceLocation tableId, int count, String firstOpDescription) {
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
    public List<LogEntry> getForTable(ResourceLocation tableId) {
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
