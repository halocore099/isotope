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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Loads registry data from main menu without requiring user interaction.
 *
 * <h2>Overview</h2>
 * Creates a minimal temporary world purely for registry access, scans
 * structures and loot tables, then tears down immediately.
 * The user never sees or interacts with this world - it's invisible.
 *
 * <h2>Version Compatibility</h2>
 * Uses reflection to handle API differences between Minecraft versions:
 * <ul>
 *   <li><b>GameRules:</b> 1.21+ requires FeatureFlagSet param, 1.20.x uses no-arg</li>
 *   <li><b>createFreshLevel:</b> 1.21+ uses 5-param method, 1.20.x uses 4-param</li>
 * </ul>
 *
 * <h2>Future Improvement: Result Caching</h2>
 * Registry scan results could be cached to disk with version/datapack checksums.
 * On subsequent launches with matching checksums, cached data could be used
 * instead of creating a temp world. This would significantly speed up startup
 * for users who haven't changed their mod/datapack configuration.
 *
 * @see RegistryScanner
 */
public final class RegistryLoader {

    private static final RegistryLoader INSTANCE = new RegistryLoader();
    private static final String TEMP_WORLD_NAME = "_isotope_registry_temp";

    /** Maximum time to wait for world to close (in 100ms increments) */
    private static final int MAX_DISCONNECT_ATTEMPTS = 50; // 5 seconds

    /** Maximum cleanup retry attempts */
    private static final int MAX_CLEANUP_RETRIES = 3;

    /** Delay between cleanup retries in milliseconds */
    private static final int CLEANUP_RETRY_DELAY_MS = 500;

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
     *
     * @param onProgress Called with status messages during loading
     * @param onComplete Called with success/failure when loading finishes
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
            // Clean up any existing temp world from previous runs
            cleanupTempWorldWithRetry();

            reportProgress("Creating minimal world for registry access...");

            Minecraft minecraft = Minecraft.getInstance();

