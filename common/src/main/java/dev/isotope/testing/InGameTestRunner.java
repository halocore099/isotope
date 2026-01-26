package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.compat.Id;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import dev.isotope.data.loot.*;
import dev.isotope.editing.*;
import dev.isotope.registry.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Automated in-game test runner for ISOTOPE functionality.
 *
 * Runs a suite of tests that verify core features work correctly
 * when running inside Minecraft. This enables automated testing
 * across multiple Minecraft versions.
 */
public final class InGameTestRunner {

    private InGameTestRunner() {}

    /**
     * Result of a single test.
     */
    public record TestResult(
        String testName,
        boolean passed,
        @Nullable String message,
        long durationMs
    ) {
        public static TestResult pass(String name, long duration) {
            return new TestResult(name, true, null, duration);
        }

        public static TestResult pass(String name, String message, long duration) {
            return new TestResult(name, true, message, duration);
        }

        public static TestResult fail(String name, String reason, long duration) {
            return new TestResult(name, false, reason, duration);
        }
    }

    /**
     * Result of a complete test run.
     */
    public record TestReport(
        List<TestResult> results,
        int passed,
        int failed,
        long totalDurationMs
    ) {
        public boolean allPassed() {
            return failed == 0;
        }

        public String summary() {
            return String.format("%d/%d tests passed (%.2fs)",
                passed, passed + failed, totalDurationMs / 1000.0);
        }
    }

    /**
     * Run all automated tests.
     *
     * @param server Minecraft server instance
     * @param player Player to receive feedback (optional)
     * @param progressCallback Called with progress updates
     * @return TestReport with all results
     */
    public static TestReport runAllTests(
        MinecraftServer server,
        @Nullable ServerPlayer player,
        @Nullable Consumer<String> progressCallback
    ) {
        List<TestResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        sendProgress(player, progressCallback, "Starting ISOTOPE automated tests...");

        // Registry Tests
        sendProgress(player, progressCallback, "Running registry tests...");
        results.add(testLootTableRegistryScan(server));
        results.add(testEntityLootRegistry(server));
        results.add(testFeatureRegistry());

        // Parser Tests
        sendProgress(player, progressCallback, "Running parser tests...");
        results.add(testLootTableParsing(server));

        // Editor Tests
        sendProgress(player, progressCallback, "Running editor tests...");
        results.add(testClipboardManager());
        results.add(testHistoryLog());
        results.add(testLootEditOperations());

        // Loot Generation Tests
        sendProgress(player, progressCallback, "Running loot generation tests...");
        results.add(testChestLootGeneration(server));
        results.add(testMobLootGeneration(server));

        // Edit Application Tests
        sendProgress(player, progressCallback, "Running edit application tests...");
        results.add(testEditApplication(server));

        long totalDuration = System.currentTimeMillis() - startTime;

        int passed = (int) results.stream().filter(TestResult::passed).count();
        int failed = results.size() - passed;

        TestReport report = new TestReport(results, passed, failed, totalDuration);

        sendProgress(player, progressCallback, "Tests complete: " + report.summary());

        return report;
    }

    /**
     * Run only registry tests.
     */
    public static TestReport runRegistryTests(MinecraftServer server, @Nullable Consumer<String> progressCallback) {
        List<TestResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        results.add(testLootTableRegistryScan(server));
        results.add(testEntityLootRegistry(server));
        results.add(testFeatureRegistry());

        return buildReport(results, startTime);
    }

    /**
     * Run only loot generation tests.
     */
    public static TestReport runLootTests(MinecraftServer server, @Nullable Consumer<String> progressCallback) {
        List<TestResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        results.add(testChestLootGeneration(server));
        results.add(testMobLootGeneration(server));

        return buildReport(results, startTime);
    }

    /**
     * Run only editor tests.
     */
    public static TestReport runEditorTests(@Nullable Consumer<String> progressCallback) {
        List<TestResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        results.add(testClipboardManager());
        results.add(testHistoryLog());
        results.add(testLootEditOperations());

        return buildReport(results, startTime);
    }

    // ===== Individual Tests =====

