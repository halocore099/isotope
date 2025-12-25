package dev.isotope.ui;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents an open tab in the loot table editor.
 */
public record EditorTab(
    ResourceLocation tableId,
    String displayName,
    int scrollOffset,
    int selectedPool,
    int selectedEntry
) {
    /**
     * Create a new tab for a loot table.
     */
    public static EditorTab create(ResourceLocation tableId) {
        String name = tableId.getPath();
        // Shorten long names
        if (name.length() > 20) {
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash > 0) {
                name = name.substring(lastSlash + 1);
            }
        }
        return new EditorTab(tableId, name, 0, -1, -1);
    }

    /**
     * Create a copy with updated scroll position.
     */
    public EditorTab withScrollOffset(int offset) {
        return new EditorTab(tableId, displayName, offset, selectedPool, selectedEntry);
    }

    /**
     * Create a copy with updated selection.
     */
    public EditorTab withSelection(int pool, int entry) {
        return new EditorTab(tableId, displayName, scrollOffset, pool, entry);
    }

    /**
     * Check if this tab has unsaved edits.
     */
    public boolean hasUnsavedChanges() {
        return dev.isotope.editing.LootEditManager.getInstance().hasEdits(tableId);
    }
}
