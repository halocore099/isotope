package dev.isotope.analysis;

import dev.isotope.Isotope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages a headless analysis world that runs in the background.
 * The player never sees or enters this world - it exists purely for
 * registry access and loot table observation.
 */
public final class HeadlessAnalysisWorld {

    private static final HeadlessAnalysisWorld INSTANCE = new HeadlessAnalysisWorld();
    private static final String ANALYSIS_WORLD_NAME = "_isotope_analysis_temp";

    private final AtomicBoolean worldReady = new AtomicBoolean(false);
    private final AtomicBoolean analysisComplete = new AtomicBoolean(false);
    private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>(null);
    private Consumer<String> progressCallback;
    private Consumer<Boolean> completionCallback;
    private AnalysisEngine.AnalysisConfig analysisConfig;

    private HeadlessAnalysisWorld() {}

    public static HeadlessAnalysisWorld getInstance() {
        return INSTANCE;
    }

    /**
     * Start the headless analysis from the main menu.
     * Creates a temporary world, runs analysis, then cleans up.
     */
    public void startAnalysis(
            AnalysisEngine.AnalysisConfig config,
            Consumer<String> onProgress,
            Consumer<Boolean> onComplete) {

        this.progressCallback = onProgress;
        this.completionCallback = onComplete;
        this.analysisConfig = config;
        this.worldReady.set(false);
        this.analysisComplete.set(false);

        reportProgress("Creating temporary analysis world...");

        // Create the temporary world in background
        CompletableFuture.runAsync(() -> {
            try {
                createAndLoadAnalysisWorld();
            } catch (Exception e) {
                Isotope.LOGGER.error("Failed to create analysis world", e);
                reportProgress("ERROR: " + e.getMessage());
                if (completionCallback != null) {
                    Minecraft.getInstance().execute(() -> completionCallback.accept(false));
                }
            }
        });
    }

    private void createAndLoadAnalysisWorld() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();

        // Clean up any existing analysis world
        cleanupAnalysisWorld();

        reportProgress("Initializing world data...");

