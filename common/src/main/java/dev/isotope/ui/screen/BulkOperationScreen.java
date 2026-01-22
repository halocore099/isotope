package dev.isotope.ui.screen;

import dev.isotope.bulk.BulkOperation;
import dev.isotope.bulk.BulkOperation.BulkResult;
import dev.isotope.bulk.BulkOperation.Type;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Screen for bulk operations across multiple loot tables.
 */
@Environment(EnvType.CLIENT)
public class BulkOperationScreen extends Screen {

    private static final int DIALOG_WIDTH = 450;
    private static final int DIALOG_HEIGHT = 380;

    private final Screen parent;

    private Type selectedType = Type.REMOVE_ITEM;
    private EditBox itemInput;
    private EditBox item2Input; // For replace operation
    private EditBox scaleInput; // For scale operations

    @Nullable
    private BulkResult previewResult;

    private Button previewButton;
    private Button applyButton;

    private int scrollOffset = 0;

    public BulkOperationScreen(Screen parent) {
        super(Component.literal("Bulk Operations"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Item input (for remove/replace)
        itemInput = new EditBox(font, dialogX + 120, dialogY + 112, 200, 18, Component.literal("Item"));
        itemInput.setHint(Component.literal("minecraft:diamond"));
        itemInput.visible = isItemOperation();
        addRenderableWidget(itemInput);

        // Second item input (for replace)
        item2Input = new EditBox(font, dialogX + 120, dialogY + 137, 200, 18, Component.literal("Replace with"));
        item2Input.setHint(Component.literal("minecraft:emerald"));
        item2Input.visible = (selectedType == Type.REPLACE_ITEM);
        addRenderableWidget(item2Input);

        // Scale factor input (for scale operations)
        scaleInput = new EditBox(font, dialogX + 120, dialogY + 112, 80, 18, Component.literal("Scale"));
        scaleInput.setHint(Component.literal("2.0"));
        scaleInput.setValue("2.0");
        scaleInput.visible = isScaleOperation();
        addRenderableWidget(scaleInput);

        // Preview button
        previewButton = addRenderableWidget(Button.builder(
            Component.literal("Preview"),
            b -> runPreview()
        ).pos(dialogX + 10, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());

        // Apply button
        applyButton = addRenderableWidget(Button.builder(
            Component.literal("Apply"),
            b -> applyChanges()
        ).pos(dialogX + 100, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());
        applyButton.active = false;

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal(UIConstants.LABEL_CLOSE),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 90, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());

        // Help button (top right)
        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.BULK_OPERATIONS)
        ).pos(dialogX + DIALOG_WIDTH - 25, dialogY + 2).size(20, 20).build());
    }

    private boolean isItemOperation() {
        return selectedType == Type.REMOVE_ITEM || selectedType == Type.REPLACE_ITEM;
    }

    private boolean isScaleOperation() {
        return selectedType == Type.SCALE_WEIGHTS || selectedType == Type.SCALE_COUNTS;
    }

    private boolean isNoInputOperation() {
        return selectedType == Type.REMOVE_EMPTY_POOLS || selectedType == Type.NORMALIZE_WEIGHTS;
    }

    private void updateInputVisibility() {
        itemInput.visible = isItemOperation();
        item2Input.visible = (selectedType == Type.REPLACE_ITEM);
        scaleInput.visible = isScaleOperation();
    }

