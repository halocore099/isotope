package dev.isotope.observation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Records a single loot table invocation observed at runtime.
 * This is the ground truth - we saw this happen.
 */
public record LootInvocation(
    ResourceLocation tableId,
    BlockPos position,
    long timestamp,
    String contextType,
    List<ResourceLocation> itemsGenerated
) {
    /**
     * Check if this invocation occurred near a given position.
     */
    public boolean isNear(BlockPos other, int radius) {
        return position.closerThan(other, radius);
    }

    /**
     * Get the dimension-agnostic chunk position.
     */
    public long chunkKey() {
        return ((long) (position.getX() >> 4) << 32) | ((long) (position.getZ() >> 4) & 0xFFFFFFFFL);
    }
}
