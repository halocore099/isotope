package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic scrollable list widget with vanilla Minecraft styling.
 * Uses slot-like rendering with proper selection highlights.
 */
@Environment(EnvType.CLIENT)
public class ScrollableListWidget<T> extends AbstractWidget {

    private static final int SCROLLBAR_WIDTH = 6;

    private final List<T> items = new ArrayList<>();
    private final ItemRenderer<T> itemRenderer;
    private final Consumer<T> onSelect;
    private final int itemHeight;

    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private T selectedItem = null;

    @FunctionalInterface
    public interface ItemRenderer<T> {
        void render(GuiGraphics graphics, T item, int x, int y, int width, int height,
                    boolean selected, boolean hovered);
    }

    public ScrollableListWidget(int x, int y, int width, int height, int itemHeight,
                                 ItemRenderer<T> itemRenderer, Consumer<T> onSelect) {
        super(x, y, width, height, Component.empty());
        this.itemHeight = itemHeight;
        this.itemRenderer = itemRenderer;
        this.onSelect = onSelect;
    }

    public void setItems(List<T> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        this.scrollOffset = 0;

        // Preserve selection if item still exists
        if (selectedItem != null && items.contains(selectedItem)) {
            selectedIndex = items.indexOf(selectedItem);
        } else {
            selectedIndex = -1;
            selectedItem = null;
        }
    }

    public void clearSelection() {
        selectedIndex = -1;
        selectedItem = null;
    }

    public T getSelected() {
        return selectedItem;
    }

    public List<T> getItems() {
        return items;
    }

    public void setSelected(T item) {
        if (items.contains(item)) {
            selectedItem = item;
            selectedIndex = items.indexOf(item);
            ensureVisible(selectedIndex);
        }
    }

    private void ensureVisible(int index) {
        int visibleItems = getVisibleItemCount();
        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + visibleItems) {
            scrollOffset = index - visibleItems + 1;
        }
    }

    private int getVisibleItemCount() {
        return height / itemHeight;
    }

    private int getMaxScroll() {
        return Math.max(0, items.size() - getVisibleItemCount());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark inset background (like inventory slots)
        renderSlotBackground(graphics);

        // Enable scissor for content clipping
        graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

        int visibleCount = getVisibleItemCount();
        int contentWidth = hasScrollbar() ? width - SCROLLBAR_WIDTH - 4 : width - 4;

        for (int i = 0; i < visibleCount && scrollOffset + i < items.size(); i++) {
            int index = scrollOffset + i;
            T item = items.get(index);
            int itemY = getY() + 2 + (i * itemHeight);

            boolean isSelected = index == selectedIndex;
            boolean isHovered = mouseX >= getX() + 2 && mouseX < getX() + 2 + contentWidth
                && mouseY >= itemY && mouseY < itemY + itemHeight;

            // Selection/hover background with vanilla-style highlight
            if (isSelected) {
                // White selection outline
                graphics.fill(getX() + 2, itemY, getX() + 2 + contentWidth, itemY + itemHeight,
                    IsotopeColors.LIST_ITEM_SELECTED);
                // Inner highlight
                renderSelectionBorder(graphics, getX() + 2, itemY, contentWidth, itemHeight);
            } else if (isHovered) {
                graphics.fill(getX() + 2, itemY, getX() + 2 + contentWidth, itemY + itemHeight,
                    IsotopeColors.LIST_ITEM_HOVER);
            }

            itemRenderer.render(graphics, item, getX() + 4, itemY, contentWidth - 4, itemHeight,
                isSelected, isHovered);
        }

        graphics.disableScissor();

        // Scrollbar
        if (hasScrollbar()) {
            renderVanillaScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderSlotBackground(GuiGraphics graphics) {
        int x = getX();
        int y = getY();

        // Dark inner area
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, IsotopeColors.BACKGROUND_SLOT_DARK);

        // 3D border (inset effect like inventory slots)
        // Top shadow
        graphics.fill(x, y, x + width, y + 1, IsotopeColors.BORDER_OUTER_DARK);
        // Left shadow
        graphics.fill(x, y, x + 1, y + height, IsotopeColors.BORDER_OUTER_DARK);
        // Bottom highlight
        graphics.fill(x, y + height - 1, x + width, y + height, IsotopeColors.BORDER_OUTER_LIGHT);
        // Right highlight
        graphics.fill(x + width - 1, y, x + width, y + height, IsotopeColors.BORDER_OUTER_LIGHT);
    }

    private void renderSelectionBorder(GuiGraphics graphics, int x, int y, int width, int height) {
        // White border around selected item
        graphics.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF);
    }

    private boolean hasScrollbar() {
        return items.size() > getVisibleItemCount();
    }

    private void renderVanillaScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int scrollbarX = getX() + width - SCROLLBAR_WIDTH - 1;
        int trackY = getY() + 1;
        int trackHeight = height - 2;

        // Track background
        graphics.fill(scrollbarX, trackY, scrollbarX + SCROLLBAR_WIDTH, trackY + trackHeight,
            IsotopeColors.SCROLLBAR_TRACK);

        // Calculate thumb
        int thumbHeight = Math.max(20, (int) ((float) getVisibleItemCount() / items.size() * trackHeight));
        int maxThumbY = trackHeight - thumbHeight;
        int thumbY = trackY + (getMaxScroll() > 0 ? (int) ((float) scrollOffset / getMaxScroll() * maxThumbY) : 0);

        // Check if mouse is over thumb
        boolean thumbHovered = mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH
            && mouseY >= thumbY && mouseY < thumbY + thumbHeight;

        // Thumb with vanilla styling
        int thumbColor = thumbHovered ? IsotopeColors.SCROLLBAR_THUMB_HOVER : IsotopeColors.SCROLLBAR_THUMB;
        graphics.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);

        // Thumb border
        graphics.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + 1, 0xFFC6C6C6);
        graphics.fill(scrollbarX, thumbY, scrollbarX + 1, thumbY + thumbHeight, 0xFFC6C6C6);
        graphics.fill(scrollbarX, thumbY + thumbHeight - 1, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF555555);
        graphics.fill(scrollbarX + SCROLLBAR_WIDTH - 1, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF555555);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int relativeY = (int) mouseY - getY() - 2;
        int clickedIndex = scrollOffset + (relativeY / itemHeight);

        if (clickedIndex >= 0 && clickedIndex < items.size()) {
            selectedIndex = clickedIndex;
            selectedItem = items.get(clickedIndex);
            if (onSelect != null) {
                onSelect.accept(selectedItem);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        scrollOffset = Mth.clamp(scrollOffset - (int) scrollY, 0, getMaxScroll());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Narration for accessibility
    }
}
