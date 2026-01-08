package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootTableSerializer;
import dev.isotope.export.ExportManager;
import dev.isotope.export.KubeJSExporter;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.validation.LootTableValidator;
import dev.isotope.session.EditorSession;
import dev.isotope.session.SessionManager;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.KeyboardShortcuts;
import dev.isotope.ui.TabManager;
import dev.isotope.ui.widget.ActivityBar;
import dev.isotope.ui.widget.CommandPalette;
import dev.isotope.ui.widget.ContextMenu;
import dev.isotope.ui.widget.EditorTabBar;
import dev.isotope.ui.widget.DiffPanel;
import dev.isotope.ui.widget.DropRatePanel;
import dev.isotope.ui.widget.GlobalSearchWidget;
import dev.isotope.ui.widget.HistoryLogPanel;
import dev.isotope.ui.widget.InlineEditField;
import dev.isotope.ui.widget.LootTableBrowserWidget;
import dev.isotope.ui.widget.LootTableEditPanel;
import dev.isotope.ui.widget.ValidationPanel;
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


/**
 * Unified loot table editor screen with VS Code-style layout.
 *
 * Layout:
 * - Activity bar (left edge): Panel switching icons
 * - Left panel: Loot table browser OR analysis panels
 * - Right panel: Edit area with tabs
 * - Command palette (Ctrl+P): Quick access to all commands
 */
@Environment(EnvType.CLIENT)
public class LootEditorScreen extends Screen implements KeyboardShortcuts.ShortcutContext {

    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_BAR_HEIGHT = 22;
    private static final int STATUS_BAR_HEIGHT = 20;
    private static final int LEFT_PANEL_WIDTH = 200;
    private static final int PADDING = 5;

    // Activity bar panel IDs
    private static final String PANEL_BROWSER = "browser";
    private static final String PANEL_SEARCH = "search";
    private static final String PANEL_ANALYSIS = "analysis";
    private static final String PANEL_HISTORY = "history";
    private static final String PANEL_VALIDATION = "validation";

    // Core widgets
    private ActivityBar activityBar;
    private CommandPalette commandPalette;
    private LootTableBrowserWidget browser;
    private LootTableEditPanel editPanel;
    private EditorTabBar tabBar;
    private GlobalSearchWidget searchWidget;
    private DropRatePanel dropRatePanel;
    private DiffPanel diffPanel;
    private HistoryLogPanel historyPanel;
    private ValidationPanel validationPanel;

    // Essential toolbar buttons (kept minimal)
    private Button undoButton;
    private Button redoButton;
    private Button testModeButton;
    private Button exportButton;

    // Overlay states
    private boolean commandPaletteVisible = false;
    private boolean globalSearchVisible = false;

    // Context menu
    @Nullable
    private ContextMenu contextMenu;
    private boolean contextMenuVisible = false;

    // Active panel (controlled by activity bar)
    private String activePanel = PANEL_BROWSER;

    // Analysis panel visibility (when PANEL_ANALYSIS is active)
    private boolean showRates = true;
    private boolean showDiff = false;

    // Tab management
    private final TabManager tabManager = new TabManager();

    // Edit listener for updating button states
    private final LootEditManager.EditListener editListener = this::updateButtonStates;
    private final TabManager.TabChangeListener tabListener = this::onTabsChanged;

    public LootEditorScreen() {
        super(Component.literal("ISOTOPE"));
    }

