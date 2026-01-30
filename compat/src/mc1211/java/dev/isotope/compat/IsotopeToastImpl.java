package dev.isotope.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

/**
 * MC 1.21.11+ Toast implementation.
 *
 * Implements Toast interface directly for 1.21.11+ where the API is stable.
 */
@Environment(EnvType.CLIENT)
public class IsotopeToastImpl implements Toast {

    public static final int DISPLAY_TIME_MS = 3000;
    public static final int FADE_TIME_MS = 200;
    public static final int WIDTH = 220;
    public static final int HEIGHT = 34;

    // Colors (matching IsotopeColors)
    private static final int TOAST_BACKGROUND = 0xFF1E1E1E;
    private static final int TOAST_BORDER = 0xFF333333;
    private static final int TOAST_TITLE = 0xFFFFFFFF;
    private static final int TOAST_MESSAGE = 0xFFCCCCCC;
    private static final int SYNTAX_CYAN = 0xFF4EC9B0;
    private static final int ERROR_BRIGHT = 0xFFF14C4C;
    private static final int TOAST_INFO = 0xFF3794FF;
    private static final int ACCENT_GOLD = 0xFFDCDCAA;

    public enum Type {
        SUCCESS(SYNTAX_CYAN, "✓"),
        ERROR(ERROR_BRIGHT, "✕"),
        INFO(TOAST_INFO, "ℹ"),
        WARNING(ACCENT_GOLD, "⚠");

        public final int color;
        public final String icon;
        Type(int color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }

    private final Type type;
    private final Component title;
    private final Component message;
    private long startTime = -1;

    public IsotopeToastImpl(Type type, Component title, Component message) {
        this.type = type;
        this.title = title;
        this.message = message;
    }

    @Override
    public Visibility getWantedVisibility() {
        if (this.startTime < 0) {
            this.startTime = System.currentTimeMillis();
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < DISPLAY_TIME_MS + FADE_TIME_MS ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void update(ToastManager toastManager, long time) {
        if (this.startTime < 0) {
            this.startTime = System.currentTimeMillis();
        }
    }

    @Override
    public void render(GuiGraphics graphics, Font font, long time) {
        if (this.startTime < 0) {
            this.startTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        float alpha = 1.0f;
        if (elapsed > DISPLAY_TIME_MS) {
            alpha = 1.0f - (float)(elapsed - DISPLAY_TIME_MS) / FADE_TIME_MS;
            alpha = Math.max(0, Math.min(1, alpha));
        }

        int bgAlpha = (int)(alpha * 0xF0);
        int textAlpha = (int)(alpha * 0xFF);

        // Background with shadow
        graphics.fill(2, 2, WIDTH, HEIGHT, withAlpha(0x000000, (int)(alpha * 0x40)));
        graphics.fill(0, 0, WIDTH - 2, HEIGHT - 2, withAlpha(TOAST_BACKGROUND, bgAlpha));

        // Colored accent bar (left edge)
        graphics.fill(0, 0, 3, HEIGHT - 2, withAlpha(type.color, textAlpha));

        // Border
        renderOutline(graphics, 0, 0, WIDTH - 2, HEIGHT - 2, withAlpha(TOAST_BORDER, (int)(alpha * 0x50)));

        // Icon
        graphics.drawString(font, type.icon, 8, 7, withAlpha(type.color, textAlpha), false);

        // Title
        graphics.drawString(font, title, 22, 7, withAlpha(TOAST_TITLE, textAlpha), false);

        // Message (dimmer)
        graphics.drawString(font, message, 22, 19, withAlpha(TOAST_MESSAGE, textAlpha), false);

        // ISOTOPE badge (subtle)
        String badge = "ISOTOPE";
        int badgeWidth = font.width(badge);
        graphics.drawString(font, badge, WIDTH - badgeWidth - 8, 19, withAlpha(0xFFFFFF, (int)(alpha * 0x40)), false);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    public Type getType() {
        return type;
    }

    public Component getTitle() {
        return title;
    }

    public Component getMessage() {
        return message;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static void renderOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
