package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-style window frame using solid fills and borders.
 * Compatible with MC 1.21.6+ rendering changes.
 */
@Environment(EnvType.CLIENT)
public class IsotopeWindow {

    private static final int BORDER = 4;
    private static final int TITLE_HEIGHT = 18;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Component title;

    public IsotopeWindow(int x, int y, int width, int height, Component title) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.title = title;
    }

    /**
     * Create a window centered on screen.
     */
    public static IsotopeWindow centered(Screen screen, int width, int height, Component title) {
        int x = (screen.width - width) / 2;
        int y = (screen.height - height) / 2;
        return new IsotopeWindow(x, y, width, height, title);
    }

    /**
     * Create a window that fills most of the screen with margins.
     */
    public static IsotopeWindow fullscreen(Screen screen, int margin, Component title) {
        return new IsotopeWindow(margin, margin, screen.width - margin * 2, screen.height - margin * 2, title);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background behind window
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), IsotopeColors.OVERLAY_DARK);

        // Render window frame using fills (compatible with MC 1.21.6+)
        renderFrame(graphics);

        // Render title
        renderTitle(graphics);
    }

    private void renderFrame(GuiGraphics graphics) {
        // Outer border (dark)
        graphics.fill(x, y, x + width, y + height, IsotopeColors.BORDER_OUTER_DARK);

        // Inner fill (main background)
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, IsotopeColors.BACKGROUND_MEDIUM);

        // Title bar background (slightly darker)
        graphics.fill(x + 2, y + 2, x + width - 2, y + TITLE_HEIGHT + BORDER, IsotopeColors.BACKGROUND_DARK);

        // Title bar separator
        graphics.fill(x + 2, y + TITLE_HEIGHT + BORDER,
            x + width - 2, y + TITLE_HEIGHT + BORDER + 1,
            IsotopeColors.BORDER_INNER);

        // Highlight border (top and left)
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, IsotopeColors.BORDER_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, IsotopeColors.BORDER_HIGHLIGHT);

        // Shadow border (bottom and right)
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, IsotopeColors.BORDER_OUTER_DARK);
    }

    private void renderTitle(GuiGraphics graphics) {
        // Render title centered in title bar area
        int titleX = x + width / 2;
        int titleY = y + BORDER + 4;
        graphics.drawCenteredString(
            net.minecraft.client.Minecraft.getInstance().font,
            title,
            titleX,
            titleY,
            IsotopeColors.ACCENT_GOLD
        );
    }

    /**
     * Get the content area bounds (inside the frame, below title bar).
     */
    public int getContentX() {
        return x + BORDER + 2;
    }

    public int getContentY() {
        return y + BORDER + TITLE_HEIGHT + 4;
    }

    public int getContentWidth() {
        return width - BORDER * 2 - 4;
    }

    public int getContentHeight() {
        return height - BORDER * 2 - TITLE_HEIGHT - 6;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
