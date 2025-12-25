package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.save.AnalysisSaveManager;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import dev.isotope.ui.widget.IsotopeWindow;
import dev.isotope.ui.widget.LootCategoryListWidget;
import dev.isotope.ui.widget.LootTableDetailPanel;
import dev.isotope.ui.widget.LootTableListWidget;
import dev.isotope.ui.widget.NamespaceListWidget;
import dev.isotope.ui.widget.StructureDetailPanel;
import dev.isotope.ui.widget.StructureListWidget;
import dev.isotope.ui.widget.VanillaTabBar;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Main ISOTOPE screen with 3-panel layout.
 * Left: Namespace (mod) list
 * Center: Structure list
 * Right: Structure details
 */
@Environment(EnvType.CLIENT)
public class MainScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Worldgen Analysis");

    // Panel dimensions
    private static final int LEFT_PANEL_WIDTH = 150;
    private static final int RIGHT_PANEL_WIDTH = 220;
    private static final int WINDOW_MARGIN = 20;
    private static final int PADDING = 5;

    // Window frame
    private IsotopeWindow window;
    private VanillaTabBar tabBar;

    // Structure tab widgets
    private NamespaceListWidget namespaceList;
    private StructureListWidget structureList;
    private StructureDetailPanel detailPanel;

    // Loot table tab widgets
    private LootCategoryListWidget categoryList;
    private LootTableListWidget lootTableList;
    private LootTableDetailPanel lootDetailPanel;

    // Current tab
    private Tab currentTab = Tab.STRUCTURES;

    // Track buttons added to screen for proper cleanup
    private final java.util.List<Button> addedDetailButtons = new java.util.ArrayList<>();

    private enum Tab {
        STRUCTURES, LOOT_TABLES, EXPORT
    }

    public MainScreen(@Nullable Screen parent) {
        super(TITLE, parent);
    }

    @Override
    protected void init() {
        super.init();

        // Create window frame
        window = IsotopeWindow.fullscreen(this, WINDOW_MARGIN, TITLE);

        // Tab bar below window title
        int tabBarY = window.getY() + 28;
        tabBar = new VanillaTabBar(window.getContentX(), tabBarY, window.getContentWidth())
            .addTab("Structures")
            .addTab("Loot Tables")
            .addTab("Export")
            .onTabChange(this::onTabChanged);
        tabBar.setSelectedIndex(currentTab.ordinal());
        this.addRenderableWidget(tabBar);

        // Action buttons in title area
        int buttonY = window.getY() + 5;
        int buttonX = window.getX() + window.getWidth() - 70;

        // Close button
        this.addRenderableWidget(
            Button.builder(Component.literal("X"), button -> onClose())
                .pos(buttonX + 50, buttonY)
                .size(16, 16)
                .build()
        );

        // Save button
        this.addRenderableWidget(
            Button.builder(Component.literal("Save"), button -> saveCurrentAnalysis())
                .pos(buttonX, buttonY)
                .size(45, 16)
                .build()
        );

        // Calculate content area (below tabs)
        int contentY = tabBarY + 28;
        int contentHeight = window.getContentY() + window.getContentHeight() - contentY - 20;
        int contentWidth = window.getContentWidth();
        int centerPanelWidth = contentWidth - LEFT_PANEL_WIDTH - RIGHT_PANEL_WIDTH - (PADDING * 2);

        // Initialize 3-panel layout based on current tab
        if (currentTab == Tab.STRUCTURES) {
            initStructuresPanel(contentY, contentHeight, centerPanelWidth);
        } else if (currentTab == Tab.LOOT_TABLES) {
            initLootTablesPanel(contentY, contentHeight, centerPanelWidth);
        }
        // Note: EXPORT tab is handled in onTabChanged to avoid init loop
    }

    private void onTabChanged(int tabIndex) {
        Tab newTab = Tab.values()[tabIndex];
        if (currentTab != newTab) {
            if (newTab == Tab.EXPORT) {
                // Open export screen without changing currentTab
                // This prevents a loop when returning from ExportScreen
                if (minecraft != null) {
                    minecraft.setScreen(new ExportScreen(this));
                }
                // Reset tab bar selection to previous tab
                tabBar.setSelectedIndex(currentTab.ordinal());
            } else {
                currentTab = newTab;
                rebuildWidgets();
            }
        }
    }

    private void initStructuresPanel(int contentY, int contentHeight, int centerPanelWidth) {
        int contentX = window.getContentX();

        // Left panel - Namespace list
        namespaceList = new NamespaceListWidget(
            contentX,
            contentY,
            LEFT_PANEL_WIDTH,
            contentHeight,
            this::onNamespaceSelected
        );
        this.addRenderableWidget(namespaceList);

        // Center panel - Structure list
        structureList = new StructureListWidget(
            contentX + LEFT_PANEL_WIDTH + PADDING,
            contentY,
            centerPanelWidth,
            contentHeight,
            this::onStructureSelected
        );
        this.addRenderableWidget(structureList);

        // Right panel - Details with editing controls
        detailPanel = new StructureDetailPanel(
            contentX + LEFT_PANEL_WIDTH + PADDING + centerPanelWidth + PADDING,
            contentY,
            RIGHT_PANEL_WIDTH,
            contentHeight,
            this::onViewLootTable,
            this::onAddLinkToStructure,
            this::onRemoveLinkFromStructure
        );

        // Load data
        if (ClientDataProvider.getInstance().isDataAvailable()) {
            namespaceList.loadData();
            // Structure list will be populated when namespace is selected
            NamespaceListWidget.NamespaceEntry selected = namespaceList.getSelected();
            if (selected != null) {
                structureList.loadForNamespace(selected.namespace());
            }
        } else {
            Isotope.LOGGER.warn("Registry data not available - UI will show empty lists");
        }
    }

    private void initLootTablesPanel(int contentY, int contentHeight, int centerPanelWidth) {
        int contentX = window.getContentX();

        // Left panel - Category list
        categoryList = new LootCategoryListWidget(
            contentX,
            contentY,
            LEFT_PANEL_WIDTH,
            contentHeight,
            this::onCategorySelected
        );
        this.addRenderableWidget(categoryList);

        // Center panel - Loot table list
        lootTableList = new LootTableListWidget(
            contentX + LEFT_PANEL_WIDTH + PADDING,
            contentY,
            centerPanelWidth,
            contentHeight,
            this::onLootTableSelected
        );
        this.addRenderableWidget(lootTableList);

        // Right panel - Loot table details
        lootDetailPanel = new LootTableDetailPanel(
            contentX + LEFT_PANEL_WIDTH + PADDING + centerPanelWidth + PADDING,
            contentY,
            RIGHT_PANEL_WIDTH,
            contentHeight
        );
        lootDetailPanel.setOnEditClicked(this::onEditLootTable);

        // Load data
        if (ClientDataProvider.getInstance().isDataAvailable()) {
            categoryList.loadData();
            // Load all observed loot tables (category filtering not applicable in observation model)
            lootTableList.loadAll();
        }
    }

    private void onNamespaceSelected(NamespaceListWidget.NamespaceEntry entry) {
        if (structureList != null && entry != null) {
            structureList.loadForNamespace(entry.namespace());
            structureList.clearSelection();
            if (detailPanel != null) {
                detailPanel.setStructure(null);
                refreshDetailButtons();
            }
        }
    }

    private void onStructureSelected(StructureListWidget.StructureEntry entry) {
        if (detailPanel != null) {
            detailPanel.setStructure(entry);
            refreshDetailButtons();
        }
    }

    private void refreshDetailButtons() {
        // Remove previously added buttons from the screen
        for (Button btn : addedDetailButtons) {
            this.removeWidget(btn);
        }
        addedDetailButtons.clear();

        // Add new buttons from the detail panel
        for (Button btn : detailPanel.getButtons()) {
            this.addRenderableWidget(btn);
            addedDetailButtons.add(btn);
        }
    }

    private void onViewLootTable(ResourceLocation tableId) {
        // Switch to loot tables tab and select this table
        Isotope.LOGGER.info("View loot table: {}", tableId);
        currentTab = Tab.LOOT_TABLES;
        rebuildWidgets();
        // TODO: Auto-select the table in the list
    }

    private void onAddLinkToStructure(ResourceLocation structureId) {
        // Open loot table picker
        if (minecraft == null) return;

        LootTablePickerScreen picker = new LootTablePickerScreen(
            this,
            structureId,
            lootTableId -> {
                // Add the link
                StructureLootLinker.getInstance().addManualLink(structureId, lootTableId);
                Isotope.LOGGER.info("Added manual link: {} -> {}", structureId, lootTableId);

                // Refresh the structure list and detail panel
                refreshAfterLinkChange();
            }
        );
        minecraft.setScreen(picker);
    }

    private void onRemoveLinkFromStructure(ResourceLocation structureId, ResourceLocation lootTableId) {
        // Remove the link
        StructureLootLinker.getInstance().removeLink(structureId, lootTableId);
        Isotope.LOGGER.info("Removed link: {} -> {}", structureId, lootTableId);

        // Refresh the structure list and detail panel
        refreshAfterLinkChange();
    }

    private void refreshAfterLinkChange() {
        // Re-select current structure to refresh detail panel
        if (structureList != null) {
            StructureListWidget.StructureEntry selected = structureList.getSelected();
            if (selected != null) {
                // Reload the structure entry with updated links
                structureList.reloadCurrentEntry();
                onStructureSelected(structureList.getSelected());
            }
        }
    }

    private void onCategorySelected(LootCategoryListWidget.CategoryEntry entry) {
        if (lootTableList != null && entry != null) {
            // Filter by category (null means all)
            lootTableList.loadForCategory(entry.category());
            lootTableList.clearSelection();
            if (lootDetailPanel != null) {
                lootDetailPanel.setLootTable(null);
            }
        }
    }

    private void onLootTableSelected(LootTableListWidget.LootTableEntry entry) {
        if (lootDetailPanel != null) {
            lootDetailPanel.setLootTable(entry);
        }
    }

    private void onEditLootTable(ResourceLocation tableId) {
        if (minecraft == null) return;
        Isotope.LOGGER.info("Opening editor for loot table: {}", tableId);
        minecraft.setScreen(new LootTableEditorScreen(tableId, this));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render window frame first (includes dim background)
        if (window != null) {
            window.render(graphics, mouseX, mouseY, partialTick);
        }

        // Render widgets on top
        super.render(graphics, mouseX, mouseY, partialTick);

        // Status bar with counts
        renderStatusBar(graphics);

        // Detail panel (custom rendering, not a widget)
        if (currentTab == Tab.STRUCTURES && detailPanel != null) {
            detailPanel.render(graphics, mouseX, mouseY, partialTick);
        } else if (currentTab == Tab.LOOT_TABLES && lootDetailPanel != null) {
            lootDetailPanel.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderStatusBar(GuiGraphics graphics) {
        if (window == null) return;

        int statusY = window.getY() + window.getHeight() - 15;
        int statusX = window.getContentX();

        if (!ClientDataProvider.getInstance().isDataAvailable()) {
            graphics.drawString(
                this.font,
                "No world loaded - registry data unavailable",
                statusX,
                statusY,
                IsotopeColors.STATUS_WARNING
            );
            return;
        }

        ClientDataProvider provider = ClientDataProvider.getInstance();
        String status = String.format(
            "%d structures | %d loot tables | %d with loot",
            provider.getTotalStructureCount(),
            provider.getTotalLootTableCount(),
            provider.getStructuresWithLootCount()
        );

        graphics.drawString(
            this.font,
            status,
            statusX,
            statusY,
            IsotopeColors.TEXT_MUTED
        );
    }

    private void renderPlaceholder(GuiGraphics graphics, String message) {
        int messageWidth = this.font.width(message);
        graphics.drawString(
            this.font,
            message,
            (this.width - messageWidth) / 2,
            this.height / 2,
            IsotopeColors.TEXT_MUTED
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle clicks for loot detail panel (Edit button)
        if (currentTab == Tab.LOOT_TABLES && lootDetailPanel != null) {
            if (lootDetailPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Handle scrolling for detail panels
        if (currentTab == Tab.LOOT_TABLES && lootDetailPanel != null) {
            if (lootDetailPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F1 - show keyboard shortcuts help
        if (keyCode == GLFW.GLFW_KEY_F1) {
            if (minecraft != null) {
                minecraft.setScreen(new ShortcutsScreen(this));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveCurrentAnalysis() {
        var result = AnalysisSaveManager.getInstance().saveCurrentAnalysis(null);
        if (result.isPresent()) {
            Isotope.LOGGER.info("Analysis saved: {}", result.get().getDisplayName());
            // Could show a toast notification here
        } else {
            Isotope.LOGGER.error("Failed to save analysis");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
