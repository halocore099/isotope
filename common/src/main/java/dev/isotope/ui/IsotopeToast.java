package dev.isotope.ui;

import dev.isotope.compat.MCVersion;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Custom toast notifications for ISOTOPE.
 *
 * VS Code-style notifications with:
 * - Icon indicators (check, X, info, warning)
 * - Colored accent bar
 * - Smooth fade animation
 * - Auto-dismiss after 3 seconds
 *
 * Version-agnostic implementation that works on both 1.20.x and 1.21.x
 * using dynamic proxy to handle Toast interface API differences.
 */
@Environment(EnvType.CLIENT)
public class IsotopeToast {

    private static final int DISPLAY_TIME_MS = 3000;
    private static final int FADE_TIME_MS = 200;

    // Cached reflection
    private static Method addToastMethod = null;
    private static Class<?> toastClass = null;
    private static Class<?> toastVisibilityClass = null;
    private static Object visibilityShow = null;
    private static Object visibilityHide = null;
    private static boolean reflectionInitialized = false;

    public enum Type {
        SUCCESS(IsotopeColors.SYNTAX_CYAN, "[OK]"),
        ERROR(IsotopeColors.ERROR_BRIGHT, "[X]"),
        INFO(IsotopeColors.TOAST_INFO, "[i]"),
        WARNING(IsotopeColors.ACCENT_GOLD, "[!]");

        public final int color;
        public final String icon;
        Type(int color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }

    // === Static helpers (main entry points) ===

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
        if (mc == null) return;

        try {
            initReflection();

            // Create a dynamic toast proxy and add it
            Object toast = createDynamicToast(type, Component.literal(title), Component.literal(message));
            if (toast != null) {
                Object toastManager = getToastManager(mc);
                if (toastManager != null) {
                    addToast(toastManager, toast);
                    return;
                }
            }
        } catch (Exception e) {
            // Fall through to console fallback
        }

        // Fallback: log to console
        System.out.println("[ISOTOPE] " + type.icon + " " + title + ": " + message);
    }

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            toastClass = Class.forName("net.minecraft.client.gui.components.toasts.Toast");
            toastVisibilityClass = Class.forName("net.minecraft.client.gui.components.toasts.Toast$Visibility");

