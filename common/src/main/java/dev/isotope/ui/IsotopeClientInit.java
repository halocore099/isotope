package dev.isotope.ui;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import dev.isotope.Isotope;
import dev.isotope.registry.RegistryScanner;
import dev.isotope.ui.screen.LootEditorScreen;
import dev.isotope.ui.screen.LoadingScreen;
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

        // Register screen modification event
        ClientGuiEvent.INIT_POST.register(IsotopeClientInit::onScreenInit);

        initialized = true;
        Isotope.LOGGER.info("ISOTOPE client UI hooks registered");
    }

    private static void onScreenInit(Screen screen, ScreenAccess access) {
        if (screen instanceof TitleScreen) {
            addIsotopeButtonToTitleScreen(screen, access);
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

        // Use vanilla Button for native Minecraft styling
        Button button = Button.builder(
            Component.literal("ISOTOPE"),
            btn -> openFromPauseMenu(screen)
        )
        .pos(x, y)
        .size(buttonWidth, buttonHeight)
        .build();

        access.addRenderableWidget(button);

        Isotope.LOGGER.debug("Added ISOTOPE button to pause screen");
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
     */
    private static void openFromPauseMenu(Screen parentScreen) {
        Minecraft minecraft = Minecraft.getInstance();

        // Registries should already be scanned when world loaded
        if (!RegistryScanner.isScanned()) {
            Isotope.LOGGER.warn("Opening ISOTOPE but registries not scanned yet");
        }

        // Open editor directly
        minecraft.setScreen(new LootEditorScreen());
    }
}
