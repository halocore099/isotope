package dev.isotope.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * MC 1.21.0-1.21.10 teleport compatibility.
 *
 * Uses reflection to handle varying teleportTo signatures and RelativeMovement class location.
 */
public final class TeleportCompat {

    private static final Method TELEPORT_METHOD;

    static {
        Method method = null;

        // Find the teleportTo method that takes ServerLevel, 3 doubles, Set, and 2 floats
        for (Method m : ServerPlayer.class.getMethods()) {
            if (m.getName().equals("teleportTo") && m.getParameterCount() >= 6) {
                Class<?>[] params = m.getParameterTypes();
                // Check if first param is ServerLevel and has enough params
                if (params.length > 0 && ServerLevel.class.isAssignableFrom(params[0])) {
                    // Could be 7 or 8 params depending on version
                    if (m.getParameterCount() >= 7) {
                        method = m;
                        break;
                    }
                }
            }
        }

        TELEPORT_METHOD = method;
    }

    private TeleportCompat() {}

    /**
     * Teleport a player to a position in a world.
     *
     * @param player The player to teleport
     * @param level The target level
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param relatives Relative movement flags (ignored, uses empty set internally)
     * @param yaw Yaw rotation
     * @param pitch Pitch rotation
     */
    public static void teleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z,
                                   Set<?> relatives, float yaw, float pitch) {
        try {
            if (TELEPORT_METHOD != null) {
                int paramCount = TELEPORT_METHOD.getParameterCount();

                // Create empty set of the correct type for relatives
                Set<?> relSet = Set.of();

                if (paramCount == 8) {
                    // MC 1.21.4+: includes boolean parameter at end
                    TELEPORT_METHOD.invoke(player, level, x, y, z, relSet, yaw, pitch, true);
                } else if (paramCount == 7) {
                    // MC 1.21.0-1.21.3: no boolean parameter
                    TELEPORT_METHOD.invoke(player, level, x, y, z, relSet, yaw, pitch);
                } else if (paramCount == 6) {
                    // Even older: no Set parameter
                    TELEPORT_METHOD.invoke(player, level, x, y, z, yaw, pitch);
                }
            } else {
                // Fallback: use simple teleport
                player.teleportTo(x, y, z);
            }
        } catch (Exception e) {
            // Fallback
            player.teleportTo(x, y, z);
        }
    }
}
