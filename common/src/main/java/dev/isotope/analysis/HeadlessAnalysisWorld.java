package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.observation.ObservationSession;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages a headless analysis world for runtime observation.
 *
 * The world exists purely to:
 * 1. Force structure generation
 * 2. Observe loot table invocations
 * 3. Build the ground-truth structure → loot relationship
 *
 * Player never sees or interacts with this world.
 */
public final class HeadlessAnalysisWorld {

    private static final HeadlessAnalysisWorld INSTANCE = new HeadlessAnalysisWorld();
    private static final String ANALYSIS_WORLD_NAME = "_isotope_analysis_temp";

    private final AtomicBoolean worldReady = new AtomicBoolean(false);
    private final AtomicBoolean analysisComplete = new AtomicBoolean(false);
    private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>(null);
    private Consumer<String> progressCallback;
    private Consumer<Boolean> completionCallback;

    private HeadlessAnalysisWorld() {}

    public static HeadlessAnalysisWorld getInstance() {
        return INSTANCE;
    }

    /**
     * Start the headless observation-based analysis from the main menu.
     */
    public void startAnalysis(
            Consumer<String> onProgress,
            Consumer<Boolean> onComplete) {

        this.progressCallback = onProgress;
        this.completionCallback = onComplete;
        this.worldReady.set(false);
        this.analysisComplete.set(false);

        reportProgress("Creating observation world...");

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

        reportProgress("Initializing observation world...");

        // Create world on the main thread
        minecraft.execute(() -> {
            try {
                // Configure world for maximum structure generation
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

                // Use normal world options - we need structures to generate
                WorldOptions worldOptions = new WorldOptions(
                    System.currentTimeMillis(), // Random seed
                    true,  // Generate structures - CRITICAL for observation
                    false  // Bonus chest
                );

                reportProgress("Loading observation world...");

                // Create the world - this will trigger SERVER_STARTED event
                minecraft.createWorldOpenFlows().createFreshLevel(
                    ANALYSIS_WORLD_NAME,
                    levelSettings,
                    worldOptions,
                    WorldPresets::createNormalWorldDimensions,
                    null
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
     * Triggered by RegistryScanner when it detects the analysis world.
     */
    public void onServerReady(MinecraftServer server) {
        if (!ANALYSIS_WORLD_NAME.equals(server.getWorldData().getLevelName())) {
            return;
        }

        Isotope.LOGGER.info("Observation world ready - starting observation session");
        this.serverRef.set(server);
        this.worldReady.set(true);

        reportProgress("Observation world ready - starting structure placement...");

        // Run observation session
        CompletableFuture.supplyAsync(() -> {
            try {
                return runObservationSession(server);
            } catch (Exception e) {
                Isotope.LOGGER.error("Observation session failed", e);
                return false;
            }
        }).thenAccept(success -> {
            analysisComplete.set(true);

            Minecraft.getInstance().execute(() -> {
                reportProgress(success ? "Observation complete!" : "Observation failed!");

                // Disconnect from the analysis world
                disconnectFromAnalysisWorld();

                // Schedule callback after disconnect
                CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        Minecraft.getInstance().execute(() -> {
                            if (completionCallback != null) {
                                completionCallback.accept(success);
                            }
                        });
                    });
            });
        });
    }

    /**
     * Run the observation session - place structures and observe loot.
     */
    private boolean runObservationSession(MinecraftServer server) {
        reportProgress("Starting observation session...");

        // Run the observation session
        ObservationSession.SessionResult result = ObservationSession.getInstance()
            .runSession(server, this::reportProgress);

        if (!result.success()) {
            reportProgress("ERROR: " + result.error());
            return false;
        }

        reportProgress(String.format(
            "Observation complete: %d structures placed, %d with loot, %d unique loot tables",
            result.structuresPlaced(),
            result.structuresWithLoot(),
            result.uniqueLootTables()
        ));

        // Log any failures
        if (!result.failedStructures().isEmpty()) {
            reportProgress(String.format("Warning: %d structures failed to place",
                result.failedStructures().size()));
        }

        return true;
    }

    private void disconnectFromAnalysisWorld() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            reportProgress("Disconnecting from observation world...");
            minecraft.disconnect();
        }

        serverRef.set(null);
    }

    private void cleanupAnalysisWorld() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Path savesDir = minecraft.gameDirectory.toPath().resolve("saves");
            Path analysisDir = savesDir.resolve(ANALYSIS_WORLD_NAME);

            if (Files.exists(analysisDir)) {
                Isotope.LOGGER.info("Cleaning up analysis world folder: {}", analysisDir);

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
        disconnectFromAnalysisWorld();
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
