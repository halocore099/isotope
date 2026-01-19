package dev.isotope.compat;

import net.minecraft.SharedConstants;

/**
 * Minecraft version detection and comparison utilities.
 *
 * This class provides compile-time constants for version checks
 * and runtime version detection.
 *
 * Stonecutter conditionals set these values based on target version.
 * The syntax //? if <condition> { ... //?} is processed at build time.
 */
public final class MCVersion {

    private MCVersion() {}

    // Compile-time version constants - set by Stonecutter preprocessor
    //? if >=1.21 {
    public static final boolean IS_1_21_PLUS = true;
    //?} else {
    /*public static final boolean IS_1_21_PLUS = false;*/
    //?}

    //? if >=1.20.6 {
    public static final boolean IS_1_20_6_PLUS = true;
    //?} else {
    /*public static final boolean IS_1_20_6_PLUS = false;*/
    //?}

    //? if >=1.20.4 {
    public static final boolean IS_1_20_4_PLUS = true;
    //?} else {
    /*public static final boolean IS_1_20_4_PLUS = false;*/
    //?}

    //? if >=1.20.2 {
    public static final boolean IS_1_20_2_PLUS = true;
    //?} else {
    /*public static final boolean IS_1_20_2_PLUS = false;*/
    //?}

    //? if <1.21 {
    /*public static final boolean IS_1_20_X = true;*/
    //?} else {
    public static final boolean IS_1_20_X = false;
    //?}

    /**
     * Get the current Minecraft version string at runtime.
     */
    public static String getVersionString() {
        return SharedConstants.getCurrentVersion().getName();
    }

    /**
     * Get the current protocol version at runtime.
     */
    public static int getProtocolVersion() {
        return SharedConstants.getProtocolVersion();
    }

    /**
     * Check if the current MC version is at least the specified version.
     * This is a runtime check - prefer compile-time constants when possible.
     */
    public static boolean isAtLeast(int major, int minor) {
        String version = getVersionString();
        String[] parts = version.split("\\.");

        if (parts.length < 2) return false;

        try {
            int currentMajor = Integer.parseInt(parts[0]);
            int currentMinor = Integer.parseInt(parts[1]);

            if (currentMajor > major) return true;
            if (currentMajor < major) return false;
            return currentMinor >= minor;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Check if registry returns Optional (1.21+) vs nullable (1.20.x).
     */
    public static boolean hasOptionalRegistryLookup() {
        return IS_1_21_PLUS;
    }

    /**
     * Check if random sequence is supported in loot tables (1.20+).
     */
    public static boolean hasRandomSequence() {
        return true; // Available in all supported versions (1.20.1+)
    }
}
