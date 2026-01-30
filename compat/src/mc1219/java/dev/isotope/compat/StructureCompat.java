package dev.isotope.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * MC 1.21.0-1.21.10 Structure.generate() compatibility.
 *
 * Uses reflection to handle varying signatures:
 * - MC 1.21.4+: generate(Holder, ResourceKey, RegistryAccess, ChunkGenerator, BiomeSource, RandomState, StructureTemplateManager, long, ChunkPos, int, ServerLevel, Predicate)
 * - MC 1.21.0-1.21.3: generate(RegistryAccess, ChunkGenerator, BiomeSource, RandomState, StructureTemplateManager, long, ChunkPos, int, LevelHeightAccessor, Predicate)
 */
public final class StructureCompat {

    private static final Method GENERATE_METHOD;
    private static final int PARAM_COUNT;

    static {
        Method method = null;
        int paramCount = 0;

        // Find the generate method
        for (Method m : Structure.class.getMethods()) {
            if (m.getName().equals("generate") && m.getReturnType() == StructureStart.class) {
                if (m.getParameterCount() > paramCount) {
                    method = m;
                    paramCount = m.getParameterCount();
                }
            }
        }

        GENERATE_METHOD = method;
        PARAM_COUNT = paramCount;
    }

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

        try {
            if (GENERATE_METHOD == null) {
                return null;
            }

            RegistryAccess registryAccess = level.registryAccess();
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            BiomeSource biomeSource = generator.getBiomeSource();
            RandomState randomState = level.getChunkSource().randomState();
            StructureTemplateManager templateManager = level.getStructureManager();

            if (PARAM_COUNT >= 12) {
                // MC 1.21.4+: includes Holder and dimension key
                return (StructureStart) GENERATE_METHOD.invoke(structure,
                    holder,
                    level.dimension(),
                    registryAccess,
                    generator,
                    biomeSource,
                    randomState,
                    templateManager,
                    seed,
                    chunkPos,
                    references,
                    level,
                    biomePredicate
                );
            } else if (PARAM_COUNT >= 10) {
                // MC 1.21.0-1.21.3: no Holder or dimension key
                return (StructureStart) GENERATE_METHOD.invoke(structure,
                    registryAccess,
                    generator,
                    biomeSource,
                    randomState,
                    templateManager,
                    seed,
                    chunkPos,
                    references,
                    level,
                    biomePredicate
                );
            }
        } catch (Exception e) {
            // Generation failed
        }

        return null;
    }
}
