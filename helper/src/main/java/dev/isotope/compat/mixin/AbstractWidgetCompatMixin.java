package dev.isotope.compat.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Adds 1.21-style position/size accessor methods to AbstractWidget for 1.20.x compatibility.
 *
 * In 1.20.x, AbstractWidget only has protected fields (x, y, width, height).
 * In 1.21.x, AbstractWidget has getX(), getY(), setX(), setY(), getRight(), getBottom() methods.
 *
 * This mixin adds the 1.21-style methods so Isotope's UI code works unchanged.
 */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetCompatMixin {

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    /**
     * Flag to prevent infinite recursion when redirecting mouseScrolled calls.
     */
    @Unique
    private boolean isotope$inScrollRedirect = false;

    /**
     * Gets the X position of this widget.
     * 1.21 method, delegating to field in 1.20.x.
     */
    public int getX() {
        return this.x;
    }

    /**
     * Gets the Y position of this widget.
     * 1.21 method, delegating to field in 1.20.x.
     */
    public int getY() {
        return this.y;
    }

    /**
     * Sets the X position of this widget.
     * 1.21 method, delegating to field in 1.20.x.
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Sets the Y position of this widget.
     * 1.21 method, delegating to field in 1.20.x.
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * Gets the width of this widget.
     * This might already exist in 1.20.x, but adding for consistency.
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Gets the height of this widget.
     * This might already exist in 1.20.x, but adding for consistency.
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Gets the right edge X coordinate (x + width).
     * 1.21 method.
     */
    public int getRight() {
        return this.x + this.width;
    }

    /**
     * Gets the bottom edge Y coordinate (y + height).
     * 1.21 method.
     */
    public int getBottom() {
        return this.y + this.height;
    }

    /**
     * Adds 1.21-style mouseScrolled with separate scrollX and scrollY parameters.
     * This method exists so Isotope widgets can override it with @Override.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Default implementation: call 3-param method via reflection
        try {
            java.lang.reflect.Method method = AbstractWidget.class.getMethod("mouseScrolled", double.class, double.class, double.class);
            return (Boolean) method.invoke(this, mouseX, mouseY, scrollY);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Intercepts 1.20.x mouseScrolled(3-param) calls and redirects to the 4-param version.
     */
    @Inject(method = "mouseScrolled(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void isotope$redirectMouseScrolled(double mouseX, double mouseY, double delta,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (isotope$inScrollRedirect) {
            return;
        }

        try {
            Method fourParamMethod = this.getClass().getMethod("mouseScrolled",
                double.class, double.class, double.class, double.class);

            if (fourParamMethod.getDeclaringClass() != AbstractWidget.class) {
                isotope$inScrollRedirect = true;
                try {
                    boolean result = this.mouseScrolled(mouseX, mouseY, 0.0, delta);
                    cir.setReturnValue(result);
                } finally {
                    isotope$inScrollRedirect = false;
                }
            }
        } catch (NoSuchMethodException e) {
            // No 4-param method found, let the original proceed
        }
    }
}
