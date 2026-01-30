package dev.isotope.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * MC 1.21.11+ teleport compatibility.
 *
 * Uses reflection to handle teleport method variations.
 */
public final class TeleportCompat {

    private static final Method TELEPORT_METHOD;

    static {
        Method method = null;

        // Find the teleportTo method that takes ServerLevel, 3 doubles, Set, and 2 floats
        for (Method m : ServerPlayer.class.getMethods()) {
            if (m.getName().equals("teleportTo") && m.getParameterCount() >= 6) {
                Class<?>[] params = m.getParameterTypes();
                // Check if first param is ServerLevel
                if (params.length > 0 && ServerLevel.class.isAssignableFrom(params[0])) {
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
                Set<?> relSet = Set.of();

                if (paramCount == 8) {
                    TELEPORT_METHOD.invoke(player, level, x, y, z, relSet, yaw, pitch, true);
                } else if (paramCount == 7) {
                    TELEPORT_METHOD.invoke(player, level, x, y, z, relSet, yaw, pitch);
                }
            } else {
                player.teleportTo(x, y, z);
            }
        } catch (Exception e) {
            player.teleportTo(x, y, z);
        }
    }
}
