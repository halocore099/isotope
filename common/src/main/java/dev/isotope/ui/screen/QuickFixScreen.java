package dev.isotope.ui.screen;

import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootTableParser;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import dev.isotope.wizards.QuickFix;
import dev.isotope.wizards.QuickFix.FixResult;
import dev.isotope.wizards.QuickFix.FixType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Screen for selecting and applying quick fix wizards.
 */
@Environment(EnvType.CLIENT)
public class QuickFixScreen extends Screen {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 350;
    private static final int ROW_HEIGHT = 28;

    private final Screen parent;
    private final ResourceLocation tableId;

    @Nullable
    private LootTableStructure structure;

    @Nullable
    private FixType selectedFix;

    @Nullable
    private FixResult previewResult;

    private Button applyButton;

    public QuickFixScreen(Screen parent, ResourceLocation tableId) {
        super(Component.literal("Quick Fix Wizards"));
        this.parent = parent;
        this.tableId = tableId;
    }

    @Override
    protected void init() {
        super.init();

        // Load structure
        structure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(null);
        if (structure == null && minecraft != null && minecraft.getSingleplayerServer() != null) {
            structure = LootTableParser.parse(minecraft.getSingleplayerServer(), tableId).orElse(null);
        }

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Apply button
        applyButton = addRenderableWidget(Button.builder(
            Component.literal("Apply Fix"),
            b -> applySelectedFix()
        ).pos(dialogX + DIALOG_WIDTH - 180, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());
        applyButton.active = false;

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal(UIConstants.LABEL_CLOSE),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 90, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());

        // Help button (top right)
        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.QUICK_FIX_WIZARDS)
        ).pos(dialogX + DIALOG_WIDTH - 25, dialogY + 2).size(20, 20).build());
    }

    private void applySelectedFix() {
        if (previewResult != null && previewResult.changesCount() > 0) {
            QuickFix.apply(tableId, previewResult);
            IsotopeToast.success("Applied", previewResult.changesCount() + " changes made");
            onClose();
        }
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
        graphics.drawString(font, "⚡ Quick Fix Wizards", dialogX + 10, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Table name
        String tableName = tableId.getPath();
        if (tableName.length() > 40) {
            tableName = "..." + tableName.substring(tableName.length() - 37);
        }
        graphics.drawString(font, tableName, dialogX + DIALOG_WIDTH - font.width(tableName) - 10, dialogY + 8,
            IsotopeColors.TEXT_MUTED, false);

        if (structure == null) {
            graphics.drawCenteredString(font, "Failed to load loot table", width / 2, dialogY + 100,
                IsotopeColors.ERROR_BRIGHT);
            return;
        }

        // Fix list
        int listY = dialogY + 30;
        int listHeight = DIALOG_HEIGHT - 120;

        // Left side: Fix options
        int leftWidth = 200;
        graphics.fill(dialogX + 5, listY, dialogX + leftWidth, listY + listHeight, IsotopeColors.POOL_HEADER_BACKGROUND);

        int y = listY + 5;
        for (FixType fix : FixType.values()) {
            boolean isSelected = fix == selectedFix;
            boolean isHovered = mouseX >= dialogX + 5 && mouseX < dialogX + leftWidth &&
                mouseY >= y && mouseY < y + ROW_HEIGHT;

            if (isSelected) {
                graphics.fill(dialogX + 5, y, dialogX + leftWidth, y + ROW_HEIGHT, IsotopeColors.ITEM_ENTITY_SELECTED);
            } else if (isHovered) {
                graphics.fill(dialogX + 5, y, dialogX + leftWidth, y + ROW_HEIGHT, IsotopeColors.POOL_HEADER_HOVER);
            }

            graphics.drawString(font, fix.name, dialogX + 12, y + 5,
                isSelected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_PRIMARY, false);

            // Truncate description if needed
            String desc = fix.description;
            if (font.width(desc) > leftWidth - 20) {
                desc = font.plainSubstrByWidth(desc, leftWidth - 30) + "...";
            }
            graphics.drawString(font, desc, dialogX + 12, y + 16, IsotopeColors.TEXT_MUTED, false);

            y += ROW_HEIGHT;
        }

        // Right side: Preview
        int rightX = dialogX + leftWidth + 10;
        int rightWidth = DIALOG_WIDTH - leftWidth - 20;
        graphics.fill(rightX, listY, rightX + rightWidth, listY + listHeight, IsotopeColors.BACKGROUND_SOLID);

        graphics.drawString(font, "Preview", rightX + 10, listY + 8, IsotopeColors.TEXT_SECONDARY, false);

        if (previewResult != null) {
            int previewY = listY + 25;

            if (previewResult.changesCount() == 0) {
                graphics.drawString(font, "No changes needed", rightX + 10, previewY, IsotopeColors.TEXT_MUTED, false);
            } else {
                graphics.drawString(font, previewResult.changesCount() + " changes:", rightX + 10, previewY,
                    IsotopeColors.TEXT_PRIMARY, false);
                previewY += 14;

                // Show first few operations
                int shown = 0;
                for (var op : previewResult.operations()) {
                    if (shown >= 8) {
                        graphics.drawString(font, "... and " + (previewResult.changesCount() - shown) + " more",
                            rightX + 15, previewY, IsotopeColors.TEXT_MUTED, false);
                        break;
                    }

                    String opText = describeOperation(op);
                    if (font.width(opText) > rightWidth - 20) {
                        opText = font.plainSubstrByWidth(opText, rightWidth - 30) + "...";
                    }
                    graphics.drawString(font, "• " + opText, rightX + 15, previewY, IsotopeColors.TEXT_SECONDARY, false);
                    previewY += 12;
                    shown++;
                }
            }
        } else {
            graphics.drawString(font, "Select a fix to preview", rightX + 10, listY + 50, IsotopeColors.TEXT_MUTED, false);
        }

        // Re-render buttons
        for (var child : this.children()) {
            if (child instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private String describeOperation(dev.isotope.editing.LootEditOperation op) {
        if (op instanceof dev.isotope.editing.LootEditOperation.ModifyEntryWeight mew) {
            return "Set weight to " + mew.newWeight() + " (pool " + mew.poolIndex() + ", entry " + mew.entryIndex() + ")";
        } else if (op instanceof dev.isotope.editing.LootEditOperation.SetItemCount sic) {
            return "Set count (pool " + sic.poolIndex() + ", entry " + sic.entryIndex() + ")";
        } else if (op instanceof dev.isotope.editing.LootEditOperation.RemoveFunction rf) {
            return "Remove function (pool " + rf.poolIndex() + ", entry " + rf.entryIndex() + ")";
        } else if (op instanceof dev.isotope.editing.LootEditOperation.RemoveEntry re) {
            return "Remove entry (pool " + re.poolIndex() + ", entry " + re.entryIndex() + ")";
        }
        return op.getClass().getSimpleName();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (structure == null) return super.mouseClicked(mouseX, mouseY, button);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;
        int listY = dialogY + 30;
        int leftWidth = 200;

        // Check if clicked on a fix option
        if (mouseX >= dialogX + 5 && mouseX < dialogX + leftWidth) {
            int y = listY + 5;
            for (FixType fix : FixType.values()) {
                if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    selectedFix = fix;
                    previewResult = QuickFix.preview(fix, tableId, structure);
                    applyButton.active = previewResult.changesCount() > 0;
                    return true;
                }
                y += ROW_HEIGHT;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
