package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.compat.Id;
import dev.isotope.editing.LootEditManager;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Manages test mode state and world cleanup.
 *
 * Note: World creation is done manually by the user through normal Minecraft UI.
 * This manager tracks the test mode state and handles world deletion on exit.
 */
public final class TestWorldManager {

    private static final TestWorldManager INSTANCE = new TestWorldManager();
    private static final String WORLD_PREFIX = "isotope_test_";

    private TestWorldManager() {}

    public static TestWorldManager getInstance() {
        return INSTANCE;
    }

    /**
     * Generate a unique test world name with timestamp.
     */
    public String generateWorldName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return WORLD_PREFIX + timestamp;
    }

    /**
     * Enter test mode and prepare for world creation.
     * The user will create the world manually, but we track the state.
     *
     * @param worldType The type of world to create (VOID or NORMAL)
     */
    public void enterTestMode(TestModeState.WorldType worldType) {
        Set<Id> editedTables = LootEditManager.getInstance().getEditedTables();
        String worldName = generateWorldName();

        // Enter test mode state
        TestModeState.getInstance().enterTestMode(worldName, editedTables, worldType);

        Isotope.LOGGER.info("Entered test mode: suggestedName={}, tables={}, type={}",
            worldName, editedTables.size(), worldType.displayName);
    }

    // World name pending deletion (set when exiting, cleared after deletion)
    private String pendingDeleteWorld = null;

    // Flag to indicate we're in the process of exiting (prevents UI from reopening)
    private volatile boolean isExiting = false;

    /**
     * Exit test mode, disconnect from world, and optionally delete it.
     */
    public void exitTestWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        String worldName = TestModeState.getInstance().getTestWorldName();
        Isotope.LOGGER.info("Exiting test mode: world={}", worldName);

        // Set exiting flag to prevent TestingScreen from reopening
        isExiting = true;

        // Store world name for deletion after disconnect
        pendingDeleteWorld = worldName;

        // Clear test mode state
        TestModeState.getInstance().exitTestMode();

        // Close current screen first
        minecraft.setScreen(null);

        // Use clearClientLevel - this is what "Save and Quit to Title" uses
        // Schedule on next tick to avoid being in a callback chain
        minecraft.execute(() -> {
            if (minecraft.level != null) {
                // Show saving screen and properly close the world
                minecraft.clearClientLevel(new net.minecraft.client.gui.screens.GenericMessageScreen(
                    net.minecraft.network.chat.Component.translatable("menu.savingLevel")
                ));
            }
        });
    }

    /**
     * Called when returning to title screen to clean up test world.
     * Should be called from client tick or title screen init.
     *
     * @return true if cleanup was triggered (user just exited test mode)
     */
    public boolean checkPendingCleanup() {
        if (pendingDeleteWorld != null) {
            String worldToDelete = pendingDeleteWorld;
            pendingDeleteWorld = null;
            isExiting = false; // Clear exiting flag

            // Delete on background thread
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Wait for world to fully close
                    deleteWorld(worldToDelete);
                } catch (InterruptedException ignored) {}
            }, "Isotope-WorldCleanup").start();

            return true; // Cleanup was triggered
        }

        // Also clear exiting flag if somehow still set
        if (isExiting) {
            isExiting = false;
        }

        return false;
    }

    /**
     * Check if there's a world pending deletion.
     */
    public boolean hasPendingCleanup() {
        return pendingDeleteWorld != null;
    }

    /**
     * Check if we're currently in the process of exiting test mode.
     * Used to prevent TestingScreen from reopening during exit.
     */
    public boolean isExiting() {
        return isExiting;
    }

    /**
     * Called when a world is loaded to check if it's a test world.
     * This allows auto-detecting if user named their world with our prefix.
     */
    public void onWorldLoad(String worldName) {
        if (isTestWorld(worldName) && !TestModeState.getInstance().isInTestMode()) {
            // User created a world with our prefix, enable test mode
            Set<Id> editedTables = LootEditManager.getInstance().getEditedTables();
            TestModeState.getInstance().enterTestMode(worldName, editedTables, TestModeState.WorldType.VOID);
            Isotope.LOGGER.info("Auto-enabled test mode for world: {}", worldName);
        }
    }

    /**
     * Delete a world folder by name.
     */
    private void deleteWorld(String worldName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        File savesDir = minecraft.gameDirectory.toPath().resolve("saves").toFile();
        File worldDir = new File(savesDir, worldName);

        if (worldDir.exists() && worldDir.isDirectory()) {
            try {
                FileUtils.deleteDirectory(worldDir);
                Isotope.LOGGER.info("Deleted test world: {}", worldName);
            } catch (IOException e) {
                Isotope.LOGGER.error("Failed to delete test world: {}", worldName, e);
            }
        } else {
            Isotope.LOGGER.warn("Test world directory not found: {}", worldName);
        }
    }

    /**
     * Check if a world name is a test world.
     */
    public boolean isTestWorld(String worldName) {
        return worldName != null && worldName.startsWith(WORLD_PREFIX);
    }

    /**
     * Clean up any orphaned test worlds (from crashes, etc).
     */
    public void cleanupOrphanedTestWorlds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        File savesDir = minecraft.gameDirectory.toPath().resolve("saves").toFile();
        if (!savesDir.exists()) return;

        File[] files = savesDir.listFiles();
        if (files == null) return;

        int cleaned = 0;
        for (File file : files) {
            if (file.isDirectory() && isTestWorld(file.getName())) {
                try {
                    FileUtils.deleteDirectory(file);
                    cleaned++;
                    Isotope.LOGGER.info("Cleaned up orphaned test world: {}", file.getName());
                } catch (IOException e) {
                    Isotope.LOGGER.error("Failed to clean up test world: {}", file.getName(), e);
                }
            }
        }

        if (cleaned > 0) {
            Isotope.LOGGER.info("Cleaned up {} orphaned test world(s)", cleaned);
        }
    }
}
