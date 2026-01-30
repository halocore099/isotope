package dev.isotope.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge 21.9-21.10 (MC 1.21.9-1.21.10) distribution helper.
 * Uses FMLEnvironment.getDist() method access (like MC 1.21.11+).
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
