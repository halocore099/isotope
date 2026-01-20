package dev.isotope.registry;

import dev.isotope.Isotope;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;

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

            // Create GameRules - version specific
            GameRules gameRules = createGameRules();

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

            // Create the world - version specific
            createFreshLevel(minecraft, levelSettings, worldOptions);

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
     * Create GameRules - version specific.
     * 1.21+ requires FeatureFlags, 1.20.x uses no-arg constructor.
     */
    private GameRules createGameRules() {
        try {
            // Try 1.21+ API first
            Class<?> featureFlagsClass = Class.forName("net.minecraft.world.flag.FeatureFlags");
            Object defaultFlags = featureFlagsClass.getField("DEFAULT_FLAGS").get(null);
            return GameRules.class.getConstructor(Class.forName("net.minecraft.world.flag.FeatureFlagSet"))
                .newInstance(defaultFlags);
        } catch (Exception e) {
            // Fall back to 1.20.x no-arg constructor
            try {
                return GameRules.class.getConstructor().newInstance();
            } catch (Exception e2) {
                Isotope.LOGGER.error("Failed to create GameRules", e2);
                throw new RuntimeException("Cannot create GameRules", e2);
            }
        }
    }

    /**
     * Create a fresh level - version specific.
     * 1.21+ uses 5-param method, 1.20.x uses 4-param method.
     */
    private void createFreshLevel(Minecraft minecraft, LevelSettings levelSettings, WorldOptions worldOptions) throws Exception {
        Object worldOpenFlows = minecraft.createWorldOpenFlows();

        // Try 1.21+ API first (5 params with WorldPresets::createNormalWorldDimensions)
        try {
            Class<?> worldPresetsClass = Class.forName("net.minecraft.world.level.levelgen.presets.WorldPresets");

            // Find createNormalWorldDimensions method
            java.lang.reflect.Method createNormalMethod = null;
            for (java.lang.reflect.Method m : worldPresetsClass.getMethods()) {
                if (m.getName().equals("createNormalWorldDimensions")) {
                    createNormalMethod = m;
                    break;
                }
            }

            if (createNormalMethod != null) {
                final java.lang.reflect.Method finalMethod = createNormalMethod;

                // Create a Function proxy for WorldPresets::createNormalWorldDimensions
                java.util.function.Function<Object, Object> dimensionFactory = registryAccess -> {
                    try {
                        return finalMethod.invoke(null, registryAccess);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                // Find the 5-param createFreshLevel
                for (java.lang.reflect.Method m : worldOpenFlows.getClass().getMethods()) {
                    if (m.getName().equals("createFreshLevel") && m.getParameterCount() == 5) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params[3].getName().contains("Function")) {
                            m.invoke(worldOpenFlows, TEMP_WORLD_NAME, levelSettings, worldOptions, dimensionFactory, null);
                            Isotope.LOGGER.info("Created temp world using 1.21+ API");
                            return;
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            Isotope.LOGGER.debug("WorldPresets class not found: {}", e.getMessage());
        } catch (Exception e) {
            Isotope.LOGGER.debug("1.21+ world creation failed: {}", e.getMessage());
        }

        // Try 1.20.x API (4 params)
        try {
            Class<?> worldPresetsClass = Class.forName("net.minecraft.world.level.levelgen.presets.WorldPresets");

            java.lang.reflect.Method createNormalMethod = null;
            for (java.lang.reflect.Method m : worldPresetsClass.getMethods()) {
                if (m.getName().equals("createNormalWorldDimensions")) {
                    createNormalMethod = m;
                    break;
                }
            }

            if (createNormalMethod != null) {
                final java.lang.reflect.Method finalMethod = createNormalMethod;

                java.util.function.Function<Object, Object> dimensionFactory = registryAccess -> {
                    try {
                        return finalMethod.invoke(null, registryAccess);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                for (java.lang.reflect.Method m : worldOpenFlows.getClass().getMethods()) {
                    if (m.getName().equals("createFreshLevel") && m.getParameterCount() == 4) {
                        m.invoke(worldOpenFlows, TEMP_WORLD_NAME, levelSettings, worldOptions, dimensionFactory);
                        Isotope.LOGGER.info("Created temp world using 1.20.x API");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("1.20.x world creation failed: {}", e.getMessage());
        }

        throw new RuntimeException("Could not find compatible createFreshLevel method");
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
