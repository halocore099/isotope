package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootTableSerializer;
import dev.isotope.export.ExportManager;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.session.EditorSession;
import dev.isotope.session.SessionManager;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.KeyboardShortcuts;
import dev.isotope.ui.TabManager;
import dev.isotope.ui.widget.EditorTabBar;
import dev.isotope.ui.widget.DiffPanel;
import dev.isotope.ui.widget.DropRatePanel;
import dev.isotope.ui.widget.GlobalSearchWidget;
import dev.isotope.ui.widget.HistoryLogPanel;
import dev.isotope.ui.widget.InlineEditField;
import dev.isotope.ui.widget.LootTableBrowserWidget;
import dev.isotope.ui.widget.LootTableEditPanel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Unified loot table editor screen.
 *
 * Single screen with 2-panel layout:
 * - Left: Loot table browser with mod filter, search, and categories
 * - Right: Inline editing of selected loot table
 */
@Environment(EnvType.CLIENT)
public class LootEditorScreen extends Screen implements KeyboardShortcuts.ShortcutContext {

    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_BAR_HEIGHT = 22;
    private static final int LEFT_PANEL_WIDTH = 200;
    private static final int PADDING = 5;

    // Widgets
    private LootTableBrowserWidget browser;
    private LootTableEditPanel editPanel;
    private EditorTabBar tabBar;
    private GlobalSearchWidget searchWidget;
    private DropRatePanel dropRatePanel;
    private DiffPanel diffPanel;
    private HistoryLogPanel historyPanel;
    private Button undoButton;
    private Button redoButton;
    private Button ratesButton;
    private Button diffButton;
    private Button testModeButton;
    private Button exportButton;
    private Button sessionsButton;
    private Button importButton;
    private Button compareButton;
    private Button historyButton;
    private Button copyJsonButton;

    // Search overlay state
    private boolean searchVisible = false;

    // Bottom panels state
    private boolean dropRatesVisible = false;
    private boolean diffVisible = false;
    private boolean historyVisible = false;

    // Tab management
    private final TabManager tabManager = new TabManager();

    // State
    private boolean exporting = false;

    // Edit listener for updating button states
    private final LootEditManager.EditListener editListener = this::updateButtonStates;
    private final TabManager.TabChangeListener tabListener = this::onTabsChanged;

    public LootEditorScreen() {
        super(Component.literal("ISOTOPE"));
    }

