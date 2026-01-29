package dev.isotope.ui.screen;

import dev.isotope.compat.ui.VersionedScreen;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Simple picker dialog for selecting a function or condition to edit.
 * Shows a list of items and calls back with the selected index.
 */
@Environment(EnvType.CLIENT)
public class FunctionConditionPickerScreen extends VersionedScreen {

    private static final int DIALOG_WIDTH = 280;
    private static final int ITEM_HEIGHT = 24;
    private static final int MAX_VISIBLE_ITEMS = 8;

    @Nullable
    private final Screen parent;
    private final String title;
    private final List<String> items;
    private final IntConsumer onSelect;

    private int selectedIndex = -1;
    private int scrollOffset = 0;

    public FunctionConditionPickerScreen(@Nullable Screen parent, String title, List<String> items, IntConsumer onSelect) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.items = items;
        this.onSelect = onSelect;
    }

    private int getDialogHeight() {
        int visibleItems = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        return 60 + visibleItems * ITEM_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        int dialogHeight = getDialogHeight();
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - dialogHeight) / 2;

        // Cancel button
        addRenderableWidget(Button.builder(
            Component.literal(UIConstants.LABEL_CANCEL),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH / 2 - 40, dialogY + dialogHeight - 30).size(80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DARK);

        int dialogHeight = getDialogHeight();
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - dialogHeight) / 2;

        // Dialog background
        ScreenUtils.drawDialogBackground(graphics, dialogX, dialogY, DIALOG_WIDTH, dialogHeight);

        // Header
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 26, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.drawString(font, title, dialogX + 12, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Items list
        int listY = dialogY + 30;
        int listHeight = dialogHeight - 65;
        int visibleItems = listHeight / ITEM_HEIGHT;

        graphics.enableScissor(dialogX + 8, listY, dialogX + DIALOG_WIDTH - 8, listY + listHeight);

        for (int i = 0; i < items.size(); i++) {
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            if (itemY + ITEM_HEIGHT < listY || itemY > listY + listHeight) continue;

            boolean hovered = mouseX >= dialogX + 10 && mouseX < dialogX + DIALOG_WIDTH - 10 &&
                mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean selected = i == selectedIndex;

            if (selected) {
                graphics.fill(dialogX + 10, itemY, dialogX + DIALOG_WIDTH - 10, itemY + ITEM_HEIGHT - 2, IsotopeColors.SINGLE_SELECT_BACKGROUND);
                ScreenUtils.renderOutline(graphics, dialogX + 10, itemY, DIALOG_WIDTH - 20, ITEM_HEIGHT - 2, IsotopeColors.ACCENT_GOLD);
            } else if (hovered) {
                graphics.fill(dialogX + 10, itemY, dialogX + DIALOG_WIDTH - 10, itemY + ITEM_HEIGHT - 2, IsotopeColors.INPUT_BACKGROUND);
            }

            // Index number
            graphics.drawString(font, String.valueOf(i + 1) + ".", dialogX + 15, itemY + 6, IsotopeColors.TEXT_MUTED, false);

            // Item name
            graphics.drawString(font, items.get(i), dialogX + 35, itemY + 6, IsotopeColors.TEXT_PRIMARY, false);
        }

        graphics.disableScissor();

        // Scrollbar if needed
        if (items.size() > visibleItems) {
            int scrollbarX = dialogX + DIALOG_WIDTH - 12;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleItems / items.size());
            int maxScroll = Math.max(0, items.size() - visibleItems);
            int thumbY = listY + (scrollOffset * (scrollbarHeight - thumbHeight) / Math.max(1, maxScroll));

            graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + scrollbarHeight, IsotopeColors.SCROLLBAR_TRACK);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, IsotopeColors.SCROLLBAR_THUMB);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (button != 0) return super.mouseClicked(event, focused);

        int dialogHeight = getDialogHeight();
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - dialogHeight) / 2;
        int listY = dialogY + 30;
        int listHeight = dialogHeight - 65;

        // Check item clicks
        if (mouseX >= dialogX + 10 && mouseX < dialogX + DIALOG_WIDTH - 10 &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < items.size()) {
                // Double-click or single click to select and confirm
                if (selectedIndex == clickedIndex) {
                    // Already selected, this is a double-click - confirm
                    selectAndClose(clickedIndex);
                } else {
                    selectedIndex = clickedIndex;
                }
                return true;
            }
        }

        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        // Enter to confirm selection
        if ((keyCode == UIConstants.KEY_ENTER || keyCode == UIConstants.KEY_NUMPAD_ENTER) && selectedIndex >= 0) {
            selectAndClose(selectedIndex);
            return true;
        }

        // Escape to close
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }

        // Arrow keys to navigate
        if (keyCode == UIConstants.KEY_DOWN && selectedIndex < items.size() - 1) {
            selectedIndex++;
            ensureVisible(selectedIndex);
            return true;
        }
        if (keyCode == UIConstants.KEY_UP && selectedIndex > 0) {
            selectedIndex--;
            ensureVisible(selectedIndex);
            return true;
        }

        // Number keys for quick selection (1-9)
        if (keyCode >= 49 && keyCode <= 57) { // 1-9 keys
            int index = keyCode - 49; // Convert to 0-based index
            if (index < items.size()) {
                selectAndClose(index);
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dialogHeight = getDialogHeight();
        int listHeight = dialogHeight - 65;
        int visibleItems = listHeight / ITEM_HEIGHT;
        int maxScroll = Math.max(0, items.size() - visibleItems);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
        return true;
    }

    private void ensureVisible(int index) {
        int dialogHeight = getDialogHeight();
        int listHeight = dialogHeight - 65;
        int visibleItems = listHeight / ITEM_HEIGHT;

        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + visibleItems) {
            scrollOffset = index - visibleItems + 1;
        }
    }

    private void selectAndClose(int index) {
        onSelect.accept(index);
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
