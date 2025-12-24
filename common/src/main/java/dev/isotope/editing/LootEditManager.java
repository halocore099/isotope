package dev.isotope.editing;

import dev.isotope.Isotope;
import dev.isotope.data.loot.LootTableStructure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for loot table edits.
 *
 * Tracks all edits by table ID, controls test mode, and caches parsed structures.
 * When test mode is active, the mixin will use edited structures instead of
 * the original ones for loot generation.
 */
public final class LootEditManager {

    private static final LootEditManager INSTANCE = new LootEditManager();

    // Edits by table ID
    private final Map<ResourceLocation, LootTableEdit> edits = new ConcurrentHashMap<>();

    // Cache of original parsed structures
    private final Map<ResourceLocation, LootTableStructure> originalCache = new ConcurrentHashMap<>();

    // Cache of edited structures (rebuilt when operations change)
    private final Map<ResourceLocation, LootTableStructure> editedCache = new ConcurrentHashMap<>();

    // Test mode flag
    private volatile boolean testModeActive = false;

    // Listeners for edit changes
    private final List<EditListener> listeners = new ArrayList<>();

    private LootEditManager() {}

    public static LootEditManager getInstance() {
        return INSTANCE;
    }

    // ===== Test Mode =====

    /**
     * Check if test mode is active.
     * When active, edited loot tables will be used during loot generation.
     */
    public boolean isTestModeActive() {
        return testModeActive;
    }

    /**
     * Enable or disable test mode.
     */
    public void setTestModeActive(boolean active) {
        if (this.testModeActive != active) {
            this.testModeActive = active;
            Isotope.LOGGER.info("ISOTOPE test mode: {}", active ? "ENABLED" : "DISABLED");
            notifyListeners();
        }
    }

    /**
     * Toggle test mode on/off.
     */
    public void toggleTestMode() {
        setTestModeActive(!testModeActive);
    }

    // ===== Original Structure Access =====

    /**
     * Get the original (unedited) structure for a table.
     * Parses and caches if not already cached.
     */
    public Optional<LootTableStructure> getOriginalStructure(MinecraftServer server, ResourceLocation tableId) {
        // Check cache first
        LootTableStructure cached = originalCache.get(tableId);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Parse from resources
        Optional<LootTableStructure> parsed = LootTableParser.parse(server, tableId);
        parsed.ifPresent(structure -> originalCache.put(tableId, structure));
        return parsed;
    }

    /**
     * Get a cached original structure (does not parse if missing).
     */
    public Optional<LootTableStructure> getCachedOriginalStructure(ResourceLocation tableId) {
        return Optional.ofNullable(originalCache.get(tableId));
    }

    /**
     * Pre-cache an original structure.
     */
    public void cacheOriginalStructure(LootTableStructure structure) {
        originalCache.put(structure.id(), structure);
    }

    // ===== Edited Structure Access =====

    /**
     * Get the edited structure for a table.
     * Returns the original if no edits exist.
     */
    public Optional<LootTableStructure> getEditedStructure(ResourceLocation tableId) {
        // Check edited cache first
        LootTableStructure edited = editedCache.get(tableId);
        if (edited != null) {
            return Optional.of(edited);
        }

        // Check if we have edits for this table
        LootTableEdit edit = edits.get(tableId);
        if (edit == null || !edit.hasOperations()) {
            return getCachedOriginalStructure(tableId);
        }

        // Get original and apply edits
        LootTableStructure original = originalCache.get(tableId);
        if (original == null) {
            return Optional.empty();
        }

        // Apply all operations and cache
        LootTableStructure result = LootEditApplicator.applyAll(original, edit);
        editedCache.put(tableId, result);
        return Optional.of(result);
    }

    /**
     * Check if a table has edits.
     */
    public boolean hasEdits(ResourceLocation tableId) {
        LootTableEdit edit = edits.get(tableId);
        return edit != null && edit.hasOperations();
    }

    // ===== Edit Operations =====

