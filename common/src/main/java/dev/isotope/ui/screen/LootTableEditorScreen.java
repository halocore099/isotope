package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.editing.LootTableParser;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.EntryDetailPanel;
import dev.isotope.ui.widget.EntryListWidget;
import dev.isotope.ui.widget.IsotopeWindow;
import dev.isotope.ui.widget.PoolListWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Loot Table Editor screen with 3-panel layout.
 *
 * Left panel: Pool list
 * Center panel: Entry list (for selected pool)
 * Right panel: Entry details
 *
 * Supports viewing loot table structure and making edits.
 */
@Environment(EnvType.CLIENT)
public class LootTableEditorScreen extends IsotopeScreen {

    // Panel dimensions
    private static final int WINDOW_MARGIN = 15;
    private static final int LEFT_PANEL_WIDTH = 140;
    private static final int RIGHT_PANEL_WIDTH = 200;
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 35;
    private static final int PANEL_GAP = 4;

    // The loot table being edited
    private final ResourceLocation tableId;
    @Nullable
    private LootTableStructure structure;
    @Nullable
    private LootTableStructure editedStructure;

    // Window
    private IsotopeWindow window;

    // Widgets
    private PoolListWidget poolList;
    private EntryListWidget entryList;
    private EntryDetailPanel detailPanel;

    // Buttons
    private Button testModeButton;
    private Button saveButton;
    private Button undoButton;

    // Selection state
    private int selectedPoolIndex = -1;

    public LootTableEditorScreen(ResourceLocation tableId, @Nullable Screen parent) {
        super(Component.literal("Edit: " + tableId.toString()), parent);
        this.tableId = tableId;
    }

    @Override
    protected void init() {
        super.init();

        // Load the loot table
        loadLootTable();

        // Create window frame
        window = IsotopeWindow.fullscreen(this, WINDOW_MARGIN,
            Component.literal("Edit: " + tableId.getPath()));

        int contentX = window.getContentX();
        int contentY = window.getContentY() + HEADER_HEIGHT;
        int contentWidth = window.getContentWidth();
        int contentHeight = window.getContentHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;

        // Calculate panel widths
        int centerPanelWidth = contentWidth - LEFT_PANEL_WIDTH - RIGHT_PANEL_WIDTH - (PANEL_GAP * 2);

        // Create pool list (left panel)
        poolList = new PoolListWidget(
            contentX,
            contentY,
            LEFT_PANEL_WIDTH,
            contentHeight,
            this::onPoolSelected
        );
        this.addRenderableWidget(poolList);

        // Create entry list (center panel)
        entryList = new EntryListWidget(
            contentX + LEFT_PANEL_WIDTH + PANEL_GAP,
            contentY,
            centerPanelWidth,
            contentHeight,
            this::onEntrySelected
        );
        this.addRenderableWidget(entryList);

        // Create detail panel (right panel)
        detailPanel = new EntryDetailPanel(
            contentX + LEFT_PANEL_WIDTH + centerPanelWidth + (PANEL_GAP * 2),
            contentY,
            RIGHT_PANEL_WIDTH,
            contentHeight
        );
        detailPanel.setOnWeightChanged(this::onWeightChanged);
        detailPanel.setOnCountChanged(this::onCountChanged);
        detailPanel.setOnDeleteEntry(this::onDeleteEntry);
        detailPanel.setOnAddFunction(this::onAddFunction);
        detailPanel.setOnRemoveFunction(this::onRemoveFunction);
        detailPanel.setOnAddCondition(this::onAddCondition);
        detailPanel.setOnRemoveCondition(this::onRemoveCondition);
        this.addRenderableWidget(detailPanel);

        // Header buttons
        int headerY = window.getContentY() + 5;

        // Close button
        this.addRenderableWidget(
            Button.builder(Component.literal("X"), button -> onClose())
                .pos(window.getX() + window.getWidth() - 22, window.getY() + 5)
                .size(16, 16)
                .build()
        );

        // Footer buttons
        int footerY = window.getY() + window.getHeight() - FOOTER_HEIGHT + 5;
        int buttonWidth = 100;
        int buttonSpacing = 8;

        // Test mode toggle
        testModeButton = Button.builder(
                Component.literal(getTestModeLabel()),
                button -> toggleTestMode()
            )
            .pos(contentX, footerY)
            .size(buttonWidth + 20, 20)
            .build();
        this.addRenderableWidget(testModeButton);

        // Undo button
        undoButton = Button.builder(
                Component.literal("Undo"),
                button -> undoLastEdit()
            )
            .pos(contentX + buttonWidth + 28, footerY)
            .size(60, 20)
            .build();
        this.addRenderableWidget(undoButton);

        // Save button (right side)
        saveButton = Button.builder(
                Component.literal("Save & Close"),
                button -> saveAndClose()
            )
            .pos(window.getX() + window.getWidth() - WINDOW_MARGIN - buttonWidth - 10, footerY)
            .size(buttonWidth, 20)
            .build();
        this.addRenderableWidget(saveButton);

        // Populate with data
        refreshUI();
    }

