package dev.isotope.mixin;

import dev.isotope.observation.StructureObserver;
import dev.isotope.observation.StructurePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to observe when structures are placed in the world.
 * This lets us know exactly where structures are generated.
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {

    @Shadow
    @Final
    private Structure structure;

    @Shadow
    public abstract BoundingBox getBoundingBox();

    @Shadow
    public abstract ChunkPos getChunkPos();

    /**
     * After a structure is placed in a chunk, record it.
     */
    @Inject(
        method = "placeInChunk",
        at = @At("TAIL")
    )
    private void isotope$afterPlaceInChunk(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            CallbackInfo ci) {

        if (!StructureObserver.getInstance().isRecording()) {
            return;
        }

        // Get the structure's registry key
        ResourceLocation structureId = level.registryAccess()
            .lookupOrThrow(Registries.STRUCTURE)
            .listElements()
            .filter(holder -> holder.value() == this.structure)
            .findFirst()
            .map(holder -> holder.key().location())
            .orElse(null);

        if (structureId == null) {
            return;
        }

        BoundingBox bounds = this.getBoundingBox();
        BlockPos origin = new BlockPos(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ()
        );

        StructurePlacement placement = StructurePlacement.observed(
            structureId,
            origin,
            bounds
        );

        StructureObserver.getInstance().onStructurePlaced(placement);
    }
}
