package dev.isotope.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * MC 1.21.0-1.21.10 entity spawning compatibility.
 *
 * Uses reflection to handle varying entity spawn API:
 * - MC 1.21.1-1.21.3: EntityType.create(level, consumer, pos, MobSpawnType, boolean, boolean)
 * - MC 1.21.4+: EntityType.create(level, consumer, pos, EntitySpawnReason, boolean, boolean)
 */
public final class EntitySpawnCompat {

    private static final Method CREATE_METHOD;
    private static final Object COMMAND_SPAWN_REASON;

    static {
        Method createMethod = null;
        Object commandSpawnReason = null;

        // Find the create method with spawn reason parameter
        for (Method method : EntityType.class.getMethods()) {
            if (method.getName().equals("create") && method.getParameterCount() == 6) {
                Class<?>[] params = method.getParameterTypes();
                // Looking for: (ServerLevel, Consumer/null, BlockPos, SpawnReason, boolean, boolean)
                if (params[0] == ServerLevel.class && params[2] == BlockPos.class) {
                    createMethod = method;

                    // Get the spawn reason enum class (4th parameter)
                    Class<?> spawnReasonClass = params[3];
                    if (spawnReasonClass.isEnum()) {
                        // Find COMMAND enum constant
                        for (Object constant : spawnReasonClass.getEnumConstants()) {
                            if (constant.toString().equals("COMMAND")) {
                                commandSpawnReason = constant;
                                break;
                            }
                        }
                        // Fallback to SPAWN_EGG if COMMAND doesn't exist
                        if (commandSpawnReason == null) {
                            for (Object constant : spawnReasonClass.getEnumConstants()) {
                                if (constant.toString().equals("SPAWN_EGG")) {
                                    commandSpawnReason = constant;
                                    break;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        CREATE_METHOD = createMethod;
        COMMAND_SPAWN_REASON = commandSpawnReason;
    }

    private EntitySpawnCompat() {}

    /**
     * Create an entity with COMMAND spawn reason.
     * Works across MC versions with different spawn reason enums.
     *
     * @param entityType The entity type to spawn
     * @param level The server level
     * @param pos The spawn position
     * @return The created entity, or null if creation failed
     */
    public static Entity createEntity(EntityType<?> entityType, ServerLevel level, BlockPos pos) {
        return createEntity(entityType, level, null, pos);
    }

    /**
     * Create an entity with COMMAND spawn reason and optional consumer.
     * Works across MC versions with different spawn reason enums.
     *
     * @param entityType The entity type to spawn
     * @param level The server level
     * @param consumer Optional consumer to configure the entity (can be null)
     * @param pos The spawn position
     * @return The created entity, or null if creation failed
     */
    @SuppressWarnings("unchecked")
    public static Entity createEntity(EntityType<?> entityType, ServerLevel level, Consumer<Entity> consumer, BlockPos pos) {
        if (CREATE_METHOD == null || COMMAND_SPAWN_REASON == null) {
            return null;
        }

        try {
            return (Entity) CREATE_METHOD.invoke(entityType, level, consumer, pos, COMMAND_SPAWN_REASON, false, false);
        } catch (Exception e) {
            return null;
        }
    }
}
