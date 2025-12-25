package dev.isotope.ui.screen;

import dev.isotope.importing.DatapackImporter;
import dev.isotope.importing.DatapackImporter.DatapackInfo;
import dev.isotope.importing.DatapackImporter.ImportResult;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.TabManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Screen for importing loot tables from datapacks.
 */
@Environment(EnvType.CLIENT)
public class ImportScreen extends Screen {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 320;
    private static final int LIST_ITEM_HEIGHT = 36;
    private static final int PADDING = 10;

    private final Screen parent;
    private final TabManager tabManager;

    // UI state
    private List<DatapackInfo> datapacks = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    @Nullable
    private DatapackInfo selectedDatapack = null;
    private boolean importing = false;
    private List<String> importLog = new ArrayList<>();

    // Widgets
    private EditBox pathField;
    private Button scanButton;
    private Button importButton;
    private Button importPathButton;
    private Button closeButton;

    public ImportScreen(Screen parent, TabManager tabManager) {
        super(Component.literal("Import Datapack"));
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Custom path input
        pathField = new EditBox(font, dialogX + PADDING, dialogY + 30, DIALOG_WIDTH - PADDING * 2 - 100, 20,
            Component.literal("Path"));
        pathField.setHint(Component.literal("Custom datapack path..."));
        pathField.setMaxLength(256);
        addRenderableWidget(pathField);

        // Import from path button
        importPathButton = Button.builder(Component.literal("Import Path"), this::onImportPath)
            .pos(dialogX + DIALOG_WIDTH - PADDING - 90, dialogY + 30)
            .size(80, 20)
            .build();
        addRenderableWidget(importPathButton);

        // Scan button (top right of list)
        scanButton = Button.builder(Component.literal("Scan"), this::onScan)
            .pos(dialogX + DIALOG_WIDTH - PADDING - 50, dialogY + 55)
            .size(40, 16)
            .build();
        addRenderableWidget(scanButton);

        // Bottom buttons
        int buttonY = dialogY + DIALOG_HEIGHT - PADDING - 20;

        importButton = Button.builder(Component.literal("Import Selected"), this::onImportSelected)
            .pos(dialogX + PADDING, buttonY)
            .size(120, 20)
            .build();
        addRenderableWidget(importButton);

        closeButton = Button.builder(Component.literal("Close"), b -> onClose())
            .pos(dialogX + DIALOG_WIDTH - PADDING - 80, buttonY)
            .size(80, 20)
            .build();
        addRenderableWidget(closeButton);

        // Initial scan
        scanForDatapacks();
        updateButtonStates();
    }

    private void scanForDatapacks() {
        datapacks = DatapackImporter.getInstance().findAvailableDatapacks();
        selectedDatapack = null;
        calculateMaxScroll();
        updateButtonStates();
    }