    @Override
    protected void init() {
        super.init();

        // Header buttons (right side)
        int buttonY = 5;

        // Export button
        exportButton = Button.builder(Component.literal("Export"), this::onExport)
            .pos(width - 75, buttonY)
            .size(70, 20)
            .tooltip(Tooltip.create(Component.literal("Export edits as datapack (Ctrl+S)")))
            .build();
        addRenderableWidget(exportButton);

        // Test mode toggle
        testModeButton = Button.builder(Component.literal(getTestModeLabel()), this::onToggleTestMode)
            .pos(width - 165, buttonY)
            .size(85, 20)
            .tooltip(Tooltip.create(Component.literal("Toggle test mode - applies edits to loot generation in-game")))
            .build();
        addRenderableWidget(testModeButton);

        // Redo button
        redoButton = Button.builder(Component.literal("Redo"), this::onRedo)
            .pos(width - 220, buttonY)
            .size(50, 20)
            .tooltip(Tooltip.create(Component.literal("Redo last undone change (Ctrl+Y)")))
            .build();
        addRenderableWidget(redoButton);

        // Undo button
        undoButton = Button.builder(Component.literal("Undo"), this::onUndo)
            .pos(width - 275, buttonY)
            .size(50, 20)
            .tooltip(Tooltip.create(Component.literal("Undo last change (Ctrl+Z)")))
            .build();
        addRenderableWidget(undoButton);

        // Rates toggle button
        ratesButton = Button.builder(Component.literal("Rates"), this::onToggleRates)
            .pos(width - 330, buttonY)
            .size(50, 20)
            .tooltip(Tooltip.create(Component.literal("Show drop rate visualization panel")))
            .build();
        addRenderableWidget(ratesButton);

        // Diff toggle button
        diffButton = Button.builder(Component.literal("Diff"), this::onToggleDiff)
            .pos(width - 380, buttonY)
            .size(45, 20)
            .tooltip(Tooltip.create(Component.literal("Show changes vs original loot table")))
            .build();
        addRenderableWidget(diffButton);

        // History toggle button
        historyButton = Button.builder(Component.literal("Log"), this::onToggleHistory)
            .pos(width - 420, buttonY)
            .size(35, 20)
            .tooltip(Tooltip.create(Component.literal("Show edit history log")))
            .build();
        addRenderableWidget(historyButton);

        // Sessions button
        sessionsButton = Button.builder(Component.literal("Sessions"), this::onOpenSessions)
            .pos(width - 495, buttonY)
            .size(70, 20)
            .tooltip(Tooltip.create(Component.literal("Save/load editing sessions")))
            .build();
        addRenderableWidget(sessionsButton);

        // Import button
        importButton = Button.builder(Component.literal("Import"), this::onOpenImport)
            .pos(width - 555, buttonY)
            .size(55, 20)
            .tooltip(Tooltip.create(Component.literal("Import edits from existing datapack")))
            .build();
        addRenderableWidget(importButton);

        // Compare button
        compareButton = Button.builder(Component.literal("Compare"), this::onOpenCompare)
            .pos(width - 625, buttonY)
            .size(65, 20)
            .tooltip(Tooltip.create(Component.literal("Compare two loot tables side-by-side")))
            .build();
        addRenderableWidget(compareButton);

        // Copy JSON button
        copyJsonButton = Button.builder(Component.literal("Copy"), this::onCopyJson)
            .pos(width - 675, buttonY)
            .size(45, 20)
            .tooltip(Tooltip.create(Component.literal("Copy loot table JSON to clipboard")))
            .build();
        addRenderableWidget(copyJsonButton);

        // Register listeners
        LootEditManager.getInstance().addListener(editListener);
        tabManager.addListener(tabListener);

        // Tab bar (right side, under header)
        int tabBarX = LEFT_PANEL_WIDTH + PADDING * 2;
        int tabBarWidth = width - tabBarX - PADDING;
        tabBar = new EditorTabBar(tabBarX, HEADER_HEIGHT, tabBarWidth, tabManager);
        addRenderableWidget(tabBar);

        // Left panel - Browser
        int contentY = HEADER_HEIGHT + TAB_BAR_HEIGHT + PADDING;
        int contentHeight = height - contentY - PADDING;

        browser = new LootTableBrowserWidget(
            PADDING,
            HEADER_HEIGHT,
            LEFT_PANEL_WIDTH,
            height - HEADER_HEIGHT - PADDING,
            this::onTableSelected
        );
        addRenderableWidget(browser);

        // Right panel - Edit panel
        int editX = LEFT_PANEL_WIDTH + PADDING * 2;
        int editWidth = width - editX - PADDING;

        // Calculate panel sizes based on visibility
        int bottomPanelHeight = 0;
        if (dropRatesVisible) bottomPanelHeight += 180;
        if (diffVisible) bottomPanelHeight += 150;
        if (historyVisible) bottomPanelHeight += 120;
        int editPanelHeight = contentHeight - bottomPanelHeight;

        editPanel = new LootTableEditPanel(
            editX,
            contentY,
            editWidth,
            editPanelHeight
        );
        addRenderableWidget(editPanel);

        int bottomY = contentY + editPanelHeight;

        // Drop rate panel
        int dropRateHeight = dropRatesVisible ? 180 : 0;
        dropRatePanel = new DropRatePanel(
            editX,
            bottomY,
            editWidth,
            dropRateHeight
        );
        dropRatePanel.visible = dropRatesVisible;
        addRenderableWidget(dropRatePanel);
        bottomY += dropRateHeight;

        // Diff panel
        int diffHeight = diffVisible ? 150 : 0;
        diffPanel = new DiffPanel(
            editX,
            bottomY,
            editWidth,
            diffHeight
        );
        diffPanel.visible = diffVisible;
        addRenderableWidget(diffPanel);
        bottomY += diffHeight;

        // History log panel
        int historyHeight = historyVisible ? 120 : 0;
        historyPanel = new HistoryLogPanel(
            editX,
            bottomY,
            editWidth,
            historyHeight > 0 ? historyHeight : 120
        );
        historyPanel.visible = historyVisible;
        addRenderableWidget(historyPanel);

        // Load data
        browser.loadData();

        // Global search overlay (initially hidden)
        int searchWidth = 300;
        int searchHeight = 400;
        searchWidget = new GlobalSearchWidget(
            (width - searchWidth) / 2,
            (height - searchHeight) / 2,
            searchWidth,
            searchHeight,
            this::onSearchResultSelected
        );
        searchWidget.visible = searchVisible;
        addRenderableWidget(searchWidget);

        // Restore from active tab if any
        tabManager.getActiveTableId().ifPresent(tableId -> editPanel.setLootTable(tableId));

        // Initialize button states
        updateButtonStates();
    }

