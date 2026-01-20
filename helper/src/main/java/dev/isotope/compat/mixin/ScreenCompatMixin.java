package dev.isotope.compat.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Adds 1.21-style method signatures to Screen for 1.20.x compatibility.
 *
 * This allows Isotope's UI code to use the newer 4-parameter signatures.
 * The mixin intercepts 1.20.x method calls and redirects them to 1.21-style signatures.
 */
@Mixin(Screen.class)
public abstract class ScreenCompatMixin {

    /**
     * Flag to prevent infinite recursion when redirecting mouseScrolled calls.
     */
    @Unique
    private boolean isotope$inScrollRedirect = false;

    /**
     * Adds 1.21-style mouseScrolled with separate scrollX and scrollY parameters.
     * This method exists so Isotope screens can override it with @Override.
     *
     * 1.21 signature: mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
     * 1.20 signature: mouseScrolled(double mouseX, double mouseY, double delta)
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Default implementation: call the 3-param method via reflection
        // This avoids compile-time dependency on the exact signature
        try {
            java.lang.reflect.Method method = Screen.class.getMethod("mouseScrolled", double.class, double.class, double.class);
            return (Boolean) method.invoke(this, mouseX, mouseY, scrollY);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Intercepts 1.20.x mouseScrolled(3-param) calls and redirects to the 4-param version.
     * This allows Isotope screens that override mouseScrolled(4-param) to receive the events.
     */
    @Inject(method = "mouseScrolled(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void isotope$redirectMouseScrolled(double mouseX, double mouseY, double delta,
                                                CallbackInfoReturnable<Boolean> cir) {
        // Prevent recursion
        if (isotope$inScrollRedirect) {
            return;
        }

        // Check if this class (or a subclass) has overridden the 4-param version
        try {
            Method fourParamMethod = this.getClass().getMethod("mouseScrolled",
                double.class, double.class, double.class, double.class);

            // Only redirect if a subclass has overridden it (not the mixin-added default)
            if (fourParamMethod.getDeclaringClass() != Screen.class) {
                isotope$inScrollRedirect = true;
                try {
                    // Call the 4-param version with scrollX=0, scrollY=delta
                    boolean result = this.mouseScrolled(mouseX, mouseY, 0.0, delta);
                    cir.setReturnValue(result);
                } finally {
                    isotope$inScrollRedirect = false;
                }
            }
        } catch (NoSuchMethodException e) {
            // No 4-param method found, let the original 3-param proceed
        }
    }

    /**
     * Adds 1.21-style renderBackground with mouse position and partial tick.
     * On 1.20.x, these parameters are ignored and we call the simpler version.
     *
     * 1.21 signature: renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
     * 1.20 signature: renderBackground(GuiGraphics graphics)
     */
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Delegate to 1.20.x 1-param method via reflection
        try {
            java.lang.reflect.Method method = Screen.class.getMethod("renderBackground", GuiGraphics.class);
            method.invoke(this, graphics);
        } catch (Exception e) {
            // Ignore - non-critical
        }
    }
}