    private void loadLootTable() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            Isotope.LOGGER.warn("Cannot load loot table - no server available");
            return;
        }

        // Try to get from cache or parse
        Optional<LootTableStructure> cached = LootEditManager.getInstance().getCachedOriginalStructure(tableId);
        if (cached.isPresent()) {
            structure = cached.get();
        } else {
            Optional<LootTableStructure> parsed = LootTableParser.parse(server, tableId);
            if (parsed.isPresent()) {
                structure = parsed.get();
                LootEditManager.getInstance().cacheOriginalStructure(structure);
            }
        }

        // Get edited structure if exists
        if (LootEditManager.getInstance().hasEdits(tableId)) {
            editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(null);
        } else {
            editedStructure = structure;
        }
    }

    private void refreshUI() {
        LootTableStructure displayStructure = editedStructure != null ? editedStructure : structure;

        if (displayStructure != null) {
            poolList.setPools(displayStructure.pools());

            // Select first pool if none selected
            if (selectedPoolIndex < 0 && !displayStructure.pools().isEmpty()) {
                selectedPoolIndex = 0;
                poolList.setSelected(displayStructure.pools().get(0));
                onPoolSelected(displayStructure.pools().get(0));
            }
        }

        updateButtonStates();
    }

    private void onPoolSelected(LootPool pool) {
        LootTableStructure displayStructure = editedStructure != null ? editedStructure : structure;
        if (displayStructure == null) return;

        selectedPoolIndex = displayStructure.pools().indexOf(pool);
        entryList.setEntries(pool.entries());
        entryList.clearSelection();
        detailPanel.clearEntry();
    }

    private void onEntrySelected(LootEntry entry) {
        LootTableStructure displayStructure = editedStructure != null ? editedStructure : structure;
        if (displayStructure == null || selectedPoolIndex < 0) return;

        LootPool pool = displayStructure.pools().get(selectedPoolIndex);
        int entryIndex = pool.entries().indexOf(entry);
        detailPanel.setEntry(entry, selectedPoolIndex, entryIndex);
    }

    private void onWeightChanged(int entryIndex, int newWeight) {
        if (selectedPoolIndex < 0 || entryIndex < 0) return;

        LootEditOperation op = new LootEditOperation.ModifyEntryWeight(
            selectedPoolIndex, entryIndex, newWeight);
        LootEditManager.getInstance().applyOperation(tableId, op);

        // Refresh
        editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
        refreshCurrentSelection();
    }

    private void onCountChanged(int entryIndex, NumberProvider newCount) {
        if (selectedPoolIndex < 0 || entryIndex < 0) return;

        LootEditOperation op = new LootEditOperation.SetItemCount(
            selectedPoolIndex, entryIndex, newCount);
        LootEditManager.getInstance().applyOperation(tableId, op);

        // Refresh
        editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
        refreshCurrentSelection();
    }

    private void onDeleteEntry() {
        int entryIndex = detailPanel.getEntryIndex();
        if (selectedPoolIndex < 0 || entryIndex < 0) return;

        LootEditOperation op = new LootEditOperation.RemoveEntry(
            selectedPoolIndex, entryIndex);
        LootEditManager.getInstance().applyOperation(tableId, op);

        // Refresh
        editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
        detailPanel.clearEntry();
        refreshUI();
    }

    private void onAddFunction() {
        int entryIndex = detailPanel.getEntryIndex();
        if (selectedPoolIndex < 0 || entryIndex < 0) return;
        if (minecraft == null) return;

        FunctionPickerScreen picker = new FunctionPickerScreen(this, function -> {
            LootEditOperation op = new LootEditOperation.AddFunction(
                selectedPoolIndex, entryIndex, function);
            LootEditManager.getInstance().applyOperation(tableId, op);

            // Refresh
            editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
            refreshCurrentSelection();
        });
        minecraft.setScreen(picker);
    }

    private void onRemoveFunction(int functionIndex) {
        int entryIndex = detailPanel.getEntryIndex();
        if (selectedPoolIndex < 0 || entryIndex < 0 || functionIndex < 0) return;

        LootEditOperation op = new LootEditOperation.RemoveFunction(
            selectedPoolIndex, entryIndex, functionIndex);
        LootEditManager.getInstance().applyOperation(tableId, op);

        // Refresh
        editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
        refreshCurrentSelection();
    }

    private void onAddCondition() {
        int entryIndex = detailPanel.getEntryIndex();
        if (selectedPoolIndex < 0 || entryIndex < 0) return;
        if (minecraft == null) return;

        ConditionPickerScreen picker = new ConditionPickerScreen(this, condition -> {
            LootEditOperation op = new LootEditOperation.AddCondition(
                selectedPoolIndex, entryIndex, condition);
            LootEditManager.getInstance().applyOperation(tableId, op);

            // Refresh
            editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
            refreshCurrentSelection();
        });
        minecraft.setScreen(picker);
    }

    private void onRemoveCondition(int conditionIndex) {
        int entryIndex = detailPanel.getEntryIndex();
        if (selectedPoolIndex < 0 || entryIndex < 0 || conditionIndex < 0) return;

        LootEditOperation op = new LootEditOperation.RemoveCondition(
            selectedPoolIndex, entryIndex, conditionIndex);
        LootEditManager.getInstance().applyOperation(tableId, op);

        // Refresh
        editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
        refreshCurrentSelection();
    }

    private void refreshCurrentSelection() {
        LootTableStructure displayStructure = editedStructure != null ? editedStructure : structure;
        if (displayStructure == null || selectedPoolIndex < 0) return;

        LootPool pool = displayStructure.pools().get(selectedPoolIndex);
        entryList.setEntries(pool.entries());

        // Re-select the same entry index if still valid
        int entryIndex = detailPanel.getEntryIndex();
        if (entryIndex >= 0 && entryIndex < pool.entries().size()) {
            LootEntry entry = pool.entries().get(entryIndex);
            entryList.setSelected(entry);
            detailPanel.setEntry(entry, selectedPoolIndex, entryIndex);
        } else {
            detailPanel.clearEntry();
        }

        updateButtonStates();
    }

    private String getTestModeLabel() {
        boolean active = LootEditManager.getInstance().isTestModeActive();
        return "Test Mode: " + (active ? "ON" : "OFF");
    }

    private void toggleTestMode() {
        LootEditManager.getInstance().toggleTestMode();
        testModeButton.setMessage(Component.literal(getTestModeLabel()));
    }

    private void undoLastEdit() {
        if (LootEditManager.getInstance().undoLastOperation(tableId)) {
            // Refresh edited structure
            editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
            refreshUI();
        }
    }

    private void saveAndClose() {
        // Edits are already saved to LootEditManager
        onClose();
    }

    private void updateButtonStates() {
        boolean hasEdits = LootEditManager.getInstance().hasEdits(tableId);
        undoButton.active = hasEdits;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render window frame
        window.render(graphics, mouseX, mouseY, partialTick);

        // Render header info
        renderHeaderInfo(graphics);

        // Render panel labels
        renderPanelLabels(graphics);

        // Render footer status
        renderFooterStatus(graphics);
    }

    private void renderHeaderInfo(GuiGraphics graphics) {
        int headerY = window.getContentY() + 8;
        int x = window.getContentX() + 5;

        // Table ID
        graphics.drawString(font, "Table: " + tableId.toString(), x, headerY,
            IsotopeColors.TEXT_PRIMARY, false);

        // Table type
        if (structure != null) {
            String typeStr = "Type: " + structure.getTypeDisplayName();
            graphics.drawString(font, typeStr, x, headerY + 12, IsotopeColors.TEXT_SECONDARY, false);

            // Pool/entry counts
            String countStr = structure.getPoolCount() + " pools, " + structure.getTotalEntryCount() + " entries";
            graphics.drawString(font, countStr, x, headerY + 24, IsotopeColors.TEXT_MUTED, false);
        }

        // Edit indicator
        if (LootEditManager.getInstance().hasEdits(tableId)) {
            var edit = LootEditManager.getInstance().getEdit(tableId);
            if (edit != null) {
                String editStr = "[" + edit.getOperationCount() + " edits]";
                int editWidth = font.width(editStr);
                graphics.drawString(font, editStr,
                    window.getX() + window.getWidth() - editWidth - 40, headerY,
                    IsotopeColors.BADGE_MODIFIED, false);
            }
        }
    }

    private void renderPanelLabels(GuiGraphics graphics) {
        int labelY = window.getContentY() + HEADER_HEIGHT - 12;

        // Pool label
        graphics.drawString(font, "Pools", window.getContentX() + 4, labelY,
            IsotopeColors.ACCENT_GOLD, false);

        // Entry label
        int entryLabelX = window.getContentX() + LEFT_PANEL_WIDTH + PANEL_GAP + 4;
        graphics.drawString(font, "Entries", entryLabelX, labelY,
            IsotopeColors.ACCENT_GOLD, false);

        // Details label
        int detailsLabelX = window.getContentX() + window.getContentWidth() - RIGHT_PANEL_WIDTH + 4;
        graphics.drawString(font, "Details", detailsLabelX, labelY,
            IsotopeColors.ACCENT_GOLD, false);
    }

    private void renderFooterStatus(GuiGraphics graphics) {
        // Test mode indicator
        if (LootEditManager.getInstance().isTestModeActive()) {
            int statusY = window.getY() + window.getHeight() - 12;
            graphics.drawString(font, "Test mode active - edits affect loot generation",
                window.getContentX() + 140, statusY,
                IsotopeColors.STATUS_WARNING, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // First, route to detail panel if it's editing a field
        if (detailPanel != null && detailPanel.isEditingField()) {
            if (detailPanel.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // Ctrl+Z for undo
        if (keyCode == 90 && (modifiers & 2) != 0) { // 'Z' with Ctrl
            undoLastEdit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Route to detail panel if it's editing a field
        if (detailPanel != null && detailPanel.isEditingField()) {
            if (detailPanel.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }
}
