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
 * Generic scrollable list widget for ISOTOPE UI.
 */
@Environment(EnvType.CLIENT)
public class ScrollableListWidget<T> extends AbstractWidget {

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
        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_DARK);

        // Border
        int borderColor = IsotopeColors.BORDER_DEFAULT;
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);

        // Enable scissor for content clipping
        graphics.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);

        int visibleCount = getVisibleItemCount();
        int contentWidth = hasScrollbar() ? width - 8 : width - 2;

        for (int i = 0; i < visibleCount && scrollOffset + i < items.size(); i++) {
            int index = scrollOffset + i;
            T item = items.get(index);
            int itemY = getY() + 1 + (i * itemHeight);

            boolean isSelected = index == selectedIndex;
            boolean isHovered = mouseX >= getX() && mouseX < getX() + contentWidth
                && mouseY >= itemY && mouseY < itemY + itemHeight;

            // Selection/hover background
            if (isSelected) {
                graphics.fill(getX() + 1, itemY, getX() + 1 + contentWidth, itemY + itemHeight,
                    IsotopeColors.LIST_SELECTED);
            } else if (isHovered) {
                graphics.fill(getX() + 1, itemY, getX() + 1 + contentWidth, itemY + itemHeight,
                    IsotopeColors.LIST_HOVER);
            }

            itemRenderer.render(graphics, item, getX() + 4, itemY, contentWidth - 6, itemHeight,
                isSelected, isHovered);
        }

        graphics.disableScissor();

        // Scrollbar
        if (hasScrollbar()) {
            renderScrollbar(graphics);
        }
    }

    private boolean hasScrollbar() {
        return items.size() > getVisibleItemCount();
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int scrollbarX = getX() + width - 6;
        int trackHeight = height - 2;
        int thumbHeight = Math.max(20, (int) ((float) getVisibleItemCount() / items.size() * trackHeight));
        int thumbY = getY() + 1 + (int) ((float) scrollOffset / getMaxScroll() * (trackHeight - thumbHeight));

        // Track
        graphics.fill(scrollbarX, getY() + 1, scrollbarX + 5, getY() + height - 1,
            IsotopeColors.SCROLLBAR_TRACK);

        // Thumb
        graphics.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight,
            IsotopeColors.SCROLLBAR_THUMB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int relativeY = (int) mouseY - getY() - 1;
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