    private void runPreview() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Must be in a world");
            return;
        }

        switch (selectedType) {
            case REMOVE_ITEM -> {
                String itemText = itemInput.getValue().trim();
                if (itemText.isEmpty()) {
                    IsotopeToast.warning("Input Required", "Enter an item ID");
                    return;
                }
                ResourceLocation item = ResourceLocation.tryParse(itemText);
                if (item == null) {
                    IsotopeToast.error("Invalid", "Invalid item ID format");
                    return;
                }
                previewResult = BulkOperation.previewRemoveItem(minecraft.getSingleplayerServer(), item);
            }
            case REPLACE_ITEM -> {
                String itemText = itemInput.getValue().trim();
                String item2Text = item2Input.getValue().trim();
                if (itemText.isEmpty() || item2Text.isEmpty()) {
                    IsotopeToast.warning("Input Required", "Enter both item IDs");
                    return;
                }
                ResourceLocation item = ResourceLocation.tryParse(itemText);
                ResourceLocation item2 = ResourceLocation.tryParse(item2Text);
                if (item == null || item2 == null) {
                    IsotopeToast.error("Invalid", "Invalid item ID format");
                    return;
                }
                previewResult = BulkOperation.previewReplaceItem(minecraft.getSingleplayerServer(), item, item2);
            }
            case SCALE_WEIGHTS -> {
                float scale = parseScale();
                if (scale <= 0) return;
                previewResult = BulkOperation.previewScaleWeights(minecraft.getSingleplayerServer(), scale);
            }
            case SCALE_COUNTS -> {
                float scale = parseScale();
                if (scale <= 0) return;
                previewResult = BulkOperation.previewScaleCounts(minecraft.getSingleplayerServer(), scale);
            }
            case REMOVE_EMPTY_POOLS -> {
                previewResult = BulkOperation.previewRemoveEmptyPools(minecraft.getSingleplayerServer());
            }
            case NORMALIZE_WEIGHTS -> {
                previewResult = BulkOperation.previewNormalizeWeights(minecraft.getSingleplayerServer());
            }
        }

        applyButton.active = previewResult != null && previewResult.totalChanges() > 0;
        scrollOffset = 0;
    }

    private float parseScale() {
        float scale = ScreenUtils.parseFloatSafe(scaleInput.getValue(), -1);
        if (scale <= 0) {
            IsotopeToast.error("Invalid", "Scale must be positive");
            return -1;
        }
        if (scale > 100) {
            IsotopeToast.error("Invalid", "Scale too large (max 100)");
            return -1;
        }
        return scale;
    }

    private void applyChanges() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null || previewResult == null) return;

        switch (selectedType) {
            case REMOVE_ITEM -> {
                ResourceLocation item = ResourceLocation.tryParse(itemInput.getValue().trim());
                if (item != null) {
                    BulkOperation.applyRemoveItem(minecraft.getSingleplayerServer(), item);
                    IsotopeToast.success("Applied", "Removed " + item.getPath() + " from " + previewResult.tablesAffected() + " tables");
                }
            }
            case REPLACE_ITEM -> {
                ResourceLocation item = ResourceLocation.tryParse(itemInput.getValue().trim());
                ResourceLocation item2 = ResourceLocation.tryParse(item2Input.getValue().trim());
                if (item != null && item2 != null) {
                    BulkOperation.applyReplaceItem(minecraft.getSingleplayerServer(), item, item2);
                    IsotopeToast.success("Applied", "Replaced in " + previewResult.tablesAffected() + " tables");
                }
            }
            case SCALE_WEIGHTS -> {
                float scale = parseScale();
                if (scale > 0) {
                    BulkOperation.applyScaleWeights(minecraft.getSingleplayerServer(), scale);
                    IsotopeToast.success("Applied", "Scaled weights in " + previewResult.tablesAffected() + " tables");
                }
            }
            case SCALE_COUNTS -> {
                float scale = parseScale();
                if (scale > 0) {
                    BulkOperation.applyScaleCounts(minecraft.getSingleplayerServer(), scale);
                    IsotopeToast.success("Applied", "Scaled counts in " + previewResult.tablesAffected() + " tables");
                }
            }
            case REMOVE_EMPTY_POOLS -> {
                BulkOperation.applyRemoveEmptyPools(minecraft.getSingleplayerServer());
                IsotopeToast.success("Applied", "Removed " + previewResult.totalChanges() + " empty pools");
            }
            case NORMALIZE_WEIGHTS -> {
                BulkOperation.applyNormalizeWeights(minecraft.getSingleplayerServer());
                IsotopeToast.success("Applied", "Normalized weights in " + previewResult.tablesAffected() + " tables");
            }
        }

        previewResult = null;
        applyButton.active = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Background
        ScreenUtils.drawDialogBackground(graphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT);

        // Title bar
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 24, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.drawString(font, "Bulk Operations", dialogX + 10, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Operation type selector - Row 1
        int y = dialogY + 35;
        graphics.drawString(font, "Operation:", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);

        int btnX = dialogX + 80;
        int btnWidth = 85;
        for (Type type : new Type[]{Type.REMOVE_ITEM, Type.REPLACE_ITEM}) {
            renderTypeButton(graphics, type, btnX, y, btnWidth, mouseX, mouseY);
            btnX += btnWidth + 5;
        }

        // Row 2 for scale operations
        y = dialogY + 52;
        btnX = dialogX + 80;
        for (Type type : new Type[]{Type.SCALE_WEIGHTS, Type.SCALE_COUNTS}) {
            renderTypeButton(graphics, type, btnX, y, btnWidth, mouseX, mouseY);
            btnX += btnWidth + 5;
        }

        // Row 3 for cleanup operations
        y = dialogY + 69;
        btnX = dialogX + 80;
        for (Type type : new Type[]{Type.REMOVE_EMPTY_POOLS, Type.NORMALIZE_WEIGHTS}) {
            renderTypeButton(graphics, type, btnX, y, btnWidth, mouseX, mouseY);
            btnX += btnWidth + 5;
        }

        // Description
        y = dialogY + 92;
        graphics.drawString(font, selectedType.description, dialogX + 10, y, IsotopeColors.TEXT_MUTED, false);

        // Input labels
        if (isItemOperation()) {
            String label = selectedType == Type.REPLACE_ITEM ? "Item to replace:" : "Item to remove:";
            graphics.drawString(font, label, dialogX + 10, dialogY + 115, IsotopeColors.TEXT_SECONDARY, false);

            if (selectedType == Type.REPLACE_ITEM) {
                graphics.drawString(font, "Replace with:", dialogX + 10, dialogY + 140, IsotopeColors.TEXT_SECONDARY, false);
            }
        } else if (isScaleOperation()) {
            graphics.drawString(font, "Scale factor:", dialogX + 10, dialogY + 115, IsotopeColors.TEXT_SECONDARY, false);
            graphics.drawString(font, "(e.g., 2.0 = double, 0.5 = halve)", dialogX + 210, dialogY + 115, IsotopeColors.TEXT_MUTED, false);
        } else if (isNoInputOperation()) {
            graphics.drawString(font, "No parameters required - click Preview", dialogX + 10, dialogY + 115, IsotopeColors.TEXT_MUTED, false);
        }

        // Preview results
        if (previewResult != null) {
            int previewY = dialogY + 150;
            graphics.fill(dialogX + 5, previewY, dialogX + DIALOG_WIDTH - 5, dialogY + DIALOG_HEIGHT - 40, IsotopeColors.POOL_HEADER_BACKGROUND);

            String summary = String.format("%d changes across %d tables",
                previewResult.totalChanges(), previewResult.tablesAffected());
            graphics.drawString(font, summary, dialogX + 10, previewY + 5, IsotopeColors.TEXT_PRIMARY, false);

            int listY = previewY + 20;
            int listHeight = DIALOG_HEIGHT - 210;
            graphics.enableScissor(dialogX + 5, listY, dialogX + DIALOG_WIDTH - 5, listY + listHeight);

            int entryY = listY - scrollOffset;
            for (var entry : previewResult.changesByTable().entrySet()) {
                if (entryY + 12 > listY - 12 && entryY < listY + listHeight + 12) {
                    String tableName = entry.getKey().getPath();
                    if (font.width(tableName) > 300) {
                        tableName = "..." + tableName.substring(tableName.length() - 40);
                    }
                    graphics.drawString(font, "• " + tableName + " (" + entry.getValue().size() + ")",
                        dialogX + 15, entryY, IsotopeColors.TEXT_SECONDARY, false);
                }
                entryY += 14;
            }

            graphics.disableScissor();
        }

        // Re-render widgets
        for (var child : this.children()) {
            if (child instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            } else if (child instanceof EditBox box && box.visible) {
                box.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderTypeButton(GuiGraphics graphics, Type type, int x, int y, int width, int mouseX, int mouseY) {
        boolean selected = type == selectedType;
        boolean hovered = ScreenUtils.isMouseOver(mouseX, mouseY, x, y - 2, width, 14);

        if (selected) {
            graphics.fill(x, y - 2, x + width, y + 12, IsotopeColors.ACCENT_GOLD);
        } else if (hovered) {
            graphics.fill(x, y - 2, x + width, y + 12, IsotopeColors.BORDER_DEFAULT);
        } else {
            graphics.fill(x, y - 2, x + width, y + 12, IsotopeColors.ENTRY_BACKGROUND);
        }

        graphics.drawString(font, type.name, x + 4, y, selected ? IsotopeColors.BORDER_OUTER_DARK : IsotopeColors.TEXT_PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Row 1 operation type selection
        int y = dialogY + 35;
        int btnX = dialogX + 80;
        int btnWidth = 85;
        for (Type type : new Type[]{Type.REMOVE_ITEM, Type.REPLACE_ITEM}) {
            if (ScreenUtils.isMouseOver(mouseX, mouseY, btnX, y - 2, btnWidth, 14)) {
                if (selectedType != type) {
                    selectedType = type;
                    updateInputVisibility();
                    previewResult = null;
                    applyButton.active = false;
                }
                return true;
            }
            btnX += btnWidth + 5;
        }

        // Row 2 operation type selection
        y = dialogY + 52;
        btnX = dialogX + 80;
        for (Type type : new Type[]{Type.SCALE_WEIGHTS, Type.SCALE_COUNTS}) {
            if (ScreenUtils.isMouseOver(mouseX, mouseY, btnX, y - 2, btnWidth, 14)) {
                if (selectedType != type) {
                    selectedType = type;
                    updateInputVisibility();
                    previewResult = null;
                    applyButton.active = false;
                }
                return true;
            }
            btnX += btnWidth + 5;
        }

        // Row 3 operation type selection
        y = dialogY + 69;
        btnX = dialogX + 80;
        for (Type type : new Type[]{Type.REMOVE_EMPTY_POOLS, Type.NORMALIZE_WEIGHTS}) {
            if (ScreenUtils.isMouseOver(mouseX, mouseY, btnX, y - 2, btnWidth, 14)) {
                if (selectedType != type) {
                    selectedType = type;
                    updateInputVisibility();
                    previewResult = null;
                    applyButton.active = false;
                }
                return true;
            }
            btnX += btnWidth + 5;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (previewResult != null) {
            int maxScroll = Math.max(0, previewResult.changesByTable().size() * 14 - 100);
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