    private void calculateMaxScroll() {
        int listHeight = DIALOG_HEIGHT - 170;
        int contentHeight = datapacks.size() * LIST_ITEM_HEIGHT;
        maxScroll = Math.max(0, contentHeight - listHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void updateButtonStates() {
        importButton.active = selectedDatapack != null && !importing;
        importPathButton.active = !pathField.getValue().trim().isEmpty() && !importing;
        scanButton.active = !importing;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, 0xFF333333);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title
        graphics.drawCenteredString(font, "Import Datapack", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Separator
        int separatorY = dialogY + 55;
        graphics.fill(dialogX + PADDING, separatorY, dialogX + DIALOG_WIDTH - PADDING, separatorY + 1, 0xFF333333);

        // Section header
        graphics.drawString(font, "Available Datapacks:", dialogX + PADDING, dialogY + 60, IsotopeColors.TEXT_PRIMARY, false);

        // List area
        int listX = dialogX + PADDING;
        int listY = dialogY + 75;
        int listWidth = DIALOG_WIDTH - PADDING * 2;
        int listHeight = DIALOG_HEIGHT - 170;

        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF252525);

        if (datapacks.isEmpty() && !importing) {
            graphics.drawCenteredString(font, "No datapacks found with loot tables",
                dialogX + DIALOG_WIDTH / 2, listY + listHeight / 2 - 8, IsotopeColors.TEXT_MUTED);
            graphics.drawCenteredString(font, "Click Scan to refresh",
                dialogX + DIALOG_WIDTH / 2, listY + listHeight / 2 + 4, IsotopeColors.TEXT_MUTED);
        } else {
            // Clip region for scrolling
            graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

            int renderY = listY - scrollOffset;
            for (DatapackInfo info : datapacks) {
                if (renderY + LIST_ITEM_HEIGHT > listY && renderY < listY + listHeight) {
                    boolean isSelected = info.equals(selectedDatapack);
                    boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                        mouseY >= renderY && mouseY < renderY + LIST_ITEM_HEIGHT;

                    // Background
                    if (isSelected) {
                        graphics.fill(listX, renderY, listX + listWidth, renderY + LIST_ITEM_HEIGHT, 0xFF3a5a8a);
                    } else if (isHovered) {
                        graphics.fill(listX, renderY, listX + listWidth, renderY + LIST_ITEM_HEIGHT, 0xFF353535);
                    }

                    // Datapack name
                    int textColor = isSelected ? 0xFFFFFFFF : IsotopeColors.TEXT_PRIMARY;
                    graphics.drawString(font, info.name(), listX + 4, renderY + 4, textColor, false);

                    // Loot table count
                    String countText = info.lootTableCount() + " loot tables";
                    graphics.drawString(font, countText, listX + 4, renderY + 14, IsotopeColors.TEXT_SECONDARY, false);

                    // Description (if available)
                    if (!info.description().isEmpty()) {
                        String desc = info.description();
                        if (font.width(desc) > listWidth - 8) {
                            desc = font.plainSubstrByWidth(desc, listWidth - 16) + "...";
                        }
                        graphics.drawString(font, desc, listX + 4, renderY + 24, IsotopeColors.TEXT_MUTED, false);
                    }

                    // Path on right side
                    String path = info.path().getFileName().toString();
                    int pathWidth = font.width(path);
                    graphics.drawString(font, path, listX + listWidth - pathWidth - 8, renderY + 4,
                        IsotopeColors.TEXT_MUTED, false);
                }
                renderY += LIST_ITEM_HEIGHT;
            }

            graphics.disableScissor();

            // Scrollbar
            if (maxScroll > 0) {
                int scrollbarX = listX + listWidth - 4;
                int thumbHeight = Math.max(20, (int) ((float) listHeight / (listHeight + maxScroll) * listHeight));
                int thumbY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - thumbHeight));

                graphics.fill(scrollbarX, listY, scrollbarX + 3, listY + listHeight, 0xFF2a2a2a);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF555555);
            }
        }

        // Import log (below list if importing)
        if (!importLog.isEmpty()) {
            int logY = listY + listHeight + 5;
            int logHeight = 40;
            graphics.fill(listX, logY, listX + listWidth, logY + logHeight, 0xFF1e1e1e);

            int lineY = logY + 2;
            int linesToShow = Math.min(3, importLog.size());
            for (int i = importLog.size() - linesToShow; i < importLog.size(); i++) {
                String line = importLog.get(i);
                if (font.width(line) > listWidth - 4) {
                    line = font.plainSubstrByWidth(line, listWidth - 10) + "...";
                }
                graphics.drawString(font, line, listX + 2, lineY, IsotopeColors.TEXT_SECONDARY, false);
                lineY += 12;
            }
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        int listX = dialogX + PADDING;
        int listY = dialogY + 75;
        int listWidth = DIALOG_WIDTH - PADDING * 2;
        int listHeight = DIALOG_HEIGHT - 170;

        // Check list clicks
        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int renderY = listY - scrollOffset;
            for (DatapackInfo info : datapacks) {
                if (mouseY >= renderY && mouseY < renderY + LIST_ITEM_HEIGHT) {
                    selectedDatapack = info;
                    updateButtonStates();
                    return true;
                }
                renderY += LIST_ITEM_HEIGHT;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        int listX = dialogX + PADDING;
        int listY = dialogY + 75;
        int listWidth = DIALOG_WIDTH - PADDING * 2;
        int listHeight = DIALOG_HEIGHT - 170;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void onScan(Button button) {
        importLog.clear();
        importLog.add("Scanning for datapacks...");
        scanForDatapacks();
        importLog.add("Found " + datapacks.size() + " datapack(s) with loot tables");
    }

    private void onImportSelected(Button button) {
        if (selectedDatapack == null || importing) return;

        startImport(selectedDatapack.path().toString());
    }

    private void onImportPath(Button button) {
        String path = pathField.getValue().trim();
        if (path.isEmpty() || importing) return;

        startImport(path);
    }

    private void startImport(String pathString) {
        importing = true;
        updateButtonStates();
        importLog.clear();
        importLog.add("Starting import...");

        CompletableFuture.supplyAsync(() ->
            DatapackImporter.getInstance().importFromPath(pathString, log -> {
                if (minecraft != null) {
                    minecraft.execute(() -> {
                        importLog.add(log);
                        // Keep only last 20 lines
                        while (importLog.size() > 20) {
                            importLog.remove(0);
                        }
                    });
                }
            })
        ).thenAccept(result -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    importing = false;
                    updateButtonStates();

                    if (result.success()) {
                        // Apply imported tables
                        DatapackImporter.getInstance().applyImportedTables(result.importedTables());

                        // Open first few in tabs
                        int tabsToOpen = Math.min(5, result.importedTables().size());
                        for (int i = 0; i < tabsToOpen; i++) {
                            tabManager.openTab(result.importedTables().get(i).tableId());
                        }

                        IsotopeToast.success("Import Complete",
                            result.tablesImported() + " tables imported");

                        if (!result.errors().isEmpty()) {
                            importLog.add("Completed with " + result.errors().size() + " error(s)");
                        }
                    } else {
                        IsotopeToast.error("Import Failed",
                            result.errors().isEmpty() ? "Unknown error" : result.errors().get(0));
                    }
                });
            }
        });
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
