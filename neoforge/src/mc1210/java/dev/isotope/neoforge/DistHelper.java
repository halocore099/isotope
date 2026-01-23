package dev.isotope.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge 21.0-21.8 (MC 1.21.0-1.21.10) distribution helper.
 * Uses FMLEnvironment.dist field access.
 */
public final class DistHelper {
    private DistHelper() {}

    public static Dist getDist() {
        return FMLEnvironment.dist;
    }

    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