        // Create world on the main thread
        minecraft.execute(() -> {
            try {
                // Configure minimal world settings for analysis
                // Use default GameRules with all vanilla feature flags
                GameRules gameRules = new GameRules(FeatureFlags.DEFAULT_FLAGS);

                LevelSettings levelSettings = new LevelSettings(
                    ANALYSIS_WORLD_NAME,
                    GameType.SPECTATOR,
                    false, // hardcore
                    Difficulty.PEACEFUL,
                    true, // commands
                    gameRules,
                    WorldDataConfiguration.DEFAULT
                );

                // Use a flat preset for minimal world generation
                WorldOptions worldOptions = new WorldOptions(
                    0L, // seed (doesn't matter for analysis)
                    false, // generate structures - we'll place manually
                    false // bonus chest
                );

                reportProgress("Loading analysis world...");

                // Create the world - this will trigger SERVER_STARTED event
                // Pass world name directly - createFreshLevel handles level access internally
                minecraft.createWorldOpenFlows().createFreshLevel(
                    ANALYSIS_WORLD_NAME,
                    levelSettings,
                    worldOptions,
                    WorldPresets::createNormalWorldDimensions,
                    null  // No parent screen for headless operation
                );

            } catch (Exception e) {
                Isotope.LOGGER.error("Failed to create analysis world", e);
                reportProgress("ERROR: Failed to create world - " + e.getMessage());
                if (completionCallback != null) {
                    completionCallback.accept(false);
                }
            }
        });
    }

    /**
     * Called when the analysis server has started.
     * This is triggered by RegistryScanner when it detects the analysis world.
     */
    public void onServerReady(MinecraftServer server) {
        if (!ANALYSIS_WORLD_NAME.equals(server.getWorldData().getLevelName())) {
            return; // Not our analysis world
        }

        Isotope.LOGGER.info("Analysis world server ready - starting analysis");
        this.serverRef.set(server);
        this.worldReady.set(true);

        reportProgress("Analysis world ready - scanning registries...");

        // Run analysis immediately
        CompletableFuture.supplyAsync(() -> {
            try {
                return runAnalysis(server);
            } catch (Exception e) {
                Isotope.LOGGER.error("Analysis failed", e);
                return false;
            }
        }).thenAccept(success -> {
            analysisComplete.set(true);

            // Notify completion callback first (so UI can update)
            Minecraft.getInstance().execute(() -> {
                reportProgress(success ? "Analysis complete!" : "Analysis failed!");

                if (completionCallback != null) {
                    completionCallback.accept(success);
                }

                // Stop the server but don't disconnect - let the UI handle screen transitions
                // The analysis data is already preserved in the registries
                stopAnalysisServer();
            });
        });
    }

    private boolean runAnalysis(MinecraftServer server) {
        reportProgress("Scanning structure registry...");

        // Scan registries
        dev.isotope.registry.StructureRegistry.getInstance().scan(server);

        reportProgress("Scanning loot table registry...");
        dev.isotope.registry.LootTableRegistry.getInstance().scan(server);

        reportProgress("Building structure-loot links...");
        StructureLootLinker.getInstance().buildLinks();

        reportProgress("Initializing loot contents registry...");
        LootContentsRegistry.getInstance().init(server);

        // Run loot table sampling
        ServerLevel level = server.overworld();
        var lootTables = analysisConfig.analyzeAllLootTables()
            ? dev.isotope.registry.LootTableRegistry.getInstance().getAll()
            : dev.isotope.registry.LootTableRegistry.getInstance()
                .getByCategory(dev.isotope.data.LootTableInfo.LootTableCategory.CHEST);

        reportProgress("Sampling " + lootTables.size() + " loot tables...");

        int sampled = 0;
        for (var tableInfo : lootTables) {
            if (analysisComplete.get()) break; // Cancelled

            AnalysisEngine.getInstance().sampleSingleTable(server, level, tableInfo, analysisConfig.sampleCount());
            sampled++;

            if (sampled % 50 == 0) {
                reportProgress("Sampled " + sampled + "/" + lootTables.size() + " tables...");
            }
        }

        reportProgress("Analysis complete: " + sampled + " loot tables sampled");
        return true;
    }

    /**
     * Stop the analysis server without disconnecting.
     * This allows the UI (AnalysisProgressScreen) to remain visible
     * while the user reviews results and clicks Continue.
     */
    private void stopAnalysisServer() {
        MinecraftServer server = serverRef.get();

        if (server != null && server.isRunning()) {
            reportProgress("Closing analysis world...");

            // Stop the server gracefully - this triggers SERVER_STOPPING event
            // which will preserve registry data (see RegistryScanner)
            server.halt(false);
        }

        // Clean up the temp world folder after a delay
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); // Wait for server to fully stop
                cleanupAnalysisWorld();
            } catch (Exception e) {
                Isotope.LOGGER.warn("Failed to cleanup analysis world", e);
            }
        });
    }

    /**
     * Close analysis world and return to main menu.
     * Used by cancel() to abort analysis.
     */
    private void closeAnalysisWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = serverRef.get();

        if (server != null && server.isRunning()) {
            reportProgress("Closing analysis world...");
            server.halt(false);
        }

        // Return to main menu
        minecraft.disconnect();

        // Clean up the temp world folder after a delay
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); // Wait for server to fully stop
                cleanupAnalysisWorld();
            } catch (Exception e) {
                Isotope.LOGGER.warn("Failed to cleanup analysis world", e);
            }
        });
    }

    private void cleanupAnalysisWorld() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Path savesDir = minecraft.gameDirectory.toPath().resolve("saves");
            Path analysisDir = savesDir.resolve(ANALYSIS_WORLD_NAME);

            if (Files.exists(analysisDir)) {
                Isotope.LOGGER.info("Cleaning up analysis world folder: {}", analysisDir);

                // Delete recursively
                Files.walk(analysisDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            Isotope.LOGGER.warn("Failed to delete: {}", path);
                        }
                    });
            }
        } catch (IOException e) {
            Isotope.LOGGER.warn("Failed to cleanup analysis world", e);
        }
    }

    /**
     * Check if this is the analysis world.
     */
    public boolean isAnalysisWorld(MinecraftServer server) {
        return ANALYSIS_WORLD_NAME.equals(server.getWorldData().getLevelName());
    }

    public boolean isWorldReady() {
        return worldReady.get();
    }

    public boolean isAnalysisComplete() {
        return analysisComplete.get();
    }

    public void cancel() {
        analysisComplete.set(true);
        closeAnalysisWorld();
    }

    private void reportProgress(String message) {
        Isotope.LOGGER.info("[HeadlessAnalysis] {}", message);
        if (progressCallback != null) {
            Minecraft.getInstance().execute(() -> progressCallback.accept(message));
        }
    }

    public static String getAnalysisWorldName() {
        return ANALYSIS_WORLD_NAME;
    }
}