    @Override
    protected void init() {
        super.init();

        // Register listeners
        LootEditManager.getInstance().addListener(editListener);
        tabManager.addListener(tabListener);

        // === Activity Bar (left edge) ===
        activityBar = new ActivityBar(0, HEADER_HEIGHT, height - HEADER_HEIGHT - STATUS_BAR_HEIGHT)
            .addItem(PANEL_BROWSER, "\u2630", "Browser (1)")        // ☰
            .addItem(PANEL_SEARCH, "\u2315", "Global Search (2)")   // ⌕
            .addItem(PANEL_ANALYSIS, "\u2261", "Analysis (3)")      // ≡
            .addItem(PANEL_HISTORY, "\u2398", "History (4)")        // ⎘
            .addItem(PANEL_VALIDATION, "\u2714", "Validation (5)")  // ✔
            .onSelect(item -> onActivityItemSelected(item.id()));
        activityBar.setSelectedId(activePanel);
        addRenderableWidget(activityBar);

        // === Header Toolbar (minimal - right side only) ===
        int buttonY = 5;
        int buttonX = width - PADDING;

        // Export button (primary action)
        buttonX -= 70;
        exportButton = Button.builder(Component.literal("Export"), b -> onExport())
            .pos(buttonX, buttonY)
            .size(65, 20)
            .tooltip(Tooltip.create(Component.literal("Export edits as datapack (Ctrl+S)")))
            .build();
        addRenderableWidget(exportButton);

        // Test mode toggle
        buttonX -= 90;
        testModeButton = Button.builder(Component.literal(getTestModeLabel()), b -> onToggleTestMode())
            .pos(buttonX, buttonY)
            .size(85, 20)
            .tooltip(Tooltip.create(Component.literal("Toggle test mode (F5)")))
            .build();
        addRenderableWidget(testModeButton);

        // Redo button
        buttonX -= 45;
        redoButton = Button.builder(Component.literal("\u21B7"), b -> onRedo())  // ↷
            .pos(buttonX, buttonY)
            .size(40, 20)
            .tooltip(Tooltip.create(Component.literal("Redo (Ctrl+Y)")))
            .build();
        addRenderableWidget(redoButton);

        // Undo button
        buttonX -= 45;
        undoButton = Button.builder(Component.literal("\u21B6"), b -> onUndo())  // ↶
            .pos(buttonX, buttonY)
            .size(40, 20)
            .tooltip(Tooltip.create(Component.literal("Undo (Ctrl+Z)")))
            .build();
        addRenderableWidget(undoButton);

        // === Main Content Area ===
        int contentX = ActivityBar.WIDTH;
        int contentY = HEADER_HEIGHT;
        int contentWidth = width - ActivityBar.WIDTH;
        int contentHeight = height - HEADER_HEIGHT - STATUS_BAR_HEIGHT;

        // Tab bar
        int tabBarX = contentX + LEFT_PANEL_WIDTH + PADDING;
        int tabBarWidth = contentWidth - LEFT_PANEL_WIDTH - PADDING * 2;
        tabBar = new EditorTabBar(tabBarX, contentY, tabBarWidth, tabManager);
        addRenderableWidget(tabBar);

        // Edit panel (always visible on right, fills available space)
        int editX = contentX + LEFT_PANEL_WIDTH + PADDING;
        int editY = contentY + TAB_BAR_HEIGHT + PADDING;
        int editWidth = contentWidth - LEFT_PANEL_WIDTH - PADDING * 2;
        int editHeight = contentHeight - TAB_BAR_HEIGHT - PADDING * 2;

        editPanel = new LootTableEditPanel(editX, editY, editWidth, editHeight);
        editPanel.setContextMenuListener(this::onContextMenuRequested);
        addRenderableWidget(editPanel);

        // === Left Panel (varies by activity bar selection) ===
        int leftPanelX = contentX;
        int leftPanelY = contentY;
        int leftPanelHeight = contentHeight;

        switch (activePanel) {
            case PANEL_BROWSER:
                browser = new LootTableBrowserWidget(
                    leftPanelX, leftPanelY, LEFT_PANEL_WIDTH, leftPanelHeight,
                    this::onTableSelected
                );
                addRenderableWidget(browser);
                browser.loadData();
                break;

            case PANEL_SEARCH:
                // Global search in left panel mode
                searchWidget = new GlobalSearchWidget(
                    leftPanelX, leftPanelY, LEFT_PANEL_WIDTH, leftPanelHeight,
                    this::onSearchResultSelected
                );
                addRenderableWidget(searchWidget);
                searchWidget.focusSearch();
                break;

            case PANEL_ANALYSIS:
                // Toggle buttons at top
                int toggleY = leftPanelY;
                int toggleBtnWidth = (LEFT_PANEL_WIDTH - 10) / 3;

                // Rates toggle
                Button ratesBtn = Button.builder(Component.literal(showRates ? "● Rates" : "○ Rates"), b -> {
                    showRates = !showRates;
                    if (!showRates && !showDiff) showDiff = true; // At least one must be on
                    rebuildWidgets();
                })
                    .pos(leftPanelX + 2, toggleY + 2)
                    .size(toggleBtnWidth, 16)
                    .build();
                addRenderableWidget(ratesBtn);

                // Diff toggle
                Button diffBtn = Button.builder(Component.literal(showDiff ? "● Diff" : "○ Diff"), b -> {
                    showDiff = !showDiff;
                    if (!showRates && !showDiff) showRates = true; // At least one must be on
                    rebuildWidgets();
                })
                    .pos(leftPanelX + 4 + toggleBtnWidth, toggleY + 2)
                    .size(toggleBtnWidth, 16)
                    .build();
                addRenderableWidget(diffBtn);

                // Both toggle
                Button bothBtn = Button.builder(Component.literal("Both"), b -> {
                    showRates = true;
                    showDiff = true;
                    rebuildWidgets();
                })
                    .pos(leftPanelX + 6 + toggleBtnWidth * 2, toggleY + 2)
                    .size(toggleBtnWidth, 16)
                    .build();
                addRenderableWidget(bothBtn);

                // Show rates and diff panels below the toggles
                int analysisY = leftPanelY + 22;
                int remainingHeight = leftPanelHeight - 22;

                if (showRates && showDiff) {
                    // Split space between both panels
                    int ratesHeight = remainingHeight * 2 / 3;
                    int diffHeight = remainingHeight - ratesHeight - 5;

                    dropRatePanel = new DropRatePanel(leftPanelX, analysisY, LEFT_PANEL_WIDTH, ratesHeight);
                    addRenderableWidget(dropRatePanel);

                    diffPanel = new DiffPanel(leftPanelX, analysisY + ratesHeight + 5, LEFT_PANEL_WIDTH, diffHeight);
                    addRenderableWidget(diffPanel);
                } else if (showRates) {
                    // Rates takes full height
                    dropRatePanel = new DropRatePanel(leftPanelX, analysisY, LEFT_PANEL_WIDTH, remainingHeight);
                    addRenderableWidget(dropRatePanel);
                } else if (showDiff) {
                    // Diff takes full height
                    diffPanel = new DiffPanel(leftPanelX, analysisY, LEFT_PANEL_WIDTH, remainingHeight);
                    addRenderableWidget(diffPanel);
                }

                updateAnalysisPanels();
                break;

            case PANEL_HISTORY:
                historyPanel = new HistoryLogPanel(leftPanelX, leftPanelY, LEFT_PANEL_WIDTH, leftPanelHeight);
                addRenderableWidget(historyPanel);
                historyPanel.refreshEntries();
                break;

            case PANEL_VALIDATION:
                validationPanel = new ValidationPanel(leftPanelX, leftPanelY, LEFT_PANEL_WIDTH, leftPanelHeight);
                validationPanel.setOnIssueSelected((poolIdx, entryIdx) -> {
                    // Navigate to the issue in the edit panel
                    if (editPanel != null && poolIdx >= 0) {
                        editPanel.selectPoolEntry(poolIdx, entryIdx);
                    }
                });
                addRenderableWidget(validationPanel);
                // Set current table for validation
                var tableId = getSelectedTable();
                if (tableId != null) {
                    validationPanel.setTable(tableId);
                }
                break;
        }

        // === Command Palette (overlay, initially hidden) ===
        commandPalette = new CommandPalette(width, height);
        registerCommands();
        commandPalette.onClose(v -> {
            commandPaletteVisible = false;
        });
        // Keep visible=false so it doesn't render in super.render() - we render it manually for z-order
        commandPalette.visible = false;
        addRenderableWidget(commandPalette);

        // Restore from active tab if any
        tabManager.getActiveTableId().ifPresent(tableId -> editPanel.setLootTable(tableId));

        // Initialize button states
        updateButtonStates();
    }