    private void onSearchResultSelected(ResourceLocation tableId) {
        // Open the table in a tab
        tabManager.openTab(tableId);
        // Hide search overlay
        searchVisible = false;
        searchWidget.visible = false;
    }

    private void onTableSelected(ResourceLocation tableId) {
        // Open in a tab (or switch to existing tab)
        tabManager.openTab(tableId);
        // Tab listener will handle updating edit panel
    }

    private void onTabsChanged() {
        // Update edit panel to show active tab's table
        var activeTable = getSelectedTable();
        editPanel.setLootTable(activeTable);
        updateDropRatePanel();
        updateDiffPanel();
        updateButtonStates();
    }

    /**
     * Get the currently selected (active tab's) table ID.
     */
    @Nullable
    private ResourceLocation getSelectedTable() {
        return tabManager.getActiveTableId().orElse(null);
    }

    private String getTestModeLabel() {
        boolean active = LootEditManager.getInstance().isTestModeActive();
        return "Test Mode " + (active ? "●" : "○");
    }

    private void onToggleTestMode(Button button) {
        LootEditManager.getInstance().toggleTestMode();
        testModeButton.setMessage(Component.literal(getTestModeLabel()));
    }

    private void onToggleRates(Button button) {
        dropRatesVisible = !dropRatesVisible;
        // Reinitialize to resize panels
        rebuildWidgets();
        updateDropRatePanel();
    }

    private void onToggleDiff(Button button) {
        diffVisible = !diffVisible;
        // Reinitialize to resize panels
        rebuildWidgets();
        updateDiffPanel();
    }

    private void onToggleHistory(Button button) {
        historyVisible = !historyVisible;
        // Reinitialize to resize panels
        rebuildWidgets();
        if (historyVisible && historyPanel != null) {
            historyPanel.refreshEntries();
        }
    }

    private void onOpenSessions(Button button) {
        if (minecraft != null) {
            minecraft.setScreen(new SessionScreen(this, tabManager));
        }
    }

    private void onOpenImport(Button button) {
        if (minecraft != null) {
            minecraft.setScreen(new ImportScreen(this, tabManager));
        }
    }

    private void onOpenCompare(Button button) {
        if (minecraft != null) {
            // If a table is selected, use it as the left side
            ResourceLocation current = getSelectedTable();
            if (current != null) {
                minecraft.setScreen(new CompareScreen(this, current, null));
            } else {
                minecraft.setScreen(new CompareScreen(this));
            }
        }
    }