            // Create GameRules using version-appropriate API
            GameRules gameRules = createGameRulesCompat();

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
                0L,    // Fixed seed for reproducibility
                false, // No structures
                false  // No bonus chest
            );

            // Create the world using version-appropriate API
            createFreshLevelCompat(minecraft, levelSettings, worldOptions);

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to create temp world for registry scan", e);
            reportProgress("ERROR: " + e.getMessage());
            loading.set(false);
            if (completionCallback != null) {
                completionCallback.accept(false);
            }
        }
    }

    // ===== Version Compatibility Layer =====

    /**
     * Create GameRules using version-appropriate constructor.
     *
     * <p>API difference:
     * <ul>
     *   <li>1.21+: {@code new GameRules(FeatureFlags.DEFAULT_FLAGS)}</li>
     *   <li>1.20.x: {@code new GameRules()}</li>
     * </ul>
     */
    private GameRules createGameRulesCompat() {
        // Try 1.21+ API: GameRules(FeatureFlagSet)
        try {
            Class<?> featureFlagsClass = Class.forName("net.minecraft.world.flag.FeatureFlags");
            Object defaultFlags = featureFlagsClass.getField("DEFAULT_FLAGS").get(null);
            Class<?> featureFlagSetClass = Class.forName("net.minecraft.world.flag.FeatureFlagSet");

            GameRules rules = GameRules.class.getConstructor(featureFlagSetClass).newInstance(defaultFlags);
            Isotope.LOGGER.debug("Created GameRules using 1.21+ API (FeatureFlagSet)");
            return rules;

        } catch (ClassNotFoundException e) {
            Isotope.LOGGER.debug("FeatureFlags class not found, trying 1.20.x API");
        } catch (NoSuchFieldException e) {
            Isotope.LOGGER.debug("DEFAULT_FLAGS field not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            Isotope.LOGGER.debug("GameRules(FeatureFlagSet) constructor not found: {}", e.getMessage());
        } catch (Exception e) {
            Isotope.LOGGER.debug("1.21+ GameRules creation failed: {}", e.getMessage());
        }

        // Fall back to 1.20.x API: GameRules()
        try {
            GameRules rules = GameRules.class.getConstructor().newInstance();
            Isotope.LOGGER.debug("Created GameRules using 1.20.x API (no-arg)");
            return rules;
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to create GameRules with any known API", e);
            throw new RuntimeException("Cannot create GameRules - unsupported Minecraft version?", e);
        }
    }

    /**
     * Create a fresh level using version-appropriate method.
     *
     * <p>API difference:
     * <ul>
     *   <li>1.21+: {@code createFreshLevel(name, settings, options, dimensionFactory, null)} (5 params)</li>
     *   <li>1.20.x: {@code createFreshLevel(name, settings, options, dimensionFactory)} (4 params)</li>
     * </ul>
     */
    private void createFreshLevelCompat(Minecraft minecraft, LevelSettings levelSettings, WorldOptions worldOptions) throws Exception {
        Object worldOpenFlows = minecraft.createWorldOpenFlows();

        // Get the dimension factory (WorldPresets::createNormalWorldDimensions)
        Function<Object, Object> dimensionFactory = createDimensionFactory();
        if (dimensionFactory == null) {
            throw new RuntimeException("Could not create dimension factory - WorldPresets.createNormalWorldDimensions not found");
        }

        // Try 1.21+ API: 5-param createFreshLevel
        Method method5Param = findCreateFreshLevelMethod(worldOpenFlows, 5);
        if (method5Param != null) {
            try {
                method5Param.invoke(worldOpenFlows, TEMP_WORLD_NAME, levelSettings, worldOptions, dimensionFactory, null);
                Isotope.LOGGER.info("Created temp world using 1.21+ API (5-param createFreshLevel)");
                return;
            } catch (Exception e) {
                Isotope.LOGGER.debug("5-param createFreshLevel invocation failed: {}", e.getMessage());
            }
        }

        // Try 1.20.x API: 4-param createFreshLevel
        Method method4Param = findCreateFreshLevelMethod(worldOpenFlows, 4);
        if (method4Param != null) {
            try {
                method4Param.invoke(worldOpenFlows, TEMP_WORLD_NAME, levelSettings, worldOptions, dimensionFactory);
                Isotope.LOGGER.info("Created temp world using 1.20.x API (4-param createFreshLevel)");
                return;
            } catch (Exception e) {
                Isotope.LOGGER.debug("4-param createFreshLevel invocation failed: {}", e.getMessage());
            }
        }

        // No compatible method found
        Isotope.LOGGER.error("Could not find compatible createFreshLevel method. " +
            "WorldOpenFlows methods: {}", listMethodSignatures(worldOpenFlows));
        throw new RuntimeException("Could not find compatible createFreshLevel method");
    }

    /**
     * Find the createFreshLevel method with the specified parameter count.
     * Validates that the method has a Function parameter in the right position.
     */
    private Method findCreateFreshLevelMethod(Object worldOpenFlows, int paramCount) {
        for (Method m : worldOpenFlows.getClass().getMethods()) {
            if (m.getName().equals("createFreshLevel") && m.getParameterCount() == paramCount) {
                Class<?>[] params = m.getParameterTypes();
                // Function param should be at index 3
                if (params.length > 3 && params[3].getName().contains("Function")) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Create a Function that wraps WorldPresets.createNormalWorldDimensions.
     * This is used to configure the world dimensions for the temp world.
     */
    private Function<Object, Object> createDimensionFactory() {
        try {
            Class<?> worldPresetsClass = Class.forName("net.minecraft.world.level.levelgen.presets.WorldPresets");

            Method createNormalMethod = null;
            for (Method m : worldPresetsClass.getMethods()) {
                if (m.getName().equals("createNormalWorldDimensions")) {
                    createNormalMethod = m;
                    break;
                }
            }

            if (createNormalMethod == null) {
                Isotope.LOGGER.error("WorldPresets.createNormalWorldDimensions method not found");
                return null;
            }

            final Method finalMethod = createNormalMethod;
            return registryAccess -> {
                try {
                    return finalMethod.invoke(null, registryAccess);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke createNormalWorldDimensions", e);
                }
            };

        } catch (ClassNotFoundException e) {
            Isotope.LOGGER.error("WorldPresets class not found", e);
            return null;
        }
    }

    /**
     * List method signatures for debugging.
     */
    private String listMethodSignatures(Object obj) {
        StringBuilder sb = new StringBuilder();
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().contains("Level") || m.getName().contains("World")) {
                sb.append(m.getName()).append("(").append(m.getParameterCount()).append("), ");
            }
        }
        return sb.toString();
    }

    // ===== Temp World Lifecycle =====

    /**
     * Called by RegistryScanner when the temp world starts.
     * At this point, registries have already been scanned.
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
     * Uses exponential polling to avoid busy-waiting.
     */
    private void waitForDisconnectAndContinue(Minecraft minecraft, int attempts) {
        if (attempts > MAX_DISCONNECT_ATTEMPTS) {
            Isotope.LOGGER.warn("Timeout waiting for temp world to close after {}ms",
                attempts * 100);
            reportProgress("Warning: Cleanup taking longer than expected...");

            // Still trigger callback - registries are loaded even if cleanup is slow
            minecraft.execute(() -> {
                if (completionCallback != null) {
                    completionCallback.accept(true);
                }
            });

            // Schedule async cleanup attempt
            CompletableFuture.runAsync(this::cleanupTempWorldWithRetry);
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
        Isotope.LOGGER.info("World closed after {} attempts ({}ms), cleaning up",
            attempts, attempts * 100);
        cleanupTempWorldWithRetry();

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

    // ===== Cleanup =====

    /**
     * Clean up temp world directory with retry logic.
     * Files may be locked briefly after world close on some systems.
     */
    private void cleanupTempWorldWithRetry() {
        for (int attempt = 1; attempt <= MAX_CLEANUP_RETRIES; attempt++) {
            try {
                if (cleanupTempWorldInternal()) {
                    if (attempt > 1) {
                        Isotope.LOGGER.info("Temp world cleaned up on attempt {}", attempt);
                    }
                    return;
                }
            } catch (Exception e) {
                if (attempt < MAX_CLEANUP_RETRIES) {
                    Isotope.LOGGER.debug("Cleanup attempt {} failed, retrying in {}ms: {}",
                        attempt, CLEANUP_RETRY_DELAY_MS, e.getMessage());
                    try {
                        Thread.sleep(CLEANUP_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    Isotope.LOGGER.warn("Failed to cleanup temp world after {} attempts: {}",
                        MAX_CLEANUP_RETRIES, e.getMessage());
                }
            }
        }
    }

    /**
     * Internal cleanup implementation.
     * @return true if directory was cleaned up or didn't exist
     */
    private boolean cleanupTempWorldInternal() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        Path savesDir = minecraft.gameDirectory.toPath().resolve("saves");
        Path tempDir = savesDir.resolve(TEMP_WORLD_NAME);

        if (!Files.exists(tempDir)) {
            return true;
        }

        // Track if any deletion failed
        AtomicBoolean anyFailed = new AtomicBoolean(false);

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    anyFailed.set(true);
                    Isotope.LOGGER.debug("Could not delete {}: {}", path, e.getMessage());
                }
            });

        if (anyFailed.get()) {
            // Throw to trigger retry
            throw new IOException("Some files could not be deleted");
        }

        return true;
    }

    // ===== Progress Reporting =====

    private void reportProgress(String message) {
        Isotope.LOGGER.info("[RegistryLoader] {}", message);
        if (progressCallback != null) {
            Minecraft.getInstance().execute(() -> progressCallback.accept(message));
        }
    }

    // ===== State Management =====

    /**
     * Reset state (for testing or re-initialization).
     */
    public void reset() {
        loading.set(false);
        loaded.set(false);
        StructureRegistry.getInstance().reset();
        LootTableRegistry.getInstance().reset();
        StructureLootLinker.getInstance().reset();
    }
}
