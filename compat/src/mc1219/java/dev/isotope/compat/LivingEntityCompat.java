package dev.isotope.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * MC 1.21.0-1.21.10 LivingEntity compatibility.
 *
 * Uses reflection to handle varying kill() signatures:
 * - MC 1.21.4+: kill(ServerLevel)
 * - MC 1.21.0-1.21.3: kill()
 */
public final class LivingEntityCompat {

    private static final Method KILL_METHOD;
    private static final boolean REQUIRES_LEVEL;

    static {
        Method method = null;
        boolean requiresLevel = false;

        // Try to find kill(ServerLevel) first
        try {
            method = LivingEntity.class.getMethod("kill", ServerLevel.class);
            requiresLevel = true;
        } catch (NoSuchMethodException e) {
            // Try kill() with no args
            try {
                method = LivingEntity.class.getMethod("kill");
                requiresLevel = false;
            } catch (NoSuchMethodException e2) {
                // Neither found
            }
        }

        KILL_METHOD = method;
        REQUIRES_LEVEL = requiresLevel;
    }

    private LivingEntityCompat() {}

    /**
     * Kill a living entity.
     *
     * @param entity The entity to kill
     * @param level The server level (used in newer versions)
     */
    public static void kill(LivingEntity entity, ServerLevel level) {
        try {
            if (KILL_METHOD != null) {
                if (REQUIRES_LEVEL) {
                    KILL_METHOD.invoke(entity, level);
                } else {
                    KILL_METHOD.invoke(entity);
                }
            }
        } catch (Exception e) {
            // Silent failure - entity may still die from damage
        }
    }
}
