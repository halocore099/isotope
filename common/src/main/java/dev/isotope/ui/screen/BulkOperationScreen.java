package dev.isotope.ui.screen;

import dev.isotope.bulk.BulkOperation;
import dev.isotope.bulk.BulkOperation.BulkResult;
import dev.isotope.bulk.BulkOperation.Type;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
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
    private static final int DIALOG_HEIGHT = 350;

    private final Screen parent;

    private Type selectedType = Type.REMOVE_ITEM;
    private EditBox itemInput;
    private EditBox item2Input; // For replace operation

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

        // Item input
        itemInput = new EditBox(font, dialogX + 120, dialogY + 80, 200, 18, Component.literal("Item"));
        itemInput.setHint(Component.literal("minecraft:diamond"));
        addRenderableWidget(itemInput);

        // Second item input (for replace)
        item2Input = new EditBox(font, dialogX + 120, dialogY + 105, 200, 18, Component.literal("Replace with"));
        item2Input.setHint(Component.literal("minecraft:emerald"));
        item2Input.visible = (selectedType == Type.REPLACE_ITEM);
        addRenderableWidget(item2Input);

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
            Component.literal("Close"),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 90, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());
    }

    private void runPreview() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Must be in a world");
            return;
        }

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

        switch (selectedType) {
            case REMOVE_ITEM -> {
                previewResult = BulkOperation.previewRemoveItem(minecraft.getSingleplayerServer(), item);
            }
            case REPLACE_ITEM -> {
                String item2Text = item2Input.getValue().trim();
                if (item2Text.isEmpty()) {
                    IsotopeToast.warning("Input Required", "Enter replacement item");
                    return;
                }
                ResourceLocation item2 = ResourceLocation.tryParse(item2Text);
                if (item2 == null) {
                    IsotopeToast.error("Invalid", "Invalid replacement item ID");
                    return;
                }
                previewResult = BulkOperation.previewReplaceItem(minecraft.getSingleplayerServer(), item, item2);
            }
            default -> {
                IsotopeToast.info("Not Implemented", "This operation is not yet available");
                return;
            }
        }

        applyButton.active = previewResult != null && previewResult.totalChanges() > 0;
        scrollOffset = 0;
    }

    private void applyChanges() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null || previewResult == null) return;

        String itemText = itemInput.getValue().trim();
        ResourceLocation item = ResourceLocation.tryParse(itemText);
        if (item == null) return;

        switch (selectedType) {
            case REMOVE_ITEM -> {
                BulkOperation.applyRemoveItem(minecraft.getSingleplayerServer(), item);
                IsotopeToast.success("Applied", "Removed " + item.getPath() + " from " + previewResult.tablesAffected() + " tables");
            }
            case REPLACE_ITEM -> {
                String item2Text = item2Input.getValue().trim();
                ResourceLocation item2 = ResourceLocation.tryParse(item2Text);
                if (item2 != null) {
                    BulkOperation.applyReplaceItem(minecraft.getSingleplayerServer(), item, item2);
                    IsotopeToast.success("Applied", "Replaced in " + previewResult.tablesAffected() + " tables");
                }
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
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, 0xFF000000);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title bar
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 24, 0xFF252525);
        graphics.drawString(font, "📦 Bulk Operations", dialogX + 10, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Operation type selector
        int y = dialogY + 35;
        graphics.drawString(font, "Operation:", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);

        int btnX = dialogX + 80;
        for (Type type : new Type[]{Type.REMOVE_ITEM, Type.REPLACE_ITEM}) {
            boolean selected = type == selectedType;
            boolean hovered = mouseX >= btnX && mouseX < btnX + 80 && mouseY >= y - 2 && mouseY < y + 12;

            if (selected) {
                graphics.fill(btnX, y - 2, btnX + 80, y + 12, IsotopeColors.ACCENT_GOLD);
            } else if (hovered) {
                graphics.fill(btnX, y - 2, btnX + 80, y + 12, 0xFF404040);
            }

            graphics.drawString(font, type.name, btnX + 4, y, selected ? 0xFF000000 : IsotopeColors.TEXT_PRIMARY, false);
            btnX += 85;
        }

        // Input labels
        y = dialogY + 60;
        graphics.drawString(font, "Item to " + (selectedType == Type.REPLACE_ITEM ? "replace:" : "remove:"),
            dialogX + 10, dialogY + 83, IsotopeColors.TEXT_SECONDARY, false);

        if (selectedType == Type.REPLACE_ITEM) {
            graphics.drawString(font, "Replace with:", dialogX + 10, dialogY + 108, IsotopeColors.TEXT_SECONDARY, false);
        }

        // Preview results
        if (previewResult != null) {
            int previewY = dialogY + 140;
            graphics.fill(dialogX + 5, previewY, dialogX + DIALOG_WIDTH - 5, dialogY + DIALOG_HEIGHT - 40, 0xFF252525);

            String summary = String.format("%d changes across %d tables",
                previewResult.totalChanges(), previewResult.tablesAffected());
            graphics.drawString(font, summary, dialogX + 10, previewY + 5, IsotopeColors.TEXT_PRIMARY, false);

            int listY = previewY + 20;
            int listHeight = DIALOG_HEIGHT - 180;
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
            } else if (child instanceof EditBox box) {
                box.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Operation type selection
        int y = dialogY + 35;
        int btnX = dialogX + 80;
        for (Type type : new Type[]{Type.REMOVE_ITEM, Type.REPLACE_ITEM}) {
            if (mouseX >= btnX && mouseX < btnX + 80 && mouseY >= y - 2 && mouseY < y + 12) {
                selectedType = type;
                item2Input.visible = (type == Type.REPLACE_ITEM);
                previewResult = null;
                applyButton.active = false;
                return true;
            }
            btnX += 85;
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
