package dev.isotope.compat;

import net.minecraft.commands.CommandSourceStack;

import java.util.ServiceLoader;

/**
 * Version adapter for Minecraft API differences across versions.
 *
 * This interface provides methods that have different implementations
 * between MC versions, allowing common code to work across all versions.
 */
public interface McVersion {

    /**
     * Singleton instance loaded via ServiceLoader.
     */
    McVersion INSTANCE = loadInstance();

    /**
     * @return The Minecraft version string (e.g., "1.21.11")
     */
    String getMinecraftVersion();

    /**
     * @return The API version used for compat (e.g., "mc1211" or "mc1210")
     */
    String getApiVersion();

    /**
     * Check if a command source has gamemaster (operator level 2) permission.
     *
     * In MC 1.21.11+: {@code source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}
     * In MC 1.21.0-1.21.10: {@code source.hasPermission(2)}
     */
    boolean hasGamemasterPermission(CommandSourceStack source);

    /**
     * @return true if this is MC 1.21.11 or newer
     */
    default boolean is1211OrNewer() {
        return "mc1211".equals(getApiVersion());
    }

    /**
     * @return true if this is MC 1.21.0-1.21.10
     */
    default boolean is1210OrOlder() {
        return "mc1210".equals(getApiVersion());
    }

    private static McVersion loadInstance() {
        ServiceLoader<McVersion> loader = ServiceLoader.load(McVersion.class);
        return loader.findFirst().orElseThrow(() ->
            new IllegalStateException("No McVersion implementation found. " +
                "Ensure the correct version-specific compat module is included.")
        );
    }
}
