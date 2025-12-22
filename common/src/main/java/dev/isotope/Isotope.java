package dev.isotope;

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
        LOGGER.warn("[{}] This is a developer tool. Not intended for survival gameplay.", MOD_NAME);
    }
}
