package dev.isotope.ui.widget;

import dev.isotope.editing.HistoryLog;
import dev.isotope.editing.HistoryLog.LogEntry;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
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
 * Collapsible panel showing session-wide edit history.
 */
@Environment(EnvType.CLIENT)
public class HistoryLogPanel extends AbstractWidget {

    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 14;
    private static final int PADDING = 4;

    private boolean collapsed = false;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int expandedHeight;

    // Cache
    private List<LogEntry> entries;
    private long lastUpdate = 0;

    public HistoryLogPanel(int x, int y, int width, int expandedHeight) {
        super(x, y, width, expandedHeight, Component.literal("History"));
        this.expandedHeight = expandedHeight;
        refreshEntries();
    }

    /**
     * Toggle collapsed state.
     */
    public void toggleCollapsed() {
        collapsed = !collapsed;
        if (!collapsed) {
            refreshEntries();
        }
    }

    /**
     * Check if collapsed.
     */
    public boolean isCollapsed() {
        return collapsed;
    }

    /**
     * Get actual height (header only if collapsed).
     */
    public int getActualHeight() {
        return collapsed ? HEADER_HEIGHT : expandedHeight;
    }

    /**
     * Refresh entries from the log.
     */
    public void refreshEntries() {
        entries = HistoryLog.getInstance().getAll();

        // Calculate max scroll
        int contentHeight = entries.size() * ROW_HEIGHT;
        int viewHeight = expandedHeight - HEADER_HEIGHT - PADDING;
        maxScroll = Math.max(0, contentHeight - viewHeight);

        // Auto-scroll to bottom for new entries
        if (System.currentTimeMillis() - lastUpdate > 100) {
            scrollOffset = maxScroll;
        }
        lastUpdate = System.currentTimeMillis();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        int renderHeight = getActualHeight();

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + renderHeight, IsotopeColors.BACKGROUND_MEDIUM);
        graphics.renderOutline(getX(), getY(), width, renderHeight, IsotopeColors.BORDER_DEFAULT);

        // Header
        graphics.fill(getX(), getY(), getX() + width, getY() + HEADER_HEIGHT, IsotopeColors.POOL_HEADER_BACKGROUND);

        // Collapse arrow
        String arrow = collapsed ? "▶" : "▼";
        graphics.drawString(font, arrow, getX() + PADDING, getY() + 6, IsotopeColors.TEXT_MUTED, false);

        // Title
        graphics.drawString(font, "History", getX() + PADDING + 12, getY() + 6, IsotopeColors.ACCENT_GOLD, false);

        // Entry count
        int count = HistoryLog.getInstance().getCount();
        String countText = "(" + count + ")";
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, getX() + width - countWidth - PADDING, getY() + 6,
            IsotopeColors.TEXT_MUTED, false);

        if (collapsed) {
            return;
        }

        // Empty state
        if (entries == null || entries.isEmpty()) {
            int centerX = getX() + width / 2;
            int centerY = getY() + HEADER_HEIGHT + 50;
            graphics.drawString(font, "📝", centerX - 6, centerY - 20, IsotopeColors.ENTRY_BACKGROUND_HOVER, false);
            String msg = "No changes yet";
            graphics.drawString(font, msg, centerX - font.width(msg) / 2, centerY, IsotopeColors.TEXT_MUTED, false);
            String hint = "Edit a loot table to see history";
            graphics.drawString(font, hint, centerX - font.width(hint) / 2, centerY + 14, IsotopeColors.BUTTON_BACKGROUND, false);
            return;
        }

        // Entries
        graphics.enableScissor(getX(), getY() + HEADER_HEIGHT, getX() + width, getY() + expandedHeight);

        int y = getY() + HEADER_HEIGHT + PADDING - scrollOffset;

        for (int i = entries.size() - 1; i >= 0; i--) {
            LogEntry entry = entries.get(i);

            if (y + ROW_HEIGHT > getY() + HEADER_HEIGHT && y < getY() + expandedHeight) {
                // Time
                graphics.drawString(font, entry.formattedTime(), getX() + PADDING, y + 2,
                    IsotopeColors.TEXT_MUTED, false);

                // Table name (shortened)
                String tableName = entry.tableId().getPath();
                if (tableName.contains("/")) {
                    tableName = tableName.substring(tableName.lastIndexOf("/") + 1);
                }
                if (font.width(tableName) > 80) {
                    tableName = font.plainSubstrByWidth(tableName, 75) + "..";
                }
                graphics.drawString(font, tableName, getX() + 55, y + 2, IsotopeColors.TEXT_SECONDARY, false);

                // Description
                String desc = entry.description();
                int descX = getX() + 140;
                int maxDescWidth = width - 150;
                if (font.width(desc) > maxDescWidth) {
                    desc = font.plainSubstrByWidth(desc, maxDescWidth - 10) + "...";
                }

                int descColor = getOperationColor(entry.operationType());
                graphics.drawString(font, desc, descX, y + 2, descColor, false);
            }
            y += ROW_HEIGHT;
        }

        graphics.disableScissor();

        // Scrollbar
        ScreenUtils.renderScrollbar(graphics, getX() + width - 4, getY() + HEADER_HEIGHT, 3, expandedHeight - HEADER_HEIGHT,
            scrollOffset, maxScroll, IsotopeColors.ENTRY_BACKGROUND, IsotopeColors.BUTTON_BACKGROUND);
    }

    private int getOperationColor(String operationType) {
        return switch (operationType) {
            case "ADD_POOL", "ADD_ENTRY", "ADD_FUNCTION", "ADD_CONDITION",
                 "ADD_POOL_FUNC", "ADD_POOL_COND", "ADD_TABLE_FUNC", "ADD_CHILD" -> IsotopeColors.ACCENT_GREEN;  // Green
            case "REMOVE_POOL", "REMOVE_ENTRY", "REMOVE_FUNCTION", "REMOVE_CONDITION",
                 "REMOVE_POOL_FUNC", "REMOVE_POOL_COND", "REMOVE_TABLE_FUNC", "REMOVE_CHILD" -> IsotopeColors.STATUS_ERROR;  // Red
            case "UNDO" -> IsotopeColors.SCROLLBAR_THUMB;  // Gray
            case "REDO" -> IsotopeColors.ACCENT_BLUE;  // Light blue
            case "SET_RANDOM_SEQ" -> IsotopeColors.ACCENT_AQUA;  // Cyan
            case "MODIFY_CHILD" -> IsotopeColors.SOURCE_FEATURE;  // Orange
            default -> IsotopeColors.STATUS_WARNING;  // Yellow for modifications
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check header click for collapse toggle
        if (mouseY >= getY() && mouseY < getY() + HEADER_HEIGHT) {
            toggleCollapsed();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!collapsed && isMouseOver(mouseX, mouseY)) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
