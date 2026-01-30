package dev.isotope.compat;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.LevelSettings;

import java.util.function.Function;

/**
 * MC 1.21.11+ world generation compatibility.
 *
 * Direct API calls to WorldPresets and WorldOpenFlows methods.
 */
public final class WorldGenCompat {

    private WorldGenCompat() {}

    /**
     * Create a fresh level with flat (superflat) world generation.
     */
    public static void createFlatWorld(
            WorldOpenFlows worldOpenFlows,
            String worldName,
            LevelSettings levelSettings,
            WorldOptions worldOptions
    ) {
        worldOpenFlows.createFreshLevel(
                worldName,
                levelSettings,
                worldOptions,
                WorldPresets::createFlatWorldDimensions,
                null
        );
    }

    /**
     * Create a fresh level with normal world generation.
     */
    public static void createNormalWorld(
            WorldOpenFlows worldOpenFlows,
            String worldName,
            LevelSettings levelSettings,
            WorldOptions worldOptions
    ) {
        worldOpenFlows.createFreshLevel(
                worldName,
                levelSettings,
                worldOptions,
                WorldPresets::createNormalWorldDimensions,
                null
        );
    }
}
