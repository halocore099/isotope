package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * VS Code-style activity bar for panel switching.
 *
 * Renders as a vertical strip on the left edge with icon buttons.
 * Each icon toggles a different panel/mode.
 */
@Environment(EnvType.CLIENT)
public class ActivityBar extends AbstractWidget {

    public static final int WIDTH = 32;
    private static final int ICON_SIZE = 24;
    private static final int ICON_PADDING = 4;

    private final List<ActivityItem> items = new ArrayList<>();
    private final java.util.Map<String, Integer> badges = new java.util.HashMap<>();
    private int selectedIndex = 0;
    private int hoveredIndex = -1;

    @Nullable
    private Consumer<ActivityItem> onItemSelected;

    public ActivityBar(int x, int y, int height) {
        super(x, y, WIDTH, height, Component.empty());
    }

    /**
     * Add an activity item to the bar.
     */
    public ActivityBar addItem(String id, String iconChar, String tooltip) {
        items.add(new ActivityItem(id, iconChar, tooltip));
        return this;
    }

    /**
     * Set callback for when an item is selected.
     */
    public ActivityBar onSelect(Consumer<ActivityItem> callback) {
        this.onItemSelected = callback;
        return this;
    }

    /**
     * Get the currently selected item ID.
     */
    public String getSelectedId() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            return items.get(selectedIndex).id();
        }
        return "";
    }

    /**
     * Set the selected item by ID.
     */
    public void setSelectedId(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                selectedIndex = i;
                return;
            }
        }
    }

    /**
     * Set the selected item by index.
     */
    public void setSelectedIndex(int index) {
        if (index >= 0 && index < items.size()) {
            selectedIndex = index;
        }
    }

    /**
     * Set a badge count for an item.
     * @param id The item ID
     * @param count The badge count (0 to hide)
     */
    public void setBadge(String id, int count) {
        if (count > 0) {
            badges.put(id, count);
        } else {
            badges.remove(id);
        }
    }

    /**
     * Get the badge count for an item.
     */
    public int getBadge(String id) {
        return badges.getOrDefault(id, 0);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_SOLID);

        // Right border
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, IsotopeColors.BORDER_INNER);

        // Update hovered state
        hoveredIndex = -1;
        if (isMouseOver(mouseX, mouseY)) {
            int relY = mouseY - getY();
            int itemHeight = ICON_SIZE + ICON_PADDING * 2;
            int index = relY / itemHeight;
            if (index >= 0 && index < items.size()) {
                hoveredIndex = index;
            }
        }

        // Render items
        int itemY = getY();
        int itemHeight = ICON_SIZE + ICON_PADDING * 2;

        for (int i = 0; i < items.size(); i++) {
            ActivityItem item = items.get(i);
            boolean isSelected = (i == selectedIndex);
            boolean isHovered = (i == hoveredIndex);

            int itemX = getX();

            // Selection indicator (left edge highlight)
            if (isSelected) {
                graphics.fill(itemX, itemY, itemX + 2, itemY + itemHeight, IsotopeColors.ACCENT_GOLD);
            }

            // Background highlight
            if (isHovered && !isSelected) {
                graphics.fill(itemX + 2, itemY, itemX + width - 1, itemY + itemHeight,
                    IsotopeColors.withAlpha(IsotopeColors.TEXT_PRIMARY, 0x20));
            } else if (isSelected) {
                graphics.fill(itemX + 2, itemY, itemX + width - 1, itemY + itemHeight,
                    IsotopeColors.withAlpha(IsotopeColors.TEXT_PRIMARY, 0x10));
            }

            // Icon (centered)
            int iconX = itemX + (width - 8) / 2; // Approximate center for single char
            int iconY = itemY + ICON_PADDING + (ICON_SIZE - 8) / 2;

            int iconColor = isSelected ? IsotopeColors.TEXT_PRIMARY :
                           isHovered ? IsotopeColors.TEXT_SECONDARY :
                           IsotopeColors.TEXT_MUTED;

            graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                item.iconChar(),
                itemX + width / 2,
                iconY,
                iconColor
            );

            // Render badge if present
            int badgeCount = badges.getOrDefault(item.id(), 0);
            if (badgeCount > 0) {
                String badgeText = badgeCount > 99 ? "99+" : String.valueOf(badgeCount);
                var font = net.minecraft.client.Minecraft.getInstance().font;
                int badgeWidth = Math.max(font.width(badgeText) + 4, 12);
                int badgeX = itemX + width - badgeWidth - 2;
                int badgeY = itemY + 2;

                // Badge background (red for errors)
                graphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 10, IsotopeColors.ERROR_BRIGHT);
                // Badge text
                graphics.drawString(font, badgeText, badgeX + 2, badgeY + 1, IsotopeColors.TEXT_PRIMARY, false);
            }

            itemY += itemHeight;
        }

        // Render tooltip for hovered item
        if (hoveredIndex >= 0 && hoveredIndex < items.size()) {
            ActivityItem hoveredItem = items.get(hoveredIndex);
            int tooltipY = getY() + hoveredIndex * itemHeight + itemHeight / 2;

            // Draw tooltip to the right of the bar
            String tooltip = hoveredItem.tooltip();
            int tooltipWidth = net.minecraft.client.Minecraft.getInstance().font.width(tooltip) + 8;
            int tooltipX = getX() + width + 4;

            graphics.fill(tooltipX - 2, tooltipY - 6, tooltipX + tooltipWidth, tooltipY + 8,
                IsotopeColors.BACKGROUND_SOLID);
            graphics.fill(tooltipX - 2, tooltipY - 6, tooltipX + tooltipWidth, tooltipY - 5,
                IsotopeColors.BORDER_INNER);
            graphics.fill(tooltipX - 2, tooltipY + 7, tooltipX + tooltipWidth, tooltipY + 8,
                IsotopeColors.BORDER_INNER);
            graphics.fill(tooltipX - 2, tooltipY - 6, tooltipX - 1, tooltipY + 8,
                IsotopeColors.BORDER_INNER);
            graphics.fill(tooltipX + tooltipWidth - 1, tooltipY - 6, tooltipX + tooltipWidth, tooltipY + 8,
                IsotopeColors.BORDER_INNER);

            graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                tooltip,
                tooltipX + 2,
                tooltipY - 4,
                IsotopeColors.TEXT_PRIMARY,
                false
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }

        int relY = (int) mouseY - getY();
        int itemHeight = ICON_SIZE + ICON_PADDING * 2;
        int clickedIndex = relY / itemHeight;

        if (clickedIndex >= 0 && clickedIndex < items.size()) {
            selectedIndex = clickedIndex;
            if (onItemSelected != null) {
                onItemSelected.accept(items.get(clickedIndex));
            }
            return true;
        }

        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.literal(items.get(selectedIndex).tooltip()));
        }
    }

    /**
     * Represents an item in the activity bar.
     */
    public record ActivityItem(String id, String iconChar, String tooltip) {}
}
