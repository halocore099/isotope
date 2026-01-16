package dev.isotope.ui;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import dev.isotope.Isotope;
import dev.isotope.registry.RegistryScanner;
import dev.isotope.testing.TestModeState;
import dev.isotope.testing.TestWorldManager;
import dev.isotope.ui.screen.LootEditorScreen;
import dev.isotope.ui.screen.LoadingScreen;
import dev.isotope.ui.screen.TestingScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Client-side initialization for ISOTOPE UI.
 *
 * Flow:
 * - From Title Screen: Load registries (invisible) → MainScreen
 * - From Pause Menu: MainScreen directly (already scanned)
 */
@Environment(EnvType.CLIENT)
public final class IsotopeClientInit {

    private static boolean initialized = false;

    private IsotopeClientInit() {}

    public static void init() {
        if (initialized) {
            Isotope.LOGGER.warn("IsotopeClientInit already initialized");
            return;
        }

        // Initialize theme manager
        ThemeManager.getInstance().initialize(Minecraft.getInstance().gameDirectory.toPath());

        // Register screen modification event
        ClientGuiEvent.INIT_POST.register(IsotopeClientInit::onScreenInit);

        initialized = true;
        Isotope.LOGGER.info("ISOTOPE client UI hooks registered");
    }

    private static void onScreenInit(Screen screen, ScreenAccess access) {
        if (screen instanceof TitleScreen) {
            addIsotopeButtonToTitleScreen(screen, access);

            // Also ensure test mode is fully cleared (safety check)
            if (TestModeState.getInstance().isInTestMode()) {
                Isotope.LOGGER.warn("Test mode was still active on title screen - clearing");
                TestModeState.getInstance().exitTestMode();
            }

            // Clean up any pending test worlds and auto-open editor
            if (TestWorldManager.getInstance().checkPendingCleanup()) {
                Isotope.LOGGER.info("Returned from test mode - opening editor");
                // Use execute to open after title screen is fully initialized
                Minecraft.getInstance().execute(() -> {
                    if (RegistryScanner.isScanned()) {
                        Minecraft.getInstance().setScreen(new LootEditorScreen());
                    }
                });
            }
        } else if (screen instanceof PauseScreen) {
            addIsotopeButtonToPauseScreen(screen, access);
        }
    }

    private static void addIsotopeButtonToTitleScreen(Screen screen, ScreenAccess access) {
        // Position at bottom of screen, above copyright text
        int buttonWidth = 150;
        int buttonHeight = 20;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = screen.height - 35; // Near bottom, above copyright

        // Use vanilla Button for native Minecraft styling
        Button button = Button.builder(
            Component.literal("ISOTOPE"),
            btn -> openFromMainMenu(screen)
        )
        .pos(x, y)
        .size(buttonWidth, buttonHeight)
        .build();

        access.addRenderableWidget(button);

        Isotope.LOGGER.debug("Added ISOTOPE button to title screen at ({}, {})", x, y);
    }

    private static void addIsotopeButtonToPauseScreen(Screen screen, ScreenAccess access) {
        // Don't add button if we're in the process of exiting test mode
        if (TestWorldManager.getInstance().isExiting()) {
            Isotope.LOGGER.debug("Skipping ISOTOPE button - exiting test mode");
            return;
        }

        // Only add if we're in a singleplayer world (integrated server)
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) {
            // Not in singleplayer/LAN - registry data not available
            return;
        }

        // Position in pause menu
        int buttonWidth = 150;
        int buttonHeight = 20;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = screen.height / 4 + 120 + 24; // Below other pause menu buttons

        // Check if we're in test mode
        boolean inTestMode = TestModeState.getInstance().isInTestMode();

        // Use vanilla Button for native Minecraft styling
        Button button = Button.builder(
            Component.literal(inTestMode ? "ISOTOPE [TEST MODE]" : "ISOTOPE"),
            btn -> openFromPauseMenu(screen)
        )
        .pos(x, y)
        .size(buttonWidth, buttonHeight)
        .build();

        access.addRenderableWidget(button);

        Isotope.LOGGER.debug("Added ISOTOPE button to pause screen (testMode={})", inTestMode);
    }

    /**
     * Opens ISOTOPE from the title screen.
     * Loads registries first, then opens the editor.
     */
    private static void openFromMainMenu(Screen parentScreen) {
        Minecraft minecraft = Minecraft.getInstance();

        // Check if registries already loaded
        if (RegistryScanner.isScanned()) {
            Isotope.LOGGER.info("Registries already loaded");
            minecraft.setScreen(new LootEditorScreen());
            return;
        }

        // Show loading screen - it handles registry loading and transitions to LootEditorScreen
        minecraft.setScreen(new LoadingScreen(parentScreen));
    }

    /**
     * Opens ISOTOPE from the pause menu.
     * Registry data is already available from the loaded world.
     * Opens TestingScreen if in test mode, otherwise normal editor.
     */
    private static void openFromPauseMenu(Screen parentScreen) {
        // Don't open anything if we're exiting test mode
        if (TestWorldManager.getInstance().isExiting()) {
            Isotope.LOGGER.debug("Not opening ISOTOPE - exiting test mode");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        // Registries should already be scanned when world loaded
        if (!RegistryScanner.isScanned()) {
            Isotope.LOGGER.warn("Opening ISOTOPE but registries not scanned yet");
        }

        // Check if we're in test mode
        if (TestModeState.getInstance().isInTestMode()) {
            // Open testing UI
            minecraft.setScreen(new TestingScreen());
        } else {
            // Open normal editor
            minecraft.setScreen(new LootEditorScreen());
        }
    }
}