    /**
     * Get the edit record for a table (creates if not exists).
     */
    public LootTableEdit getOrCreateEdit(ResourceLocation tableId) {
        return edits.computeIfAbsent(tableId, LootTableEdit::create);
    }

    /**
     * Get the edit record for a table (may be null).
     */
    public LootTableEdit getEdit(ResourceLocation tableId) {
        return edits.get(tableId);
    }

    /**
     * Apply an operation to a table's edits.
     */
    public void applyOperation(ResourceLocation tableId, LootEditOperation operation) {
        LootTableEdit edit = getOrCreateEdit(tableId);
        LootTableEdit newEdit = edit.withOperation(operation);
        edits.put(tableId, newEdit);

        // Invalidate edited cache for this table
        editedCache.remove(tableId);

        Isotope.LOGGER.debug("Applied edit to {}: {}", tableId, operation.getDescription());
        notifyListeners();
    }

    /**
     * Undo the last operation on a table.
     */
    public boolean undoLastOperation(ResourceLocation tableId) {
        LootTableEdit edit = edits.get(tableId);
        if (edit == null || !edit.hasOperations()) {
            return false;
        }

        LootTableEdit newEdit = edit.undoTo(edit.getOperationCount() - 1);
        if (newEdit.hasOperations()) {
            edits.put(tableId, newEdit);
        } else {
            edits.remove(tableId);
        }

        // Invalidate edited cache
        editedCache.remove(tableId);

        Isotope.LOGGER.debug("Undid last edit on {}", tableId);
        notifyListeners();
        return true;
    }

    /**
     * Clear all edits for a table.
     */
    public void clearEdits(ResourceLocation tableId) {
        edits.remove(tableId);
        editedCache.remove(tableId);
        Isotope.LOGGER.debug("Cleared all edits for {}", tableId);
        notifyListeners();
    }

    /**
     * Clear all edits.
     */
    public void clearAllEdits() {
        edits.clear();
        editedCache.clear();
        Isotope.LOGGER.info("Cleared all loot table edits");
        notifyListeners();
    }

    /**
     * Get all tables that have been edited.
     */
    public Set<ResourceLocation> getEditedTables() {
        return new HashSet<>(edits.keySet());
    }

    /**
     * Get the number of edited tables.
     */
    public int getEditedTableCount() {
        return (int) edits.values().stream()
            .filter(LootTableEdit::hasOperations)
            .count();
    }

    // ===== Cache Management =====

    /**
     * Clear all caches (usually on world unload).
     */
    public void clearCaches() {
        originalCache.clear();
        editedCache.clear();
        Isotope.LOGGER.debug("Cleared loot table caches");
    }

    /**
     * Clear everything (caches and edits).
     */
    public void reset() {
        edits.clear();
        originalCache.clear();
        editedCache.clear();
        testModeActive = false;
        Isotope.LOGGER.info("Reset LootEditManager");
        notifyListeners();
    }

    // ===== Serialization for Save System =====

    /**
     * Get all edits for serialization.
     */
    public Map<ResourceLocation, LootTableEdit> getAllEdits() {
        return new HashMap<>(edits);
    }

    /**
     * Load edits from save system.
     */
    public void loadEdits(Map<ResourceLocation, LootTableEdit> savedEdits) {
        edits.clear();
        edits.putAll(savedEdits);
        editedCache.clear(); // Force rebuild
        Isotope.LOGGER.info("Loaded {} loot table edits", savedEdits.size());
        notifyListeners();
    }

    // ===== Listeners =====

    /**
     * Add a listener for edit changes.
     */
    public void addListener(EditListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(EditListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (EditListener listener : listeners) {
            try {
                listener.onEditsChanged();
            } catch (Exception e) {
                Isotope.LOGGER.error("Error notifying edit listener: {}", e.getMessage());
            }
        }
    }

    /**
     * Listener interface for edit changes.
     */
    @FunctionalInterface
    public interface EditListener {
        void onEditsChanged();
    }
}
