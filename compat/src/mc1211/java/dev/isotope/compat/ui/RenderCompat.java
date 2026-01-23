package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

/**
 * MC 1.21.11+ rendering compatibility.
 *
 * Provides version-agnostic methods for rendering operations that changed between versions.
 * In MC 1.21.11+, GuiGraphics.pose() returns Matrix3x2fStack.
 */
@Environment(EnvType.CLIENT)
public final class RenderCompat {

    private RenderCompat() {}

    /**
     * Push a matrix onto the pose stack.
     * In MC 1.21.11+, uses Matrix3x2fStack.pushMatrix().
     */
    public static void pushMatrix(GuiGraphics graphics) {
        graphics.pose().pushMatrix();
    }

    /**
     * Pop a matrix from the pose stack.
     * In MC 1.21.11+, uses Matrix3x2fStack.popMatrix().
     */
    public static void popMatrix(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }

    /**
     * Translate by x, y.
     * In MC 1.21.11+, uses Matrix3x2fStack.translate(x, y).
     */
    public static void translate(GuiGraphics graphics, float x, float y) {
        graphics.pose().translate(x, y);
    }

    /**
     * Translate by x, y - int version.
     */
    public static void translate(GuiGraphics graphics, int x, int y) {
        graphics.pose().translate((float) x, (float) y);
    }

    /**
     * Scale uniformly in x and y.
     * In MC 1.21.11+, uses Matrix3x2fStack.scale(x, y).
     */
    public static void scale(GuiGraphics graphics, float x, float y) {
        graphics.pose().scale(x, y);
    }
}
