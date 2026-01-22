package dev.isotope.ui.widget;

import dev.isotope.analysis.OrphanDetector;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.validation.LootTableValidator;
import dev.isotope.validation.LootTableValidator.ValidationIssue;
import dev.isotope.validation.LootTableValidator.ValidationResult;
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
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Panel that displays validation issues for the current loot table.
 */
@Environment(EnvType.CLIENT)
public class ValidationPanel extends AbstractWidget {

    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 24;
    private static final int ICON_SIZE = 8;
    private static final int FIX_BUTTON_WIDTH = 30;

    @Nullable
    private Identifier currentTableId;
    @Nullable
    private ValidationResult validationResult;
    private List<ValidationIssue> displayedIssues = new ArrayList<>();
    private boolean isOrphan = false;

    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private int hoveredFixButton = -1; // Index of hovered fix button

    // Orphan tooltip state
    private boolean orphanRowHovered = false;
    private int orphanTooltipX = 0;
    private int orphanTooltipY = 0;

    // Callback when an issue is clicked (poolIdx, entryIdx)
    @Nullable
    private BiConsumer<Integer, Integer> onIssueSelected;

    public ValidationPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Validation"));
    }

    /**
     * Set the loot table to validate.
     */
    public void setTable(@Nullable Identifier tableId) {
        this.currentTableId = tableId;

        if (tableId == null) {
            validationResult = null;
            displayedIssues.clear();
            isOrphan = false;
            return;
        }

        // Check orphan status
        isOrphan = OrphanDetector.getInstance().isOrphanLootTable(tableId);

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
        hoveredFixButton = -1;
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
    public void refresh(Identifier tableId) {
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
                graphics.fill(badgeX, getY() + 5, badgeX + errorWidth, getY() + 17, IsotopeColors.ERROR_BRIGHT);
                graphics.drawString(mc.font, errorText, badgeX + 4, getY() + 7, IsotopeColors.TEXT_PRIMARY, false);
                badgeX -= 4;
            }

            if (validationResult.warningCount() > 0) {
                String warnText = String.valueOf(validationResult.warningCount());
                int warnWidth = mc.font.width(warnText) + 8;
                badgeX -= warnWidth;
                graphics.fill(badgeX, getY() + 5, badgeX + warnWidth, getY() + 17, IsotopeColors.CONFIDENCE_LOW);
                graphics.drawString(mc.font, warnText, badgeX + 4, getY() + 7, IsotopeColors.BORDER_OUTER_DARK, false);
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

        if (displayedIssues.isEmpty() && !isOrphan) {
            // No issues - show success
            graphics.drawString(mc.font, "\u2714 No issues found", getX() + 8, contentY + 10, IsotopeColors.ACCENT_GREEN, false);
            return;
        }

        int issueStartY = contentY;

        // Orphan warning (shown above validation issues)
        orphanRowHovered = false;
        if (isOrphan) {
            int orphanY = contentY + 4;
            int orphanRowX = getX() + 4;
            int orphanRowW = width - 8;

            // Check if hovering over orphan row
            orphanRowHovered = mouseX >= orphanRowX && mouseX < orphanRowX + orphanRowW &&
                mouseY >= orphanY && mouseY < orphanY + ROW_HEIGHT;

            // Highlight on hover
            int bgColor = orphanRowHovered ? IsotopeColors.ORPHAN_HOVER : IsotopeColors.ITEM_BOOKMARKS_HOVER;
            graphics.fill(orphanRowX, orphanY, getX() + width - 4, orphanY + ROW_HEIGHT, bgColor);
            graphics.fill(orphanRowX, orphanY + 2, getX() + 7, orphanY + ROW_HEIGHT - 2, IsotopeColors.STATUS_WARNING);
            graphics.drawString(mc.font, "\u26A0", getX() + 12, orphanY + 4, IsotopeColors.STATUS_WARNING, false);
            graphics.drawString(mc.font, "Orphan", getX() + 26, orphanY + 4, IsotopeColors.TEXT_PRIMARY, false);
            graphics.drawString(mc.font, "Not linked to any structure", getX() + 12, orphanY + 16,
                IsotopeColors.TEXT_SECONDARY, false);

            if (orphanRowHovered) {
                orphanTooltipX = mouseX + 8;
                orphanTooltipY = orphanY + ROW_HEIGHT + 4;
            }

            issueStartY += ROW_HEIGHT + 4;
        }

        if (displayedIssues.isEmpty()) {
            return;
        }

        // Render issues list
        graphics.enableScissor(getX(), issueStartY, getX() + width, getY() + height);

        int y = issueStartY + 4 - scrollOffset;
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

            graphics.fill(getX() + width - 6, contentY, getX() + width - 2, getY() + height, IsotopeColors.BACKGROUND_MEDIUM);
            graphics.fill(getX() + width - 5, scrollbarY, getX() + width - 3, scrollbarY + scrollbarHeight, IsotopeColors.TEXT_MUTED);
        }

        // Orphan tooltip (render last so it's on top)
        if (orphanRowHovered && currentTableId != null) {
            var orphanInfo = OrphanDetector.getInstance().getLootTableOrphanInfo(currentTableId);
            if (!orphanInfo.reasons().isEmpty()) {
                // Build tooltip lines
                List<String> lines = new ArrayList<>();
                lines.add("Orphan Reasons:");
                for (var reason : orphanInfo.reasons()) {
                    lines.add("• " + reason.getDescription());
                }

                // Calculate tooltip dimensions
                int tooltipWidth = 0;
                for (String line : lines) {
                    tooltipWidth = Math.max(tooltipWidth, mc.font.width(line));
                }
                tooltipWidth += 12;
                int tooltipHeight = lines.size() * 10 + 6;

                // Position tooltip
                int tooltipX = Math.min(orphanTooltipX, getX() + width - tooltipWidth - 4);
                int tooltipY = orphanTooltipY;

                // Draw tooltip background
                graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, IsotopeColors.BORDER_OUTER_DARK);
                graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, IsotopeColors.TOOLTIP_BACKGROUND);
                graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, IsotopeColors.TOOLTIP_BACKGROUND_WARNING);

                // Draw lines
                int lineY = tooltipY + 4;
                for (int i = 0; i < lines.size(); i++) {
                    int color = i == 0 ? IsotopeColors.STATUS_WARNING : IsotopeColors.TEXT_MUTED;
                    graphics.drawString(mc.font, lines.get(i), tooltipX + 4, lineY, color, false);
                    lineY += 10;
                }
            }
        }
    }

    private void renderIssue(GuiGraphics graphics, Minecraft mc, ValidationIssue issue, int index,
                             int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = ScreenUtils.isMouseOver(mouseX, mouseY, x, y, width, ROW_HEIGHT);
        boolean selected = index == selectedIndex;
        boolean canFix = canAutoFix(issue);

        // Background
        if (selected) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, IsotopeColors.ITEM_ENTITY_SELECTED);
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, IsotopeColors.ENTRY_BACKGROUND);
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
            selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_PRIMARY, false);

        // Fix button (if issue can be auto-fixed)
        if (canFix) {
            int btnX = x + width - FIX_BUTTON_WIDTH - 4;
            int btnY = y + 2;
            int btnH = ROW_HEIGHT - 4;
            boolean fixHovered = mouseX >= btnX && mouseX < btnX + FIX_BUTTON_WIDTH &&
                                 mouseY >= btnY && mouseY < btnY + btnH;

            if (fixHovered) {
                hoveredFixButton = index;
                graphics.fill(btnX, btnY, btnX + FIX_BUTTON_WIDTH, btnY + btnH, IsotopeColors.ACCENT_GREEN);
            } else {
                graphics.fill(btnX, btnY, btnX + FIX_BUTTON_WIDTH, btnY + btnH, IsotopeColors.FIX_BUTTON_BG);
            }
            graphics.drawString(mc.font, "Fix", btnX + 8, y + 8, fixHovered ? IsotopeColors.BORDER_OUTER_DARK : IsotopeColors.TEXT_PRIMARY, false);
        }

        // Message (truncated if needed)
        String message = issue.message();
        int maxMessageWidth = width - (canFix ? FIX_BUTTON_WIDTH + 40 : 30);
        if (mc.font.width(message) > maxMessageWidth) {
            message = mc.font.plainSubstrByWidth(message, maxMessageWidth - 10) + "...";
        }
        graphics.drawString(mc.font, message, x + 8, y + 16, IsotopeColors.TEXT_SECONDARY, false);
    }

    /**
     * Check if an issue can be auto-fixed.
     */
    private boolean canAutoFix(ValidationIssue issue) {
        return switch (issue.type()) {
            case ZERO_WEIGHT -> true;        // Set weight to 1
            case ZERO_ROLLS -> true;         // Set rolls to 1
            case EMPTY_POOL -> true;         // Remove pool
            case UNREACHABLE_ENTRY -> true;  // Set first entry weight to 1
            default -> false;
        };
    }

    /**
     * Apply auto-fix for the given issue.
     */
    private void applyFix(ValidationIssue issue) {
        if (currentTableId == null) return;

        LootEditManager manager = LootEditManager.getInstance();
        LootTableStructure structure = manager.getEditedStructure(currentTableId)
            .orElse(manager.getCachedOriginalStructure(currentTableId).orElse(null));

        if (structure == null) return;

        boolean fixed = false;
        String fixMessage = "";

        switch (issue.type()) {
            case ZERO_WEIGHT -> {
                if (issue.entryIndex() >= 0) {
                    manager.applyOperation(currentTableId,
                        new LootEditOperation.ModifyEntryWeight(issue.poolIndex(), issue.entryIndex(), 1));
                    fixed = true;
                    fixMessage = "Set weight to 1";
                }
            }
            case ZERO_ROLLS -> {
                if (issue.poolIndex() >= 0 && issue.poolIndex() < structure.pools().size()) {
                    manager.applyOperation(currentTableId,
                        new LootEditOperation.ModifyPoolRolls(issue.poolIndex(), new NumberProvider.Constant(1)));
                    fixed = true;
                    fixMessage = "Set rolls to 1";
                }
            }
            case EMPTY_POOL -> {
                if (issue.poolIndex() >= 0 && issue.poolIndex() < structure.pools().size()) {
                    manager.applyOperation(currentTableId,
                        new LootEditOperation.RemovePool(issue.poolIndex()));
                    fixed = true;
                    fixMessage = "Removed empty pool";
                }
            }
            case UNREACHABLE_ENTRY -> {
                // All entries have 0 weight - set the first entry to weight 1
                if (issue.poolIndex() >= 0 && issue.poolIndex() < structure.pools().size()) {
                    LootPool pool = structure.pools().get(issue.poolIndex());
                    if (!pool.entries().isEmpty()) {
                        manager.applyOperation(currentTableId,
                            new LootEditOperation.ModifyEntryWeight(issue.poolIndex(), 0, 1));
                        fixed = true;
                        fixMessage = "Set first entry weight to 1";
                    }
                }
            }
            default -> {
                IsotopeToast.info("Cannot Auto-Fix", "This issue requires manual correction");
                return;
            }
        }

        if (fixed) {
            IsotopeToast.success("Fixed", fixMessage);
            // Refresh validation
            setTable(currentTableId);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!isMouseOver(mouseX, mouseY)) return false;

        int contentY = getY() + HEADER_HEIGHT;

        if (mouseY < contentY) return false;

        // Calculate which issue was clicked
        int relativeY = (int) mouseY - contentY + scrollOffset - 4;
        int index = relativeY / ROW_HEIGHT;

        if (index >= 0 && index < displayedIssues.size()) {
            ValidationIssue issue = displayedIssues.get(index);

            // Check if Fix button was clicked
            if (canAutoFix(issue)) {
                int issueY = contentY + 4 + (index * ROW_HEIGHT) - scrollOffset;
                int btnX = getX() + 4 + width - 8 - FIX_BUTTON_WIDTH - 4;
                int btnY = issueY + 2;
                int btnH = ROW_HEIGHT - 4;

                if (mouseX >= btnX && mouseX < btnX + FIX_BUTTON_WIDTH &&
                    mouseY >= btnY && mouseY < btnY + btnH) {
                    applyFix(issue);
                    return true;
                }
            }

            // Normal click - select issue and navigate
            selectedIndex = index;

            // Trigger callback to navigate to issue location
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
     * Get the number of issues (including orphan warning).
     */
    public int getIssueCount() {
        return displayedIssues.size() + (isOrphan ? 1 : 0);
    }

    /**
     * Check if this table is orphaned.
     */
    public boolean isOrphan() {
        return isOrphan;
    }

    /**
     * Get the number of errors.
     */
    public int getErrorCount() {
        return validationResult != null ? validationResult.errorCount() : 0;
    }
}
