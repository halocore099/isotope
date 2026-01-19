package dev.isotope.compat;

import net.minecraft.SharedConstants;

/**
 * Runtime Minecraft version detection.
 * Uses SharedConstants and reflection to determine the running MC version.
 *
 * This class detects the version at runtime instead of using build-time conditionals,
 * allowing a single build to work across multiple MC versions.
 */
public final class MCVersion {

    private static final int[] VERSION;
    public static final boolean IS_1_21_PLUS;
    public static final boolean IS_1_20_6_PLUS;
    public static final boolean IS_1_20_4_PLUS;
    public static final boolean IS_1_20_2_PLUS;
    public static final boolean IS_1_20_X;

    static {
        VERSION = parseVersion();
        IS_1_21_PLUS = isAtLeast(1, 21, 0);
        IS_1_20_6_PLUS = isAtLeast(1, 20, 6);
        IS_1_20_4_PLUS = isAtLeast(1, 20, 4);
        IS_1_20_2_PLUS = isAtLeast(1, 20, 2);
        IS_1_20_X = !IS_1_21_PLUS;
    }

    private MCVersion() {}

    /**
     * Check if running MC 1.21.0 or newer.
     * - New registry API with Optional<Holder.Reference<T>>
     * - ToastManager (not ToastComponent)
     * - ResourceLocation.parse(), fromNamespaceAndPath(), withDefaultNamespace()
     * - RenderType::guiTextured for blit calls
     */
    public static boolean is1_21Plus() {
        return IS_1_21_PLUS;
    }

    /**
     * Check if running MC 1.20.6 or newer.
     */
    public static boolean is1_20_6Plus() {
        return IS_1_20_6_PLUS;
    }

    /**
     * Check if running MC 1.20.4 or newer.
     */
    public static boolean is1_20_4Plus() {
        return IS_1_20_4_PLUS;
    }

    /**
     * Check if running MC 1.20.2 or newer.
     * - Uses ReloadableServerRegistries.Holder for loot table tracking
     */
    public static boolean is1_20_2Plus() {
        return IS_1_20_2_PLUS;
    }

    /**
     * Check if running a 1.20.x version (not 1.21+).
     */
    public static boolean is1_20_X() {
        return IS_1_20_X;
    }

    /**
     * Check if running at least the specified version.
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (VERSION[0] > major) return true;
        if (VERSION[0] < major) return false;
        if (VERSION[1] > minor) return true;
        if (VERSION[1] < minor) return false;
        return VERSION[2] >= patch;
    }

    /**
     * Check if running at least the specified version (no patch).
     */
    public static boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /**
     * Get the current MC version as a string.
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
     * Get the major version number (e.g., 1 for 1.21.4).
     */
    public static int getMajor() {
        return VERSION[0];
    }

    /**
     * Get the minor version number (e.g., 21 for 1.21.4).
     */
    public static int getMinor() {
        return VERSION[1];
    }

    /**
     * Get the patch version number (e.g., 4 for 1.21.4).
     */
    public static int getPatch() {
        return VERSION[2];
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

    private static int[] parseVersion() {
        try {
            String versionName = SharedConstants.getCurrentVersion().getName();
            // Handle versions like "1.21.4", "1.20.1", "1.21"
            String[] parts = versionName.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = 0;
            if (parts.length > 2) {
                // Handle suffixes like "1.21.4-pre1"
                String patchStr = parts[2].replaceAll("[^0-9].*", "");
                if (!patchStr.isEmpty()) {
                    patch = Integer.parseInt(patchStr);
                }
            }
            return new int[]{major, minor, patch};
        } catch (Exception e) {
            // Fallback to assuming a recent version
            return new int[]{1, 21, 0};
        }
    }
}
