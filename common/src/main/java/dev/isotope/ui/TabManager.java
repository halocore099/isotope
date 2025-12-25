package dev.isotope.ui;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages open tabs in the loot table editor.
 */
public final class TabManager {

    private final List<EditorTab> tabs = new ArrayList<>();
    private int activeTabIndex = -1;

    // Listeners for tab changes
    private final List<TabChangeListener> listeners = new ArrayList<>();

    /**
     * Open a new tab or switch to existing tab for the given table.
     */
    public void openTab(ResourceLocation tableId) {
        // Check if already open
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).tableId().equals(tableId)) {
                switchTab(i);
                return;
            }
        }

        // Create new tab
        EditorTab tab = EditorTab.create(tableId);
        tabs.add(tab);
        activeTabIndex = tabs.size() - 1;
        notifyListeners();
    }

    /**
     * Close a tab by index.
     */
    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        tabs.remove(index);

        // Adjust active index
        if (tabs.isEmpty()) {
            activeTabIndex = -1;
        } else if (activeTabIndex >= tabs.size()) {
            activeTabIndex = tabs.size() - 1;
        } else if (activeTabIndex > index) {
            activeTabIndex--;
        }

        notifyListeners();
    }

    /**
     * Close the currently active tab.
     */
    public void closeActiveTab() {
        if (activeTabIndex >= 0) {
            closeTab(activeTabIndex);
        }
    }

    /**
     * Switch to a tab by index.
     */
    public void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (index == activeTabIndex) return;

        activeTabIndex = index;
        notifyListeners();
    }

    /**
     * Switch to the next tab (cycling).
     */
    public void nextTab() {
        if (tabs.size() <= 1) return;
        switchTab((activeTabIndex + 1) % tabs.size());
    }

    /**
     * Switch to the previous tab (cycling).
     */
    public void previousTab() {
        if (tabs.size() <= 1) return;
        switchTab((activeTabIndex - 1 + tabs.size()) % tabs.size());
    }

    /**
     * Get the active tab.
     */
    public Optional<EditorTab> getActiveTab() {
        if (activeTabIndex < 0 || activeTabIndex >= tabs.size()) {
            return Optional.empty();
        }
        return Optional.of(tabs.get(activeTabIndex));
    }

    /**
     * Get the active tab's table ID.
     */
    public Optional<ResourceLocation> getActiveTableId() {
        return getActiveTab().map(EditorTab::tableId);
    }

    /**
     * Get all open tabs.
     */
    public List<EditorTab> getTabs() {
        return new ArrayList<>(tabs);
    }

    /**
     * Get the number of open tabs.
     */
    public int getTabCount() {
        return tabs.size();
    }

    /**
     * Get the active tab index.
     */
    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    /**
     * Check if a table is currently open in a tab.
     */
    public boolean isOpen(ResourceLocation tableId) {
        return tabs.stream().anyMatch(t -> t.tableId().equals(tableId));
    }

    /**
     * Update the state of the active tab.
     */
    public void updateActiveTab(int scrollOffset, int selectedPool, int selectedEntry) {
        if (activeTabIndex < 0 || activeTabIndex >= tabs.size()) return;

        EditorTab current = tabs.get(activeTabIndex);
        EditorTab updated = new EditorTab(
            current.tableId(),
            current.displayName(),
            scrollOffset,
            selectedPool,
            selectedEntry
        );
        tabs.set(activeTabIndex, updated);
    }

    /**
     * Close all tabs.
     */
    public void closeAll() {
        tabs.clear();
        activeTabIndex = -1;
        notifyListeners();
    }

    // ===== Listeners =====

    public void addListener(TabChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TabChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (TabChangeListener listener : listeners) {
            listener.onTabsChanged();
        }
    }

    @FunctionalInterface
    public interface TabChangeListener {
        void onTabsChanged();
    }
}
