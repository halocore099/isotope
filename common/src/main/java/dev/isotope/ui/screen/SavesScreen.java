package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.save.AnalysisSaveManager;
import dev.isotope.save.SaveMetadata;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.IsotopeWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Screen for managing analysis saves.
 * Entry point when clicking ISOTOPE button - shows saved analyses and allows
 * creating new ones or loading existing ones.
 */
@Environment(EnvType.CLIENT)
public class SavesScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Analysis Saves");
    private static final int WINDOW_WIDTH = 400;
    private static final int WINDOW_HEIGHT = 300;

    private IsotopeWindow window;
    private SavesList savesList;
    private Button newAnalysisButton;
    private Button loadButton;
    private Button deleteButton;

    @Nullable
    private SaveMetadata selectedSave = null;

    public SavesScreen(@Nullable Screen parent) {
        super(TITLE, parent);
    }

    @Override
    protected void init() {
        super.init();

        // Initialize save manager
        AnalysisSaveManager.getInstance().init();

        // Create centered window
        window = IsotopeWindow.centered(this, WINDOW_WIDTH, WINDOW_HEIGHT, TITLE);

        // Saves list inside window
        int listX = window.getContentX() + 5;
        int listY = window.getContentY() + 5;
        int listWidth = window.getContentWidth() - 10;
        int listHeight = window.getContentHeight() - 50;

        this.savesList = new SavesList(this.minecraft, listWidth, listHeight, listY, 36);
        this.savesList.setX(listX);
        this.addRenderableWidget(this.savesList);

        // Buttons at bottom of window
        int buttonY = window.getContentY() + window.getContentHeight() - 30;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonSpacing = 10;
        int totalButtonsWidth = buttonWidth * 3 + buttonSpacing * 2;
        int buttonStartX = window.getX() + (window.getWidth() - totalButtonsWidth) / 2;

        // New Analysis button
        newAnalysisButton = Button.builder(Component.literal("New Analysis"), btn -> startNewAnalysis())
            .pos(buttonStartX, buttonY)
            .size(buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(newAnalysisButton);

        // Load button
        loadButton = Button.builder(Component.literal("Load"), btn -> loadSelectedSave())
            .pos(buttonStartX + buttonWidth + buttonSpacing, buttonY)
            .size(buttonWidth, buttonHeight)
            .build();
        loadButton.active = false;
        this.addRenderableWidget(loadButton);

        // Delete button
        deleteButton = Button.builder(Component.literal("Delete"), btn -> deleteSelectedSave())
            .pos(buttonStartX + (buttonWidth + buttonSpacing) * 2, buttonY)
            .size(buttonWidth, buttonHeight)
            .build();
        deleteButton.active = false;
        this.addRenderableWidget(deleteButton);

        // Populate the list
        refreshSavesList();
    }

    private void refreshSavesList() {
        this.savesList.children().clear();
        List<SaveMetadata> saves = AnalysisSaveManager.getInstance().listSaves();

        for (SaveMetadata save : saves) {
            this.savesList.children().add(new SaveEntry(save));
        }

        // Clear selection
        this.selectedSave = null;
        updateButtonStates();
    }

    private void onSaveSelected(@Nullable SaveMetadata save) {
        this.selectedSave = save;
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedSave != null;
        loadButton.active = hasSelection;
        deleteButton.active = hasSelection;
    }

    private void startNewAnalysis() {
        // Clear any previous save reference
        AnalysisSaveManager.getInstance().clearCurrentSave();

        // Go to LoadingScreen which handles registry scanning
        Minecraft.getInstance().setScreen(new LoadingScreen(this));
    }

    private void loadSelectedSave() {
        if (selectedSave == null) return;

        Isotope.LOGGER.info("Loading save: {}", selectedSave.id());
        var loadResult = AnalysisSaveManager.getInstance().loadSave(selectedSave.id());

        if (loadResult.isPresent()) {
            // Go directly to MainScreen with loaded data
            Minecraft.getInstance().setScreen(new MainScreen(this));
        } else {
            // Show error (could add a toast/popup here)
            Isotope.LOGGER.error("Failed to load save: {}", selectedSave.id());
        }
    }

    private void deleteSelectedSave() {
        if (selectedSave == null) return;

        Isotope.LOGGER.info("Deleting save: {}", selectedSave.id());
        boolean success = AnalysisSaveManager.getInstance().deleteSave(selectedSave.id());

        if (success) {
            refreshSavesList();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render window frame first
        if (window != null) {
            window.render(graphics, mouseX, mouseY, partialTick);
        }

        // Render widgets on top
        super.render(graphics, mouseX, mouseY, partialTick);

        // Empty state message
        if (savesList.children().isEmpty()) {
            int centerX = window != null ? window.getX() + window.getWidth() / 2 : this.width / 2;
            int centerY = window != null ? window.getY() + window.getHeight() / 2 : this.height / 2;

            graphics.drawCenteredString(
                this.font,
                "No saved analyses found.",
                centerX,
                centerY - 10,
                IsotopeColors.TEXT_MUTED
            );
            graphics.drawCenteredString(
                this.font,
                "Click 'New Analysis' to begin.",
                centerX,
                centerY + 5,
                IsotopeColors.TEXT_MUTED
            );
        }
    }

    // --- Inner classes for list widget ---

    @Environment(EnvType.CLIENT)
    class SavesList extends ObjectSelectionList<SaveEntry> {

        public SavesList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        @Override
        public void setSelected(@Nullable SaveEntry entry) {
            super.setSelected(entry);
            SavesScreen.this.onSaveSelected(entry != null ? entry.save : null);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Dark background for list
            graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), IsotopeColors.BACKGROUND_DARK);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Environment(EnvType.CLIENT)
    class SaveEntry extends ObjectSelectionList.Entry<SaveEntry> {
        private final SaveMetadata save;

        public SaveEntry(SaveMetadata save) {
            this.save = save;
        }

        @Override
        public Component getNarration() {
            return Component.literal(save.getDisplayName());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            // Background
            int bgColor = hovering ? IsotopeColors.LIST_ITEM_HOVER : IsotopeColors.LIST_ITEM_BG;
            if (savesList.getSelected() == this) {
                bgColor = IsotopeColors.LIST_ITEM_SELECTED;
            }
            graphics.fill(left, top, left + width, top + height - 2, bgColor);

            // Name
            graphics.drawString(
                SavesScreen.this.font,
                save.getDisplayName(),
                left + 5,
                top + 3,
                IsotopeColors.TEXT_PRIMARY,
                false
            );

            // Date
            graphics.drawString(
                SavesScreen.this.font,
                save.getFormattedDate(),
                left + 5,
                top + 14,
                IsotopeColors.TEXT_MUTED,
                false
            );

            // Summary (structures, tables, linked)
            graphics.drawString(
                SavesScreen.this.font,
                save.getSummary(),
                left + 5,
                top + 25,
                IsotopeColors.TEXT_SECONDARY,
                false
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                savesList.setSelected(this);
                return true;
            }
            return false;
        }
    }
}
