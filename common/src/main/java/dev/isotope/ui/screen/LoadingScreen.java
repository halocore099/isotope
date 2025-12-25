package dev.isotope.ui.screen;

import dev.isotope.registry.RegistryLoader;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Simple loading screen shown while scanning registries from main menu.
 *
 * Shows a spinner and status messages, then transitions to MainScreen.
 */
@Environment(EnvType.CLIENT)
public class LoadingScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE");

    private final Screen parentScreen;
    private final Supplier<Screen> targetScreenFactory;
    private String statusMessage = "Initializing...";
    private boolean loadingStarted = false;
    private int dots = 0;
    private int tickCount = 0;

    public LoadingScreen(@Nullable Screen parent) {
        this(parent, LootEditorScreen::new);
    }

    public LoadingScreen(@Nullable Screen parent, Supplier<Screen> targetScreenFactory) {
        super(TITLE, parent);
        this.parentScreen = parent;
        this.targetScreenFactory = targetScreenFactory;
    }

    @Override
    protected void init() {
        super.init();

        if (!loadingStarted) {
            loadingStarted = true;
            startLoading();
        }
    }

    private void startLoading() {
        RegistryLoader.getInstance().loadFromMainMenu(
            this::onProgress,
            this::onComplete
        );
    }

    private void onProgress(String message) {
        this.statusMessage = message;
    }

    private void onComplete(boolean success) {
        if (success && minecraft != null) {
            // Transition to target screen
            minecraft.setScreen(targetScreenFactory.get());
        } else {
            // Go back to parent on failure
            statusMessage = "Failed to load registries. Press ESC to go back.";
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        graphics.fill(0, 0, width, height, 0xFF0A0A0A);

        // Title
        String title = "ISOTOPE";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, (width - titleWidth) / 2, height / 2 - 40, IsotopeColors.ACCENT_CYAN);

        // Subtitle
        String subtitle = "Worldgen Analysis IDE";
        int subtitleWidth = font.width(subtitle);
        graphics.drawString(font, subtitle, (width - subtitleWidth) / 2, height / 2 - 25, IsotopeColors.TEXT_SECONDARY);

        // Animated dots
        String dotsStr = ".".repeat((dots % 3) + 1);
        String loadingText = "Loading" + dotsStr;
        int loadingWidth = font.width(loadingText);
        graphics.drawString(font, loadingText, (width - loadingWidth) / 2, height / 2, IsotopeColors.TEXT_PRIMARY);

        // Status message
        int statusWidth = font.width(statusMessage);
        graphics.drawString(font, statusMessage, (width - statusWidth) / 2, height / 2 + 20, IsotopeColors.TEXT_MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;
        if (tickCount % 10 == 0) {
            dots++;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Allow ESC to cancel loading
        return true;
    }

    @Override
    public void onClose() {
        // Cancel loading if in progress
        if (RegistryLoader.getInstance().isLoading()) {
            // Can't really cancel mid-load, but we can go back
        }
        super.onClose();
    }
}
