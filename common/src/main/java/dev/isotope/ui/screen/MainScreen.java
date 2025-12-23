package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import dev.isotope.ui.widget.IsotopeButton;
import dev.isotope.ui.widget.LootCategoryListWidget;
import dev.isotope.ui.widget.LootTableDetailPanel;
import dev.isotope.ui.widget.LootTableListWidget;
import dev.isotope.ui.widget.NamespaceListWidget;
import dev.isotope.ui.widget.StructureDetailPanel;
import dev.isotope.ui.widget.StructureListWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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
    private static final int HEADER_HEIGHT = 55;
    private static final int PADDING = 5;

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

    private enum Tab {
        STRUCTURES, LOOT_TABLES, EXPORT
    }

    public MainScreen(@Nullable Screen parent) {
        super(TITLE, parent);
    }

    @Override
    protected void init() {
        super.init();

        // Close button in top-right
        int closeButtonSize = 20;
        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("X"),
                button -> onClose()
            )
            .pos(this.width - closeButtonSize - 5, 5)
            .size(closeButtonSize, closeButtonSize)
            .style(IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );

        // Tab buttons
        int tabY = 30;
        int tabWidth = 100;
        int tabHeight = 20;
        int tabX = 10;

        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Structures"),
                button -> switchTab(Tab.STRUCTURES)
            )
            .pos(tabX, tabY)
            .size(tabWidth, tabHeight)
            .style(currentTab == Tab.STRUCTURES ? IsotopeButton.ButtonStyle.PRIMARY : IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );

        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Loot Tables"),
                button -> switchTab(Tab.LOOT_TABLES)
            )
            .pos(tabX + tabWidth + 5, tabY)
            .size(tabWidth, tabHeight)
            .style(currentTab == Tab.LOOT_TABLES ? IsotopeButton.ButtonStyle.PRIMARY : IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );

        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Export"),
                button -> switchTab(Tab.EXPORT)
            )
            .pos(tabX + (tabWidth + 5) * 2, tabY)
            .size(tabWidth, tabHeight)
            .style(currentTab == Tab.EXPORT ? IsotopeButton.ButtonStyle.PRIMARY : IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );

        // Calculate panel dimensions
        int contentY = HEADER_HEIGHT;
        int contentHeight = this.height - HEADER_HEIGHT - PADDING;
        int centerPanelWidth = this.width - LEFT_PANEL_WIDTH - RIGHT_PANEL_WIDTH - (PADDING * 4);

        // Initialize 3-panel layout based on current tab
        if (currentTab == Tab.STRUCTURES) {
            initStructuresPanel(contentY, contentHeight, centerPanelWidth);
        } else if (currentTab == Tab.LOOT_TABLES) {
            initLootTablesPanel(contentY, contentHeight, centerPanelWidth);
        } else if (currentTab == Tab.EXPORT) {
            // Open export screen
            if (minecraft != null) {
                minecraft.setScreen(new ExportScreen(this));
            }
        }
    }

    private void initStructuresPanel(int contentY, int contentHeight, int centerPanelWidth) {
        // Left panel - Namespace list
        namespaceList = new NamespaceListWidget(
            PADDING,
            contentY,
            LEFT_PANEL_WIDTH,
            contentHeight,
            this::onNamespaceSelected
        );
        this.addRenderableWidget(namespaceList);

        // Center panel - Structure list
        structureList = new StructureListWidget(
            PADDING + LEFT_PANEL_WIDTH + PADDING,
            contentY,
            centerPanelWidth,
            contentHeight,
            this::onStructureSelected
        );
        this.addRenderableWidget(structureList);

        // Right panel - Details (not a widget, custom rendering)
        detailPanel = new StructureDetailPanel(
            PADDING + LEFT_PANEL_WIDTH + PADDING + centerPanelWidth + PADDING,
            contentY,
            RIGHT_PANEL_WIDTH,
            contentHeight,
            this::onViewLootTable
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
        // Left panel - Category list
        categoryList = new LootCategoryListWidget(
            PADDING,
            contentY,
            LEFT_PANEL_WIDTH,
            contentHeight,
            this::onCategorySelected
        );
        this.addRenderableWidget(categoryList);

        // Center panel - Loot table list
        lootTableList = new LootTableListWidget(
            PADDING + LEFT_PANEL_WIDTH + PADDING,
            contentY,
            centerPanelWidth,
            contentHeight,
            this::onLootTableSelected
        );
        this.addRenderableWidget(lootTableList);

        // Right panel - Loot table details
        lootDetailPanel = new LootTableDetailPanel(
            PADDING + LEFT_PANEL_WIDTH + PADDING + centerPanelWidth + PADDING,
            contentY,
            RIGHT_PANEL_WIDTH,
            contentHeight
        );

        // Load data
        if (ClientDataProvider.getInstance().isDataAvailable()) {
            categoryList.loadData();
            LootCategoryListWidget.CategoryEntry selected = categoryList.getSelected();
            if (selected != null) {
                lootTableList.loadForCategory(selected.category());
            }
        }
    }

    private void switchTab(Tab tab) {
        if (currentTab != tab) {
            currentTab = tab;
            rebuildWidgets();
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
        // Remove old detail buttons
        for (Button btn : detailPanel.getButtons()) {
            this.removeWidget(btn);
        }
        // Add new ones
        for (Button btn : detailPanel.getButtons()) {
            this.addRenderableWidget(btn);
        }
    }

    private void onViewLootTable(ResourceLocation tableId) {
        // Switch to loot tables tab and select this table
        Isotope.LOGGER.info("View loot table: {}", tableId);
        currentTab = Tab.LOOT_TABLES;
        rebuildWidgets();
        // TODO: Auto-select the table in the list
    }

    private void onCategorySelected(LootCategoryListWidget.CategoryEntry entry) {
        if (lootTableList != null && entry != null) {
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title bar
        renderTitleBar(graphics);

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
        if (!ClientDataProvider.getInstance().isDataAvailable()) {
            graphics.drawString(
                this.font,
                "No world loaded - registry data unavailable",
                this.width / 2 - 100,
                this.height - 15,
                IsotopeColors.STATUS_WARNING
            );
            return;
        }

        ClientDataProvider provider = ClientDataProvider.getInstance();
        String status = String.format(
            "%d structures | %d loot tables | %d linked",
            provider.getTotalStructureCount(),
            provider.getTotalLootTableCount(),
            provider.getLinkedStructureCount()
        );

        graphics.drawString(
            this.font,
            status,
            PADDING,
            this.height - 15,
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
    public boolean isPauseScreen() {
        return true;
    }
}
