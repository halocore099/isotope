package dev.isotope.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.function.Function;

/**
 * MC 1.21.11+ world generation compatibility.
 *
 * Direct API calls to WorldPresets methods.
 */
public final class WorldGenCompat {

    private WorldGenCompat() {}

    /**
     * Get a function to create flat world dimensions.
     */
    public static Function<HolderLookup.Provider, WorldDimensions> createFlatWorldDimensions() {
        return WorldPresets::createFlatWorldDimensions;
    }

    /**
     * Get a function to create normal world dimensions.
     */
    public static Function<HolderLookup.Provider, WorldDimensions> createNormalWorldDimensions() {
        return WorldPresets::createNormalWorldDimensions;
    }
}
