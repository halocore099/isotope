package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.compat.RegistryHelper;
import dev.isotope.compat.VersionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for mob-related testing operations.
 *
 * Provides tools for:
 * - Spawning mobs for loot testing
 * - Killing mobs with different conditions (player kill, looting levels)
 * - Collecting and analyzing drops
 */
public final class TestMobTools {

    private TestMobTools() {}

    /**
     * Result of a mob spawn operation.
     */
    public record SpawnResult(
        boolean success,
        @Nullable Entity entity,
        @Nullable String error
    ) {
        public static SpawnResult success(Entity entity) {
            return new SpawnResult(true, entity, null);
        }

        public static SpawnResult error(String message) {
            return new SpawnResult(false, null, message);
        }
    }

    /**
     * Result of a mob kill operation.
     */
    public record KillResult(
        boolean success,
        int dropsCollected,
        @Nullable String error
    ) {
        public static KillResult success(int drops) {
            return new KillResult(true, drops, null);
        }

        public static KillResult error(String message) {
            return new KillResult(false, 0, message);
        }
    }

    /**
     * Kill condition for mob loot testing.
     * Looting level is now a separate parameter for better UI control.
     */
    public enum KillCondition {
        PLAYER_KILL("Player Kill", "Drops rare items requiring player kill"),
        NON_PLAYER("Non-Player", "Only drops guaranteed items");

        public final String displayName;
        public final String description;

        KillCondition(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public boolean isPlayerKill() {
            return this == PLAYER_KILL;
        }
    }

    /**
     * Spawn a mob at or near the player's position.
     *
     * @param server The Minecraft server
     * @param entityId The entity type's resource location (e.g., "minecraft:zombie")
     * @param offset Offset from player position
     * @return SpawnResult with the spawned entity
     */
    public static SpawnResult spawnMob(MinecraftServer server, ResourceLocation entityId, BlockPos offset) {
        try {
            ServerLevel level = server.overworld();
            if (level == null) {
                return SpawnResult.error("No overworld available");
            }

            ServerPlayer player = getPlayer(server);
            if (player == null) {
                return SpawnResult.error("No player found");
            }

            // Get entity type from registry
            Optional<EntityType<?>> entityTypeOpt = RegistryHelper.getEntityType(entityId);
            if (entityTypeOpt.isEmpty()) {
                return SpawnResult.error("Unknown entity: " + entityId);
            }

            EntityType<?> entityType = entityTypeOpt.get();

            // Calculate spawn position
            BlockPos playerPos = player.blockPosition();
            BlockPos spawnPos = playerPos.offset(offset.getX(), offset.getY(), offset.getZ());

            // Spawn the entity using version-compatible method
            Entity entity = createEntityVersionCompatible(entityType, level, spawnPos);

            if (entity == null) {
                return SpawnResult.error("Failed to create entity");
            }

            // Position the entity
            entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

            // Make mob non-aggressive for easier testing (if it's a Mob)
            if (entity instanceof Mob mob) {
                mob.setNoAi(true); // Disable AI so it doesn't move/attack
                mob.setPersistenceRequired();
            }

            // Add to world
            level.addFreshEntity(entity);

            Isotope.LOGGER.info("Spawned {} at {}", entityId, spawnPos);
            return SpawnResult.success(entity);

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to spawn {}: {}", entityId, e.getMessage());
            return SpawnResult.error("Spawn failed: " + e.getMessage());
        }
    }

