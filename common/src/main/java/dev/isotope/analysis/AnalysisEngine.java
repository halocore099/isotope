package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Background analysis engine for ISOTOPE.
 * Performs loot table sampling and structure analysis without player interaction.
 */
public final class AnalysisEngine {

    private static final AnalysisEngine INSTANCE = new AnalysisEngine();

    // Analysis state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private int totalTasks = 0;
    private String currentTask = "";

    // Results
    private final Map<ResourceLocation, LootSampleResult> lootSamples = new ConcurrentHashMap<>();

    // Callbacks
    private Consumer<AnalysisProgress> progressCallback;
    private Consumer<AnalysisResult> completionCallback;

    private AnalysisEngine() {}

    public static AnalysisEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Start analysis in background.
     * Must be called from server thread or with server access.
     */
    public CompletableFuture<AnalysisResult> startAnalysis(
            MinecraftServer server,
            AnalysisConfig config,
            Consumer<AnalysisProgress> onProgress,
            Consumer<AnalysisResult> onComplete) {

        if (running.get()) {
            Isotope.LOGGER.warn("Analysis already in progress");
            return CompletableFuture.completedFuture(
                AnalysisResult.error("Analysis already in progress"));
        }

        this.progressCallback = onProgress;
        this.completionCallback = onComplete;
        this.running.set(true);
        this.cancelled.set(false);
        this.progress.set(0);
        this.lootSamples.clear();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return runAnalysis(server, config);
            } catch (Exception e) {
                Isotope.LOGGER.error("Analysis failed", e);
                return AnalysisResult.error("Analysis failed: " + e.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    private AnalysisResult runAnalysis(MinecraftServer server, AnalysisConfig config) {
        long startTime = System.currentTimeMillis();

        // Calculate total tasks
        Collection<StructureInfo> structures = StructureRegistry.getInstance().getAll();
        Collection<LootTableInfo> lootTables = config.analyzeAllLootTables
            ? LootTableRegistry.getInstance().getAll()
            : LootTableRegistry.getInstance().getByCategory(LootTableInfo.LootTableCategory.CHEST);

        totalTasks = structures.size() + lootTables.size();

        reportProgress("Starting analysis...", 0);

        // Phase 1: Ensure structure-loot links are built
        reportProgress("Building structure-loot links...", 0);
        StructureLootLinker.getInstance().buildLinks();

        if (cancelled.get()) return AnalysisResult.cancelled();

        // Phase 2: Sample loot tables
        reportProgress("Sampling loot tables...", 10);
        int lootProgress = 0;
        ServerLevel level = server.overworld();

        for (LootTableInfo tableInfo : lootTables) {
            if (cancelled.get()) return AnalysisResult.cancelled();

            currentTask = "Sampling: " + tableInfo.path();
            LootSampleResult sample = sampleLootTable(server, level, tableInfo, config.sampleCount);
            lootSamples.put(tableInfo.id(), sample);

            lootProgress++;
            int percent = 10 + (lootProgress * 80 / lootTables.size());
            reportProgress(currentTask, percent);
        }

        // Phase 3: Analyze loot contents (already done in M2, trigger lazy init)
        reportProgress("Analyzing loot table contents...", 90);
        LootContentsRegistry.getInstance().init(server);

        // Phase 4: Complete
        long elapsed = System.currentTimeMillis() - startTime;
        reportProgress("Analysis complete", 100);

        AnalysisResult result = new AnalysisResult(
            true,
            null,
            structures.size(),
            lootTables.size(),
            StructureLootLinker.getInstance().linkedStructureCount(),
            lootSamples.size(),
            elapsed
        );

        if (completionCallback != null) {
            completionCallback.accept(result);
        }

        Isotope.LOGGER.info("Analysis complete: {} structures, {} loot tables sampled in {}ms",
            structures.size(), lootSamples.size(), elapsed);

        return result;
    }

    /**
     * Sample a loot table multiple times to observe probability distribution.
     */
    private LootSampleResult sampleLootTable(MinecraftServer server, ServerLevel level,
                                              LootTableInfo tableInfo, int sampleCount) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tableInfo.id());
        LootTable table = server.reloadableRegistries().getLootTable(key);

        if (table == LootTable.EMPTY) {
            return LootSampleResult.empty(tableInfo.id());
        }

        Map<ResourceLocation, ItemSampleData> itemData = new HashMap<>();
        int totalItems = 0;

        try {
            // Create loot params for chest context
            LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO));