    private static TestResult testLootTableRegistryScan(MinecraftServer server) {
        long start = System.currentTimeMillis();
        String testName = "LootTableRegistry.scan()";

        try {
            LootTableRegistry registry = LootTableRegistry.getInstance();
            registry.scan(server);

            int total = registry.getAll().size();
            if (total == 0) {
                return TestResult.fail(testName, "No loot tables found", duration(start));
            }

            // Verify some vanilla tables exist
            boolean hasChests = registry.getByCategory(LootTableCategory.CHEST).size() > 0;
            boolean hasEntities = registry.getByCategory(LootTableCategory.ENTITY).size() > 0;

            if (!hasChests) {
                return TestResult.fail(testName, "No chest loot tables found", duration(start));
            }
            if (!hasEntities) {
                return TestResult.fail(testName, "No entity loot tables found", duration(start));
            }

            return TestResult.pass(testName, "Found " + total + " loot tables", duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testEntityLootRegistry(MinecraftServer server) {
        long start = System.currentTimeMillis();
        String testName = "EntityLootRegistry.initialize()";

        try {
            // Ensure LootTableRegistry is populated first
            LootTableRegistry.getInstance().scan(server);

            EntityLootRegistry registry = EntityLootRegistry.getInstance();
            registry.initialize();

            int count = registry.getAllAsLootSources().size();
            if (count == 0) {
                return TestResult.fail(testName, "No entity loot mappings found", duration(start));
            }

            // Verify zombie exists
            Id zombieTable = Id.of("minecraft", "entities/zombie");
            if (!registry.isEntityLootTable(zombieTable)) {
                return TestResult.fail(testName, "Zombie loot table not recognized", duration(start));
            }

            return TestResult.pass(testName, "Found " + count + " entity loot mappings", duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testFeatureRegistry() {
        long start = System.currentTimeMillis();
        String testName = "FeatureRegistry.initialize()";

        try {
            FeatureRegistry registry = FeatureRegistry.getInstance();
            registry.initialize();

            int count = registry.getAllAsLootSources().size();
            if (count == 0) {
                return TestResult.fail(testName, "No feature mappings found", duration(start));
            }

            // Verify dungeon feature exists
            var dungeonOpt = registry.get(Id.of("minecraft", "monster_room"));
            if (dungeonOpt.isEmpty()) {
                return TestResult.fail(testName, "Dungeon (monster_room) feature not found", duration(start));
            }

            return TestResult.pass(testName, "Found " + count + " feature mappings", duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testLootTableParsing(MinecraftServer server) {
        long start = System.currentTimeMillis();
        String testName = "LootTableParser.parse()";

        try {
            Id tableId = Id.of("minecraft", "chests/simple_dungeon");

            Optional<LootTableStructure> result = LootTableParser.parse(server, tableId);

            if (result.isEmpty()) {
                return TestResult.fail(testName, "Failed to parse simple_dungeon", duration(start));
            }

            LootTableStructure table = result.get();

            if (table.pools().isEmpty()) {
                return TestResult.fail(testName, "Parsed table has no pools", duration(start));
            }

            if (table.pools().get(0).entries().isEmpty()) {
                return TestResult.fail(testName, "Parsed pool has no entries", duration(start));
            }

            return TestResult.pass(testName, "Parsed " + table.pools().size() + " pools", duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testClipboardManager() {
        long start = System.currentTimeMillis();
        String testName = "ClipboardManager operations";

        try {
            ClipboardManager clipboard = ClipboardManager.getInstance();
            clipboard.clear();

            // Test entry copy
            LootEntry testEntry = LootEntry.item(Id.of("minecraft", "diamond"), 5);
            Id sourceTable = Id.of("minecraft", "test");

            clipboard.copyEntry(testEntry, sourceTable);

            if (!clipboard.hasEntry()) {
                return TestResult.fail(testName, "Entry not stored after copy", duration(start));
            }

            if (clipboard.getEntry().isEmpty()) {
                return TestResult.fail(testName, "getEntry() returned empty", duration(start));
            }

            // Test pool copy clears entry
            LootPool testPool = LootPool.simple(1, List.of(testEntry));
            clipboard.copyPool(testPool, sourceTable);

            if (!clipboard.hasPool()) {
                return TestResult.fail(testName, "Pool not stored after copy", duration(start));
            }
            if (clipboard.hasEntry()) {
                return TestResult.fail(testName, "Entry not cleared after pool copy", duration(start));
            }

            // Test clear
            clipboard.clear();
            if (clipboard.hasContent()) {
                return TestResult.fail(testName, "Content not cleared", duration(start));
            }

            return TestResult.pass(testName, duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testHistoryLog() {
        long start = System.currentTimeMillis();
        String testName = "HistoryLog operations";

        try {
            HistoryLog history = HistoryLog.getInstance();
            history.clear();

            Id tableId = Id.of("minecraft", "test");
            LootEditOperation op = new LootEditOperation.ModifyEntryWeight(0, 0, 10);

            history.log(tableId, op);

            if (history.getCount() != 1) {
                return TestResult.fail(testName, "Log count incorrect", duration(start));
            }

            List<HistoryLog.LogEntry> entries = history.getAll();
            if (entries.isEmpty()) {
                return TestResult.fail(testName, "getAll() returned empty", duration(start));
            }

            if (!entries.get(0).tableId().equals(tableId)) {
                return TestResult.fail(testName, "Table ID not preserved", duration(start));
            }

            history.clear();
            if (history.getCount() != 0) {
                return TestResult.fail(testName, "Clear did not reset count", duration(start));
            }

            return TestResult.pass(testName, duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testLootEditOperations() {
        long start = System.currentTimeMillis();
        String testName = "LootEditOperation types";

        try {
            Id tableId = Id.of("minecraft", "test");
            LootTableEdit edit = LootTableEdit.create(tableId);

            // Test adding operations
            var op1 = new LootEditOperation.ModifyEntryWeight(0, 0, 10);
            var op2 = new LootEditOperation.RemovePool(0);

            edit = edit.withOperation(op1);
            if (edit.getOperationCount() != 1) {
                return TestResult.fail(testName, "Operation not added", duration(start));
            }

            edit = edit.withOperation(op2);
            if (edit.getOperationCount() != 2) {
                return TestResult.fail(testName, "Second operation not added", duration(start));
            }

            // Test undo
            edit = edit.undoTo(1);
            if (edit.getOperationCount() != 1) {
                return TestResult.fail(testName, "undoTo() failed", duration(start));
            }

            // Test descriptions
            List<String> descriptions = edit.getOperationDescriptions();
            if (descriptions.isEmpty()) {
                return TestResult.fail(testName, "No operation descriptions", duration(start));
            }

            return TestResult.pass(testName, duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testChestLootGeneration(MinecraftServer server) {
        long start = System.currentTimeMillis();
        String testName = "Chest loot generation";

        try {
            Id tableId = Id.of("minecraft", "chests/simple_dungeon");

            var result = LootTestRunner.runChestTest(server, tableId, 10, 0, null);

            if (!result.success()) {
                return TestResult.fail(testName, result.error(), duration(start));
            }

            DropStatistics stats = result.statistics();
            if (stats.getTestCount() != 10) {
                return TestResult.fail(testName, "Wrong test count: " + stats.getTestCount(), duration(start));
            }

            int totalDrops = stats.getTotalDrops();
            if (totalDrops == 0) {
                return TestResult.fail(testName, "No items generated", duration(start));
            }

            return TestResult.pass(testName, "Generated " + totalDrops + " items in 10 tests", duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testMobLootGeneration(MinecraftServer server) {
        long start = System.currentTimeMillis();
        String testName = "Mob loot generation";

        try {
            Id entityId = Id.of("minecraft", "zombie");

            var result = LootTestRunner.runMobTest(
                server, entityId, 5,
                TestMobTools.KillCondition.PLAYER_KILL, 0, null
            );

            if (!result.success()) {
                return TestResult.fail(testName, result.error(), duration(start));
            }

            DropStatistics stats = result.statistics();
            if (stats.getTestCount() != 5) {
                return TestResult.fail(testName, "Wrong test count: " + stats.getTestCount(), duration(start));
            }

            // Zombies should drop rotten flesh
            return TestResult.pass(testName, "Completed " + stats.getTestCount() + " mob kills", duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    private static TestResult testEditApplication(MinecraftServer server) {
        long start = System.currentTimeMillis();
        String testName = "Edit application";

        try {
            LootEditManager editManager = LootEditManager.getInstance();
            Id tableId = Id.of("minecraft", "chests/simple_dungeon");

            // Clear any existing edits
            editManager.clearEdits(tableId);

            // Create an edit
            var op = new LootEditOperation.ModifyEntryWeight(0, 0, 999);
            editManager.applyOperation(tableId, op);

            if (!editManager.hasEdits(tableId)) {
                return TestResult.fail(testName, "Edit not recorded", duration(start));
            }

            // Verify we can get the edit
            LootTableEdit edit = editManager.getEdit(tableId);
            if (edit == null) {
                return TestResult.fail(testName, "getEdit() returned null", duration(start));
            }

            if (edit.getOperationCount() != 1) {
                return TestResult.fail(testName, "Wrong operation count", duration(start));
            }

            // Clean up
            editManager.clearEdits(tableId);

            return TestResult.pass(testName, duration(start));

        } catch (Exception e) {
            Isotope.LOGGER.error("Test failed: {}", testName, e);
            return TestResult.fail(testName, e.getMessage(), duration(start));
        }
    }

    // ===== Helper Methods =====

    private static long duration(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    private static TestReport buildReport(List<TestResult> results, long startTime) {
        long totalDuration = System.currentTimeMillis() - startTime;
        int passed = (int) results.stream().filter(TestResult::passed).count();
        int failed = results.size() - passed;
        return new TestReport(results, passed, failed, totalDuration);
    }

    private static void sendProgress(@Nullable ServerPlayer player,
                                      @Nullable Consumer<String> callback,
                                      String message) {
        if (callback != null) {
            callback.accept(message);
        }
        if (player != null) {
            player.sendSystemMessage(Component.literal("[ISOTOPE Test] " + message));
        }
        Isotope.LOGGER.info("[Test] {}", message);
    }
}
