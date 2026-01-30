package dev.isotope.compat;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.function.Predicate;

/**
 * MC 1.21.11+ Structure.generate() compatibility.
 *
 * Direct API call with full parameter list.
 */
public final class StructureCompat {

    private StructureCompat() {}

    /**
     * Generate a structure start.
     *
     * @return The generated StructureStart, or null if generation failed
     */
    public static StructureStart generate(
            Structure structure,
            Holder<Structure> holder,
            ServerLevel level,
            ChunkPos chunkPos,
            long seed,
            int references,
            Predicate<Holder<Biome>> biomePredicate) {

        return structure.generate(
            holder,
            level.dimension(),
            level.registryAccess(),
            level.getChunkSource().getGenerator(),
            level.getChunkSource().getGenerator().getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            seed,
            chunkPos,
            references,
            level,
            biomePredicate
        );
    }
}
