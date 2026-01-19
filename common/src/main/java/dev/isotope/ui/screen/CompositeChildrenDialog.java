package dev.isotope.ui.screen;

import dev.isotope.data.loot.LootEntry;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import dev.isotope.compat.RegistryHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for viewing and editing children of composite entries (alternatives, group, sequence).
 */
public class CompositeChildrenDialog extends DialogScreen {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 320;
    private static final int CHILD_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 50;
    private static final int BUTTON_HEIGHT = 28;

    private final int poolIdx;
    private final int entryIdx;
    private final String compositeType;
    private final List<LootEntry> children;
    private final Consumer<ChildOperation> onOperation;

    private int scrollOffset = 0;
    private int hoveredChildIdx = -1;

    public sealed interface ChildOperation {
        record Add(int index, LootEntry child) implements ChildOperation {}
        record Remove(int index) implements ChildOperation {}
        record Modify(int index, LootEntry newChild) implements ChildOperation {}
    }

    public CompositeChildrenDialog(@Nullable Screen parent, int poolIdx, int entryIdx,
                                    String compositeType, List<LootEntry> children,
                                    Consumer<ChildOperation> onOperation) {
        super(parent, "Edit Composite Entry");
        this.poolIdx = poolIdx;
        this.entryIdx = entryIdx;
        this.compositeType = compositeType;
        this.children = new ArrayList<>(children);
        this.onOperation = onOperation;
    }

    @Override
    protected int getDialogWidth() {
        return DIALOG_WIDTH;
    }

    @Override
    protected int getDialogHeight() {
        return DIALOG_HEIGHT;
    }

    @Override
    protected void renderDialogContent(GuiGraphics graphics, int dialogX, int dialogY, int mouseX, int mouseY, float partialTick) {
        // Subtitle explaining behavior
        String typeLabel = compositeType.replace("minecraft:", "");
        String subtitle = switch (compositeType) {
            case "minecraft:alternatives" -> "First matching child drops (like OR)";
            case "minecraft:group" -> "All children drop together (like AND)";
            case "minecraft:sequence" -> "Children drop in order until one fails";
            default -> "Composite entry with children";
        };
        graphics.drawCenteredString(font, "Edit " + typeLabel + " Entry", dialogX + DIALOG_WIDTH / 2, dialogY + 8, IsotopeColors.ACCENT_GOLD);
        graphics.drawCenteredString(font, subtitle, dialogX + DIALOG_WIDTH / 2, dialogY + 22, IsotopeColors.TEXT_MUTED);

        // Children count
        String countText = children.size() + " children";
        graphics.drawString(font, countText, dialogX + 10, dialogY + 40, IsotopeColors.TEXT_SECONDARY);

        // Add child button
        int addBtnX = dialogX + DIALOG_WIDTH - 110;
        int addBtnY = dialogY + 36;
        boolean addHovered = ScreenUtils.isMouseOver(mouseX, mouseY, addBtnX, addBtnY, 100, 18);
        graphics.fill(addBtnX, addBtnY, addBtnX + 100, addBtnY + 18, addHovered ? IsotopeColors.SUCCESS_BACKGROUND : IsotopeColors.SUCCESS_TINT);
        graphics.drawCenteredString(font, "+ Add Child", addBtnX + 50, addBtnY + 5, addHovered ? IsotopeColors.ACCENT_GREEN : IsotopeColors.SUCCESS_LIGHT);

        // Children list area
        int listX = dialogX + 10;
        int listY = dialogY + HEADER_HEIGHT + 8;
        int listWidth = DIALOG_WIDTH - 20;
        int listHeight = DIALOG_HEIGHT - HEADER_HEIGHT - BUTTON_HEIGHT - 16;

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.renderOutline(listX, listY, listWidth, listHeight, IsotopeColors.BORDER_DEFAULT);

        // Render visible children
        int maxVisible = listHeight / CHILD_HEIGHT;
        hoveredChildIdx = -1;

        for (int i = 0; i < Math.min(maxVisible, children.size() - scrollOffset); i++) {
            int childIdx = scrollOffset + i;
            if (childIdx >= children.size()) break;

            LootEntry child = children.get(childIdx);
            int childY = listY + i * CHILD_HEIGHT;

            boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= childY && mouseY < childY + CHILD_HEIGHT;

            if (isHovered) {
                hoveredChildIdx = childIdx;
                graphics.fill(listX + 1, childY, listX + listWidth - 1, childY + CHILD_HEIGHT, IsotopeColors.POOL_HEADER_HOVER);
            }

            // Index number
            graphics.drawString(font, String.valueOf(childIdx + 1) + ".", listX + 8, childY + 8, IsotopeColors.TEXT_MUTED);

            // Item icon
            int iconX = listX + 30;
            if (child.name().isPresent()) {
                ResourceLocation itemId = child.name().get();
                var itemOpt = RegistryHelper.getItem(itemId);
                if (itemOpt.isPresent()) {
                    ItemStack stack = new ItemStack(itemOpt.get());
                    graphics.renderItem(stack, iconX, childY + 4);
                }
            }

            // Child name
            String childName = child.getDisplayName();
            if (font.width(childName) > 180) {
                childName = font.plainSubstrByWidth(childName, 175) + "...";
            }
            graphics.drawString(font, childName, iconX + 22, childY + 8, IsotopeColors.TEXT_PRIMARY);

            // Weight
            graphics.drawString(font, "W:" + child.weight(), listX + listWidth - 100, childY + 8, IsotopeColors.TEXT_MUTED);

            // Remove button
            int removeBtnX = listX + listWidth - 30;
            boolean removeHovered = mouseX >= removeBtnX && mouseX < removeBtnX + 20 &&
                mouseY >= childY + 4 && mouseY < childY + 20;
            if (removeHovered) {
                graphics.fill(removeBtnX, childY + 4, removeBtnX + 20, childY + 20, IsotopeColors.DESTRUCTIVE_BACKGROUND);
            }
            graphics.drawString(font, "×", removeBtnX + 7, childY + 7, removeHovered ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.TEXT_MUTED);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, "▲ Scroll up", listX + listWidth / 2, listY - 10, IsotopeColors.TEXT_MUTED);
        }
        if (scrollOffset + maxVisible < children.size()) {
            graphics.drawCenteredString(font, "▼ Scroll down", listX + listWidth / 2, listY + listHeight + 2, IsotopeColors.TEXT_MUTED);
        }

