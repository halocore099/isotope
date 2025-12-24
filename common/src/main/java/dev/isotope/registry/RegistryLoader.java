package dev.isotope.registry;

import dev.isotope.Isotope;
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
import java.util.function.Consumer;

/**
 * Loads registry data from main menu without requiring user interaction.
 *
 * Creates a minimal temporary world purely for registry access, scans
 * structures and loot tables, then tears down immediately.
 *
 * The user never sees or interacts with this world - it's invisible.
 */
public final class RegistryLoader {

    private static final RegistryLoader INSTANCE = new RegistryLoader();
    private static final String TEMP_WORLD_NAME = "_isotope_registry_temp";

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private Consumer<String> progressCallback;
    private Consumer<Boolean> completionCallback;

    private RegistryLoader() {}

    public static RegistryLoader getInstance() {
        return INSTANCE;
    }

    /**
     * Check if registries have already been loaded.
     */
    public boolean isLoaded() {
        return loaded.get() || RegistryScanner.isScanned();
    }

    /**
     * Check if loading is in progress.
     */
    public boolean isLoading() {
        return loading.get();
    }

    /**
     * Load registries from main menu.
     * Creates a minimal temp world, scans registries, tears down.
     */
    public void loadFromMainMenu(Consumer<String> onProgress, Consumer<Boolean> onComplete) {
        if (loading.get()) {
            Isotope.LOGGER.warn("Registry loading already in progress");
            return;
        }

        if (isLoaded()) {
            Isotope.LOGGER.info("Registries already loaded");
            onComplete.accept(true);
            return;
        }

        this.progressCallback = onProgress;
        this.completionCallback = onComplete;
        this.loading.set(true);

        reportProgress("Initializing registry scan...");

        // Create temp world on main thread
        Minecraft.getInstance().execute(this::createTempWorld);
    }

    private void createTempWorld() {
        try {
            // Clean up any existing temp world
            cleanupTempWorld();

            reportProgress("Creating minimal world for registry access...");

            Minecraft minecraft = Minecraft.getInstance();

            // Minimal world settings - we just need registry access
            GameRules gameRules = new GameRules(FeatureFlags.DEFAULT_FLAGS);

            LevelSettings levelSettings = new LevelSettings(
                TEMP_WORLD_NAME,
                GameType.SPECTATOR,
                false,
                Difficulty.PEACEFUL,
                false,
                gameRules,
                WorldDataConfiguration.DEFAULT
            );

            // Flat world with no structures - fastest to create
            WorldOptions worldOptions = new WorldOptions(
                0L,    // Fixed seed
                false, // No structures
                false  // No bonus chest
            );

            // Create the world - RegistryScanner.onServerStarted will be triggered
            minecraft.createWorldOpenFlows().createFreshLevel(
                TEMP_WORLD_NAME,
                levelSettings,
                worldOptions,
                WorldPresets::createNormalWorldDimensions,
                null
            );

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to create temp world for registry scan", e);
            reportProgress("ERROR: " + e.getMessage());
            loading.set(false);
            if (completionCallback != null) {
                completionCallback.accept(false);
            }
        }
    }

    /**
     * Called by RegistryScanner when the temp world starts.
     */
    public void onTempWorldReady(MinecraftServer server) {
        if (!TEMP_WORLD_NAME.equals(server.getWorldData().getLevelName())) {
            return;
        }

        Isotope.LOGGER.info("Temp world ready - registries scanned");
        reportProgress("Registry scan complete!");

        // Registries are already scanned by RegistryScanner.onServerStarted
        // Just need to disconnect and clean up

        loaded.set(true);
        loading.set(false);

        // Disconnect from temp world - must be done from render thread
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            reportProgress("Cleaning up...");

            // Stop the integrated server and disconnect
            if (minecraft.getSingleplayerServer() != null) {
                Isotope.LOGGER.info("Stopping integrated server...");
                minecraft.getSingleplayerServer().halt(false);
            }

            minecraft.disconnect();
            Isotope.LOGGER.info("Disconnected from temp world");

            // Wait for world to fully close, then cleanup and open MainScreen
            waitForDisconnectAndContinue(minecraft, 0);
        });
    }

    /**
     * Wait for world to close, then cleanup and trigger callback.
     */
    private void waitForDisconnectAndContinue(Minecraft minecraft, int attempts) {
        if (attempts > 50) { // Max 5 seconds
            Isotope.LOGGER.error("Timeout waiting for temp world to close");
            minecraft.execute(() -> {
                if (completionCallback != null) {
                    completionCallback.accept(true); // Still open MainScreen
                }
            });
            return;
        }

        // Check if world is still active
        if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            // Still closing, wait a bit more
            CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> waitForDisconnectAndContinue(minecraft, attempts + 1));
            return;
        }

        // World is closed, cleanup temp files and trigger callback
        Isotope.LOGGER.info("World closed after {} attempts, cleaning up", attempts);
        cleanupTempWorld();

        minecraft.execute(() -> {
            if (completionCallback != null) {
                Isotope.LOGGER.info("Opening MainScreen...");
                completionCallback.accept(true);
            }
        });
    }

    /**
     * Check if this is the temporary registry loading world.
     */
    public boolean isTempWorld(MinecraftServer server) {
        return TEMP_WORLD_NAME.equals(server.getWorldData().getLevelName());
    }

    private void cleanupTempWorld() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Path savesDir = minecraft.gameDirectory.toPath().resolve("saves");
            Path tempDir = savesDir.resolve(TEMP_WORLD_NAME);

            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            }
        } catch (IOException e) {
            Isotope.LOGGER.warn("Failed to cleanup temp world", e);
        }
    }

    private void reportProgress(String message) {
        Isotope.LOGGER.info("[RegistryLoader] {}", message);
        if (progressCallback != null) {
            Minecraft.getInstance().execute(() -> progressCallback.accept(message));
        }
    }

    /**
     * Reset state (for testing).
     */
    public void reset() {
        loading.set(false);
        loaded.set(false);
        StructureRegistry.getInstance().reset();
        LootTableRegistry.getInstance().reset();
        StructureLootLinker.getInstance().reset();
    }
}
