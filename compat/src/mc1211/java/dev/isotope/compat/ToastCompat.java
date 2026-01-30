package dev.isotope.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;

/**
 * MC 1.21.11+ toast compatibility.
 *
 * In MC 1.21.11+, uses Minecraft.getToastManager() directly.
 */
@Environment(EnvType.CLIENT)
public final class ToastCompat {

    private ToastCompat() {}

    /**
     * Add a toast notification.
     */
    public static void addToast(Minecraft mc, Toast toast) {
        if (mc != null) {
            mc.getToastManager().addToast(toast);
        }
    }

    /**
     * Show an ISOTOPE toast notification.
     */
    public static void showIsotopeToast(IsotopeToastImpl.Type type, String title, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            addToast(mc, new IsotopeToastImpl(
                type,
                Component.literal(title),
                Component.literal(message)
            ));
        }
    }
}
