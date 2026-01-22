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

        // Background with shadow (using withAlpha for consistency)
        graphics.fill(2, 2, width(), height(), IsotopeColors.withAlpha(0x000000, (int)(alpha * 0x40)));
        graphics.fill(0, 0, width() - 2, height() - 2, IsotopeColors.withAlpha(IsotopeColors.TOAST_BACKGROUND, bgAlpha));

        // Colored accent bar (left edge)
        graphics.fill(0, 0, 3, height() - 2, IsotopeColors.withAlpha(type.color, textAlpha));

        // Border
        graphics.renderOutline(0, 0, width() - 2, height() - 2, IsotopeColors.withAlpha(IsotopeColors.TOAST_BORDER, (int)(alpha * 0x50)));

        // Icon
        graphics.drawString(font, type.icon, 8, 7, IsotopeColors.withAlpha(type.color, textAlpha), false);

        // Title
        graphics.drawString(font, title, 22, 7, IsotopeColors.withAlpha(IsotopeColors.TOAST_TITLE, textAlpha), false);

        // Message (dimmer)
        graphics.drawString(font, message, 22, 19, IsotopeColors.withAlpha(IsotopeColors.TOAST_MESSAGE, textAlpha), false);

        // ISOTOPE badge (subtle)
        String badge = "ISOTOPE";
        int badgeWidth = font.width(badge);
        graphics.drawString(font, badge, width() - badgeWidth - 8, 19, IsotopeColors.withAlpha(0xFFFFFF, (int)(alpha * 0x40)), false);
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
