package dev.isotope.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * MC 1.21.0-1.21.10 toast compatibility.
 *
 * Uses reflection to handle varying toast API across versions.
 * Creates a dynamic proxy for the Toast interface to handle
 * different method signatures.
 */
@Environment(EnvType.CLIENT)
public final class ToastCompat {

    private static final Method GET_TOASTS_METHOD;
    private static final Method ADD_TOAST_METHOD;

    static {
        Method getToastsMethod = null;
        Method addToastMethod = null;

        try {
            // Try getToastManager() first (MC 1.21.4+)
            getToastsMethod = Minecraft.class.getMethod("getToastManager");
        } catch (NoSuchMethodException e) {
            try {
                // Fall back to getToasts() (MC 1.21.1-1.21.3)
                getToastsMethod = Minecraft.class.getMethod("getToasts");
            } catch (NoSuchMethodException e2) {
                // Should not happen
            }
        }

        if (getToastsMethod != null) {
            try {
                Class<?> toastContainerClass = getToastsMethod.getReturnType();
                addToastMethod = toastContainerClass.getMethod("addToast", Toast.class);
            } catch (NoSuchMethodException e) {
                // Should not happen
            }
        }

        GET_TOASTS_METHOD = getToastsMethod;
        ADD_TOAST_METHOD = addToastMethod;
    }

    private ToastCompat() {}

    /**
     * Add a toast notification.
     * Works across MC versions with different toast APIs.
     */
    public static void addToast(Minecraft mc, Toast toast) {
        if (mc == null || GET_TOASTS_METHOD == null || ADD_TOAST_METHOD == null) {
            return;
        }

        try {
            Object toastContainer = GET_TOASTS_METHOD.invoke(mc);
            if (toastContainer != null) {
                ADD_TOAST_METHOD.invoke(toastContainer, toast);
            }
        } catch (Exception e) {
            // Silently fail - toast notifications are not critical
        }
    }

    /**
     * Show an ISOTOPE toast notification.
     * Creates a dynamic proxy to handle Toast interface variations.
     */
    public static void showIsotopeToast(IsotopeToastImpl.Type type, String title, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || GET_TOASTS_METHOD == null || ADD_TOAST_METHOD == null) {
            return;
        }

        try {
            // Create the toast data holder
            IsotopeToastImpl data = new IsotopeToastImpl(type,
                Component.literal(title), Component.literal(message));

            // Create a dynamic proxy that implements Toast
            Toast proxyToast = (Toast) Proxy.newProxyInstance(
                Toast.class.getClassLoader(),
                new Class<?>[] { Toast.class },
                new ToastInvocationHandler(data)
            );

            // Add the toast
            Object toastContainer = GET_TOASTS_METHOD.invoke(mc);
            if (toastContainer != null) {
                ADD_TOAST_METHOD.invoke(toastContainer, proxyToast);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    /**
     * Invocation handler that delegates to IsotopeToastImpl.
     * Handles different Toast interface method signatures.
     */
    private static class ToastInvocationHandler implements InvocationHandler {
        private final IsotopeToastImpl data;

        ToastInvocationHandler(IsotopeToastImpl data) {
            this.data = data;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            switch (methodName) {
                case "width":
                    return IsotopeToastImpl.WIDTH;

                case "height":
                    return IsotopeToastImpl.HEIGHT;

                case "getWantedVisibility":
                    return data.getWantedVisibility();

                case "render":
                    // Handle different render signatures
                    if (args.length >= 2) {
                        GuiGraphics graphics = (GuiGraphics) args[0];
                        Object fontOrToastComponent = args[1];
                        return data.renderProxy(graphics, fontOrToastComponent);
                    }
                    return Toast.Visibility.HIDE;

                case "update":
                    // Some versions have update method
                    data.update();
                    return null;

                case "equals":
                    return proxy == args[0];

                case "hashCode":
                    return System.identityHashCode(proxy);

                case "toString":
                    return "IsotopeToast[" + data.getType() + "]";

                default:
                    // For any other methods, return default values
                    return getDefaultReturnValue(method.getReturnType());
            }
        }

        private Object getDefaultReturnValue(Class<?> returnType) {
            if (returnType == void.class) return null;
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0.0f;
            if (returnType == double.class) return 0.0;
            return null;
        }
    }
}