            // Get Visibility enum constants
            for (Object constant : toastVisibilityClass.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                if ("SHOW".equals(name)) {
                    visibilityShow = constant;
                } else if ("HIDE".equals(name)) {
                    visibilityHide = constant;
                }
            }
        } catch (ClassNotFoundException e) {
            // Toast class not found
        }
    }

    /**
     * Create a dynamic proxy that implements the Toast interface.
     * This works regardless of which methods the interface requires.
     */
    private static Object createDynamicToast(Type type, Component title, Component message) {
        if (toastClass == null) return null;

        ToastHandler handler = new ToastHandler(type, title, message);

        return Proxy.newProxyInstance(
            toastClass.getClassLoader(),
            new Class<?>[] { toastClass },
            handler
        );
    }

    /**
     * Get the toast manager/component from Minecraft instance.
     */
    private static Object getToastManager(Minecraft mc) {
        try {
            // Try 1.21+ method first: getToastManager()
            try {
                Method method = Minecraft.class.getMethod("getToastManager");
                return method.invoke(mc);
            } catch (NoSuchMethodException e) {
                // Fall through to 1.20.x
            }

            // Try 1.20.x method: getToasts()
            Method method = Minecraft.class.getMethod("getToasts");
            return method.invoke(mc);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Add a toast to the manager/component.
     */
    private static void addToast(Object toastManager, Object toast) {
        try {
            if (addToastMethod == null) {
                addToastMethod = toastManager.getClass().getMethod("addToast", toastClass);
            }
            addToastMethod.invoke(toastManager, toast);
        } catch (Exception e) {
            // Try without cache
            try {
                for (Method m : toastManager.getClass().getMethods()) {
                    if (m.getName().equals("addToast") && m.getParameterCount() == 1) {
                        m.invoke(toastManager, toast);
                        return;
                    }
                }
            } catch (Exception ex) {
                // Ignore - toast display is not critical
            }
        }
    }

    /**
     * InvocationHandler for the dynamic Toast proxy.
     * Handles all Toast interface methods for both 1.20.x and 1.21.x.
     */
    private static class ToastHandler implements InvocationHandler {
        private final Type type;
        private final Component title;
        private final Component message;
        private long startTime = 0;
        private boolean justUpdated = false;

        ToastHandler(Type type, Component title, Component message) {
            this.type = type;
            this.title = title;
            this.message = message;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            switch (methodName) {
                case "width":
                    return 220;

                case "height":
                    return 34;

                case "getWantedVisibility":
                    return getVisibility();

                case "render":
                    // 1.20.x: render(GuiGraphics, ToastComponent, long) -> Visibility
                    // 1.21.x: render(GuiGraphics, Font, long) -> void
                    return handleRender(args);

                case "update":
                    // 1.21.x: update(ToastManager, long) -> void
                    handleUpdate();
                    return null;

                case "slotCount":
                    // Default: 1 slot
                    return 1;

                case "toString":
                    return "IsotopeToast[" + type + "]";

                case "hashCode":
                    return System.identityHashCode(this);

                case "equals":
                    return proxy == args[0];

                default:
                    // Default implementation for unknown methods
                    if (method.getReturnType() == void.class) {
                        return null;
                    } else if (method.getReturnType() == boolean.class) {
                        return false;
                    } else if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
            }
        }

        private Object getVisibility() {
            if (startTime == 0) return visibilityShow;
            long elapsed = System.currentTimeMillis() - startTime;
            return elapsed < DISPLAY_TIME_MS + FADE_TIME_MS ? visibilityShow : visibilityHide;
        }

        private void handleUpdate() {
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
                justUpdated = true;
            }
        }

        private Object handleRender(Object[] args) {
            if (args == null || args.length < 1) return getVisibility();

            GuiGraphics graphics = (GuiGraphics) args[0];
            Font font = null;

            // Determine the font based on arguments
            if (args.length >= 2) {
                if (args[1] instanceof Font) {
                    // 1.21.x: render(GuiGraphics, Font, long)
                    font = (Font) args[1];
                    if (justUpdated) {
                        startTime = System.currentTimeMillis();
                        justUpdated = false;
                    }
                } else {
                    // 1.20.x: render(GuiGraphics, ToastComponent, long)
                    // Get font from ToastComponent via reflection
                    Object toastComponent = args[1];
                    try {
                        Method getMinecraft = toastComponent.getClass().getMethod("getMinecraft");
                        Minecraft mc = (Minecraft) getMinecraft.invoke(toastComponent);
                        font = mc.font;
                    } catch (Exception e) {
                        font = Minecraft.getInstance().font;
                    }
                }
            }

            if (font == null) {
                font = Minecraft.getInstance().font;
            }

            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            }

            renderContent(graphics, font);

            // 1.20.x render returns Visibility, 1.21.x returns void
            Class<?> returnType = null;
            try {
                // Check if return type is Visibility (1.20.x)
                if (toastVisibilityClass != null && toastVisibilityClass.isAssignableFrom(
                    args.getClass().getMethod("render", GuiGraphics.class, args[1].getClass(), long.class).getReturnType())) {
                    return getVisibility();
                }
            } catch (Exception e) {
                // Ignore
            }

            // For 1.20.x, always return visibility
            return getVisibility();
        }

        private void renderContent(GuiGraphics graphics, Font font) {
            // Calculate fade alpha
            long elapsed = System.currentTimeMillis() - startTime;
            float alpha = 1.0f;
            if (elapsed > DISPLAY_TIME_MS) {
                alpha = 1.0f - (float)(elapsed - DISPLAY_TIME_MS) / FADE_TIME_MS;
                alpha = Math.max(0, Math.min(1, alpha));
            }

            int bgAlpha = (int)(alpha * 0xF0);
            int textAlpha = (int)(alpha * 0xFF);

            // Background with shadow
            graphics.fill(2, 2, 220, 34, IsotopeColors.withAlpha(0x000000, (int)(alpha * 0x40)));
            graphics.fill(0, 0, 218, 32, IsotopeColors.withAlpha(IsotopeColors.TOAST_BACKGROUND, bgAlpha));

            // Colored accent bar (left edge)
            graphics.fill(0, 0, 3, 32, IsotopeColors.withAlpha(type.color, textAlpha));

            // Border
            graphics.renderOutline(0, 0, 218, 32, IsotopeColors.withAlpha(IsotopeColors.TOAST_BORDER, (int)(alpha * 0x50)));

            // Icon
            graphics.drawString(font, type.icon, 8, 7, IsotopeColors.withAlpha(type.color, textAlpha), false);

            // Title
            graphics.drawString(font, title, 28, 7, IsotopeColors.withAlpha(IsotopeColors.TOAST_TITLE, textAlpha), false);

            // Message (dimmer)
            graphics.drawString(font, message, 28, 19, IsotopeColors.withAlpha(IsotopeColors.TOAST_MESSAGE, textAlpha), false);

            // ISOTOPE badge (subtle)
            String badge = "ISOTOPE";
            int badgeWidth = font.width(badge);
            graphics.drawString(font, badge, 220 - badgeWidth - 8, 19, IsotopeColors.withAlpha(0xFFFFFF, (int)(alpha * 0x40)), false);
        }
    }
}
