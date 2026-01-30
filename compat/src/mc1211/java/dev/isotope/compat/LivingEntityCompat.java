package dev.isotope.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * MC 1.21.11+ LivingEntity compatibility.
 *
 * Direct API call with ServerLevel parameter.
 */
public final class LivingEntityCompat {

    private LivingEntityCompat() {}

    /**
     * Kill a living entity.
     *
     * @param entity The entity to kill
     * @param level The server level
     */
    public static void kill(LivingEntity entity, ServerLevel level) {
        entity.kill(level);
    }
}
