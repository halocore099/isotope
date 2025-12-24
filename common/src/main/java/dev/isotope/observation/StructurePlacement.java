package dev.isotope.observation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Records a structure placement observed or triggered during analysis.
 */
public record StructurePlacement(
    ResourceLocation structureId,
    BlockPos origin,
    BoundingBox boundingBox,
    long timestamp,
    PlacementSource source
) {
    public enum PlacementSource {
        /** Structure was forced by ISOTOPE for analysis */
        FORCED,
        /** Structure was observed during natural worldgen */
        NATURAL,
        /** Structure was placed via command */
        COMMAND
    }

    /**
     * Create a forced placement record.
     */
    public static StructurePlacement forced(ResourceLocation structureId, BlockPos origin, BoundingBox bounds) {
        return new StructurePlacement(
            structureId,
            origin,
            bounds,
            System.currentTimeMillis(),
            PlacementSource.FORCED
        );
    }

    /**
     * Create a natural/observed placement record.
     */
    public static StructurePlacement observed(ResourceLocation structureId, BlockPos origin, BoundingBox bounds) {
        return new StructurePlacement(
            structureId,
            origin,
            bounds,
            System.currentTimeMillis(),
            PlacementSource.NATURAL
        );
    }

    /**
     * Check if a position is within this structure's bounding box.
     */
    public boolean contains(BlockPos pos) {
        return boundingBox.isInside(pos);
    }

    /**
     * Check if a position is near this structure (within radius of bounding box).
     */
    public boolean isNear(BlockPos pos, int radius) {
        BoundingBox expanded = boundingBox.inflatedBy(radius);
        return expanded.isInside(pos);
    }

    /**
     * Get the center of the structure.
     */
    public BlockPos center() {
        return boundingBox.getCenter();
    }
}
