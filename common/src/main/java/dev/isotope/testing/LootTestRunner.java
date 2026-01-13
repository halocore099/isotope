package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.registry.EntityLootRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runs loot table tests and collects statistics.
 *
 * Supports both:
 * - Mob loot testing (spawn, kill, collect drops)
 * - Chest loot simulation (generate directly from table)
 */
public final class LootTestRunner {

    private LootTestRunner() {}

    /**
     * Result of a loot test run.
     */
    public record TestResult(
        boolean success,
        DropStatistics statistics,
        @Nullable String error
    ) {
        public static TestResult success(DropStatistics stats) {
            return new TestResult(true, stats, null);
        }

        public static TestResult error(String message) {
            return new TestResult(false, null, message);
        }
    }

    /**
     * Run mob loot tests with drop collection.
     *
     * @param server Minecraft server
     * @param entityId Entity type to test
     * @param count Number of mobs to spawn and kill
     * @param condition Kill condition
     * @param progressCallback Called with progress updates
     * @return TestResult with statistics
     */
    public static TestResult runMobTest(
        MinecraftServer server,
        ResourceLocation entityId,
        int count,
        TestMobTools.KillCondition condition,
        @Nullable Consumer<String> progressCallback
    ) {
        try {
            ServerLevel level = server.overworld();
            ServerPlayer player = getPlayer(server);

            if (level == null) {
                return TestResult.error("No overworld available");
            }
            if (player == null && condition.isPlayerKill()) {
                return TestResult.error("No player found for player kill test");
            }

            String entityName = TestMobTools.getEntityDisplayName(entityId);
            DropStatistics stats = new DropStatistics(entityId, entityName, condition.displayName);

            // Get entity type
            Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
            if (entityTypeOpt.isEmpty()) {
                return TestResult.error("Unknown entity: " + entityId);
            }
            EntityType<?> entityType = entityTypeOpt.get();

            BlockPos playerPos = player != null ? player.blockPosition() : BlockPos.ZERO;

            for (int i = 0; i < count; i++) {
                if (progressCallback != null && i % 10 == 0) {
                    progressCallback.accept("Testing " + (i + 1) + "/" + count + "...");
                }

                stats.startTest();

                // Spawn entity
                BlockPos spawnPos = playerPos.offset(3 + (i % 5) * 2, 0, 3 + (i / 5) * 2);
                Entity entity = entityType.create(level, null, spawnPos,
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND, false, false);

                if (entity == null) continue;

                entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                if (entity instanceof Mob mob) {
                    mob.setNoAi(true);
                    mob.setPersistenceRequired();
                }

                level.addFreshEntity(entity);

                // Collect drops when entity dies
                if (entity instanceof LivingEntity living) {
                    // Create damage source
                    DamageSource damageSource;
                    if (condition.isPlayerKill() && player != null) {
                        damageSource = level.damageSources().playerAttack(player);
                    } else {
                        damageSource = level.damageSources().generic();
                    }

                    // Get drops before killing (using loot table directly)
                    List<ItemStack> drops = generateEntityDrops(level, living, damageSource, condition.getLootingLevel());
                    stats.recordDrops(drops);

                    // Remove the entity
                    entity.remove(Entity.RemovalReason.KILLED);
                }
            }

            Isotope.LOGGER.info("Mob test complete: {} {} tested with {}",
                count, entityName, condition.displayName);

            return TestResult.success(stats);

        } catch (Exception e) {
            Isotope.LOGGER.error("Mob test failed: {}", e.getMessage());
            return TestResult.error("Test failed: " + e.getMessage());
        }
    }

    /**
     * Generate drops for an entity using its loot table.
     */
    private static List<ItemStack> generateEntityDrops(
        ServerLevel level,
        LivingEntity entity,
        DamageSource damageSource,
        int lootingLevel
    ) {
        List<ItemStack> drops = new ArrayList<>();

        try {
            ResourceLocation lootTableId = entity.getLootTable().orElse(null);
            if (lootTableId == null) return drops;

            LootTable lootTable = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, lootTableId));

            if (lootTable == LootTable.EMPTY) return drops;

            LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource);

            if (damageSource.getEntity() != null) {
                paramsBuilder.withParameter(LootContextParams.KILLER_ENTITY, damageSource.getEntity());
                if (damageSource.getEntity() instanceof ServerPlayer killer) {
                    paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
                }
            }

            // Add looting level
            paramsBuilder.withLuck(lootingLevel);

            LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
            lootTable.getRandomItems(params, drops::add);

        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to generate entity drops: {}", e.getMessage());
        }

        return drops;
    }

    /**
     * Run chest/container loot simulation.
     *
     * @param server Minecraft server
     * @param lootTableId Loot table to simulate
     * @param count Number of times to generate
     * @param progressCallback Called with progress updates
     * @return TestResult with statistics
     */
    public static TestResult runChestTest(
        MinecraftServer server,
        ResourceLocation lootTableId,
        int count,
        @Nullable Consumer<String> progressCallback
    ) {
        try {
            ServerLevel level = server.overworld();
            ServerPlayer player = getPlayer(server);

            if (level == null) {
                return TestResult.error("No overworld available");
            }

            String tableName = formatTableName(lootTableId);
            DropStatistics stats = new DropStatistics(lootTableId, tableName, "Chest Loot");

            // Get loot table
            LootTable lootTable = server.reloadableRegistries()
                .getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, lootTableId));

            if (lootTable == LootTable.EMPTY) {
                return TestResult.error("Loot table not found: " + lootTableId);
            }

            Vec3 origin = player != null ? player.position() : Vec3.ZERO;

            for (int i = 0; i < count; i++) {
                if (progressCallback != null && i % 10 == 0) {
                    progressCallback.accept("Generating " + (i + 1) + "/" + count + "...");
                }

                stats.startTest();

                // Generate loot
                List<ItemStack> drops = generateChestLoot(level, lootTable, origin);
                stats.recordDrops(drops);
            }

            Isotope.LOGGER.info("Chest test complete: {} generated {} times",
                lootTableId, count);

            return TestResult.success(stats);

        } catch (Exception e) {
            Isotope.LOGGER.error("Chest test failed: {}", e.getMessage());
            return TestResult.error("Test failed: " + e.getMessage());
        }
    }

    /**
     * Generate chest loot from a loot table.
     */
    private static List<ItemStack> generateChestLoot(ServerLevel level, LootTable lootTable, Vec3 origin) {
        List<ItemStack> drops = new ArrayList<>();

        try {
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withLuck(0)
                .create(LootContextParamSets.CHEST);

            lootTable.getRandomItems(params, drops::add);

        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to generate chest loot: {}", e.getMessage());
        }

        return drops;
    }

    /**
     * Generate loot and spawn items on the ground (for visual testing).
     */
    public static int spawnLootOnGround(
        MinecraftServer server,
        ResourceLocation lootTableId,
        int count
    ) {
        try {
            ServerLevel level = server.overworld();
            ServerPlayer player = getPlayer(server);

            if (level == null || player == null) return 0;

            LootTable lootTable = server.reloadableRegistries()
                .getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, lootTableId));

            if (lootTable == LootTable.EMPTY) return 0;

            int totalItems = 0;
            Vec3 spawnPos = player.position().add(3, 0, 3);

            for (int i = 0; i < count; i++) {
                List<ItemStack> drops = generateChestLoot(level, lootTable, spawnPos);

                for (ItemStack stack : drops) {
                    if (!stack.isEmpty()) {
                        // Spawn item entity
                        net.minecraft.world.entity.item.ItemEntity itemEntity =
                            new net.minecraft.world.entity.item.ItemEntity(
                                level,
                                spawnPos.x + (i % 5) * 0.5,
                                spawnPos.y + 1,
                                spawnPos.z + (i / 5) * 0.5,
                                stack.copy()
                            );
                        itemEntity.setDefaultPickUpDelay();
                        level.addFreshEntity(itemEntity);
                        totalItems += stack.getCount();
                    }
                }
            }

            return totalItems;

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to spawn loot: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Format loot table ID to display name.
     */
    private static String formatTableName(ResourceLocation tableId) {
        String path = tableId.getPath();
        // Remove common prefixes
        if (path.startsWith("chests/")) {
            path = path.substring("chests/".length());
        } else if (path.startsWith("entities/")) {
            path = path.substring("entities/".length());
        }

        // Convert to title case
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '/') {
                result.append(' ');
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    @Nullable
    private static ServerPlayer getPlayer(MinecraftServer server) {
        if (server == null) return null;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }
}
