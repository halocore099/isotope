package dev.isotope.ui.widget;

import dev.isotope.compat.RegistryHelper;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Vanilla-style window frame using the advancements screen texture.
 * Renders 9-slice borders with a title bar.
 */
@Environment(EnvType.CLIENT)
public class IsotopeWindow {

    // Vanilla window texture (from advancements screen)
    private static final ResourceLocation WINDOW_TEXTURE =
        RegistryHelper.withDefaultNamespace("textures/gui/advancements/window.png");

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
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), IsotopeColors.OVERLAY_DARK);

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
        blitTexture(graphics, WINDOW_TEXTURE, x, y, 0, 0, BORDER, BORDER);
        // Top edge (stretched)
        blitTextureScaled(graphics, WINDOW_TEXTURE, x + BORDER, y, innerWidth, BORDER,
            BORDER, 0, TEXTURE_WIDTH - BORDER * 2, BORDER);
        // Top-right corner
        blitTexture(graphics, WINDOW_TEXTURE, x + width - BORDER, y,
            TEXTURE_WIDTH - BORDER, 0, BORDER, BORDER);

        // === Middle (sides + center) ===
        // Left edge (stretched vertically)
        blitTextureScaled(graphics, WINDOW_TEXTURE, x, y + BORDER, BORDER, innerHeight,
            0, BORDER, BORDER, TEXTURE_HEIGHT - BORDER * 2);
        // Center (stretched both ways) - dark background
        graphics.fill(x + BORDER, y + BORDER, x + width - BORDER, y + height - BORDER,
            IsotopeColors.BACKGROUND_DARK);
        // Right edge (stretched vertically)
        blitTextureScaled(graphics, WINDOW_TEXTURE, x + width - BORDER, y + BORDER, BORDER, innerHeight,
            TEXTURE_WIDTH - BORDER, BORDER, BORDER, TEXTURE_HEIGHT - BORDER * 2);

        // === Bottom edge ===
        // Bottom-left corner
        blitTexture(graphics, WINDOW_TEXTURE, x, y + height - BORDER,
            0, TEXTURE_HEIGHT - BORDER, BORDER, BORDER);
        // Bottom edge (stretched)
        blitTextureScaled(graphics, WINDOW_TEXTURE, x + BORDER, y + height - BORDER, innerWidth, BORDER,
            BORDER, TEXTURE_HEIGHT - BORDER, TEXTURE_WIDTH - BORDER * 2, BORDER);
        // Bottom-right corner
        blitTexture(graphics, WINDOW_TEXTURE, x + width - BORDER, y + height - BORDER,
            TEXTURE_WIDTH - BORDER, TEXTURE_HEIGHT - BORDER, BORDER, BORDER);

        // Title bar separator
        graphics.fill(x + BORDER, y + TITLE_HEIGHT + BORDER,
            x + width - BORDER, y + TITLE_HEIGHT + BORDER + 1,
            IsotopeColors.BORDER_INNER);
    }

    // Cache for version detection and method references
    private static Boolean is121Plus = null;
    private static Method blit121Method = null;
    private static Method blit121ScaledMethod = null;
    private static Method blit120Method = null;
    private static Method blit120ScaledMethod = null;
    private static Object guiTexturedFunction = null;

    /**
     * Version-compatible blit for simple textures (no scaling).
     * Works in both 1.20.x and 1.21.x.
     */
    private void blitTexture(GuiGraphics graphics, ResourceLocation texture,
                             int x, int y, int u, int v, int width, int height) {
        try {
            if (is121Plus()) {
                // 1.21.x: blit(Function<ResourceLocation,RenderType>, ResourceLocation, x, y, u, v, width, height, texWidth, texHeight)
                if (blit121Method == null) {
                    blit121Method = GuiGraphics.class.getMethod("blit",
                        Function.class, ResourceLocation.class, int.class, int.class, float.class, float.class,
                        int.class, int.class, int.class, int.class);
                }
                blit121Method.invoke(graphics, getGuiTexturedFunction(), texture, x, y, (float) u, (float) v, width, height, 256, 256);
            } else {
                // 1.20.x: blit(ResourceLocation, x, y, u, v, width, height)
                if (blit120Method == null) {
                    blit120Method = GuiGraphics.class.getMethod("blit",
                        ResourceLocation.class, int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class);
                }
                blit120Method.invoke(graphics, texture, x, y, (float) u, (float) v, width, height, 256, 256);
            }
        } catch (Exception e) {
            // Fallback - shouldn't happen
        }
    }

    /**
     * Version-compatible blit with scaling support.
     * Works in both 1.20.x and 1.21.x.
     */
    private void blitTextureScaled(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height,
                                   int u, int v, int uWidth, int vHeight) {
        try {
            if (is121Plus()) {
                // 1.21.x: blit(Function<ResourceLocation,RenderType>, ResourceLocation, x, y, width, height, u, v, uWidth, vHeight, texWidth, texHeight)
                if (blit121ScaledMethod == null) {
                    blit121ScaledMethod = GuiGraphics.class.getMethod("blit",
                        Function.class, ResourceLocation.class, int.class, int.class, int.class, int.class,
                        float.class, float.class, int.class, int.class, int.class, int.class);
                }
                blit121ScaledMethod.invoke(graphics, getGuiTexturedFunction(), texture, x, y, width, height, (float) u, (float) v, uWidth, vHeight, 256, 256);
            } else {
                // 1.20.x: blit(ResourceLocation, x, y, width, height, u, v, uWidth, vHeight, texWidth, texHeight)
                if (blit120ScaledMethod == null) {
                    blit120ScaledMethod = GuiGraphics.class.getMethod("blit",
                        ResourceLocation.class, int.class, int.class, int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class);
                }
                blit120ScaledMethod.invoke(graphics, texture, x, y, width, height, (float) u, (float) v, uWidth, vHeight, 256, 256);
            }
        } catch (Exception e) {
            // Fallback - shouldn't happen
        }
    }

    /**
     * Get the RenderType::guiTextured method reference as a Function for 1.21+.
     */
    @SuppressWarnings("unchecked")
    private static Object getGuiTexturedFunction() {
        if (guiTexturedFunction == null) {
            try {
                Method guiTexturedMethod = RenderType.class.getMethod("guiTextured", ResourceLocation.class);
                guiTexturedFunction = (Function<ResourceLocation, RenderType>) loc -> {
                    try {
                        return (RenderType) guiTexturedMethod.invoke(null, loc);
                    } catch (Exception e) {
                        return null;
                    }
                };
            } catch (NoSuchMethodException e) {
                // Won't be called on 1.20.x
            }
        }
        return guiTexturedFunction;
    }

    /**
     * Detect if we're running on 1.21+ by checking for RenderType::guiTextured method.
     */
    private static boolean is121Plus() {
        if (is121Plus == null) {
            try {
                // Check if RenderType.guiTextured exists (1.21+ method)
                RenderType.class.getMethod("guiTextured", ResourceLocation.class);
                is121Plus = true;
            } catch (NoSuchMethodException e) {
                is121Plus = false;
            }
        }
        return is121Plus;
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