    private void onCopyJson(Button button) {
        ResourceLocation tableId = getSelectedTable();
        if (tableId == null) {
            IsotopeToast.info("Copy JSON", "No loot table selected");
            return;
        }

        // Get edited or original structure
        var structure = LootEditManager.getInstance().getEditedStructure(tableId)
            .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));

        if (structure == null) {
            IsotopeToast.error("Copy JSON", "Could not load loot table");
            return;
        }

        try {
            String json = LootTableSerializer.toJson(structure);

            // Copy to system clipboard
            if (minecraft != null) {
                minecraft.keyboardHandler.setClipboard(json);
                IsotopeToast.success("Copied", tableId.getPath() + " JSON copied to clipboard");
            }
        } catch (Exception e) {
            IsotopeToast.error("Copy Failed", e.getMessage());
            Isotope.LOGGER.error("Failed to copy JSON", e);
        }
    }

    private void updateDiffPanel() {
        if (diffPanel == null) return;
        diffPanel.setTable(getSelectedTable());
    }

    private void updateDropRatePanel() {
        if (dropRatePanel == null) return;

        var tableId = getSelectedTable();
        if (tableId == null) {
            dropRatePanel.setStructure(null);
            return;
        }

        // Get edited or original structure
        var structure = LootEditManager.getInstance().getEditedStructure(tableId)
            .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));
        dropRatePanel.setStructure(structure);
    }

    private void onUndo(Button button) {
        if (getSelectedTable() != null) {
            if (LootEditManager.getInstance().undo(getSelectedTable())) {
                editPanel.refresh();
                IsotopeToast.info("Undo", "Reverted last change");
            }
        }
    }

    private void onRedo(Button button) {
        if (getSelectedTable() != null) {
            if (LootEditManager.getInstance().redo(getSelectedTable())) {
                editPanel.refresh();
                IsotopeToast.info("Redo", "Restored change");
            }
        }
    }

    private void updateButtonStates() {
        if (getSelectedTable() != null) {
            undoButton.active = LootEditManager.getInstance().canUndo(getSelectedTable());
            redoButton.active = LootEditManager.getInstance().canRedo(getSelectedTable());
        } else {
            undoButton.active = false;
            redoButton.active = false;
        }

        // Refresh drop rate panel when edits change
        if (dropRatesVisible && dropRatePanel != null) {
            updateDropRatePanel();
        }

        // Refresh diff panel when edits change
        if (diffVisible && diffPanel != null) {
            diffPanel.recalculate();
        }

        // Refresh history panel when edits change
        if (historyVisible && historyPanel != null) {
            historyPanel.refreshEntries();
        }
    }

    private void onExport(Button button) {
        if (exporting) return;

        int editCount = LootEditManager.getInstance().getEditedTableCount();
        if (editCount == 0) {
            IsotopeToast.info("Export", "No edits to export");
            return;
        }

        exporting = true;
        exportButton.active = false;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String packName = "isotope_edits_" + timestamp;

        CompletableFuture.supplyAsync(() ->
            ExportManager.getInstance().exportEditedAsDatapack(packName, msg ->
                Isotope.LOGGER.info("[Export] {}", msg))
        ).thenAccept(result -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    exporting = false;
                    exportButton.active = true;

                    if (result.success()) {
                        IsotopeToast.success("Export Complete", editCount + " table(s) → " + packName);
                        Isotope.LOGGER.info("Exported to: {}", result.exportDirectory());
                    } else {
                        IsotopeToast.error("Export Failed", result.error());
                        Isotope.LOGGER.error("Export failed: {}", result.error());
                    }
                });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Header bar
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xFF1a1a1a);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF333333);

        // Title
        graphics.drawString(font, "ISOTOPE", 10, 10, IsotopeColors.ACCENT_GOLD, false);

        // Edit count indicator
        int editCount = LootEditManager.getInstance().getEditedTableCount();
        if (editCount > 0) {
            String editText = editCount + " edit" + (editCount > 1 ? "s" : "");
            int editWidth = font.width(editText);
            graphics.drawString(font, editText, width - 180 - editWidth, 10, IsotopeColors.BADGE_MODIFIED, false);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Delegate to centralized keyboard shortcuts handler
        if (KeyboardShortcuts.handle(keyCode, modifiers, this)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ===== ShortcutContext Implementation =====

    @Override
    public void undo() {
        onUndo(undoButton);
    }

    @Override
    public void redo() {
        onRedo(redoButton);
    }

    @Override
    public void save() {
        onExport(exportButton);
    }

    @Override
    public void focusSearch() {
        browser.focusSearch();
    }

    @Override
    public void globalSearch() {
        searchVisible = !searchVisible;
        searchWidget.visible = searchVisible;
        if (searchVisible) {
            searchWidget.focusSearch();
        }
    }

    @Override
    public void addItem() {
        if (getSelectedTable() != null) {
            editPanel.addItemToCurrentPool();
        }
    }

    @Override
    public void delete() {
        if (getSelectedTable() != null) {
            editPanel.deleteSelected();
        }
    }

    @Override
    public void duplicate() {
        if (getSelectedTable() != null) {
            editPanel.duplicateSelected();
        }
    }

    @Override
    public void escape() {
        if (searchVisible) {
            // Close search overlay first
            searchVisible = false;
            searchWidget.visible = false;
        } else {
            editPanel.clearSelection();
        }
    }

    @Override
    public void copy() {
        if (getSelectedTable() != null) {
            editPanel.copySelected();
        }
    }

    @Override
    public void paste() {
        if (getSelectedTable() != null) {
            editPanel.pasteFromClipboard();
        }
    }

    @Override
    public void showHelp() {
        if (minecraft != null) {
            minecraft.setScreen(new ShortcutsScreen(this));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Auto-save session if there are open tabs
        if (tabManager.getTabCount() > 0) {
            SessionManager.getInstance().autoSave(tabManager, getCurrentUIState());
        }

        // Unregister listeners
        LootEditManager.getInstance().removeListener(editListener);
        tabManager.removeListener(tabListener);

        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    /**
     * Get current UI panel visibility state.
     */
    public EditorSession.UIState getCurrentUIState() {
        return new EditorSession.UIState(dropRatesVisible, diffVisible, historyVisible);
    }

    /**
     * Apply UI state from a session.
     */
    public void applyUIState(EditorSession.UIState state) {
        if (state == null) return;
        dropRatesVisible = state.dropRatesVisible();
        diffVisible = state.diffVisible();
        historyVisible = state.historyVisible();
        rebuildWidgets();
    }
}