            LootParams params = paramsBuilder.create(LootContextParamSets.CHEST);

            // Sample multiple times
            for (int i = 0; i < sampleCount; i++) {
                List<ItemStack> items = table.getRandomItems(params);

                for (ItemStack stack : items) {
                    if (stack.isEmpty()) continue;

                    ResourceLocation itemId = stack.getItemHolder().unwrapKey()
                        .map(k -> k.location())
                        .orElse(null);

                    if (itemId != null) {
                        itemData.computeIfAbsent(itemId, k -> new ItemSampleData(itemId))
                            .addSample(stack.getCount());
                        totalItems++;
                    }
                }
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to sample loot table {}: {}", tableInfo.id(), e.getMessage());
            return LootSampleResult.error(tableInfo.id(), e.getMessage());
        }

        return new LootSampleResult(
            tableInfo.id(),
            sampleCount,
            totalItems,
            new ArrayList<>(itemData.values()),
            null
        );
    }

    public void cancel() {
        cancelled.set(true);
        Isotope.LOGGER.info("Analysis cancellation requested");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getProgressPercent() {
        return progress.get();
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public Optional<LootSampleResult> getSample(ResourceLocation tableId) {
        return Optional.ofNullable(lootSamples.get(tableId));
    }

    public Map<ResourceLocation, LootSampleResult> getAllSamples() {
        return Collections.unmodifiableMap(lootSamples);
    }

    private void reportProgress(String task, int percent) {
        this.currentTask = task;
        this.progress.set(percent);

        if (progressCallback != null) {
            progressCallback.accept(new AnalysisProgress(task, percent, totalTasks));
        }
    }

    // --- Inner classes ---

    public record AnalysisConfig(
        int sampleCount,
        boolean analyzeAllLootTables,
        boolean forceStructurePlacement  // For future Phase 2
    ) {
        public static AnalysisConfig defaultConfig() {
            return new AnalysisConfig(100, false, false);
        }

        public static AnalysisConfig thorough() {
            return new AnalysisConfig(1000, true, false);
        }
    }

    public record AnalysisProgress(
        String currentTask,
        int percentComplete,
        int totalTasks
    ) {}

    public record AnalysisResult(
        boolean success,
        String error,
        int structuresAnalyzed,
        int lootTablesAnalyzed,
        int structuresLinked,
        int lootTablesSampled,
        long elapsedMs
    ) {
        public static AnalysisResult error(String message) {
            return new AnalysisResult(false, message, 0, 0, 0, 0, 0);
        }

        public static AnalysisResult cancelled() {
            return new AnalysisResult(false, "Cancelled by user", 0, 0, 0, 0, 0);
        }
    }

    public record LootSampleResult(
        ResourceLocation tableId,
        int sampleCount,
        int totalItemsDropped,
        List<ItemSampleData> itemDistribution,
        String error
    ) {
        public static LootSampleResult empty(ResourceLocation tableId) {
            return new LootSampleResult(tableId, 0, 0, List.of(), null);
        }

        public static LootSampleResult error(ResourceLocation tableId, String error) {
            return new LootSampleResult(tableId, 0, 0, List.of(), error);
        }

        public boolean hasError() {
            return error != null;
        }

        public float averageItemsPerRoll() {
            return sampleCount > 0 ? (float) totalItemsDropped / sampleCount : 0;
        }
    }

    public static class ItemSampleData {
        private final ResourceLocation itemId;
        private int occurrences = 0;
        private int totalCount = 0;
        private int minCount = Integer.MAX_VALUE;
        private int maxCount = 0;

        public ItemSampleData(ResourceLocation itemId) {
            this.itemId = itemId;
        }

        public void addSample(int count) {
            occurrences++;
            totalCount += count;
            minCount = Math.min(minCount, count);
            maxCount = Math.max(maxCount, count);
        }

        public ResourceLocation getItemId() { return itemId; }
        public int getOccurrences() { return occurrences; }
        public int getTotalCount() { return totalCount; }
        public int getMinCount() { return minCount == Integer.MAX_VALUE ? 0 : minCount; }
        public int getMaxCount() { return maxCount; }

        public float getAverageCount() {
            return occurrences > 0 ? (float) totalCount / occurrences : 0;
        }

        public float getDropRate(int totalSamples) {
            return totalSamples > 0 ? (float) occurrences / totalSamples : 0;
        }
    }
}
