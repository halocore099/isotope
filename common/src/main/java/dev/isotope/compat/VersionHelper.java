package dev.isotope.compat;

import net.minecraft.world.level.GameRules;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Version detection and compatibility utilities.
 *
 * Provides runtime version detection to help code paths that
 * differ between MC versions.
 */
public final class VersionHelper {

    private VersionHelper() {}

    // Cached version detection results
    private static Boolean is121Plus = null;
    private static Boolean is120 = null;

    // Cached constructors
    private static Constructor<?> gameRulesConstructor = null;

    /**
     * Check if running on Minecraft 1.21 or later.
     * Detects by checking for ToastManager class (new in 1.21).
     */
    public static boolean is121Plus() {
        if (is121Plus == null) {
            try {
                Class.forName("net.minecraft.client.gui.components.toasts.ToastManager");
                is121Plus = true;
            } catch (ClassNotFoundException e) {
                is121Plus = false;
            }
        }
        return is121Plus;
    }

    /**
     * Check if running on Minecraft 1.20.x.
     */
    public static boolean is120() {
        if (is120 == null) {
            is120 = !is121Plus();
        }
        return is120;
    }

    /**
     * Invoke a method by name using reflection.
     * Useful for calling version-specific methods.
     *
     * @param target The object to invoke on
     * @param methodName The method name
     * @param paramTypes Parameter types
     * @param args Arguments to pass
     * @return The result, or null if failed
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Try to get a method from a class by name.
     * Returns null if not found.
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Check if a class exists.
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Create a new GameRules instance.
     * Uses reflection to handle different constructor signatures between versions.
     *
     * 1.21+: new GameRules(FeatureFlagSet)
     * 1.20.x: new GameRules() or new GameRules(Map)
     */
    public static GameRules createGameRules() {
        try {
            if (gameRulesConstructor == null) {
                // Try 1.20.x no-arg constructor first
                try {
                    gameRulesConstructor = GameRules.class.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    // Try 1.21+ FeatureFlagSet constructor
                    Class<?> flagSetClass = Class.forName("net.minecraft.world.flag.FeatureFlagSet");
                    gameRulesConstructor = GameRules.class.getDeclaredConstructor(flagSetClass);
                }
            }

            if (gameRulesConstructor.getParameterCount() == 0) {
                // 1.20.x: no-arg constructor
                return (GameRules) gameRulesConstructor.newInstance();
            } else {
                // 1.21+: need FeatureFlagSet
                Class<?> featureFlagsClass = Class.forName("net.minecraft.world.flag.FeatureFlags");
                Object defaultFlags = featureFlagsClass.getField("DEFAULT_FLAGS").get(null);
                return (GameRules) gameRulesConstructor.newInstance(defaultFlags);
            }
        } catch (Exception e) {
            // Fallback: try direct instantiation
            try {
                return GameRules.class.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create GameRules", ex);
            }
        }
    }

    /**
     * Teleport a player to a location.
     * Uses reflection to handle different method signatures between versions.
     *
     * 1.21+: teleportTo(ServerLevel, double, double, double, Set, float, float, boolean)
     * 1.20.x: teleportTo(ServerLevel, double, double, double, Set, float, float)
     */
    public static void teleportPlayer(Object player, Object level, double x, double y, double z,
                                       java.util.Set<?> relatives, float yRot, float xRot) {
        try {
            Class<?> serverPlayerClass = player.getClass();
            Class<?> serverLevelClass = level.getClass();

            // Try 1.20.x 7-param version first
            try {
                Method method = serverPlayerClass.getMethod("teleportTo",
                    serverLevelClass.getSuperclass(), // ServerLevel's parent
                    double.class, double.class, double.class,
                    java.util.Set.class, float.class, float.class);
                method.invoke(player, level, x, y, z, relatives, yRot, xRot);
                return;
            } catch (NoSuchMethodException e) {
                // Try another signature
            }

            // Try with ServerLevel directly
            for (Method method : serverPlayerClass.getMethods()) {
                if (method.getName().equals("teleportTo") && method.getParameterCount() == 7) {
                    method.invoke(player, level, x, y, z, relatives, yRot, xRot);
                    return;
                }
            }

            // Try 1.21+ 8-param version
            for (Method method : serverPlayerClass.getMethods()) {
                if (method.getName().equals("teleportTo") && method.getParameterCount() == 8) {
                    method.invoke(player, level, x, y, z, relatives, yRot, xRot, false);
                    return;
                }
            }

            // Last resort: simple teleportTo
            Method simple = serverPlayerClass.getMethod("teleportTo", double.class, double.class, double.class);
            simple.invoke(player, x, y, z);
        } catch (Exception e) {
            throw new RuntimeException("Failed to teleport player", e);
        }
    }