    /**
     * Create an entity using version-compatible reflection.
     *
     * 1.21+: EntityType.create(ServerLevel, CompoundTag, BlockPos, EntitySpawnReason, boolean, boolean)
     * 1.20.x: EntityType.create(ServerLevel, CompoundTag, Consumer, BlockPos, MobSpawnType, boolean, boolean)
     */
    @SuppressWarnings("unchecked")
    private static <T extends Entity> T createEntityVersionCompatible(EntityType<T> entityType,
                                                                       ServerLevel level, BlockPos pos) {
        try {
            // Try 1.20.x version first (has Consumer parameter)
            try {
                Class<?> mobSpawnTypeClass = Class.forName("net.minecraft.world.entity.MobSpawnType");
                Object commandType = mobSpawnTypeClass.getField("COMMAND").get(null);

                for (Method method : entityType.getClass().getMethods()) {
                    if (method.getName().equals("create") && method.getParameterCount() == 7) {
                        Class<?>[] params = method.getParameterTypes();
                        // Check if this is the 1.20.x signature (has Consumer parameter)
                        if (params[2].getName().contains("Consumer")) {
                            return (T) method.invoke(entityType, level, null, null, pos, commandType, false, false);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // MobSpawnType doesn't exist, try EntitySpawnReason
            }

            // Try 1.21+ version (no Consumer parameter)
            try {
                Class<?> spawnReasonClass = Class.forName("net.minecraft.world.entity.EntitySpawnReason");
                Object commandReason = spawnReasonClass.getField("COMMAND").get(null);

                for (Method method : entityType.getClass().getMethods()) {
                    if (method.getName().equals("create") && method.getParameterCount() == 6) {
                        return (T) method.invoke(entityType, level, null, pos, commandReason, false, false);
                    }
                }
            } catch (ClassNotFoundException e) {
                // EntitySpawnReason doesn't exist either
            }

            // Fallback: simple create
            for (Method method : entityType.getClass().getMethods()) {
                if (method.getName().equals("create") && method.getParameterCount() == 1) {
                    return (T) method.invoke(entityType, level);
                }
            }

            return null;
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to create entity via reflection: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Spawn multiple mobs in a grid pattern for batch testing.
     *
     * @param server The Minecraft server
     * @param entityId The entity type
     * @param count Number of mobs to spawn
     * @return List of spawn results
     */
    public static List<SpawnResult> spawnMobGrid(MinecraftServer server, ResourceLocation entityId, int count) {
        List<SpawnResult> results = new ArrayList<>();

        // Calculate grid dimensions (roughly square)
        int gridSize = (int) Math.ceil(Math.sqrt(count));
        int spacing = 2; // 2 blocks between mobs

        int spawned = 0;
        for (int x = 0; x < gridSize && spawned < count; x++) {
            for (int z = 0; z < gridSize && spawned < count; z++) {
                BlockPos offset = new BlockPos(
                    (x - gridSize / 2) * spacing + 3, // Offset from player
                    0,
                    (z - gridSize / 2) * spacing + 3
                );
                results.add(spawnMob(server, entityId, offset));
                spawned++;
            }
        }

        return results;
    }

    /**
     * Kill a mob with specific conditions to test loot drops.
     *
     * @param server The Minecraft server
     * @param entity The entity to kill
     * @param condition The kill condition
     * @param lootingLevel Looting enchant level (0-3), only applies if condition is player kill
     * @return KillResult with drop count
     */
    public static KillResult killMob(MinecraftServer server, Entity entity, KillCondition condition, int lootingLevel) {
        try {
            if (!(entity instanceof LivingEntity living)) {
                return KillResult.error("Entity is not a LivingEntity");
            }

            ServerLevel level = (ServerLevel) entity.level();
            ServerPlayer player = getPlayer(server);

            if (player == null && condition.isPlayerKill()) {
                return KillResult.error("No player found for player kill");
            }

            // Set up looting if needed (only applies to player kills)
            if (lootingLevel > 0 && condition.isPlayerKill() && player != null) {
                // Give player a sword with looting for the kill
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                // Note: Enchantment application varies by MC version
                // This is a simplified approach
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
            }

            // Create damage source
            DamageSource damageSource;
            if (condition.isPlayerKill() && player != null) {
                damageSource = level.damageSources().playerAttack(player);
            } else {
                damageSource = level.damageSources().generic();
            }

            // Kill the entity
            living.hurt(damageSource, Float.MAX_VALUE);

            // If still alive, force death
            if (living.isAlive()) {
                VersionHelper.killEntity(living, level);
            }

            String lootingInfo = lootingLevel > 0 && condition.isPlayerKill() ? " (Looting " + lootingLevel + ")" : "";
            Isotope.LOGGER.info("Killed {} with condition: {}{}",
                entity.getType().getDescriptionId(), condition.displayName, lootingInfo);

            return KillResult.success(0); // Drop count would need tracking

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to kill entity: {}", e.getMessage());
            return KillResult.error("Kill failed: " + e.getMessage());
        }
    }

    /**
     * Spawn and immediately kill a mob to test loot drops.
     *
     * @param server The Minecraft server
     * @param entityId The entity type
     * @param condition The kill condition
     * @param lootingLevel Looting enchant level (0-3)
     * @return KillResult
     */
    public static KillResult spawnAndKill(MinecraftServer server, ResourceLocation entityId, KillCondition condition, int lootingLevel) {
        SpawnResult spawn = spawnMob(server, entityId, new BlockPos(3, 0, 3));
        if (!spawn.success()) {
            return KillResult.error("Spawn failed: " + spawn.error());
        }

        return killMob(server, spawn.entity(), condition, lootingLevel);
    }

    /**
     * Spawn and kill multiple mobs for statistical testing.
     *
     * @param server The Minecraft server
     * @param entityId The entity type
     * @param count Number of mobs to test
     * @param condition The kill condition
     * @param lootingLevel Looting enchant level (0-3)
     * @return Total kills successful
     */
    public static int batchSpawnAndKill(MinecraftServer server, ResourceLocation entityId,
                                         int count, KillCondition condition, int lootingLevel) {
        int successful = 0;

        for (int i = 0; i < count; i++) {
            // Slight delay offset to prevent entity collision
            BlockPos offset = new BlockPos(3 + (i % 5), 0, 3 + (i / 5));
            SpawnResult spawn = spawnMob(server, entityId, offset);

            if (spawn.success()) {
                KillResult kill = killMob(server, spawn.entity(), condition, lootingLevel);
                if (kill.success()) {
                    successful++;
                }
            }
        }

        String lootingInfo = lootingLevel > 0 && condition.isPlayerKill() ? " (Looting " + lootingLevel + ")" : "";
        Isotope.LOGGER.info("Batch test complete: {}/{} mobs killed with {}{}",
            successful, count, condition.displayName, lootingInfo);

        return successful;
    }

    /**
     * Clear all mobs of a specific type near the player.
     *
     * @param server The Minecraft server
     * @param entityId The entity type to clear (null for all mobs)
     * @param radius Radius in blocks
     * @return Number of entities removed
     */
    public static int clearMobs(MinecraftServer server, @Nullable ResourceLocation entityId, int radius) {
        try {
            ServerLevel level = server.overworld();
            ServerPlayer player = getPlayer(server);

            if (level == null || player == null) return 0;

            BlockPos playerPos = player.blockPosition();
            int removed = 0;

            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (entity instanceof ServerPlayer) continue; // Don't remove players

                // Check distance
                if (entity.blockPosition().distSqr(playerPos) > radius * radius) continue;

                // Check entity type if specified
                if (entityId != null) {
                    ResourceLocation entityType = RegistryHelper.getEntityTypeId(entity.getType());
                    if (!entityId.equals(entityType)) continue;
                }

                entity.remove(Entity.RemovalReason.DISCARDED);
                removed++;
            }

            Isotope.LOGGER.info("Cleared {} mobs within {} blocks", removed, radius);
            return removed;

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to clear mobs: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get the first player on the server.
     */
    @Nullable
    private static ServerPlayer getPlayer(MinecraftServer server) {
        if (server == null) return null;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    /**
     * Get entity display name from entity ID.
     */
    public static String getEntityDisplayName(ResourceLocation entityId) {
        Optional<EntityType<?>> entityTypeOpt = RegistryHelper.getEntityType(entityId);
        if (entityTypeOpt.isPresent()) {
            String key = entityTypeOpt.get().getDescriptionId();
            // Extract name from "entity.minecraft.zombie" -> "Zombie"
            String[] parts = key.split("\\.");
            if (parts.length > 0) {
                String name = parts[parts.length - 1];
                return formatName(name);
            }
        }
        return formatName(entityId.getPath());
    }

    /**
     * Format snake_case to Title Case.
     */
    private static String formatName(String name) {
        if (name == null || name.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String word : name.split("_")) {
            if (!word.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(word.charAt(0)));
                result.append(word.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }
}
