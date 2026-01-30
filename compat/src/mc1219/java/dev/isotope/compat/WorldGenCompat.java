package dev.isotope.compat;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.LevelSettings;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * MC 1.21.0-1.21.10 world generation compatibility.
 *
 * Uses reflection to handle varying WorldPresets API and createFreshLevel signature.
 * The createFreshLevel method signature varies between MC versions:
 * - Some versions: Function<RegistryAccess, WorldDimensions>
 * - Other versions: Function<HolderLookup.Provider, WorldDimensions>
 *
 * We handle this by invoking createFreshLevel via reflection with compatible function types.
 */
public final class WorldGenCompat {

    private static final Method CREATE_FLAT_METHOD;
    private static final Method CREATE_NORMAL_METHOD;
    private static final Method CREATE_FRESH_LEVEL_METHOD;

    static {
        Method flatMethod = null;
        Method normalMethod = null;
        Method createFreshLevelMethod = null;

        // Try to find the WorldPresets methods
        for (Method method : WorldPresets.class.getMethods()) {
            String name = method.getName();
            if (name.equals("createFlatWorldDimensions") || name.contains("Flat")) {
                if (method.getParameterCount() == 1) {
                    flatMethod = method;
                }
            } else if (name.equals("createNormalWorldDimensions") || name.contains("Normal")) {
                if (method.getParameterCount() == 1) {
                    normalMethod = method;
                }
            }
        }

        // Find createFreshLevel method in WorldOpenFlows
        for (Method method : WorldOpenFlows.class.getMethods()) {
            if (method.getName().equals("createFreshLevel") && method.getParameterCount() == 5) {
                createFreshLevelMethod = method;
                break;
            }
        }

        CREATE_FLAT_METHOD = flatMethod;
        CREATE_NORMAL_METHOD = normalMethod;
        CREATE_FRESH_LEVEL_METHOD = createFreshLevelMethod;
    }

    private WorldGenCompat() {}

    /**
     * Create a fresh level with flat (superflat) world generation.
     * Handles API variation across MC versions via reflection.
     */
    public static void createFlatWorld(
            WorldOpenFlows worldOpenFlows,
            String worldName,
            LevelSettings levelSettings,
            WorldOptions worldOptions
    ) {
        createWorld(worldOpenFlows, worldName, levelSettings, worldOptions, true);
    }

    /**
     * Create a fresh level with normal world generation.
     * Handles API variation across MC versions via reflection.
     */
    public static void createNormalWorld(
            WorldOpenFlows worldOpenFlows,
            String worldName,
            LevelSettings levelSettings,
            WorldOptions worldOptions
    ) {
        createWorld(worldOpenFlows, worldName, levelSettings, worldOptions, false);
    }

    /**
     * Internal method to create a world with the specified generation type.
     */
    private static void createWorld(
            WorldOpenFlows worldOpenFlows,
            String worldName,
            LevelSettings levelSettings,
            WorldOptions worldOptions,
            boolean flat
    ) {
        if (CREATE_FRESH_LEVEL_METHOD == null) {
            throw new RuntimeException("Could not find createFreshLevel method in WorldOpenFlows");
        }

        // Create a function that works regardless of parameter type (RegistryAccess or HolderLookup.Provider)
        // Both types are compatible since RegistryAccess extends HolderLookup.Provider
        Function<HolderLookup.Provider, WorldDimensions> dimensionFunction = provider -> {
            Method targetMethod = flat ? CREATE_FLAT_METHOD : CREATE_NORMAL_METHOD;
            if (targetMethod != null) {
                try {
                    return (WorldDimensions) targetMethod.invoke(null, provider);
                } catch (Exception e) {
                    // Fallback
                    return createFallbackDimensions(provider);
                }
            }
            return createFallbackDimensions(provider);
        };

        try {
            // Call createFreshLevel via reflection - this handles the signature variance
            CREATE_FRESH_LEVEL_METHOD.invoke(
                    worldOpenFlows,
                    worldName,
                    levelSettings,
                    worldOptions,
                    dimensionFunction,
                    null  // screen (nullable)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create world: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback that tries to use whatever method is available.
     */
    private static WorldDimensions createFallbackDimensions(HolderLookup.Provider provider) {
        // Try to find any suitable method to create dimensions
        for (Method method : WorldPresets.class.getMethods()) {
            if (method.getParameterCount() == 1 &&
                WorldDimensions.class.isAssignableFrom(method.getReturnType())) {
                try {
                    return (WorldDimensions) method.invoke(null, provider);
                } catch (Exception e) {
                    // Continue trying
                }
            }
        }
        return null;
    }
}
