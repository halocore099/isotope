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
            return switch (op) {
                case LootEditOperation.AddPool ignored -> "ADD_POOL";
                case LootEditOperation.RemovePool ignored -> "REMOVE_POOL";
                case LootEditOperation.ModifyPoolRolls ignored -> "MODIFY_ROLLS";
                case LootEditOperation.AddEntry ignored -> "ADD_ENTRY";
                case LootEditOperation.RemoveEntry ignored -> "REMOVE_ENTRY";
                case LootEditOperation.ModifyEntryWeight ignored -> "MODIFY_WEIGHT";
                case LootEditOperation.ModifyEntryItem ignored -> "MODIFY_ITEM";
                case LootEditOperation.SetItemCount ignored -> "SET_COUNT";
                case LootEditOperation.AddFunction ignored -> "ADD_FUNCTION";
                case LootEditOperation.RemoveFunction ignored -> "REMOVE_FUNCTION";
                case LootEditOperation.AddCondition ignored -> "ADD_CONDITION";
                case LootEditOperation.RemoveCondition ignored -> "REMOVE_CONDITION";
                case LootEditOperation.AddPoolFunction ignored -> "ADD_POOL_FUNC";
                case LootEditOperation.RemovePoolFunction ignored -> "REMOVE_POOL_FUNC";
                case LootEditOperation.AddPoolCondition ignored -> "ADD_POOL_COND";
                case LootEditOperation.RemovePoolCondition ignored -> "REMOVE_POOL_COND";
            };
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
