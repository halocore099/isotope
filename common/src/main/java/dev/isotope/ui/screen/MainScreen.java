package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import dev.isotope.ui.widget.IsotopeButton;
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

    // Widgets
    private NamespaceListWidget namespaceList;
    private StructureListWidget structureList;
    private StructureDetailPanel detailPanel;

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

        // Initialize 3-panel layout
        if (currentTab == Tab.STRUCTURES) {
            initStructuresPanel(contentY, contentHeight, centerPanelWidth);
        } else if (currentTab == Tab.LOOT_TABLES) {
            // TODO: M5 - Loot tables tab
        } else if (currentTab == Tab.EXPORT) {
            // TODO: M7 - Export tab
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
        // TODO: M5 - Open LootTableDetailScreen
        Isotope.LOGGER.info("View loot table: {}", tableId);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title bar
        renderTitleBar(graphics);

        // Status bar with counts
        renderStatusBar(graphics);

        // Detail panel (custom rendering, not a widget)
        if (detailPanel != null && currentTab == Tab.STRUCTURES) {
            detailPanel.render(graphics, mouseX, mouseY, partialTick);
        }

        // Tab-specific placeholders
        if (currentTab == Tab.LOOT_TABLES) {
            renderPlaceholder(graphics, "Loot Tables browser - Coming in M5");
        } else if (currentTab == Tab.EXPORT) {
            renderPlaceholder(graphics, "Export functionality - Coming in M7");
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
    public boolean isPauseScreen() {
        return true;
    }
}
