package dev.isotope.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.lang.reflect.Method;

/**
 * MC 1.21.11+ entity spawning compatibility.
 *
 * In MC 1.21.11+, uses EntitySpawnReason.COMMAND directly.
 * Uses reflection to avoid type-safety issues with wildcard generics.
 */
public final class EntitySpawnCompat {

    private static final Method CREATE_METHOD;

    static {
        Method method = null;
        // Find create method with 6 parameters: (ServerLevel, Consumer, BlockPos, EntitySpawnReason, boolean, boolean)
        for (Method m : EntityType.class.getMethods()) {
            if (m.getName().equals("create") && m.getParameterCount() == 6) {
                method = m;
                break;
            }
        }
        CREATE_METHOD = method;
    }

    private EntitySpawnCompat() {}

    /**
     * Create an entity with COMMAND spawn reason.
     *
     * @param entityType The entity type to spawn
     * @param level The server level
     * @param pos The spawn position
     * @return The created entity, or null if creation failed
     */
    public static Entity createEntity(EntityType<?> entityType, ServerLevel level, BlockPos pos) {
        if (CREATE_METHOD != null) {
            try {
                return (Entity) CREATE_METHOD.invoke(entityType, level, null, pos,
                    EntitySpawnReason.COMMAND, false, false);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
