package dev.isotope.ui;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import dev.isotope.Isotope;
import dev.isotope.analysis.AnalysisEngine;
import dev.isotope.analysis.HeadlessAnalysisWorld;
import dev.isotope.ui.screen.AnalysisProgressScreen;
import dev.isotope.ui.screen.ConfirmationScreen;
import dev.isotope.ui.screen.MainScreen;
import dev.isotope.ui.widget.IsotopeButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Client-side initialization for ISOTOPE UI.
 * Adds the ISOTOPE button to the title screen and pause menu.
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
        Isotope.LOGGER.info("Screen init event: {}", screen.getClass().getSimpleName());
        if (screen instanceof TitleScreen) {
            addIsotopeButtonToTitleScreen(screen, access);
        } else if (screen instanceof PauseScreen) {
            addIsotopeButtonToPauseScreen(screen, access);
        }
    }

    private static void addIsotopeButtonToTitleScreen(Screen screen, ScreenAccess access) {
        // Position at bottom of screen, above copyright text
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = screen.height - 35; // Near bottom, above copyright

        IsotopeButton button = IsotopeButton.isotopeBuilder(
            Component.literal("ISOTOPE: Worldgen Analysis"),
            btn -> openIsotopeConfirmation(screen)
        )
        .pos(x, y)
        .size(buttonWidth, buttonHeight)
        .style(IsotopeButton.ButtonStyle.DEFAULT)
        .build();

        access.addRenderableWidget(button);

        Isotope.LOGGER.info("Added ISOTOPE button to title screen at ({}, {})", x, y);
    }

    private static void addIsotopeButtonToPauseScreen(Screen screen, ScreenAccess access) {
        // Only add if we're in a singleplayer world (integrated server)
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) {
            // Not in singleplayer/LAN - registry data not available
            Isotope.LOGGER.debug("Skipping ISOTOPE button on pause screen - not in singleplayer");
            return;
        }

        // Position in pause menu
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = screen.height / 4 + 120 + 24; // Below other pause menu buttons

        IsotopeButton button = IsotopeButton.isotopeBuilder(
            Component.literal("ISOTOPE: Worldgen Analysis"),
            btn -> openIsotopeConfirmation(screen)
        )
        .pos(x, y)
        .size(buttonWidth, buttonHeight)
        .style(IsotopeButton.ButtonStyle.DEFAULT)
        .build();

        access.addRenderableWidget(button);

        Isotope.LOGGER.debug("Added ISOTOPE button to pause screen at ({}, {})", x, y);
    }

    private static void openIsotopeConfirmation(Screen parentScreen) {
        Minecraft minecraft = Minecraft.getInstance();

        ConfirmationScreen confirmationScreen = new ConfirmationScreen(
            parentScreen,
            confirmed -> {
                if (confirmed) {
                    openMainScreen(parentScreen);
                }
            }
        );

        minecraft.setScreen(confirmationScreen);
    }

    private static void openMainScreen(Screen parentScreen) {
        Minecraft minecraft = Minecraft.getInstance();

        // Check if registry data is available (singleplayer server running)
        if (minecraft.getSingleplayerServer() == null) {
            // No world loaded - use headless analysis world
            Isotope.LOGGER.info("Starting headless analysis from main menu");
            openHeadlessAnalysis(parentScreen);
            return;
        }

        // World is loaded - run analysis using current server
        MainScreen mainScreen = new MainScreen(parentScreen);

        // Show analysis progress screen which will transition to main screen
        AnalysisProgressScreen progressScreen = new AnalysisProgressScreen(
            parentScreen,
            mainScreen,
            AnalysisEngine.AnalysisConfig.defaultConfig(),
            false  // Not headless - use current server
        );
        minecraft.setScreen(progressScreen);
    }

    private static void openHeadlessAnalysis(Screen parentScreen) {
        Minecraft minecraft = Minecraft.getInstance();

        // Show progress screen in headless mode
        AnalysisProgressScreen progressScreen = new AnalysisProgressScreen(
            parentScreen,
            null,  // Next screen will be set after analysis completes
            AnalysisEngine.AnalysisConfig.defaultConfig(),
            true   // Headless mode - create temp world
        );
        minecraft.setScreen(progressScreen);
    }
}
