package dev.isotope;

import dev.isotope.command.IsotopeCommands;
import dev.isotope.registry.RegistryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ISOTOPE - Worldgen & loot introspection toolkit for Minecraft modpacks.
 *
 * This mod allows pack developers to analyze, visualize, and rebalance
 * all structures and their loot across vanilla and modded Minecraft.
 */
public final class Isotope {
    public static final String MOD_ID = "isotope";
    public static final String MOD_NAME = "ISOTOPE";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static void init() {
        LOGGER.info("Initializing {} - Worldgen & Loot Introspection Toolkit", MOD_NAME);
        printDevWarning();

        // M1: Registry Discovery
        RegistryScanner.init();
        IsotopeCommands.register();

        LOGGER.info("{} initialization complete", MOD_NAME);
    }

    private static void printDevWarning() {
        LOGGER.warn("");
        LOGGER.warn("========================================================");
        LOGGER.warn("  ISOTOPE - DEVELOPER TOOL");
        LOGGER.warn("========================================================");
        LOGGER.warn("  This mod is intended for modpack developers only.");
        LOGGER.warn("");
        LOGGER.warn("  DO NOT include this mod in production modpacks.");
        LOGGER.warn("  It may:");
        LOGGER.warn("    - Cause world generation changes");
        LOGGER.warn("    - Impact game performance during analysis");
        LOGGER.warn("    - Modify loot tables when editing features are used");
        LOGGER.warn("");
        LOGGER.warn("  Remove ISOTOPE before publishing your modpack!");
        LOGGER.warn("========================================================");
        LOGGER.warn("");
    }
}
