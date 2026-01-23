package dev.isotope.compat;

import net.minecraft.SharedConstants;

/**
 * MC 1.21.11+ version info compatibility.
 *
 * In MC 1.21.11+, SharedConstants.getCurrentVersion().id() is used.
 */
public final class McVersionInfo {

    private McVersionInfo() {}

    /**
     * Get the current Minecraft version string.
     * In MC 1.21.11+, uses .id().
     */
    public static String getVersionString() {
        return SharedConstants.getCurrentVersion().id();
    }
}
