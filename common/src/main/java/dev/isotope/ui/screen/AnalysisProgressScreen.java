package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.analysis.HeadlessAnalysisWorld;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress screen shown during observation-based analysis.
 * Displays progress and log messages.
 */
@Environment(EnvType.CLIENT)
public class AnalysisProgressScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Running Observation");

    // State
    private boolean analysisStarted = false;
    private boolean analysisComplete = false;
    private boolean analysisSuccess = false;
    private String currentTask = "Initializing...";
    private int progressPercent = 0;
    private final List<String> logMessages = new ArrayList<>();

    // UI elements
    private Button cancelButton;
    private Button continueButton;

    /**
     * Create analysis progress screen.
     * @param parent Parent screen to return to on cancel
     */
    public AnalysisProgressScreen(@Nullable Screen parent) {
        super(TITLE, parent);
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonY = this.height - 40;

        // Cancel button (shown during analysis)
        cancelButton = Button.builder(Component.literal("Cancel"), btn -> cancelAnalysis())
            .pos(this.width / 2 - buttonWidth / 2, buttonY)
            .size(buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(cancelButton);

        // Continue button (shown after completion)
        continueButton = Button.builder(Component.literal("Continue"), btn -> openResults())
            .pos(this.width / 2 - buttonWidth / 2, buttonY)
            .size(buttonWidth, buttonHeight)
            .build();
        continueButton.visible = false;
        this.addRenderableWidget(continueButton);

        // Start analysis if not already started
        if (!analysisStarted) {
            startAnalysis();
        }
    }

    private void startAnalysis() {
        analysisStarted = true;
        addLog("Starting ISOTOPE observation session...");
        addLog("This will place and analyze all registered structures.");

        // Use HeadlessAnalysisWorld for observation-based analysis
        HeadlessAnalysisWorld.getInstance().startAnalysis(
            this::onProgress,
            this::onComplete
        );
    }

    private void onProgress(String message) {
        Minecraft.getInstance().execute(() -> {
            this.currentTask = message;
            addLog(message);

            // Estimate progress based on message content
            if (message.contains("Creating") || message.contains("Initializing")) {
                this.progressPercent = 5;
            } else if (message.contains("Loading")) {
                this.progressPercent = 10;
            } else if (message.contains("Starting observation")) {
                this.progressPercent = 15;
            } else if (message.contains("Placing")) {
                // Parse placement progress
                if (message.contains("/")) {
                    try {
                        String[] parts = message.split("[()]");
                        for (String part : parts) {
                            if (part.contains("/")) {
                                String[] nums = part.split("/");
                                int current = Integer.parseInt(nums[0].trim());
                                int total = Integer.parseInt(nums[1].replaceAll("[^0-9]", "").trim());
                                this.progressPercent = 15 + (current * 70 / total);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        this.progressPercent = Math.min(85, this.progressPercent + 1);
                    }
                }
            } else if (message.contains("Correlating")) {
                this.progressPercent = 90;
            } else if (message.contains("complete") || message.contains("Complete")) {
                this.progressPercent = 100;
            }
        });
    }

    private void onComplete(Boolean success) {
        Minecraft.getInstance().execute(() -> {
            this.analysisComplete = true;
            this.analysisSuccess = success;
            this.progressPercent = 100;

            if (success) {
                addLog("Observation session complete!");
                Isotope.LOGGER.info("Observation complete - opening results");
                // Open MainScreen to show results
                MainScreen mainScreen = new MainScreen(null);
                Minecraft.getInstance().setScreen(mainScreen);
            } else {
                addLog("Observation session failed.");
                updateButtons();
            }
        });
    }

    private void cancelAnalysis() {
        HeadlessAnalysisWorld.getInstance().cancel();
        addLog("Cancelling observation...");
    }

    private void openResults() {
        if (analysisSuccess) {
            MainScreen mainScreen = new MainScreen(null);
            Minecraft.getInstance().setScreen(mainScreen);
        } else {
            // Return to title screen
            Minecraft.getInstance().setScreen(null);
        }
    }

    private void updateButtons() {
        cancelButton.visible = !analysisComplete;
        continueButton.visible = analysisComplete;
    }

    private void addLog(String message) {
        logMessages.add(message);
        // Keep last 25 messages
        while (logMessages.size() > 25) {
            logMessages.remove(0);
        }
        Isotope.LOGGER.info("[Observation] {}", message);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, 20, IsotopeColors.ACCENT_CYAN);

        // Progress bar
        int barWidth = 300;
        int barHeight = 20;
        int barX = centerX - barWidth / 2;
        int barY = 50;

        // Bar background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, IsotopeColors.BACKGROUND_DARK);

        // Bar border
        graphics.fill(barX, barY, barX + barWidth, barY + 1, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(barX, barY + barHeight - 1, barX + barWidth, barY + barHeight, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(barX, barY, barX + 1, barY + barHeight, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(barX + barWidth - 1, barY, barX + barWidth, barY + barHeight, IsotopeColors.BORDER_DEFAULT);

        // Progress fill
        int fillWidth = (int) ((barWidth - 2) * (progressPercent / 100.0f));
        if (fillWidth > 0) {
            int fillColor = analysisComplete
                ? (analysisSuccess ? IsotopeColors.STATUS_SUCCESS : IsotopeColors.STATUS_ERROR)
                : IsotopeColors.ACCENT_CYAN;
            graphics.fill(barX + 1, barY + 1, barX + 1 + fillWidth, barY + barHeight - 1, fillColor);
        }

        // Progress text
        String progressText = progressPercent + "%";
        graphics.drawCenteredString(this.font, progressText, centerX, barY + 6, IsotopeColors.TEXT_PRIMARY);

        // Current task
        graphics.drawCenteredString(this.font, currentTask, centerX, barY + barHeight + 10, IsotopeColors.TEXT_SECONDARY);

        // Log panel
        int logX = 20;
        int logY = 100;
        int logWidth = this.width - 40;
        int logHeight = this.height - 160;

        // Log background
        graphics.fill(logX, logY, logX + logWidth, logY + logHeight, IsotopeColors.BACKGROUND_DARK);
        graphics.fill(logX, logY, logX + logWidth, logY + 1, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(logX, logY + logHeight - 1, logX + logWidth, logY + logHeight, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(logX, logY, logX + 1, logY + logHeight, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(logX + logWidth - 1, logY, logX + logWidth, logY + logHeight, IsotopeColors.BORDER_DEFAULT);

        // Log messages
        int messageY = logY + 5;
        int maxMessages = (logHeight - 10) / 12;
        int startIdx = Math.max(0, logMessages.size() - maxMessages);

        for (int i = startIdx; i < logMessages.size(); i++) {
            String msg = logMessages.get(i);
            int color = msg.startsWith("ERROR") ? IsotopeColors.STATUS_ERROR
                : msg.contains("Warning") ? IsotopeColors.STATUS_WARNING
                : msg.startsWith("  ") ? IsotopeColors.TEXT_MUTED
                : IsotopeColors.TEXT_SECONDARY;
            graphics.drawString(this.font, msg, logX + 5, messageY, color, false);
            messageY += 12;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Prevent accidental close during analysis
        return analysisComplete;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
