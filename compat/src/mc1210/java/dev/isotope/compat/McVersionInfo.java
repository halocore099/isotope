package dev.isotope.compat;

import net.minecraft.SharedConstants;

import java.lang.reflect.Method;

/**
 * MC 1.21.0-1.21.10 version info compatibility.
 *
 * Uses reflection to handle varying API signatures across MC versions:
 * - MC 1.21.1-1.21.4: WorldVersion.getName()
 * - MC 1.21.7+: WorldVersion.name() or getId()
 */
public final class McVersionInfo {

    private static final String VERSION_STRING;

    static {
        String version = "unknown";
        try {
            Object worldVersion = SharedConstants.getCurrentVersion();
            if (worldVersion != null) {
                Class<?> versionClass = worldVersion.getClass();

                // Try getName() first (MC 1.21.1-1.21.4)
                Method nameMethod = null;
                try {
                    nameMethod = versionClass.getMethod("getName");
                } catch (NoSuchMethodException e) {
                    // Try name() (MC 1.21.7+)
                    try {
                        nameMethod = versionClass.getMethod("name");
                    } catch (NoSuchMethodException e2) {
                        // Try getId()
                        try {
                            nameMethod = versionClass.getMethod("getId");
                        } catch (NoSuchMethodException e3) {
                            // Try getVersionString()
                            try {
                                nameMethod = versionClass.getMethod("getVersionString");
                            } catch (NoSuchMethodException e4) {
                                // Last resort - check interfaces
                                for (Class<?> iface : versionClass.getInterfaces()) {
                                    try {
                                        nameMethod = iface.getMethod("getName");
                                        break;
                                    } catch (NoSuchMethodException e5) {
                                        try {
                                            nameMethod = iface.getMethod("name");
                                            break;
                                        } catch (NoSuchMethodException e6) {
                                            // Continue searching
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (nameMethod != null) {
                    Object result = nameMethod.invoke(worldVersion);
                    if (result != null) {
                        version = result.toString();
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to unknown
        }
        VERSION_STRING = version;
    }

    private McVersionInfo() {}

    /**
     * Get the current Minecraft version string.
     */
    public static String getVersionString() {
        return VERSION_STRING;
    }
}