    /**
     * Register all commands in the command palette.
     */
    private void registerCommands() {
        commandPalette
            // File operations
            .addCommand("export", "\u2913", "Export as Datapack", "Ctrl+S", this::onExport)
            .addCommand("copy-json", "\u2398", "Copy JSON to Clipboard", "", this::onCopyJson)
            .addCommand("import", "\u2912", "Import from Datapack...", "", this::onOpenImport)

            // Edit operations
            .addCommand("undo", "\u21B6", "Undo", "Ctrl+Z", this::onUndo)
            .addCommand("redo", "\u21B7", "Redo", "Ctrl+Y", this::onRedo)
            .addCommand("copy", "\u2398", "Copy Entry", "Ctrl+C", () -> {
                if (getSelectedTable() != null) editPanel.copySelected();
            })
            .addCommand("paste", "\u2399", "Paste Entry", "Ctrl+V", () -> {
                if (getSelectedTable() != null) editPanel.pasteFromClipboard();
            })
            .addCommand("delete", "\u2715", "Delete Selected", "Del", () -> {
                if (getSelectedTable() != null) editPanel.deleteSelected();
            })
            .addCommand("duplicate", "\u2750", "Duplicate Entry", "Ctrl+D", () -> {
                if (getSelectedTable() != null) editPanel.duplicateSelected();
            })

            // Testing
            .addCommand("test-world", "\uD83E\uDDEA", "Test Changes in World...", "", this::onOpenTestSetup)

            // View operations
            .addCommand("toggle-test", "\u25CF", "Toggle Test Mode (Live)", "F5", this::onToggleTestMode)
            .addCommand("panel-browser", "\u2630", "Show Browser Panel", "1", () -> switchPanel(PANEL_BROWSER))
            .addCommand("panel-search", "\u2315", "Show Search Panel", "2", () -> switchPanel(PANEL_SEARCH))
            .addCommand("panel-analysis", "\u2261", "Show Analysis Panel", "3", () -> switchPanel(PANEL_ANALYSIS))
            .addCommand("panel-history", "\u2398", "Show History Panel", "4", () -> switchPanel(PANEL_HISTORY))
            .addCommand("panel-validation", "\u2714", "Show Validation Panel", "5", () -> switchPanel(PANEL_VALIDATION))
            .addCommand("global-search", "\u2315", "Global Item Search", "Ctrl+Shift+F", this::globalSearch)

            // Tools
            .addCommand("simulate", "🎲", "Simulate Drops...", "", this::onOpenSimulation)
            .addCommand("quickfix", "⚡", "Quick Fix Wizards...", "", this::onOpenQuickFix)
            .addCommand("bulk", "📦", "Bulk Operations...", "", this::onOpenBulkOps)
            .addCommand("validate", "\u2714", "Validate Loot Table", "", this::onValidateTable)
            .addCommand("validate-all", "\u2714", "Validate All Edited Tables", "", this::onValidateAll)
            .addCommand("export-validation", "\u2714", "Export Validation Report...", "", this::onExportValidationReport)
            .addCommand("kubejs", "K", "Export as KubeJS Script", "", this::onExportKubeJS)
            .addCommand("compare", "\u2194", "Compare Two Tables...", "", this::onOpenCompare)
            .addCommand("sessions", "\u2630", "Manage Sessions...", "", this::onOpenSessions)

            // Help
            .addCommand("shortcuts", "?", "Keyboard Shortcuts", "F1", this::showHelp)
            .addCommand("documentation", "\u2709", "Open Documentation", "", HelpLinks::openDocs)
            .addCommand("report-issue", "\u2691", "Report Issue on GitHub", "", HelpLinks::openIssues);
    }

