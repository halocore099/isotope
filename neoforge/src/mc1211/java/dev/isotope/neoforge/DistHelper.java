package dev.isotope.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge 21.9+ (MC 1.21.11+) distribution helper.
 * Uses FMLEnvironment.getDist() method access.
 */
public final class DistHelper {
    private DistHelper() {}

    public static Dist getDist() {
        return FMLEnvironment.getDist();
    }

    public static boolean isClient() {
        return FMLEnvironment.getDist() == Dist.CLIENT;
    }
}
