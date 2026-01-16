package dev.isotope.ui.screen;

import dev.isotope.analysis.DropRateCalculator;
import dev.isotope.analysis.DropSimulator;
import dev.isotope.analysis.DropSimulator.ItemResult;
import dev.isotope.analysis.DropSimulator.SimulationResult;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootTableParser;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Screen for running loot table drop simulations.
 *
 * Shows simulated vs theoretical drop rates with visual comparison.
 */
@Environment(EnvType.CLIENT)
public class SimulationScreen extends Screen {

    private static final int DIALOG_WIDTH = 450;
    private static final int DIALOG_HEIGHT = 380;
    private static final int BAR_HEIGHT = 12;
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private final ResourceLocation tableId;

    @Nullable
    private SimulationResult result;
    private boolean isRunning = false;
    private int progress = 0;
    private int simulationCount = 1000;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Buttons
    private Button runButton;
    private Button countButton;

    public SimulationScreen(Screen parent, ResourceLocation tableId) {
        super(Component.literal("Drop Simulation"));
        this.parent = parent;
        this.tableId = tableId;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Simulation count button
        countButton = addRenderableWidget(Button.builder(
            Component.literal("Rolls: " + simulationCount),
            b -> cycleSimulationCount()
        ).pos(dialogX + 10, dialogY + DIALOG_HEIGHT - 30).size(100, 20).build());

        // Run button
        runButton = addRenderableWidget(Button.builder(
            Component.literal("Run Simulation"),
            b -> runSimulation()
        ).pos(dialogX + 120, dialogY + DIALOG_HEIGHT - 30).size(120, 20).build());

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal("Close"),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 70, dialogY + DIALOG_HEIGHT - 30).size(60, 20).build());

        // Help button (top right)
        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.DROP_SIMULATOR)
        ).pos(dialogX + DIALOG_WIDTH - 25, dialogY + 2).size(20, 20).build());
    }

    private void cycleSimulationCount() {
        simulationCount = switch (simulationCount) {
            case 100 -> 1000;
            case 1000 -> 5000;
            case 5000 -> 10000;
            default -> 100;
        };
        countButton.setMessage(Component.literal("Rolls: " + simulationCount));
    }

    private void runSimulation() {
        if (isRunning) return;

        // Get the loot table structure
        LootTableStructure structure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(null);
        if (structure == null && minecraft != null && minecraft.getSingleplayerServer() != null) {
            structure = LootTableParser.parse(minecraft.getSingleplayerServer(), tableId).orElse(null);
        }
        if (structure == null) return;

        isRunning = true;
        progress = 0;
        runButton.active = false;
        countButton.active = false;

        // Run simulation in a thread
        final LootTableStructure finalStructure = structure;
        new Thread(() -> {
            result = DropSimulator.simulate(
                finalStructure,
                tableId,
                simulationCount,
                (completed, total) -> progress = (int) ((float) completed / total * 100)
            );

            // Calculate max scroll
            int contentHeight = (result.items().size() + 1) * ROW_HEIGHT;
            int viewHeight = DIALOG_HEIGHT - 140;
            maxScroll = Math.max(0, contentHeight - viewHeight);
            scrollOffset = 0;

            isRunning = false;
            progress = 100;

            // Re-enable buttons on main thread
            Minecraft.getInstance().execute(() -> {
                runButton.active = true;
                countButton.active = true;
            });
        }, "ISOTOPE-Simulation").start();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Title bar
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 24, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.drawString(font, "🎲 Drop Simulation", dialogX + 10, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Table name
        String tableName = tableId.getPath();
        if (tableName.length() > 50) {
            tableName = "..." + tableName.substring(tableName.length() - 47);
        }
        graphics.drawString(font, tableName, dialogX + DIALOG_WIDTH - font.width(tableName) - 10, dialogY + 8,
            IsotopeColors.TEXT_MUTED, false);

        int contentY = dialogY + 30;

        if (isRunning) {
            // Progress bar
            int barWidth = DIALOG_WIDTH - 40;
            int barX = dialogX + 20;
            int barY = contentY + 50;

            graphics.fill(barX, barY, barX + barWidth, barY + 20, IsotopeColors.BORDER_DEFAULT);
            int fillWidth = (int) (barWidth * progress / 100f);
            graphics.fill(barX, barY, barX + fillWidth, barY + 20, IsotopeColors.ACCENT_GOLD);
            graphics.renderOutline(barX, barY, barWidth, 20, IsotopeColors.BUTTON_BACKGROUND);

            String progressText = "Simulating... " + progress + "%";
            graphics.drawCenteredString(font, progressText, width / 2, barY + 6, IsotopeColors.TEXT_PRIMARY);

        } else if (result != null) {
            // Stats header
            String stats = String.format("%d rolls in %dms | Avg %.1f items/roll | %d total items",
                result.simulationCount(), result.durationMs(),
                result.avgItemsPerRoll(), result.totalItemsGenerated());
            graphics.drawString(font, stats, dialogX + 10, contentY, IsotopeColors.TEXT_SECONDARY, false);
            contentY += 16;

            // Column headers
            graphics.fill(dialogX + 5, contentY, dialogX + DIALOG_WIDTH - 5, contentY + ROW_HEIGHT, IsotopeColors.POOL_HEADER_BACKGROUND);
            graphics.drawString(font, "Item", dialogX + 10, contentY + 4, IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, "Simulated", dialogX + 180, contentY + 4, IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, "Expected", dialogX + 260, contentY + 4, IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, "Comparison", dialogX + 340, contentY + 4, IsotopeColors.TEXT_MUTED, false);
            contentY += ROW_HEIGHT;

            // Results list
            int listY = contentY;
            int listHeight = DIALOG_HEIGHT - 140;

            graphics.enableScissor(dialogX + 5, listY, dialogX + DIALOG_WIDTH - 5, listY + listHeight);

            int y = listY - scrollOffset;
            List<ItemResult> items = result.sortedByDropRate();

            for (ItemResult item : items) {
                if (y + ROW_HEIGHT > listY - ROW_HEIGHT && y < listY + listHeight + ROW_HEIGHT) {
                    renderItemRow(graphics, dialogX, y, item, mouseX, mouseY);
                }
                y += ROW_HEIGHT;
            }

            graphics.disableScissor();

            // Scrollbar
            if (maxScroll > 0) {
                int scrollbarX = dialogX + DIALOG_WIDTH - 8;
                int thumbHeight = Math.max(20, listHeight * listHeight / (listHeight + maxScroll));
                int thumbY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - thumbHeight));

                graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + listHeight, IsotopeColors.ENTRY_BACKGROUND);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, IsotopeColors.BUTTON_BACKGROUND);
            }

        } else {
            // Empty state
            int centerY = dialogY + DIALOG_HEIGHT / 2 - 20;
            graphics.drawString(font, "🎲", width / 2 - 6, centerY - 20, IsotopeColors.ENTRY_BACKGROUND_HOVER, false);
            String msg = "Click 'Run Simulation' to start";
            graphics.drawCenteredString(font, msg, width / 2, centerY, IsotopeColors.TEXT_MUTED);
            String hint = "Simulates " + simulationCount + " loot rolls";
            graphics.drawCenteredString(font, hint, width / 2, centerY + 14, IsotopeColors.BUTTON_BACKGROUND);
        }

        // Re-render buttons on top
        for (var child : this.children()) {
            if (child instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderItemRow(GuiGraphics graphics, int dialogX, int y, ItemResult item, int mouseX, int mouseY) {
        boolean hovered = mouseX >= dialogX + 5 && mouseX < dialogX + DIALOG_WIDTH - 5 &&
            mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (hovered) {
            graphics.fill(dialogX + 5, y, dialogX + DIALOG_WIDTH - 10, y + ROW_HEIGHT, IsotopeColors.ENTRY_BACKGROUND);
        }

        // Item name
        String itemName = item.item().getPath();
        if (itemName.contains("/")) {
            itemName = itemName.substring(itemName.lastIndexOf("/") + 1);
        }
        if (font.width(itemName) > 160) {
            itemName = font.plainSubstrByWidth(itemName, 155) + "...";
        }
        graphics.drawString(font, itemName, dialogX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);

        // Simulated rate
        String simRate = String.format("%.1f%%", item.dropRate() * 100);
        graphics.drawString(font, simRate, dialogX + 180, y + 4, IsotopeColors.TEXT_PRIMARY, false);

        // Expected rate
        String expRate = String.format("%.1f%%", item.theoreticalRate() * 100);
        graphics.drawString(font, expRate, dialogX + 260, y + 4, IsotopeColors.TEXT_SECONDARY, false);

        // Comparison bar
        int barX = dialogX + 340;
        int barWidth = 90;
        float variance = item.variance();
        int varColor;

        if (variance < 0.02f) {
            varColor = IsotopeColors.SYNTAX_CYAN; // Good (green)
        } else if (variance < 0.05f) {
            varColor = IsotopeColors.ACCENT_GOLD; // Okay (yellow)
        } else {
            varColor = IsotopeColors.ERROR_BRIGHT; // High variance (red)
        }

        // Draw mini bar showing sim vs expected
        float simPct = Math.min(1f, item.dropRate());
        float expPct = Math.min(1f, item.theoreticalRate());

        graphics.fill(barX, y + 3, barX + barWidth, y + 3 + BAR_HEIGHT, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(barX, y + 3, barX + (int)(barWidth * simPct), y + 3 + BAR_HEIGHT / 2, varColor);
        graphics.fill(barX, y + 3 + BAR_HEIGHT / 2, barX + (int)(barWidth * expPct), y + 3 + BAR_HEIGHT, IsotopeColors.STAR_MUTED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (result != null && maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
