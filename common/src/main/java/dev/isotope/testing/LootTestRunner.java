package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.editing.LootEditManager;
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
import net.minecraft.world.entity.MobSpawnType;
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
     * Result of a comparison test.
     */
    public record CompareResult(
        boolean success,
        DropStatistics originalStats,
        DropStatistics editedStats,
        @Nullable String error
    ) {
        public static CompareResult success(DropStatistics original, DropStatistics edited) {
            return new CompareResult(true, original, edited, null);
        }

        public static CompareResult error(String message) {
            return new CompareResult(false, null, null, message);
        }
    }

    /**
     * Run mob loot tests with drop collection.
     *
     * @param server Minecraft server
     * @param entityId Entity type to test
     * @param count Number of mobs to spawn and kill
     * @param condition Kill condition (player kill or non-player)
     * @param lootingLevel Looting enchant level (0-3), only applies to player kills
     * @param progressCallback Called with progress updates
     * @return TestResult with statistics
     */
    public static TestResult runMobTest(
        MinecraftServer server,
        ResourceLocation entityId,
        int count,
        TestMobTools.KillCondition condition,
        int lootingLevel,
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
            String conditionText = condition.displayName;
            if (lootingLevel > 0 && condition.isPlayerKill()) {
                conditionText += " (Looting " + lootingLevel + ")";
            }
            DropStatistics stats = new DropStatistics(entityId, entityName, conditionText);

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

                // Spawn entity (1.21.1: uses MobSpawnType instead of EntitySpawnReason)
                BlockPos spawnPos = playerPos.offset(3 + (i % 5) * 2, 0, 3 + (i / 5) * 2);
                Entity entity = entityType.create(level, null, spawnPos,
                    MobSpawnType.COMMAND, false, false);

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
                    // Looting only applies to player kills
                    int effectiveLooting = condition.isPlayerKill() ? lootingLevel : 0;
                    List<ItemStack> drops = generateEntityDrops(level, living, damageSource, effectiveLooting);
                    stats.recordDrops(drops);

                    // Remove the entity
                    entity.remove(Entity.RemovalReason.KILLED);
                }
            }

            Isotope.LOGGER.info("Mob test complete: {} {} tested with {}",
                count, entityName, conditionText);

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
            // In 1.21.1, getLootTable() returns ResourceKey directly (not Optional)
            var lootTableKey = entity.getLootTable();
            if (lootTableKey == null) return drops;

            LootTable lootTable = level.getServer().reloadableRegistries()
                .getLootTable(lootTableKey);

            if (lootTable == LootTable.EMPTY) return drops;

            LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource);

            if (damageSource.getEntity() != null) {
                paramsBuilder.withParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity());
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
     * @param luck Luck value (0-5, affects quality-weighted entries)
     * @param progressCallback Called with progress updates
     * @return TestResult with statistics
     */
    public static TestResult runChestTest(
        MinecraftServer server,
        ResourceLocation lootTableId,
        int count,
        int luck,
        @Nullable Consumer<String> progressCallback
    ) {
        try {
            ServerLevel level = server.overworld();
            ServerPlayer player = getPlayer(server);

            if (level == null) {
                return TestResult.error("No overworld available");
            }

            String tableName = formatTableName(lootTableId);
            String condition = luck > 0 ? "Chest Loot (Luck " + luck + ")" : "Chest Loot";
            DropStatistics stats = new DropStatistics(lootTableId, tableName, condition);

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
                List<ItemStack> drops = generateChestLoot(level, lootTable, origin, luck);
                stats.recordDrops(drops);
            }

            Isotope.LOGGER.info("Chest test complete: {} generated {} times (luck {})",
                lootTableId, count, luck);

            return TestResult.success(stats);

        } catch (Exception e) {
            Isotope.LOGGER.error("Chest test failed: {}", e.getMessage());
            return TestResult.error("Test failed: " + e.getMessage());
        }
    }

    /**
     * Generate chest loot from a loot table (with default luck of 0).
     */
    private static List<ItemStack> generateChestLoot(ServerLevel level, LootTable lootTable, Vec3 origin) {
        return generateChestLoot(level, lootTable, origin, 0);
    }

    /**
     * Generate chest loot from a loot table with specified luck.
     */
    private static List<ItemStack> generateChestLoot(ServerLevel level, LootTable lootTable, Vec3 origin, int luck) {
        List<ItemStack> drops = new ArrayList<>();

        try {
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withLuck(luck)
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
     * Generate loot and add directly to player inventory.
     *
     * @param server Minecraft server
     * @param lootTableId Loot table to generate
     * @param count Number of times to generate
     * @return Number of items collected
     */
    public static int collectLootToInventory(
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
            Vec3 origin = player.position();

            for (int i = 0; i < count; i++) {
                List<ItemStack> drops = generateChestLoot(level, lootTable, origin);

                for (ItemStack stack : drops) {
                    if (!stack.isEmpty()) {
                        // Add to player inventory
                        if (player.getInventory().add(stack.copy())) {
                            totalItems += stack.getCount();
                        } else {
                            // Inventory full - drop on ground
                            player.drop(stack.copy(), false);
                            totalItems += stack.getCount();
                        }
                    }
                }
            }

            return totalItems;

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to collect loot: {}", e.getMessage());
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

    /**
     * Run comparison test for chest loot (original vs edited).
     *
     * @param server Minecraft server
     * @param lootTableId Loot table to compare
     * @param count Number of times to generate for each version
     * @param luck Luck value (0-5)
     * @param progressCallback Called with progress updates
     * @return CompareResult with both statistics
     */
    public static CompareResult runChestCompare(
        MinecraftServer server,
        ResourceLocation lootTableId,
        int count,
        int luck,
        @Nullable Consumer<String> progressCallback
    ) {
        try {
            LootEditManager editManager = LootEditManager.getInstance();

            // Check if table has edits
            if (!editManager.hasEdits(lootTableId)) {
                return CompareResult.error("No edits to compare for this table");
            }

            // Run test with edited version (current state)
            if (progressCallback != null) {
                progressCallback.accept("Testing edited version...");
            }
            TestResult editedResult = runChestTest(server, lootTableId, count, luck, null);
            if (!editedResult.success()) {
                return CompareResult.error("Edited test failed: " + editedResult.error());
            }

            // Temporarily disable test mode to get original results
            boolean wasTestModeActive = editManager.isTestModeActive();
            editManager.setTestModeActive(false);

            try {
                if (progressCallback != null) {
                    progressCallback.accept("Testing original version...");
                }
                TestResult originalResult = runChestTest(server, lootTableId, count, luck, null);
                if (!originalResult.success()) {
                    return CompareResult.error("Original test failed: " + originalResult.error());
                }

                // Rename the statistics for clarity
                DropStatistics originalStats = new DropStatistics(lootTableId,
                    originalResult.statistics().getSourceName(), "Original");
                copyStats(originalResult.statistics(), originalStats);

                DropStatistics editedStats = new DropStatistics(lootTableId,
                    editedResult.statistics().getSourceName(), "Edited");
                copyStats(editedResult.statistics(), editedStats);

                return CompareResult.success(originalStats, editedStats);

            } finally {
                // Restore test mode
                editManager.setTestModeActive(wasTestModeActive);
            }

        } catch (Exception e) {
            Isotope.LOGGER.error("Compare test failed: {}", e.getMessage());
            return CompareResult.error("Compare failed: " + e.getMessage());
        }
    }

    /**
     * Run comparison test for mob loot (original vs edited).
     *
     * @param server Minecraft server
     * @param entityId Entity type to compare
     * @param count Number of mobs to test for each version
     * @param condition Kill condition
     * @param lootingLevel Looting enchant level (0-3)
     * @param progressCallback Called with progress updates
     * @return CompareResult with both statistics
     */
    public static CompareResult runMobCompare(
        MinecraftServer server,
        ResourceLocation entityId,
        int count,
        TestMobTools.KillCondition condition,
        int lootingLevel,
        @Nullable Consumer<String> progressCallback
    ) {
        try {
            LootEditManager editManager = LootEditManager.getInstance();

            // Get the loot table ID for this entity
            ResourceLocation lootTableId = ResourceLocation.fromNamespaceAndPath(
                entityId.getNamespace(), "entities/" + entityId.getPath());

            // Check if table has edits
            if (!editManager.hasEdits(lootTableId)) {
                return CompareResult.error("No edits to compare for this entity's loot table");
            }

            // Build condition text for display
            String conditionText = condition.displayName;
            if (lootingLevel > 0 && condition.isPlayerKill()) {
                conditionText += " + Looting " + lootingLevel;
            }

            // Run test with edited version (current state)
            if (progressCallback != null) {
                progressCallback.accept("Testing edited version...");
            }
            TestResult editedResult = runMobTest(server, entityId, count, condition, lootingLevel, null);
            if (!editedResult.success()) {
                return CompareResult.error("Edited test failed: " + editedResult.error());
            }

            // Temporarily disable test mode to get original results
            boolean wasTestModeActive = editManager.isTestModeActive();
            editManager.setTestModeActive(false);

            try {
                if (progressCallback != null) {
                    progressCallback.accept("Testing original version...");
                }
                TestResult originalResult = runMobTest(server, entityId, count, condition, lootingLevel, null);
                if (!originalResult.success()) {
                    return CompareResult.error("Original test failed: " + originalResult.error());
                }

                // Rename the statistics for clarity
                DropStatistics originalStats = new DropStatistics(entityId,
                    originalResult.statistics().getSourceName(), "Original (" + conditionText + ")");
                copyStats(originalResult.statistics(), originalStats);

                DropStatistics editedStats = new DropStatistics(entityId,
                    editedResult.statistics().getSourceName(), "Edited (" + conditionText + ")");
                copyStats(editedResult.statistics(), editedStats);

                return CompareResult.success(originalStats, editedStats);

            } finally {
                // Restore test mode
                editManager.setTestModeActive(wasTestModeActive);
            }

        } catch (Exception e) {
            Isotope.LOGGER.error("Compare test failed: {}", e.getMessage());
            return CompareResult.error("Compare failed: " + e.getMessage());
        }
    }

    /**
     * Copy statistics from source to destination.
     */
    private static void copyStats(DropStatistics source, DropStatistics dest) {
        for (int i = 0; i < source.getTestCount(); i++) {
            dest.startTest();
        }
        for (ResourceLocation itemId : source.getDroppedItems()) {
            // Create fake stacks to record the totals
            int total = source.getTotalForItem(itemId);
            if (total > 0) {
                var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
                if (itemOpt.isPresent()) {
                    dest.recordDrop(new ItemStack(itemOpt.get(), total));
                }
            }
        }
    }

    @Nullable
    private static ServerPlayer getPlayer(MinecraftServer server) {
        if (server == null) return null;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }
}