        // Empty state
        if (children.isEmpty()) {
            graphics.drawCenteredString(font, "No children yet", listX + listWidth / 2, listY + listHeight / 2 - 4, IsotopeColors.TEXT_MUTED);
            graphics.drawCenteredString(font, "Click '+ Add Child' to add one", listX + listWidth / 2, listY + listHeight / 2 + 8, IsotopeColors.TEXT_MUTED);
        }

        // Close button
        int closeBtnX = dialogX + (DIALOG_WIDTH - 80) / 2;
        int closeBtnY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT;
        boolean closeHovered = ScreenUtils.isMouseOver(mouseX, mouseY, closeBtnX, closeBtnY, 80, 20);
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 80, closeBtnY + 20, closeHovered ? IsotopeColors.TEXT_DISABLED : IsotopeColors.ENTRY_BACKGROUND);
        graphics.drawCenteredString(font, "Close", closeBtnX + 40, closeBtnY + 6, closeHovered ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int dialogX = getDialogX();
        int dialogY = getDialogY();

        // Add child button
        int addBtnX = dialogX + DIALOG_WIDTH - 110;
        int addBtnY = dialogY + 36;
        if (ScreenUtils.isMouseOver(mouseX, mouseY, addBtnX, addBtnY, 100, 18)) {
            openAddChildDialog();
            return true;
        }

        // Close button
        int closeBtnX = dialogX + (DIALOG_WIDTH - 80) / 2;
        int closeBtnY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT;
        if (ScreenUtils.isMouseOver(mouseX, mouseY, closeBtnX, closeBtnY, 80, 20)) {
            onClose();
            return true;
        }

        // Children list
        int listX = dialogX + 10;
        int listY = dialogY + HEADER_HEIGHT + 8;
        int listWidth = DIALOG_WIDTH - 20;
        int listHeight = DIALOG_HEIGHT - HEADER_HEIGHT - BUTTON_HEIGHT - 16;
        int maxVisible = listHeight / CHILD_HEIGHT;

        for (int i = 0; i < Math.min(maxVisible, children.size() - scrollOffset); i++) {
            int childIdx = scrollOffset + i;
            if (childIdx >= children.size()) break;

            int childY = listY + i * CHILD_HEIGHT;

            // Remove button
            int removeBtnX = listX + listWidth - 30;
            if (mouseX >= removeBtnX && mouseX < removeBtnX + 20 &&
                mouseY >= childY + 4 && mouseY < childY + 20) {
                removeChild(childIdx);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dialogX = getDialogX();
        int dialogY = getDialogY();
        int listX = dialogX + 10;
        int listY = dialogY + HEADER_HEIGHT + 8;
        int listWidth = DIALOG_WIDTH - 20;
        int listHeight = DIALOG_HEIGHT - HEADER_HEIGHT - BUTTON_HEIGHT - 16;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
            } else if (scrollY < 0) {
                int maxVisible = listHeight / CHILD_HEIGHT;
                if (scrollOffset + maxVisible < children.size()) {
                    scrollOffset++;
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void openAddChildDialog() {
        if (minecraft == null) return;

        // Open item picker to create a new child entry
        minecraft.setScreen(new ItemPickerScreen(this, item -> {
            if (item != null) {
                LootEntry newChild = LootEntry.item(item);
                int index = children.size();
                children.add(newChild);
                onOperation.accept(new ChildOperation.Add(index, newChild));
            }
        }));
    }

    private void removeChild(int index) {
        if (index >= 0 && index < children.size()) {
            children.remove(index);
            onOperation.accept(new ChildOperation.Remove(index));

            // Adjust scroll if needed
            int listHeight = DIALOG_HEIGHT - HEADER_HEIGHT - BUTTON_HEIGHT - 16;
            int maxVisible = listHeight / CHILD_HEIGHT;
            if (scrollOffset > 0 && scrollOffset + maxVisible > children.size()) {
                scrollOffset = Math.max(0, children.size() - maxVisible);
            }
        }
    }

    // keyPressed for escape, onClose, and isPauseScreen are provided by DialogScreen base class
}
