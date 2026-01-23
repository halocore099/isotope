package dev.isotope.compat.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

/**
 * MC 1.21.0-1.21.10 rendering compatibility.
 *
 * Provides version-agnostic methods for rendering operations that changed between versions.
 * In MC 1.21.10 and earlier, GuiGraphics.pose() returns PoseStack.
 */
@Environment(EnvType.CLIENT)
public final class RenderCompat {

    private RenderCompat() {}

    /**
     * Push a matrix onto the pose stack.
     * In MC 1.21.10 and earlier, uses PoseStack.pushPose().
     */
    public static void pushMatrix(GuiGraphics graphics) {
        graphics.pose().pushPose();
    }

    /**
     * Pop a matrix from the pose stack.
     * In MC 1.21.10 and earlier, uses PoseStack.popPose().
     */
    public static void popMatrix(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    /**
     * Translate by x, y (with z=0).
     * In MC 1.21.10 and earlier, uses PoseStack.translate(x, y, z).
     */
    public static void translate(GuiGraphics graphics, float x, float y) {
        graphics.pose().translate(x, y, 0);
    }

    /**
     * Translate by x, y (with z=0) - int version.
     */
    public static void translate(GuiGraphics graphics, int x, int y) {
        graphics.pose().translate((float) x, (float) y, 0);
    }

    /**
     * Scale uniformly in x and y (z=1).
     */
    public static void scale(GuiGraphics graphics, float x, float y) {
        graphics.pose().scale(x, y, 1);
    }

    // Legacy methods that take PoseStack directly (for backwards compatibility)

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void pushMatrix(PoseStack poseStack) {
        poseStack.pushPose();
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void popMatrix(PoseStack poseStack) {
        poseStack.popPose();
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void translate(PoseStack poseStack, float x, float y) {
        poseStack.translate(x, y, 0);
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void translate(PoseStack poseStack, int x, int y) {
        poseStack.translate((float) x, (float) y, 0);
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void scale(PoseStack poseStack, float x, float y) {
        poseStack.scale(x, y, 1);
    }
}
