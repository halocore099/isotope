package dev.isotope.ui.widget;

import dev.isotope.compat.ui.VersionedWidget;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Right-click context menu widget.
 *
 * Shows a list of actions at the cursor position.
 * Closes when an action is selected or clicked outside.
 */
@Environment(EnvType.CLIENT)
public class ContextMenu extends VersionedWidget {

    private static final int ITEM_HEIGHT = 20;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH = 120;
    private static final int SEPARATOR_HEIGHT = 9;

    private final List<MenuItem> items = new ArrayList<>();
    private int hoveredIndex = -1;

    @Nullable
    private Consumer<Void> onClose;

    /**
     * Create a context menu at the given screen position.
     */
    public ContextMenu(int x, int y) {
        super(x, y, MIN_WIDTH, 0, Component.empty());
    }

    /**
     * Add an action item to the menu.
     */
    public ContextMenu addItem(String icon, String label, @Nullable String shortcut, Runnable action) {
        items.add(new MenuItem(icon, label, shortcut, action, false, true));
        recalculateSize();
        return this;
    }

    /**
     * Add an action item that may be disabled.
     */
    public ContextMenu addItem(String icon, String label, @Nullable String shortcut, Runnable action, boolean enabled) {
        items.add(new MenuItem(icon, label, shortcut, action, false, enabled));
        recalculateSize();
        return this;
    }

    /**
     * Add a separator line.
     */
    public ContextMenu addSeparator() {
        items.add(new MenuItem("", "", null, () -> {}, true, false));
        recalculateSize();
        return this;
    }

    /**
     * Set callback for when menu closes.
     */
    public ContextMenu onClose(Consumer<Void> callback) {
        this.onClose = callback;
        return this;
    }

    /**
     * Reposition the menu (e.g., to keep it on screen).
     */
    public void reposition(int screenWidth, int screenHeight) {
        // Keep menu on screen
        if (getX() + width > screenWidth - 5) {
            setX(screenWidth - width - 5);
        }
        if (getY() + height > screenHeight - 5) {
            setY(screenHeight - height - 5);
        }
        if (getX() < 5) setX(5);
        if (getY() < 5) setY(5);
    }

    private void recalculateSize() {
        var font = Minecraft.getInstance().font;

        // Calculate width based on longest item
        int maxWidth = MIN_WIDTH;
        for (MenuItem item : items) {
            if (!item.separator()) {
                int itemWidth = PADDING * 2 + 16; // icon space
                itemWidth += font.width(item.label()) + 10;
                if (item.shortcut() != null) {
                    itemWidth += font.width(item.shortcut()) + 20;
                }
                maxWidth = Math.max(maxWidth, itemWidth);
            }
        }
        this.width = maxWidth;

        // Calculate height
        int totalHeight = PADDING * 2;
        for (MenuItem item : items) {
            totalHeight += item.separator() ? SEPARATOR_HEIGHT : ITEM_HEIGHT;
        }
        this.height = totalHeight;
    }

    private void close() {
        if (onClose != null) {
            onClose.accept(null);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        // Shadow
        graphics.fill(getX() + 2, getY() + 2, getX() + width + 2, getY() + height + 2, 0x60000000);

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_SOLID);

        // Border
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, IsotopeColors.BORDER_OUTER_LIGHT);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, IsotopeColors.BORDER_OUTER_LIGHT);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, IsotopeColors.BORDER_OUTER_DARK);

        // Update hover state
        hoveredIndex = -1;
        int itemY = getY() + PADDING;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            int itemHeight = item.separator() ? SEPARATOR_HEIGHT : ITEM_HEIGHT;

            if (!item.separator() && item.enabled() &&
                mouseX >= getX() && mouseX < getX() + width &&
                mouseY >= itemY && mouseY < itemY + itemHeight) {
                hoveredIndex = i;
            }
            itemY += itemHeight;
        }

        // Render items
        itemY = getY() + PADDING;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);

            if (item.separator()) {
                // Separator line
                int sepY = itemY + SEPARATOR_HEIGHT / 2;
                graphics.fill(getX() + PADDING, sepY, getX() + width - PADDING, sepY + 1, IsotopeColors.BORDER_INNER);
                itemY += SEPARATOR_HEIGHT;
            } else {
                boolean isHovered = (i == hoveredIndex);
                boolean isEnabled = item.enabled();

                // Hover background
                if (isHovered && isEnabled) {
                    graphics.fill(getX() + 2, itemY, getX() + width - 2, itemY + ITEM_HEIGHT,
                        IsotopeColors.withAlpha(IsotopeColors.ACCENT_GOLD, 0x40));
                }

                // Icon
                int textColor = !isEnabled ? IsotopeColors.TEXT_DISABLED :
                               isHovered ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY;

                graphics.drawString(font, item.icon(), getX() + PADDING + 2, itemY + 6, textColor, false);

                // Label
                graphics.drawString(font, item.label(), getX() + PADDING + 18, itemY + 6, textColor, false);

                // Shortcut (right-aligned)
                if (item.shortcut() != null && !item.shortcut().isEmpty()) {
                    int shortcutWidth = font.width(item.shortcut());
                    graphics.drawString(font, item.shortcut(),
                        getX() + width - shortcutWidth - PADDING - 2, itemY + 6,
                        IsotopeColors.TEXT_MUTED, false);
                }

                itemY += ITEM_HEIGHT;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!isMouseOver(mouseX, mouseY)) {
            close();
            return false;
        }

        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < items.size()) {
            MenuItem item = items.get(hoveredIndex);
            if (item.enabled() && !item.separator()) {
                close();
                item.action().run();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width &&
               mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Component.literal("Context Menu"));
    }

    /**
     * Represents an item in the context menu.
     */
    private record MenuItem(
        String icon,
        String label,
        @Nullable String shortcut,
        Runnable action,
        boolean separator,
        boolean enabled
    ) {}
}
