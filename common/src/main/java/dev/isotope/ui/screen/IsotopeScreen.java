package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base screen class for all ISOTOPE screens.
 * Provides common styling and navigation support.
 */
@Environment(EnvType.CLIENT)
public abstract class IsotopeScreen extends Screen {

    @Nullable
    protected final Screen parent;

    protected IsotopeScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected IsotopeScreen(Component title) {
        this(title, null);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderIsotopeBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders the standard ISOTOPE dark background.
     */
    protected void renderIsotopeBackground(GuiGraphics graphics) {
        // Fill with dark background
        graphics.fill(0, 0, this.width, this.height, IsotopeColors.BACKGROUND_DARK);
    }

    /**
     * Renders a panel with border.
     */
    protected void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Panel background
        graphics.fill(x, y, x + width, y + height, IsotopeColors.BACKGROUND_PANEL);
        // Border
        renderBorder(graphics, x, y, width, height, IsotopeColors.BORDER_DEFAULT);
    }

    /**
     * Renders a border around a rectangle.
     */
    protected void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        // Top
        graphics.fill(x, y, x + width, y + 1, color);
        // Bottom
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        // Left
        graphics.fill(x, y, x + 1, y + height, color);
        // Right
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /**
     * Renders centered text.
     */
    protected void renderCenteredText(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawCenteredString(this.font, text, centerX, y, color);
    }

    /**
     * Renders left-aligned text.
     */
    protected void renderText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color);
    }

    /**
     * Renders the ISOTOPE title bar.
     */
    protected void renderTitleBar(GuiGraphics graphics) {
        int titleBarHeight = 30;

        // Title bar background
        graphics.fill(0, 0, this.width, titleBarHeight, IsotopeColors.BACKGROUND_MEDIUM);

        // Title text
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, IsotopeColors.TEXT_PRIMARY);

        // Bottom border
        graphics.fill(0, titleBarHeight - 1, this.width, titleBarHeight, IsotopeColors.BORDER_DEFAULT);
    }

    /**
     * Returns true if this screen should pause the game.
     */
    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
