package dev.isotope.ui.widget;

import dev.isotope.ui.EditorTab;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.TabManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Tab bar widget for multi-tab loot table editing.
 */
@Environment(EnvType.CLIENT)
public class EditorTabBar extends AbstractWidget {

    private static final int TAB_HEIGHT = 22;
    private static final int TAB_MIN_WIDTH = 80;
    private static final int TAB_MAX_WIDTH = 150;
    private static final int TAB_PADDING = 8;
    private static final int CLOSE_BUTTON_SIZE = 10;

    private final TabManager tabManager;

    // Hover state
    private int hoveredTab = -1;
    private int hoveredClose = -1;

    public EditorTabBar(int x, int y, int width, TabManager tabManager) {
        super(x, y, width, TAB_HEIGHT, Component.empty());
        this.tabManager = tabManager;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        List<EditorTab> tabs = tabManager.getTabs();
        int activeIndex = tabManager.getActiveTabIndex();

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1a1a1a);

        if (tabs.isEmpty()) {
            // No tabs message
            graphics.drawString(font, "No tables open", getX() + 10, getY() + 7, IsotopeColors.TEXT_MUTED, false);
            return;
        }

        // Calculate tab widths
        int availableWidth = width - 30; // Leave space for + button
        int tabWidth = Math.max(TAB_MIN_WIDTH, Math.min(TAB_MAX_WIDTH, availableWidth / tabs.size()));

        // Update hover state
        hoveredTab = -1;
        hoveredClose = -1;

        int x = getX();
        for (int i = 0; i < tabs.size(); i++) {
            EditorTab tab = tabs.get(i);
            boolean isActive = i == activeIndex;
            boolean isHovered = mouseX >= x && mouseX < x + tabWidth &&
                mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;

            if (isHovered) {
                hoveredTab = i;
            }

            // Tab background
            int bgColor;
            if (isActive) {
                bgColor = 0xFF2a2a2a;
            } else if (isHovered) {
                bgColor = 0xFF252525;
            } else {
                bgColor = 0xFF1e1e1e;
            }
            graphics.fill(x, getY(), x + tabWidth - 1, getY() + TAB_HEIGHT, bgColor);

            // Active tab indicator
            if (isActive) {
                graphics.fill(x, getY(), x + tabWidth - 1, getY() + 2, IsotopeColors.ACCENT_GOLD);
            }

            // Tab separator
            graphics.fill(x + tabWidth - 1, getY() + 4, x + tabWidth, getY() + TAB_HEIGHT - 4, 0xFF333333);

            // Modified indicator
            if (tab.hasUnsavedChanges()) {
                graphics.drawString(font, "\u2022", x + 4, getY() + 7, IsotopeColors.BADGE_MODIFIED, false);
            }

            // Tab name
            String displayName = tab.displayName();
            int maxTextWidth = tabWidth - TAB_PADDING * 2 - CLOSE_BUTTON_SIZE - 8;
            if (tab.hasUnsavedChanges()) {
                maxTextWidth -= 8; // Space for dot
            }
            if (font.width(displayName) > maxTextWidth) {
                displayName = font.plainSubstrByWidth(displayName, maxTextWidth - 6) + "...";
            }

            int textX = x + TAB_PADDING + (tab.hasUnsavedChanges() ? 8 : 0);
            graphics.drawString(font, displayName, textX, getY() + 7,
                isActive ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);

            // Close button
            int closeX = x + tabWidth - TAB_PADDING - CLOSE_BUTTON_SIZE;
            int closeY = getY() + (TAB_HEIGHT - CLOSE_BUTTON_SIZE) / 2;
            boolean closeHovered = mouseX >= closeX && mouseX < closeX + CLOSE_BUTTON_SIZE &&
                mouseY >= closeY && mouseY < closeY + CLOSE_BUTTON_SIZE;

            if (closeHovered) {
                hoveredClose = i;
                graphics.fill(closeX - 2, closeY - 2, closeX + CLOSE_BUTTON_SIZE + 2, closeY + CLOSE_BUTTON_SIZE + 2, 0xFF4a2a2a);
            }

            graphics.drawString(font, "\u00D7", closeX, closeY, closeHovered ? 0xFFff6666 : IsotopeColors.TEXT_MUTED, false);

            x += tabWidth;
        }

        // + button for new tab (if there's space)
        if (x < getX() + width - 25) {
            int plusX = x + 5;
            boolean plusHovered = mouseX >= plusX && mouseX < plusX + 20 &&
                mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;

            if (plusHovered) {
                graphics.fill(plusX, getY() + 3, plusX + 18, getY() + TAB_HEIGHT - 3, 0xFF2a2a2a);
            }
            graphics.drawString(font, "+", plusX + 5, getY() + 7,
                plusHovered ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_MUTED, false);
        }

        // Bottom border
        graphics.fill(getX(), getY() + TAB_HEIGHT - 1, getX() + width, getY() + TAB_HEIGHT, 0xFF333333);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        List<EditorTab> tabs = tabManager.getTabs();
        int availableWidth = width - 30;
        int tabWidth = tabs.isEmpty() ? TAB_MIN_WIDTH :
            Math.max(TAB_MIN_WIDTH, Math.min(TAB_MAX_WIDTH, availableWidth / tabs.size()));

        // Check close button click first
        if (hoveredClose >= 0) {
            tabManager.closeTab(hoveredClose);
            return true;
        }

        // Check tab click
        if (hoveredTab >= 0) {
            if (button == 1) {
                // Middle click to close
                tabManager.closeTab(hoveredTab);
            } else {
                tabManager.switchTab(hoveredTab);
            }
            return true;
        }

        // Check + button click
        int tabsEndX = getX() + tabs.size() * tabWidth;
        if (mouseX >= tabsEndX + 5 && mouseX < tabsEndX + 25) {
            // This will be handled by parent screen to show table picker
            return true;
        }

        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
