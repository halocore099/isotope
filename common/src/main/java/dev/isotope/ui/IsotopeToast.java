package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom toast notifications for ISOTOPE.
 */
@Environment(EnvType.CLIENT)
public class IsotopeToast implements Toast {

    public enum Type {
        SUCCESS(0xFF2ecc71),  // Green
        ERROR(0xFFe74c3c),    // Red
        INFO(0xFF3498db);     // Blue

        public final int color;
        Type(int color) { this.color = color; }
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
        return this.startTime == 0 ? Visibility.SHOW :
               (System.currentTimeMillis() - this.startTime < 5000L ? Visibility.SHOW : Visibility.HIDE);
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

        // Background
        graphics.fill(0, 0, width(), height(), 0xFF1a1a1a);
        graphics.fill(0, 0, 3, height(), type.color);

        // Border
        graphics.renderOutline(0, 0, width(), height(), 0xFF333333);

        // Icon/indicator
        graphics.fill(8, 8, 14, 14, type.color);

        // Title
        graphics.drawString(font, title, 20, 7, type.color, false);

        // Message
        graphics.drawString(font, message, 20, 18, 0xFFaaaaaa, false);
    }

    @Override
    public int width() {
        return 200;
    }

    @Override
    public int height() {
        return 32;
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
