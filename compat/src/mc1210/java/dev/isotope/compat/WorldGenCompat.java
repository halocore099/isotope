package dev.isotope.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * MC 1.21.0-1.21.10 world generation compatibility.
 *
 * Uses reflection to handle varying WorldPresets API.
 * Parameter type changed between versions (RegistryAccess vs HolderLookup.Provider).
 */
public final class WorldGenCompat {

    private static final Method CREATE_FLAT_METHOD;
    private static final Method CREATE_NORMAL_METHOD;

    static {
        Method flatMethod = null;
        Method normalMethod = null;

        // Try to find the methods
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

        CREATE_FLAT_METHOD = flatMethod;
        CREATE_NORMAL_METHOD = normalMethod;
    }

    private WorldGenCompat() {}

    /**
     * Get a function to create flat world dimensions.
     * Uses HolderLookup.Provider as parameter type (supertype of RegistryAccess).
     */
    @SuppressWarnings("unchecked")
    public static Function<HolderLookup.Provider, WorldDimensions> createFlatWorldDimensions() {
        if (CREATE_FLAT_METHOD != null) {
            return provider -> {
                try {
                    return (WorldDimensions) CREATE_FLAT_METHOD.invoke(null, provider);
                } catch (Exception e) {
                    return createFallbackDimensions(provider);
                }
            };
        }
        // Fallback
        return WorldGenCompat::createFallbackDimensions;
    }

    /**
     * Get a function to create normal world dimensions.
     */
    @SuppressWarnings("unchecked")
    public static Function<HolderLookup.Provider, WorldDimensions> createNormalWorldDimensions() {
        if (CREATE_NORMAL_METHOD != null) {
            return provider -> {
                try {
                    return (WorldDimensions) CREATE_NORMAL_METHOD.invoke(null, provider);
                } catch (Exception e) {
                    return createFallbackDimensions(provider);
                }
            };
        }
        // Fallback
        return WorldGenCompat::createFallbackDimensions;
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
