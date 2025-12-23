package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.IsotopeButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Main ISOTOPE screen with 3-panel layout.
 * Stub implementation for M3 - will be completed in M4.
 */
@Environment(EnvType.CLIENT)
public class MainScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Worldgen Analysis");

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

        // Placeholder buttons for tabs (M4 will implement properly)
        int tabY = 35;
        int tabWidth = 100;
        int tabHeight = 20;
        int tabX = 10;

        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Structures"),
                button -> {} // M4: switch to structures view
            )
            .pos(tabX, tabY)
            .size(tabWidth, tabHeight)
            .style(IsotopeButton.ButtonStyle.PRIMARY)
            .build()
        );

        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Loot Tables"),
                button -> {} // M5: switch to loot tables view
            )
            .pos(tabX + tabWidth + 5, tabY)
            .size(tabWidth, tabHeight)
            .style(IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );

        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Export"),
                button -> {} // M7: open export screen
            )
            .pos(tabX + (tabWidth + 5) * 2, tabY)
            .size(tabWidth, tabHeight)
            .style(IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title bar
        renderTitleBar(graphics);

        // Placeholder content area
        int contentY = 60;
        int contentHeight = this.height - contentY - 10;

        // 3-panel layout placeholder
        int leftPanelWidth = 150;
        int rightPanelWidth = 250;
        int centerPanelWidth = this.width - leftPanelWidth - rightPanelWidth - 30;

        // Left panel (Mod list)
        renderPanel(graphics, 10, contentY, leftPanelWidth, contentHeight);
        graphics.drawString(this.font, "Mods", 15, contentY + 5, IsotopeColors.TEXT_SECONDARY);

        // Center panel (Structure/Loot list)
        renderPanel(graphics, 15 + leftPanelWidth, contentY, centerPanelWidth, contentHeight);
        graphics.drawString(this.font, "Structures", 20 + leftPanelWidth, contentY + 5, IsotopeColors.TEXT_SECONDARY);

        // Right panel (Details)
        renderPanel(graphics, 20 + leftPanelWidth + centerPanelWidth, contentY, rightPanelWidth, contentHeight);
        graphics.drawString(this.font, "Details", 25 + leftPanelWidth + centerPanelWidth, contentY + 5, IsotopeColors.TEXT_SECONDARY);

        // Placeholder message
        String message = "M4 will populate these panels with structure data";
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
