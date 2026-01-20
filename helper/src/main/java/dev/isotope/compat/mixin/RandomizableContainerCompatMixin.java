package dev.isotope.compat.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Mixin for 1.20.x to capture loot table assignments using ResourceLocation parameter.
 *
 * In 1.20.x, setLootTable takes ResourceLocation directly.
 * In 1.21+, setLootTable takes ResourceKey<LootTable>.
 *
 * This mixin is only loaded by the helper mod on 1.20.x.
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity")
public abstract class RandomizableContainerCompatMixin {

    /**
     * 1.20.x signature: setLootTable(ResourceLocation)
     */
    @Inject(
        method = "setLootTable(Lnet/minecraft/resources/ResourceLocation;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void isotope$onSetLootTable120(ResourceLocation tableId, CallbackInfo ci) {
        // Forward to LootObserver if it's recording
        // Use reflection to avoid hard dependency on main mod
        try {
            Class<?> observerClass = Class.forName("dev.isotope.observation.LootObserver");
            Method getInstanceMethod = observerClass.getMethod("getInstance");
            Object observer = getInstanceMethod.invoke(null);

            Method isRecordingMethod = observerClass.getMethod("isRecording");
            boolean isRecording = (Boolean) isRecordingMethod.invoke(observer);

            if (!isRecording) {
                return;
            }

            // Get block position
            BlockPos pos = BlockPos.ZERO;
            try {
                Object self = this;
                if (self instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                    pos = be.getBlockPos();
                }
            } catch (Exception e) {
                // Fallback to zero
            }

            // Parse caller from stack trace
            String callerOrigin = parseCallerFromStack();

            // Call onLootAssigned
            Method onLootAssignedMethod = observerClass.getMethod("onLootAssigned",
                ResourceLocation.class, String.class, BlockPos.class);
            onLootAssignedMethod.invoke(observer, tableId, callerOrigin, pos);

        } catch (ClassNotFoundException e) {
            // Main mod not loaded - ignore
        } catch (Exception e) {
            // Reflection failed - ignore
        }
    }

    /**
     * Parse the call stack to find the structure/feature that called setLootTable().
     */
    private String parseCallerFromStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (int i = 3; i < Math.min(20, stack.length); i++) {
            String className = stack[i].getClassName();
            String methodName = stack[i].getMethodName();

            if (className.startsWith("java.") ||
                className.startsWith("sun.") ||
                className.startsWith("jdk.") ||
                className.contains("Mixin") ||
                className.contains("RandomizableContainerBlockEntity")) {
                continue;
            }

            if (className.contains("Structure") ||
                className.contains("Feature") ||
                className.contains("Template") ||
                className.contains("Piece") ||
                className.contains("Jigsaw") ||
                className.contains("Pool")) {
                return className + "." + methodName;
            }

            if (!className.startsWith("net.minecraft.") &&
                !className.startsWith("com.mojang.") &&
                !className.startsWith("org.spongepowered.")) {
                return className + "." + methodName;
            }
        }

        return "unknown";
    }
}
