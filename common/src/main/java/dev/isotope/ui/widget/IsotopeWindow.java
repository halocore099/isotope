package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Vanilla-style window frame using the advancements screen texture.
 * Renders 9-slice borders with a title bar.
 */
@Environment(EnvType.CLIENT)
public class IsotopeWindow {

    // Vanilla window texture (from advancements screen)
    private static final ResourceLocation WINDOW_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");

    // The advancements window.png is 252x140, with 9-pixel borders
    private static final int TEXTURE_WIDTH = 252;
    private static final int TEXTURE_HEIGHT = 140;
    private static final int BORDER = 9;
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
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x80000000);

        // Render window frame using vanilla texture
        renderVanillaFrame(graphics);

        // Render title
        renderTitle(graphics);
    }

    private void renderVanillaFrame(GuiGraphics graphics) {
        // Use 9-slice rendering from the advancements window texture
        // The texture has corners, edges, and a center that tile/stretch

        int innerWidth = width - BORDER * 2;
        int innerHeight = height - BORDER * 2;

        // === Top edge ===
        // Top-left corner
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x, y, 0, 0, BORDER, BORDER, 256, 256);
        // Top edge (stretched)
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x + BORDER, y, innerWidth, BORDER,
            BORDER, 0, TEXTURE_WIDTH - BORDER * 2, BORDER, 256, 256);
        // Top-right corner
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x + width - BORDER, y,
            TEXTURE_WIDTH - BORDER, 0, BORDER, BORDER, 256, 256);

        // === Middle (sides + center) ===
        // Left edge (stretched vertically)
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x, y + BORDER, BORDER, innerHeight,
            0, BORDER, BORDER, TEXTURE_HEIGHT - BORDER * 2, 256, 256);
        // Center (stretched both ways) - dark background
        graphics.fill(x + BORDER, y + BORDER, x + width - BORDER, y + height - BORDER,
            IsotopeColors.BACKGROUND_DARK);
        // Right edge (stretched vertically)
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x + width - BORDER, y + BORDER, BORDER, innerHeight,
            TEXTURE_WIDTH - BORDER, BORDER, BORDER, TEXTURE_HEIGHT - BORDER * 2, 256, 256);

        // === Bottom edge ===
        // Bottom-left corner
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x, y + height - BORDER,
            0, TEXTURE_HEIGHT - BORDER, BORDER, BORDER, 256, 256);
        // Bottom edge (stretched)
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x + BORDER, y + height - BORDER, innerWidth, BORDER,
            BORDER, TEXTURE_HEIGHT - BORDER, TEXTURE_WIDTH - BORDER * 2, BORDER, 256, 256);
        // Bottom-right corner
        graphics.blit(RenderType::guiTextured, WINDOW_TEXTURE, x + width - BORDER, y + height - BORDER,
            TEXTURE_WIDTH - BORDER, TEXTURE_HEIGHT - BORDER, BORDER, BORDER, 256, 256);

        // Title bar separator
        graphics.fill(x + BORDER, y + TITLE_HEIGHT + BORDER,
            x + width - BORDER, y + TITLE_HEIGHT + BORDER + 1,
            IsotopeColors.BORDER_INNER);
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
