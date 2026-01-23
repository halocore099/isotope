package dev.isotope.compat;

import net.minecraft.SharedConstants;

/**
 * MC 1.21.0-1.21.10 version info compatibility.
 *
 * In MC 1.21.10 and earlier, SharedConstants.getCurrentVersion().getName() is used.
 */
public final class McVersionInfo {

    private McVersionInfo() {}

    /**
     * Get the current Minecraft version string.
     * In MC 1.21.10 and earlier, uses .getName().
     */
    public static String getVersionString() {
        return SharedConstants.getCurrentVersion().getName();
    }
}
