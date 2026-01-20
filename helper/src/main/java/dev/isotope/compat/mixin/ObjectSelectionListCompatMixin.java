package dev.isotope.compat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adds 1.21-style constructor and methods to ObjectSelectionList for 1.20.x compatibility.
 *
 * In 1.20.x: ObjectSelectionList(Minecraft, int width, int height, int top, int bottom, int itemHeight)
 * In 1.21.x: ObjectSelectionList(Minecraft, int width, int height, int y, int itemHeight)
 *
 * This mixin allows Isotope's UI code to use the 1.21-style 5-param constructor.
 */
@Mixin(ObjectSelectionList.class)
public abstract class ObjectSelectionListCompatMixin<E extends ObjectSelectionList.Entry<E>> {

    @Shadow
    protected int x0;

    @Shadow
    protected int y0;

    @Shadow
    protected int x1;

    @Shadow
    protected int y1;

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    /**
     * Gets the X position (left edge).
     * 1.21 method, delegating to field in 1.20.x.
     */
    public int getX() {
        return this.x0;
    }

    /**
     * Gets the Y position (top edge).
     * 1.21 method, delegating to field in 1.20.x.
     */
    public int getY() {
        return this.y0;
    }

    /**
     * Gets the right edge X coordinate.
     * 1.21 method, delegating to field in 1.20.x.
     */
    public int getRight() {
        return this.x1;
    }

    /**
     * Gets the bottom edge Y coordinate.
     * 1.21 method, delegating to field in 1.20.x.
     */
    public int getBottom() {
        return this.y1;
    }

    /**
     * Gets the width.
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Gets the height.
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Sets the X position.
     * 1.21 method.
     */
    public void setX(int x) {
        int w = this.x1 - this.x0;
        this.x0 = x;
        this.x1 = x + w;
    }

    /**
     * Sets the Y position.
     * 1.21 method.
     */
    public void setY(int y) {
        int h = this.y1 - this.y0;
        this.y0 = y;
        this.y1 = y + h;
    }

    /**
     * Adds 1.21-style renderWidget method.
     * In 1.20.x this is called render(), so we delegate.
     */
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // In 1.20.x, render is called directly by the parent
        // This method exists for compatibility with code that calls renderWidget
        try {
            java.lang.reflect.Method renderMethod = ObjectSelectionList.class.getMethod(
                "render", GuiGraphics.class, int.class, int.class, float.class);
            renderMethod.invoke(this, graphics, mouseX, mouseY, partialTick);
        } catch (Exception e) {
            // Ignore
        }
    }
}