    /**
     * Kill a living entity.
     * Uses reflection to handle different method signatures between versions.
     *
     * 1.21+: kill(ServerLevel)
     * 1.20.x: kill()
     */
    public static void killEntity(Object entity, Object level) {
        try {
            Class<?> entityClass = entity.getClass();

            // Try 1.20.x no-arg version first
            try {
                Method method = entityClass.getMethod("kill");
                method.invoke(entity);
                return;
            } catch (NoSuchMethodException e) {
                // Try 1.21+ version
            }

            // Try 1.21+ version with ServerLevel
            for (Method method : entityClass.getMethods()) {
                if (method.getName().equals("kill") && method.getParameterCount() == 1) {
                    method.invoke(entity, level);
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to kill entity", e);
        }
    }

    /**
     * Get an entity's loot table ID.
     * Returns null if the entity has no loot table or if it can't be determined.
     *
     * 1.21+: entity.getLootTable() returns Optional<ResourceKey<LootTable>>
     * 1.20.x: entity.getLootTable() returns ResourceLocation (nullable)
     */
    @SuppressWarnings("unchecked")
    public static <T> T getEntityLootTable(Object entity) {
        try {
            Method method = entity.getClass().getMethod("getLootTable");
            Object result = method.invoke(entity);

            if (result == null) {
                return null;
            }

            // Check if it's an Optional (1.21+)
            if (result.getClass().getName().equals("java.util.Optional")) {
                java.util.Optional<?> optional = (java.util.Optional<?>) result;
                if (optional.isPresent()) {
                    Object key = optional.get();
                    // ResourceKey has location() method
                    Method locationMethod = key.getClass().getMethod("location");
                    return (T) locationMethod.invoke(key);
                }
                return null;
            }

            // 1.20.x: direct ResourceLocation
            return (T) result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Spawn an entity.
     * Uses reflection to handle different method signatures between versions.
     *
     * 1.21+: EntityType.spawn(ServerLevel, BlockPos, SpawnReason)
     * 1.20.x: EntityType.spawn(ServerLevel, CompoundTag, Consumer, BlockPos, MobSpawnType, boolean, boolean)
     */
    public static Object spawnEntity(Object entityType, Object level, int x, int y, int z) {
        try {
            Class<?> entityTypeClass = entityType.getClass();
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");

            // Create BlockPos
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
                .newInstance(x, y, z);

            // Try 1.20.x version first (more parameters)
            for (Method method : entityTypeClass.getMethods()) {
                if (method.getName().equals("spawn")) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 7) {
                        // 1.20.x: spawn(ServerLevel, CompoundTag, Consumer, BlockPos, MobSpawnType, boolean, boolean)
                        Class<?> mobSpawnTypeClass = Class.forName("net.minecraft.world.entity.MobSpawnType");
                        Object commandSpawnType = mobSpawnTypeClass.getField("COMMAND").get(null);
                        return method.invoke(entityType, level, null, null, blockPos, commandSpawnType, false, false);
                    }
                }
            }

            // Try 1.21+ version (fewer parameters)
            for (Method method : entityTypeClass.getMethods()) {
                if (method.getName().equals("spawn")) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 3) {
                        // 1.21+: spawn(ServerLevel, BlockPos, SpawnReason)
                        Class<?> spawnReasonClass = Class.forName("net.minecraft.world.entity.EntitySpawnReason");
                        Object commandReason = spawnReasonClass.getField("COMMAND").get(null);
                        return method.invoke(entityType, level, blockPos, commandReason);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
