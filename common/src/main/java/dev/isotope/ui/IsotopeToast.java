package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

/**
 * Custom toast notifications for ISOTOPE.
 *
 * VS Code-style notifications with:
 * - Icon indicators (✓, ✕, ℹ)
 * - Colored accent bar
 * - Smooth fade animation
 * - Auto-dismiss after 3 seconds
 */
@Environment(EnvType.CLIENT)
public class IsotopeToast implements Toast {

    private static final int DISPLAY_TIME_MS = 3000;
    private static final int FADE_TIME_MS = 200;

    public enum Type {
        SUCCESS(IsotopeColors.SYNTAX_CYAN, "✓"),  // Teal/green - VS Code success
        ERROR(IsotopeColors.ERROR_BRIGHT, "✕"),    // Red - VS Code error
        INFO(IsotopeColors.TOAST_INFO, "ℹ"),     // Blue - VS Code info
        WARNING(IsotopeColors.ACCENT_GOLD, "⚠");  // Yellow - VS Code warning

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
    private long startTime;
    private boolean justUpdated;

    public IsotopeToast(Type type, Component title, Component message) {
        this.type = type;
        this.title = title;
        this.message = message;
    }

    @Override
    public Visibility getWantedVisibility() {
        if (this.startTime == 0) return Visibility.SHOW;
        long elapsed = System.currentTimeMillis() - this.startTime;
        return elapsed < DISPLAY_TIME_MS + FADE_TIME_MS ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (this.startTime == 0) {
            this.startTime = System.currentTimeMillis();
            this.justUpdated = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, Font font, long time) {
        if (this.justUpdated) {
            this.startTime = System.currentTimeMillis();
            this.justUpdated = false;
        }

        // Calculate fade alpha
        long elapsed = System.currentTimeMillis() - this.startTime;
        float alpha = 1.0f;
        if (elapsed > DISPLAY_TIME_MS) {
            alpha = 1.0f - (float)(elapsed - DISPLAY_TIME_MS) / FADE_TIME_MS;
            alpha = Math.max(0, Math.min(1, alpha));
        }

        int bgAlpha = (int)(alpha * 0xF0);
        int textAlpha = (int)(alpha * 0xFF);

        // Background with shadow
        graphics.fill(2, 2, width(), height(), (int)(alpha * 0x40) << 24); // Shadow
        graphics.fill(0, 0, width() - 2, height() - 2, (bgAlpha << 24) | (IsotopeColors.TOAST_BACKGROUND & 0x00FFFFFF));

        // Colored accent bar (left edge)
        int accentColor = (textAlpha << 24) | (type.color & 0x00FFFFFF);
        graphics.fill(0, 0, 3, height() - 2, accentColor);

        // Border
        int borderColor = (int)(alpha * 0x50) << 24 | (IsotopeColors.TOAST_BORDER & 0x00FFFFFF);
        graphics.renderOutline(0, 0, width() - 2, height() - 2, borderColor);

        // Icon
        int iconColor = (textAlpha << 24) | (type.color & 0x00FFFFFF);
        graphics.drawString(font, type.icon, 8, 7, iconColor, false);

        // Title
        int titleColor = (textAlpha << 24) | (IsotopeColors.TOAST_TITLE & 0x00FFFFFF);
        graphics.drawString(font, title, 22, 7, titleColor, false);

        // Message (dimmer)
        int messageColor = (textAlpha << 24) | (IsotopeColors.TOAST_MESSAGE & 0x00FFFFFF);
        graphics.drawString(font, message, 22, 19, messageColor, false);

        // ISOTOPE badge (subtle)
        String badge = "ISOTOPE";
        int badgeWidth = font.width(badge);
        int badgeColor = (int)(alpha * 0x40) << 24 | 0xFFFFFF;
        graphics.drawString(font, badge, width() - badgeWidth - 8, 19, badgeColor, false);
    }

    @Override
    public int width() {
        return 220;
    }

    @Override
    public int height() {
        return 34;
    }

    // === Static helpers ===

    public static void success(String title, String message) {
        show(Type.SUCCESS, title, message);
    }

    public static void error(String title, String message) {
        show(Type.ERROR, title, message);
    }

    public static void info(String title, String message) {
        show(Type.INFO, title, message);
    }

    public static void warning(String title, String message) {
        show(Type.WARNING, title, message);
    }

    public static void show(Type type, String title, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getToastManager().addToast(new IsotopeToast(
                type,
                Component.literal(title),
                Component.literal(message)
            ));
        }
    }
}
