package dev.isotope.ui.screen;

import dev.isotope.editing.LootEditManager;
import dev.isotope.export.ExportManager;
import dev.isotope.export.ExportManager.ExportConfig;
import dev.isotope.export.ExportManager.ExportResult;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Export screen for saving analysis results to JSON.
 */
@Environment(EnvType.CLIENT)
public class ExportScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Export Data");

    // Export options (checkboxes)
    private boolean exportStructures = true;
    private boolean exportLootTables = true;
    private boolean exportLinks = true;
    private boolean exportSamples = true;
    private boolean timestampedFolder = true;

    // State
    private boolean exporting = false;
    private ExportResult result = null;
    private final List<String> logMessages = new ArrayList<>();

    // Buttons
    private Button exportButton;
    private Button exportDatapackButton;
    private Button closeButton;

    // Export path
    private EditBox exportPathBox;
    private String defaultExportPath;

    public ExportScreen(@Nullable Screen parent) {
        super(TITLE, parent);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        // Checkbox buttons (toggle style)
        int checkboxX = centerX - 100;
        int checkboxWidth = 200;

        addCheckbox(checkboxX, startY, checkboxWidth, "Export Structures", exportStructures,
            () -> exportStructures = !exportStructures);
        addCheckbox(checkboxX, startY + 25, checkboxWidth, "Export Loot Tables", exportLootTables,
            () -> exportLootTables = !exportLootTables);
        addCheckbox(checkboxX, startY + 50, checkboxWidth, "Export Structure-Loot Links", exportLinks,
            () -> exportLinks = !exportLinks);
        addCheckbox(checkboxX, startY + 75, checkboxWidth, "Export Sample Data", exportSamples,
            () -> exportSamples = !exportSamples);
        addCheckbox(checkboxX, startY + 100, checkboxWidth, "Timestamped Folder", timestampedFolder,
            () -> timestampedFolder = !timestampedFolder);

        // Export path input
        int pathY = startY + 130;
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        defaultExportPath = gameDir.resolve("isotope-export").toString();

        exportPathBox = new EditBox(this.font, centerX - 150, pathY, 300, 20, Component.literal("Export Path"));
        exportPathBox.setMaxLength(512);
        exportPathBox.setValue(defaultExportPath);
        exportPathBox.setHint(Component.literal("Export path..."));
        this.addRenderableWidget(exportPathBox);

        // Export button
        int buttonY = this.height - 50;
        exportButton = Button.builder(Component.literal("Export to JSON"), btn -> startExport())
            .pos(centerX - 160, buttonY)
            .size(100, 20)
            .build();
        this.addRenderableWidget(exportButton);

        // Export Datapack button
        int editCount = LootEditManager.getInstance().getEditedTableCount();
        String datapackLabel = "Export Datapack" + (editCount > 0 ? " (" + editCount + ")" : "");
        exportDatapackButton = Button.builder(Component.literal(datapackLabel), btn -> startDatapackExport())
            .pos(centerX - 50, buttonY)
            .size(100, 20)
            .build();
        exportDatapackButton.active = editCount > 0;
        this.addRenderableWidget(exportDatapackButton);

        // Close button
        closeButton = Button.builder(Component.literal("Close"), btn -> onClose())
            .pos(centerX + 60, buttonY)
            .size(100, 20)
            .build();
        this.addRenderableWidget(closeButton);
    }

    private void addCheckbox(int x, int y, int width, String label, boolean initialValue, Runnable toggle) {
        this.addRenderableWidget(
            Button.builder(Component.literal((initialValue ? "[X] " : "[ ] ") + label), btn -> {
                toggle.run();
                // Update button text
                boolean newValue = label.contains("Structures") ? exportStructures :
                                  label.contains("Loot Tables") ? exportLootTables :
                                  label.contains("Links") ? exportLinks :
                                  label.contains("Sample") ? exportSamples : timestampedFolder;
                btn.setMessage(Component.literal((newValue ? "[X] " : "[ ] ") + label));
            })
            .pos(x, y)
            .size(width, 20)
            .build()
        );
    }

    private void startExport() {
        if (exporting) return;

        exporting = true;
        exportButton.active = false;
        logMessages.clear();
        result = null;

        String customPath = exportPathBox.getValue();
        // Use null if it's the default path (let ExportManager use its default logic)
        if (customPath.equals(defaultExportPath)) {
            customPath = null;
        }

        ExportConfig config = new ExportConfig(
            exportStructures,
            exportLootTables,
            exportLinks,
            exportSamples,
            timestampedFolder,
            customPath
        );

        CompletableFuture.supplyAsync(() ->
            ExportManager.getInstance().exportAll(config, this::addLog)
        ).thenAccept(res -> {
            minecraft.execute(() -> {
                this.result = res;
                this.exporting = false;
                this.exportButton.active = true;
                this.exportDatapackButton.active = LootEditManager.getInstance().getEditedTableCount() > 0;

                if (res.success()) {
                    addLog("SUCCESS: Exported " + res.exportedFiles().size() + " files");
                    addLog("Location: " + res.exportDirectory());
                } else {
                    addLog("FAILED: " + res.error());
                }
            });
        });
    }

    private void startDatapackExport() {
        if (exporting) return;

        exporting = true;
        exportButton.active = false;
        exportDatapackButton.active = false;
        logMessages.clear();
        result = null;

        // Generate datapack name with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String packName = "isotope_edits_" + timestamp;

        addLog("Creating datapack: " + packName);

        CompletableFuture.supplyAsync(() ->
            ExportManager.getInstance().exportEditedAsDatapack(packName, this::addLog)
        ).thenAccept(res -> {
            minecraft.execute(() -> {
                this.result = res;
                this.exporting = false;
                this.exportButton.active = true;
                this.exportDatapackButton.active = LootEditManager.getInstance().getEditedTableCount() > 0;

                if (res.success()) {
                    addLog("SUCCESS: Created datapack with " + res.exportedFiles().size() + " files");
                    addLog("Location: " + res.exportDirectory());
                    addLog("Copy to world/datapacks/ to use");
                } else {
                    addLog("FAILED: " + res.error());
                }
            });
        });
    }

    private void addLog(String message) {
        if (minecraft != null) {
            minecraft.execute(() -> {
                logMessages.add(message);
                while (logMessages.size() > 10) {
                    logMessages.remove(0);
                }
            });
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, 20, IsotopeColors.ACCENT_CYAN);

        // Section header
        graphics.drawCenteredString(this.font, "Export Options", centerX, 45, IsotopeColors.TEXT_SECONDARY);

        // Export path label
        graphics.drawString(this.font, "Export Path:", centerX - 150, 180, IsotopeColors.TEXT_MUTED, false);

        // Log panel (below path input)
        int logY = 220;
        int logHeight = this.height - logY - 60;

        graphics.fill(20, logY, this.width - 20, logY + logHeight, IsotopeColors.BACKGROUND_DARK);
        renderBorder(graphics, 20, logY, this.width - 40, logHeight, IsotopeColors.BORDER_DEFAULT);

        graphics.drawString(this.font, "Export Log:", 25, logY + 5, IsotopeColors.TEXT_MUTED);

        int messageY = logY + 18;
        for (String msg : logMessages) {
            int color = msg.startsWith("SUCCESS") ? IsotopeColors.STATUS_SUCCESS :
                       msg.startsWith("FAILED") ? IsotopeColors.STATUS_ERROR :
                       msg.startsWith("Location") ? IsotopeColors.ACCENT_CYAN :
                       IsotopeColors.TEXT_SECONDARY;
            graphics.drawString(this.font, msg, 25, messageY, color, false);
            messageY += 11;
        }

        // Export status
        if (exporting) {
            graphics.drawCenteredString(this.font, "Exporting...", centerX, logY - 15, IsotopeColors.STATUS_WARNING);
        } else if (result != null && result.success()) {
            graphics.drawCenteredString(this.font, "Export Complete!", centerX, logY - 15, IsotopeColors.STATUS_SUCCESS);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
