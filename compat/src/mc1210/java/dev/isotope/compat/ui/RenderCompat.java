package dev.isotope.compat.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

import java.lang.reflect.Method;

/**
 * MC 1.21.0-1.21.10 rendering compatibility.
 *
 * Uses reflection to handle varying API signatures across MC versions:
 * - MC 1.21.1-1.21.4: GuiGraphics.pose() returns PoseStack
 * - MC 1.21.7+: GuiGraphics.pose() returns Matrix3x2fStack with different method names
 */
@Environment(EnvType.CLIENT)
public final class RenderCompat {

    private static final Method POSE_METHOD;
    private static final Method PUSH_METHOD;
    private static final Method POP_METHOD;
    private static final Method TRANSLATE_METHOD;
    private static final Method SCALE_METHOD;
    private static final boolean IS_POSE_STACK;

    static {
        Method poseMethod = null;
        Method pushMethod = null;
        Method popMethod = null;
        Method translateMethod = null;
        Method scaleMethod = null;
        boolean isPoseStack = false;

        try {
            poseMethod = GuiGraphics.class.getMethod("pose");
            Class<?> returnType = poseMethod.getReturnType();
            isPoseStack = returnType.equals(PoseStack.class);

            // Find the appropriate methods on the return type
            if (isPoseStack) {
                // MC 1.21.1-1.21.4: PoseStack with pushPose/popPose
                pushMethod = returnType.getMethod("pushPose");
                popMethod = returnType.getMethod("popPose");
                translateMethod = returnType.getMethod("translate", float.class, float.class, float.class);
                scaleMethod = returnType.getMethod("scale", float.class, float.class, float.class);
            } else {
                // MC 1.21.7+: Matrix3x2fStack with push/pop or similar
                try {
                    pushMethod = returnType.getMethod("pushMatrix");
                } catch (NoSuchMethodException e) {
                    try {
                        pushMethod = returnType.getMethod("push");
                    } catch (NoSuchMethodException e2) {
                        // No push method available
                    }
                }
                try {
                    popMethod = returnType.getMethod("popMatrix");
                } catch (NoSuchMethodException e) {
                    try {
                        popMethod = returnType.getMethod("pop");
                    } catch (NoSuchMethodException e2) {
                        // No pop method available
                    }
                }
                try {
                    translateMethod = returnType.getMethod("translate", float.class, float.class);
                } catch (NoSuchMethodException e) {
                    // No translate method
                }
                try {
                    scaleMethod = returnType.getMethod("scale", float.class, float.class);
                } catch (NoSuchMethodException e) {
                    // No scale method
                }
            }
        } catch (NoSuchMethodException e) {
            // Should not happen
        }

        POSE_METHOD = poseMethod;
        PUSH_METHOD = pushMethod;
        POP_METHOD = popMethod;
        TRANSLATE_METHOD = translateMethod;
        SCALE_METHOD = scaleMethod;
        IS_POSE_STACK = isPoseStack;
    }

    private RenderCompat() {}

    /**
     * Push a matrix onto the pose stack.
     */
    public static void pushMatrix(GuiGraphics graphics) {
        try {
            if (POSE_METHOD != null && PUSH_METHOD != null) {
                Object pose = POSE_METHOD.invoke(graphics);
                PUSH_METHOD.invoke(pose);
            }
        } catch (Exception e) {
            // Silently ignore - rendering will work but may have issues
        }
    }

    /**
     * Pop a matrix from the pose stack.
     */
    public static void popMatrix(GuiGraphics graphics) {
        try {
            if (POSE_METHOD != null && POP_METHOD != null) {
                Object pose = POSE_METHOD.invoke(graphics);
                POP_METHOD.invoke(pose);
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    /**
     * Translate by x, y (with z=0 for 3D stacks).
     */
    public static void translate(GuiGraphics graphics, float x, float y) {
        try {
            if (POSE_METHOD != null && TRANSLATE_METHOD != null) {
                Object pose = POSE_METHOD.invoke(graphics);
                if (IS_POSE_STACK) {
                    TRANSLATE_METHOD.invoke(pose, x, y, 0f);
                } else {
                    TRANSLATE_METHOD.invoke(pose, x, y);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    /**
     * Translate by x, y (with z=0 for 3D stacks) - int version.
     */
    public static void translate(GuiGraphics graphics, int x, int y) {
        translate(graphics, (float) x, (float) y);
    }

    /**
     * Scale uniformly in x and y (z=1 for 3D stacks).
     */
    public static void scale(GuiGraphics graphics, float x, float y) {
        try {
            if (POSE_METHOD != null && SCALE_METHOD != null) {
                Object pose = POSE_METHOD.invoke(graphics);
                if (IS_POSE_STACK) {
                    SCALE_METHOD.invoke(pose, x, y, 1f);
                } else {
                    SCALE_METHOD.invoke(pose, x, y);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    // Legacy methods that take PoseStack directly (for backwards compatibility)

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void pushMatrix(PoseStack poseStack) {
        if (poseStack != null) {
            poseStack.pushPose();
        }
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void popMatrix(PoseStack poseStack) {
        if (poseStack != null) {
            poseStack.popPose();
        }
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void translate(PoseStack poseStack, float x, float y) {
        if (poseStack != null) {
            poseStack.translate(x, y, 0);
        }
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void translate(PoseStack poseStack, int x, int y) {
        if (poseStack != null) {
            poseStack.translate((float) x, (float) y, 0);
        }
    }

    /**
     * @deprecated Use the GuiGraphics version instead
     */
    @Deprecated
    public static void scale(PoseStack poseStack, float x, float y) {
        if (poseStack != null) {
            poseStack.scale(x, y, 1);
        }
    }
}
