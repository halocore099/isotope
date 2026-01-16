package dev.isotope.mixin;

import dev.isotope.observation.LootObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture loot table assignments to containers (chests, barrels, etc.)
 * during world generation.
 *
 * This hooks into setLootTable() to capture the exact moment a loot table is
 * assigned, along with the calling context (stack trace) which reveals what
 * structure/feature placed the container.
 *
 * This is more accurate than spatial correlation because it captures direct
 * causation rather than inferring from proximity.
 *
 * Note: In 1.21+, setLootTable is on the RandomizableContainer interface,
 * but we target the implementing class to get access to getBlockPos().
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity")
public abstract class RandomizableContainerMixin {

    /**
     * Intercept loot table assignment to capture the caller context.
     * This is called when structures/features place containers with loot.
     *
     * Using remap=false since the method is defined in the RandomizableContainer interface
     * and may have different names in different mapping sets.
     */
    @Inject(
        method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void isotope$onSetLootTable(ResourceKey<LootTable> lootTableKey, CallbackInfo ci) {
        if (!LootObserver.getInstance().isRecording()) {
            return;
        }

        // Get the loot table ID
        var tableId = lootTableKey.location();

        // Get the block position - cast this to access the method
        BlockPos pos = BlockPos.ZERO;
        try {
            // Cast to Object first to bypass mixin class type check
            Object self = this;
            if (self instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                pos = be.getBlockPos();
            }
        } catch (Exception e) {
            // Fallback to zero position
        }

        // Parse the call stack to identify the caller (structure/feature)
        String callerOrigin = parseCallerFromStack();

        // Record the assignment
        LootObserver.getInstance().onLootAssigned(tableId, callerOrigin, pos);
    }

    /**
     * Parse the call stack to find the structure/feature that called setLootTable().
     *
     * We skip:
     * - java.* classes
     * - Mixin classes (this class)
     * - RandomizableContainerBlockEntity itself
     * - Basic Minecraft world/block classes
     *
     * We're looking for structure templates, features, or mod-specific classes.
     */
    private String parseCallerFromStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        // Skip first few frames (getStackTrace, this method, the mixin method)
        for (int i = 3; i < Math.min(20, stack.length); i++) {
            String className = stack[i].getClassName();
            String methodName = stack[i].getMethodName();

            // Skip internal/utility classes
            if (className.startsWith("java.") ||
                className.startsWith("sun.") ||
                className.startsWith("jdk.") ||
                className.contains("Mixin") ||
                className.contains("RandomizableContainerBlockEntity") ||
                className.contains("BlockEntity") && methodName.equals("load")) {
                continue;
            }

            // Look for structure-related classes (high value)
            if (className.contains("Structure") ||
                className.contains("Feature") ||
                className.contains("Template") ||
                className.contains("Piece") ||
                className.contains("Jigsaw") ||
                className.contains("Pool")) {
                return className + "." + methodName;
            }

            // Look for mod classes (contains a mod namespace pattern)
            // Modded classes typically have lowercase package names with mod IDs
            if (!className.startsWith("net.minecraft.") &&
                !className.startsWith("com.mojang.") &&
                !className.startsWith("org.spongepowered.")) {
                return className + "." + methodName;
            }
        }

        // If we can't identify a specific caller, return the first non-internal frame
        for (int i = 3; i < Math.min(10, stack.length); i++) {
            String className = stack[i].getClassName();
            if (!className.startsWith("java.") &&
                !className.startsWith("sun.") &&
                !className.contains("Mixin")) {
                return className + "." + stack[i].getMethodName();
            }
        }

        return "unknown";
    }
}
