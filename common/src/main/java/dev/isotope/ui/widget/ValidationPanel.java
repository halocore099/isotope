package dev.isotope.ui.widget;

import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.validation.LootTableValidator;
import dev.isotope.validation.LootTableValidator.ValidationIssue;
import dev.isotope.validation.LootTableValidator.ValidationResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Panel that displays validation issues for the current loot table.
 */
@Environment(EnvType.CLIENT)
public class ValidationPanel extends AbstractWidget {

    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 24;
    private static final int ICON_SIZE = 8;

    @Nullable
    private ValidationResult validationResult;
    private List<ValidationIssue> displayedIssues = new ArrayList<>();

    private int scrollOffset = 0;
    private int selectedIndex = -1;

    // Callback when an issue is clicked (poolIdx, entryIdx)
    @Nullable
    private BiConsumer<Integer, Integer> onIssueSelected;

    public ValidationPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Validation"));
    }

    /**
     * Set the loot table to validate.
     */
    public void setTable(@Nullable ResourceLocation tableId) {
        if (tableId == null) {
            validationResult = null;
            displayedIssues.clear();
            return;
        }

        LootTableStructure structure = LootEditManager.getInstance().getEditedStructure(tableId)
            .orElse(LootEditManager.getInstance().getCachedOriginalStructure(tableId).orElse(null));

        if (structure != null) {
            validationResult = LootTableValidator.validate(tableId, structure);
            displayedIssues = new ArrayList<>(validationResult.issues());
        } else {
            validationResult = null;
            displayedIssues.clear();
        }

        scrollOffset = 0;
        selectedIndex = -1;
    }

    /**
     * Set callback for when an issue is selected.
     */
    public void setOnIssueSelected(BiConsumer<Integer, Integer> callback) {
        this.onIssueSelected = callback;
    }

    /**
     * Clear all issues.
     */
    public void clearIssues() {
        validationResult = null;
        displayedIssues.clear();
        scrollOffset = 0;
        selectedIndex = -1;
    }

    /**
     * Refresh validation for the current table.
     */
    public void refresh(ResourceLocation tableId) {
        setTable(tableId);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_DARK);

        // Header
        graphics.fill(getX(), getY(), getX() + width, getY() + HEADER_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);
        graphics.fill(getX(), getY() + HEADER_HEIGHT - 1, getX() + width, getY() + HEADER_HEIGHT, IsotopeColors.BORDER_INNER);

        String headerText = "Validation";
        if (validationResult != null) {
            int total = validationResult.issues().size();
            if (total > 0) {
                headerText = "Validation (" + total + ")";
            }
        }
        graphics.drawString(mc.font, headerText, getX() + 8, getY() + 8, IsotopeColors.TEXT_PRIMARY, false);

        // Summary badges in header
        if (validationResult != null && validationResult.hasIssues()) {
            int badgeX = getX() + width - 8;

            if (validationResult.errorCount() > 0) {
                String errorText = String.valueOf(validationResult.errorCount());
                int errorWidth = mc.font.width(errorText) + 8;
                badgeX -= errorWidth;
                graphics.fill(badgeX, getY() + 5, badgeX + errorWidth, getY() + 17, 0xFFf14c4c);
                graphics.drawString(mc.font, errorText, badgeX + 4, getY() + 7, 0xFFFFFFFF, false);
                badgeX -= 4;
            }

            if (validationResult.warningCount() > 0) {
                String warnText = String.valueOf(validationResult.warningCount());
                int warnWidth = mc.font.width(warnText) + 8;
                badgeX -= warnWidth;
                graphics.fill(badgeX, getY() + 5, badgeX + warnWidth, getY() + 17, 0xFFf0a020);
                graphics.drawString(mc.font, warnText, badgeX + 4, getY() + 7, 0xFF000000, false);
            }
        }

        // Content area
        int contentY = getY() + HEADER_HEIGHT;
        int contentHeight = height - HEADER_HEIGHT;

        if (validationResult == null) {
            graphics.drawString(mc.font, "No table selected", getX() + 8, contentY + 10,
                IsotopeColors.TEXT_MUTED, false);
            return;
        }

        if (displayedIssues.isEmpty()) {
            // No issues - show success
            graphics.drawString(mc.font, "\u2714 No issues found", getX() + 8, contentY + 10, 0xFF4ade80, false);
            return;
        }

        // Render issues list
        graphics.enableScissor(getX(), contentY, getX() + width, getY() + height);

        int y = contentY + 4 - scrollOffset;
        for (int i = 0; i < displayedIssues.size(); i++) {
            if (y + ROW_HEIGHT > contentY - ROW_HEIGHT && y < getY() + height + ROW_HEIGHT) {
                ValidationIssue issue = displayedIssues.get(i);
                renderIssue(graphics, mc, issue, i, getX() + 4, y, width - 8, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }

        graphics.disableScissor();

        // Scrollbar
        if (displayedIssues.size() * ROW_HEIGHT > contentHeight) {
            int scrollbarHeight = Math.max(20, contentHeight * contentHeight / (displayedIssues.size() * ROW_HEIGHT));
            int maxScroll = Math.max(0, displayedIssues.size() * ROW_HEIGHT - contentHeight);
            int scrollbarY = contentY + (maxScroll > 0 ? (scrollOffset * (contentHeight - scrollbarHeight) / maxScroll) : 0);

            graphics.fill(getX() + width - 6, contentY, getX() + width - 2, getY() + height, 0xFF1a1a1a);
            graphics.fill(getX() + width - 5, scrollbarY, getX() + width - 3, scrollbarY + scrollbarHeight, IsotopeColors.TEXT_MUTED);
        }
    }

    private void renderIssue(GuiGraphics graphics, Minecraft mc, ValidationIssue issue, int index,
                             int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
        boolean selected = index == selectedIndex;

        // Background
        if (selected) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, 0xFF3a5a8a);
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, 0xFF2a2a2a);
        }

        // Severity indicator
        int indicatorColor = issue.severity().color;
        graphics.fill(x, y + 2, x + 3, y + ROW_HEIGHT - 2, indicatorColor);

        // Icon based on severity
        String icon = switch (issue.severity()) {
            case ERROR -> "\u2716";   // ✖
            case WARNING -> "\u26A0"; // ⚠
            case INFO -> "\u2139";    // ℹ
        };
        graphics.drawString(mc.font, icon, x + 8, y + 4, indicatorColor, false);

        // Issue type
        graphics.drawString(mc.font, issue.type().name, x + 22, y + 4,
            selected ? 0xFFFFFFFF : IsotopeColors.TEXT_PRIMARY, false);

        // Message (truncated if needed)
        String message = issue.message();
        int maxMessageWidth = width - 30;
        if (mc.font.width(message) > maxMessageWidth) {
            message = mc.font.plainSubstrByWidth(message, maxMessageWidth - 10) + "...";
        }
        graphics.drawString(mc.font, message, x + 8, y + 14, IsotopeColors.TEXT_SECONDARY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int contentY = getY() + HEADER_HEIGHT;

        if (mouseY < contentY) return false;

        // Calculate which issue was clicked
        int relativeY = (int) mouseY - contentY + scrollOffset - 4;
        int index = relativeY / ROW_HEIGHT;

        if (index >= 0 && index < displayedIssues.size()) {
            selectedIndex = index;
            ValidationIssue issue = displayedIssues.get(index);

            // Trigger callback
            if (onIssueSelected != null) {
                onIssueSelected.accept(issue.poolIndex(), issue.entryIndex());
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int contentHeight = height - HEADER_HEIGHT;
        int maxScroll = Math.max(0, displayedIssues.size() * ROW_HEIGHT - contentHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));

        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Accessibility narration
    }

    /**
     * Get the number of issues.
     */
    public int getIssueCount() {
        return displayedIssues.size();
    }

    /**
     * Get the number of errors.
     */
    public int getErrorCount() {
        return validationResult != null ? validationResult.errorCount() : 0;
    }
}