    private void switchPanel(String panelId) {
        if (!activePanel.equals(panelId)) {
            activePanel = panelId;
            activityBar.setSelectedId(panelId);
            rebuildWidgets();
        }
    }

    private void onActivityItemSelected(String panelId) {
        switchPanel(panelId);
    }

    private void onTableSelected(ResourceLocation tableId) {
        tabManager.openTab(tableId);
    }

    private void onSearchResultSelected(ResourceLocation tableId) {
        tabManager.openTab(tableId);
        // Switch back to browser after selection
        switchPanel(PANEL_BROWSER);
    }

    private void onTabsChanged() {
        var activeTable = getSelectedTable();
        editPanel.setLootTable(activeTable);
        updateAnalysisPanels();
        updateValidationPanel();
        updateValidationBadge();
        updateButtonStates();
    }

    private void updateValidationPanel() {
        if (validationPanel != null && activePanel.equals(PANEL_VALIDATION)) {
            validationPanel.setTable(getSelectedTable());
        }
        updateValidationBadge();
    }

    private void updateValidationBadge() {
        // Update the badge on the validation activity bar item
        ResourceLocation tableId = getSelectedTable();
        if (tableId == null) {
            activityBar.setBadge(PANEL_VALIDATION, 0);
            return;
        }

        var structure = LootEditManager.getInstance().getEditedStructure(tableId)
            .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));

        if (structure == null) {
            activityBar.setBadge(PANEL_VALIDATION, 0);
            return;
        }

        var result = LootTableValidator.validate(tableId, structure);
        int issueCount = result.errorCount() + result.warningCount();
        activityBar.setBadge(PANEL_VALIDATION, issueCount);
    }

    // ===== Context Menu Handling =====

    private void onContextMenuRequested(LootTableEditPanel.ContextMenuRequest request) {
        // Close any existing context menu
        closeContextMenu();

        // Create appropriate context menu based on type
        if (request.type() == LootTableEditPanel.ContextMenuType.ENTRY) {
            contextMenu = createEntryContextMenu(request.x(), request.y(), request.poolIdx(), request.entryIdx());
        } else if (request.type() == LootTableEditPanel.ContextMenuType.POOL) {
            contextMenu = createPoolContextMenu(request.x(), request.y(), request.poolIdx());
        }

        if (contextMenu != null) {
            contextMenu.reposition(width, height);
            contextMenu.onClose(v -> closeContextMenu());
            contextMenuVisible = true;
        }
    }

    private ContextMenu createEntryContextMenu(int x, int y, int poolIdx, int entryIdx) {
        boolean canPaste = dev.isotope.editing.ClipboardManager.getInstance().hasEntry();
        boolean canMoveUp = entryIdx > 0;
        boolean canMoveDown = editPanel.canMoveDown();

        return new ContextMenu(x, y)
            .addItem("\u2398", "Copy", "Ctrl+C", () -> editPanel.copySelected())
            .addItem("\u2399", "Paste", "Ctrl+V", () -> editPanel.pasteFromClipboard(), canPaste)
            .addSeparator()
            .addItem("\u2191", "Move Up", "", () -> editPanel.moveEntryUp(poolIdx, entryIdx), canMoveUp)
            .addItem("\u2193", "Move Down", "", () -> editPanel.moveEntryDown(poolIdx, entryIdx), canMoveDown)
            .addSeparator()
            .addItem("\u2750", "Duplicate", "Ctrl+D", () -> editPanel.duplicateSelected())
            .addItem("\u2715", "Delete", "Del", () -> editPanel.deleteSelected())
            .addSeparator()
            .addItem("\u2606", "Save as Template...", "", () -> editPanel.saveSelectedAsTemplate());
    }

    private ContextMenu createPoolContextMenu(int x, int y, int poolIdx) {
        return new ContextMenu(x, y)
            .addItem("+", "Add Item...", "", () -> editPanel.openAddItem(poolIdx))
            .addItem("\u2630", "Add from Template...", "", () -> editPanel.openTemplatePicker(poolIdx))
            .addSeparator()
            .addItem("\u2750", "Duplicate Pool", "", () -> editPanel.duplicatePool(poolIdx))
            .addItem("\u2717", "Clear Pool", "", () -> editPanel.clearPool(poolIdx))
            .addItem("\u2715", "Delete Pool", "", () -> editPanel.deletePool(poolIdx))
            .addSeparator()
            .addItem("+", "Add New Pool", "", () -> editPanel.addPool());
    }

    private void closeContextMenu() {
        contextMenuVisible = false;
        contextMenu = null;
    }

    @Nullable
    private ResourceLocation getSelectedTable() {
        return tabManager.getActiveTableId().orElse(null);
    }

    private String getTestModeLabel() {
        boolean active = LootEditManager.getInstance().isTestModeActive();
        return "Test " + (active ? "\u25CF" : "\u25CB");  // ● or ○
    }

    private void onToggleTestMode() {
        LootEditManager.getInstance().toggleTestMode();
        testModeButton.setMessage(Component.literal(getTestModeLabel()));
        IsotopeToast.info("Test Mode", LootEditManager.getInstance().isTestModeActive() ? "Enabled" : "Disabled");
    }

    private void onUndo() {
        if (getSelectedTable() != null) {
            if (LootEditManager.getInstance().undo(getSelectedTable())) {
                editPanel.refresh();
                updateAnalysisPanels();
            }
        }
    }

    private void onRedo() {
        if (getSelectedTable() != null) {
            if (LootEditManager.getInstance().redo(getSelectedTable())) {
                editPanel.refresh();
                updateAnalysisPanels();
            }
        }
    }

    private void onExport() {
        if (minecraft != null) {
            minecraft.setScreen(new ExportScreen(this));
        }
    }

    private void onCopyJson() {
        ResourceLocation tableId = getSelectedTable();
        if (tableId == null) {
            IsotopeToast.info("Copy JSON", "No loot table selected");
            return;
        }

        var structure = LootEditManager.getInstance().getEditedStructure(tableId)
            .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));

        if (structure == null) {
            IsotopeToast.error("Copy JSON", "Could not load loot table");
            return;
        }

        try {
            String json = LootTableSerializer.toJson(structure);
            if (minecraft != null) {
                minecraft.keyboardHandler.setClipboard(json);
                IsotopeToast.success("Copied", tableId.getPath() + " JSON copied");
            }
        } catch (Exception e) {
            IsotopeToast.error("Copy Failed", e.getMessage());
            Isotope.LOGGER.error("Failed to copy JSON", e);
        }
    }

    private void onOpenImport() {
        if (minecraft != null) {
            minecraft.setScreen(new ImportScreen(this, tabManager));
        }
    }

    private void onOpenCompare() {
        if (minecraft != null) {
            ResourceLocation current = getSelectedTable();
            if (current != null) {
                minecraft.setScreen(new CompareScreen(this, current, null));
            } else {
                minecraft.setScreen(new CompareScreen(this));
            }
        }
    }

    private void onOpenSimulation() {
        if (minecraft != null) {
            ResourceLocation current = getSelectedTable();
            if (current != null) {
                minecraft.setScreen(new SimulationScreen(this, current));
            } else {
                IsotopeToast.warning("No Table", "Select a loot table first");
            }
        }
    }

    private void onOpenQuickFix() {
        if (minecraft != null) {
            ResourceLocation current = getSelectedTable();
            if (current != null) {
                minecraft.setScreen(new QuickFixScreen(this, current));
            } else {
                IsotopeToast.warning("No Table", "Select a loot table first");
            }
        }
    }

    private void onOpenBulkOps() {
        if (minecraft != null) {
            minecraft.setScreen(new BulkOperationScreen(this));
        }
    }

    private void onExportKubeJS() {
        if (LootEditManager.getInstance().getEditedTables().isEmpty()) {
            IsotopeToast.warning("No Edits", "No edited loot tables to export");
            return;
        }

        var result = KubeJSExporter.getInstance().export(msg -> {
            Isotope.LOGGER.info("[KubeJS Export] {}", msg);
        });

        if (result.success()) {
            IsotopeToast.success("KubeJS Export", "Script saved to kubejs/server_scripts/");
        } else {
            IsotopeToast.error("Export Failed", result.error());
        }
    }

    private void onValidateTable() {
        ResourceLocation tableId = getSelectedTable();
        if (tableId == null) {
            IsotopeToast.warning("No Table", "Select a loot table first");
            return;
        }

        var structure = LootEditManager.getInstance().getEditedStructure(tableId)
            .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));

        if (structure == null) {
            IsotopeToast.error("Validation", "Could not load loot table");
            return;
        }

        var result = LootTableValidator.validate(tableId, structure);

        if (!result.hasIssues()) {
            IsotopeToast.success("Validation", "No issues found!");
        } else {
            String summary = result.errorCount() + " error(s), " + result.warningCount() + " warning(s)";
            if (result.hasErrors()) {
                IsotopeToast.error("Validation", summary);
            } else {
                IsotopeToast.warning("Validation", summary);
            }

            // Log details
            for (var issue : result.issues()) {
                Isotope.LOGGER.info("[Validate] {} - {}: {}", issue.severity().label, issue.type().name, issue.message());
            }
        }
    }

    private void onValidateAll() {
        var editedTables = LootEditManager.getInstance().getEditedTables();
        if (editedTables.isEmpty()) {
            IsotopeToast.info("Validate All", "No edited tables to validate");
            return;
        }

        int totalErrors = 0;
        int totalWarnings = 0;
        int tablesWithIssues = 0;

        for (ResourceLocation tableId : editedTables) {
            var structure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(null);
            if (structure == null) continue;

            var result = LootTableValidator.validate(tableId, structure);
            if (result.hasIssues()) {
                tablesWithIssues++;
                totalErrors += result.errorCount();
                totalWarnings += result.warningCount();

                // Log issues for this table
                Isotope.LOGGER.info("[Validate All] {}: {} error(s), {} warning(s)",
                    tableId, result.errorCount(), result.warningCount());
                for (var issue : result.issues()) {
                    Isotope.LOGGER.info("  {} - {}: {}", issue.severity().label, issue.type().name, issue.message());
                }
            }
        }

        if (totalErrors == 0 && totalWarnings == 0) {
            IsotopeToast.success("Validate All", editedTables.size() + " table(s) validated, no issues!");
        } else {
            String summary = String.format("%d table(s): %d error(s), %d warning(s)",
                tablesWithIssues, totalErrors, totalWarnings);
            if (totalErrors > 0) {
                IsotopeToast.error("Validate All", summary);
            } else {
                IsotopeToast.warning("Validate All", summary);
            }
        }
    }

    private void onExportValidationReport() {
        var editedTables = LootEditManager.getInstance().getEditedTables();
        if (editedTables.isEmpty()) {
            IsotopeToast.info("Export Report", "No edited tables to validate");
            return;
        }

        // Export as markdown by default (most useful format)
        ExportManager.getInstance().exportValidationReport(
            ExportManager.ReportFormat.MARKDOWN,
            msg -> Isotope.LOGGER.info("[Validation Report] {}", msg)
        );

        IsotopeToast.success("Report Exported", "Saved to isotope-reports/");
    }

    private void onOpenSessions() {
        if (minecraft != null) {
            minecraft.setScreen(new SessionScreen(this, tabManager));
        }
    }

    private void onOpenTestSetup() {
        if (minecraft != null) {
            minecraft.setScreen(new TestSetupScreen(this));
        }
    }

    private void updateAnalysisPanels() {
        if (dropRatePanel != null) {
            var tableId = getSelectedTable();
            if (tableId != null) {
                var structure = LootEditManager.getInstance().getEditedStructure(tableId)
                    .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));
                dropRatePanel.setStructure(structure);
            } else {
                dropRatePanel.setStructure(null);
            }
        }

        if (diffPanel != null) {
            diffPanel.setTable(getSelectedTable());
        }
    }

    private void updateButtonStates() {
        ResourceLocation selected = getSelectedTable();
        if (selected != null) {
            undoButton.active = LootEditManager.getInstance().canUndo(selected);
            redoButton.active = LootEditManager.getInstance().canRedo(selected);
        } else {
            undoButton.active = false;
            redoButton.active = false;
        }

        // Update panels if visible
        if (activePanel.equals(PANEL_ANALYSIS)) {
            updateAnalysisPanels();
        }
        if (activePanel.equals(PANEL_HISTORY) && historyPanel != null) {
            historyPanel.refreshEntries();
        }
        if (activePanel.equals(PANEL_VALIDATION)) {
            updateValidationPanel();
        }

        // Always update validation badge when edits occur
        updateValidationBadge();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Header bar
        graphics.fill(0, 0, width, HEADER_HEIGHT, IsotopeColors.BACKGROUND_SOLID);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, IsotopeColors.BORDER_INNER);

        // Title
        graphics.drawString(font, "ISOTOPE", ActivityBar.WIDTH + 10, 10, IsotopeColors.ACCENT_GOLD, false);

        // Command palette hint
        graphics.drawString(font, "Ctrl+P", ActivityBar.WIDTH + 70, 11, IsotopeColors.TEXT_MUTED, false);

        // Edit count indicator
        int editCount = LootEditManager.getInstance().getEditedTableCount();
        if (editCount > 0) {
            String editText = editCount + " edit" + (editCount > 1 ? "s" : "");
            graphics.drawString(font, editText, width - 300, 10, IsotopeColors.BADGE_MODIFIED, false);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Status bar (rendered AFTER widgets so it's on top)
        int statusY = height - STATUS_BAR_HEIGHT;
        graphics.fill(0, statusY, width, height, IsotopeColors.BACKGROUND_MEDIUM);
        graphics.fill(0, statusY, width, statusY + 1, IsotopeColors.BORDER_INNER);

        // Status bar content
        ResourceLocation selectedTable = getSelectedTable();
        if (selectedTable != null) {
            graphics.drawString(font, selectedTable.toString(), ActivityBar.WIDTH + 5, statusY + 6,
                IsotopeColors.TEXT_SECONDARY, false);
        } else {
            graphics.drawString(font, "No table selected", ActivityBar.WIDTH + 5, statusY + 6,
                IsotopeColors.TEXT_MUTED, false);
        }

        // Show edit count in status bar too
        if (editCount > 0) {
            String statusEdit = editCount + " unsaved edit" + (editCount > 1 ? "s" : "");
            int statusEditWidth = font.width(statusEdit);
            graphics.drawString(font, statusEdit, width - statusEditWidth - 10, statusY + 6,
                IsotopeColors.BADGE_MODIFIED, false);
        }

        // Command palette (rendered after widgets so it's on top)
        if (commandPaletteVisible && commandPalette != null) {
            commandPalette.render(graphics, mouseX, mouseY, partialTick);
        }

        // Context menu (rendered last, on top of everything including command palette)
        if (contextMenuVisible && contextMenu != null) {
            contextMenu.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Context menu takes priority over everything
        if (contextMenuVisible && contextMenu != null) {
            if (contextMenu.isMouseOver(mouseX, mouseY)) {
                return contextMenu.mouseClicked(mouseX, mouseY, button);
            } else {
                // Click outside closes context menu
                closeContextMenu();
                return true;
            }
        }

        // Command palette takes priority
        if (commandPaletteVisible) {
            if (commandPalette.isMouseOver(mouseX, mouseY)) {
                return commandPalette.mouseClicked(mouseX, mouseY, button);
            } else {
                // Click outside closes palette
                commandPaletteVisible = false;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Context menu - Escape closes it
        if (contextMenuVisible && contextMenu != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeContextMenu();
                return true;
            }
            // Block other keys while context menu is open
            return true;
        }

        // Command palette takes priority when visible
        if (commandPaletteVisible) {
            return commandPalette.keyPressed(keyCode, scanCode, modifiers);
        }

        // Ctrl+P to open command palette
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_P) {
            commandPaletteVisible = true;
            // Don't set commandPalette.visible - we render it manually for proper z-order
            commandPalette.reset();
            setFocused(commandPalette);
            return true;
        }

        // Number keys for quick panel switching (only if no text field is focused)
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5 && modifiers == 0) {
            // Don't intercept if a text field might be focused
            if (getFocused() instanceof net.minecraft.client.gui.components.EditBox) {
                return false; // Let the edit box handle it
            }
            // Also check if edit panel is in editing mode
            if (editPanel != null && editPanel.isEditing()) {
                return false;
            }
            String[] panels = {PANEL_BROWSER, PANEL_SEARCH, PANEL_ANALYSIS, PANEL_HISTORY, PANEL_VALIDATION};
            int index = keyCode - GLFW.GLFW_KEY_1;
            switchPanel(panels[index]);
            return true;
        }

        // F5 for test mode
        if (keyCode == GLFW.GLFW_KEY_F5) {
            onToggleTestMode();
            return true;
        }

        // Edit panel - inline editing OR navigation
        if (editPanel != null) {
            // Always forward to edit panel for editing mode
            if (editPanel.isEditing()) {
                if (editPanel.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            // Forward navigation keys (arrows, home, end, enter) to edit panel
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN ||
                keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END ||
                keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (editPanel.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }

        // Let focused widgets handle the key
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Delegate to keyboard shortcuts handler
        if (KeyboardShortcuts.handle(keyCode, modifiers, this)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (commandPaletteVisible) {
            return commandPalette.charTyped(chr, modifiers);
        }

        if (editPanel != null && editPanel.isEditing()) {
            if (editPanel.charTyped(chr, modifiers)) {
                return true;
            }
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (commandPaletteVisible && commandPalette.isMouseOver(mouseX, mouseY)) {
            return commandPalette.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ===== ShortcutContext Implementation =====

    @Override
    public void undo() {
        onUndo();
    }

    @Override
    public void redo() {
        onRedo();
    }

    @Override
    public void save() {
        onExport();
    }

    @Override
    public void focusSearch() {
        if (browser != null) {
            browser.focusSearch();
        }
    }

    @Override
    public void globalSearch() {
        switchPanel(PANEL_SEARCH);
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
        if (commandPaletteVisible) {
            commandPaletteVisible = false;
        } else if (editPanel.hasSelection()) {
            editPanel.clearSelection();
        } else {
            onClose();
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
    public void moveUp() {
        if (getSelectedTable() != null && editPanel.canMoveUp()) {
            editPanel.moveEntryUp(editPanel.getSelectedPoolIdx(), editPanel.getSelectedEntryIdx());
        }
    }

    @Override
    public void moveDown() {
        if (getSelectedTable() != null && editPanel.canMoveDown()) {
            editPanel.moveEntryDown(editPanel.getSelectedPoolIdx(), editPanel.getSelectedEntryIdx());
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

        // Clean up widget resources
        if (browser != null) {
            browser.cleanup();
        }

        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    /**
     * Get current UI panel visibility state.
     */
    public EditorSession.UIState getCurrentUIState() {
        return new EditorSession.UIState(
            activePanel.equals(PANEL_ANALYSIS) && showRates,
            activePanel.equals(PANEL_ANALYSIS) && showDiff,
            activePanel.equals(PANEL_HISTORY)
        );
    }

    /**
     * Apply UI state from a session.
     */
    public void applyUIState(EditorSession.UIState state) {
        if (state == null) return;
        if (state.historyVisible()) {
            activePanel = PANEL_HISTORY;
        } else if (state.dropRatesVisible() || state.diffVisible()) {
            activePanel = PANEL_ANALYSIS;
            showRates = state.dropRatesVisible();
            showDiff = state.diffVisible();
        }
        rebuildWidgets();
    }
}
