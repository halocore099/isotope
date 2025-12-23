package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.analysis.AnalysisEngine;
import dev.isotope.analysis.AnalysisEngine.AnalysisConfig;
import dev.isotope.analysis.AnalysisEngine.AnalysisProgress;
import dev.isotope.analysis.AnalysisEngine.AnalysisResult;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.IsotopeButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Progress screen shown during analysis.
 * Displays progress bar and log messages.
 */
@Environment(EnvType.CLIENT)
public class AnalysisProgressScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Running Analysis");

    private final Screen nextScreen;
    private final AnalysisConfig config;

    // State
    private boolean analysisStarted = false;
    private boolean analysisComplete = false;
    private AnalysisResult result = null;
    private String currentTask = "Initializing...";
    private int progressPercent = 0;
    private final List<String> logMessages = new ArrayList<>();

    // UI elements
    private IsotopeButton cancelButton;
    private IsotopeButton continueButton;

    public AnalysisProgressScreen(@Nullable Screen parent, Screen nextScreen, AnalysisConfig config) {
        super(TITLE, parent);
        this.nextScreen = nextScreen;
        this.config = config;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonY = this.height - 40;

        // Cancel button (shown during analysis)
        cancelButton = IsotopeButton.isotopeBuilder(
            Component.literal("Cancel"),
            btn -> cancelAnalysis()
        )
        .pos(this.width / 2 - buttonWidth / 2, buttonY)
        .size(buttonWidth, buttonHeight)
        .style(IsotopeButton.ButtonStyle.DESTRUCTIVE)
        .build();
        this.addRenderableWidget(cancelButton);

        // Continue button (shown after completion)
        continueButton = IsotopeButton.isotopeBuilder(
            Component.literal("Continue"),
            btn -> openNextScreen()
        )
        .pos(this.width / 2 - buttonWidth / 2, buttonY)
        .size(buttonWidth, buttonHeight)
        .style(IsotopeButton.ButtonStyle.PRIMARY)
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
        addLog("Starting ISOTOPE analysis...");

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();

        if (server == null) {
            addLog("ERROR: No server available");
            addLog("Analysis requires a loaded world");
            analysisComplete = true;
            result = AnalysisResult.error("No server available");
            updateButtons();
            return;
        }

        addLog("Server found: " + server.getWorldData().getLevelName());
        addLog("Sample count: " + config.sampleCount());

        // Run analysis on server thread
        server.execute(() -> {
            CompletableFuture<AnalysisResult> future = AnalysisEngine.getInstance().startAnalysis(
                server,
                config,
                this::onProgress,
                this::onComplete
            );

            future.whenComplete((res, ex) -> {
                if (ex != null) {
                    mc.execute(() -> {
                        addLog("ERROR: " + ex.getMessage());
                        analysisComplete = true;
                        result = AnalysisResult.error(ex.getMessage());
                        updateButtons();
                    });
                }
            });
        });
    }

    private void onProgress(AnalysisProgress progress) {
        Minecraft.getInstance().execute(() -> {
            this.currentTask = progress.currentTask();
            this.progressPercent = progress.percentComplete();

            // Add significant progress milestones to log
            if (progressPercent == 10 || progressPercent == 50 || progressPercent == 90) {
                addLog(progress.currentTask());
            }
        });
    }

    private void onComplete(AnalysisResult result) {
        Minecraft.getInstance().execute(() -> {
            this.result = result;
            this.analysisComplete = true;
            this.progressPercent = 100;

            if (result.success()) {
                addLog("Analysis complete!");
                addLog(String.format("  Structures: %d (%d linked)",
                    result.structuresAnalyzed(), result.structuresLinked()));
                addLog(String.format("  Loot tables: %d sampled", result.lootTablesSampled()));
                addLog(String.format("  Time: %dms", result.elapsedMs()));
            } else {
                addLog("Analysis failed: " + result.error());
            }

            updateButtons();
        });
    }

    private void cancelAnalysis() {
        AnalysisEngine.getInstance().cancel();
        addLog("Cancelling analysis...");
    }

    private void openNextScreen() {
        Minecraft.getInstance().setScreen(nextScreen);
    }

    private void updateButtons() {
        cancelButton.visible = !analysisComplete;
        continueButton.visible = analysisComplete;
    }

    private void addLog(String message) {
        logMessages.add(message);
        // Keep last 20 messages
        while (logMessages.size() > 20) {
            logMessages.remove(0);
        }
        Isotope.LOGGER.info("[Analysis] {}", message);
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
                ? (result != null && result.success() ? IsotopeColors.STATUS_SUCCESS : IsotopeColors.STATUS_ERROR)
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
