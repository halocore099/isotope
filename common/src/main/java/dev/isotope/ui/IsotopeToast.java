package dev.isotope.ui;

import dev.isotope.compat.IsotopeToastImpl;
import dev.isotope.compat.ToastCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Static helper for showing ISOTOPE toast notifications.
 *
 * VS Code-style notifications with:
 * - Icon indicators (✓, ✕, ℹ)
 * - Colored accent bar
 * - Smooth fade animation
 * - Auto-dismiss after 3 seconds
 *
 * Uses ToastCompat to handle version differences in the Toast API.
 */
@Environment(EnvType.CLIENT)
public final class IsotopeToast {

    /**
     * Toast type alias for convenience.
     */
    public static final class Type {
        public static final IsotopeToastImpl.Type SUCCESS = IsotopeToastImpl.Type.SUCCESS;
        public static final IsotopeToastImpl.Type ERROR = IsotopeToastImpl.Type.ERROR;
        public static final IsotopeToastImpl.Type INFO = IsotopeToastImpl.Type.INFO;
        public static final IsotopeToastImpl.Type WARNING = IsotopeToastImpl.Type.WARNING;
    }

    private IsotopeToast() {}

    public static void success(String title, String message) {
        ToastCompat.showIsotopeToast(IsotopeToastImpl.Type.SUCCESS, title, message);
    }

    public static void error(String title, String message) {
        ToastCompat.showIsotopeToast(IsotopeToastImpl.Type.ERROR, title, message);
    }

    public static void info(String title, String message) {
        ToastCompat.showIsotopeToast(IsotopeToastImpl.Type.INFO, title, message);
    }

    public static void warning(String title, String message) {
        ToastCompat.showIsotopeToast(IsotopeToastImpl.Type.WARNING, title, message);
    }

    public static void show(IsotopeToastImpl.Type type, String title, String message) {
        ToastCompat.showIsotopeToast(type, title, message);
    }
}
