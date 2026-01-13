package dev.isotope.ui.widget;

import dev.isotope.analysis.OrphanDetector;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.validation.LootTableValidator;
import dev.isotope.validation.LootTableValidator.IssueType;
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

    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 24;
    private static final int ICON_SIZE = 8;
    private static final int FIX_BUTTON_WIDTH = 30;

    @Nullable
    private ResourceLocation currentTableId;
    @Nullable
    private ValidationResult validationResult;
    private List<ValidationIssue> displayedIssues = new ArrayList<>();
    private boolean isOrphan = false;

    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private int hoveredFixButton = -1; // Index of hovered fix button

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

        if (displayedIssues.isEmpty() && !isOrphan) {
            // No issues - show success
            graphics.drawString(mc.font, "\u2714 No issues found", getX() + 8, contentY + 10, 0xFF4ade80, false);
            return;
        }

        int issueStartY = contentY;

        // Orphan warning (shown above validation issues)
        if (isOrphan) {
            int orphanY = contentY + 4;
            graphics.fill(getX() + 4, orphanY, getX() + width - 4, orphanY + ROW_HEIGHT, 0xFF3a3020);
            graphics.fill(getX() + 4, orphanY + 2, getX() + 7, orphanY + ROW_HEIGHT - 2, IsotopeColors.STATUS_WARNING);
            graphics.drawString(mc.font, "\u26A0", getX() + 12, orphanY + 4, IsotopeColors.STATUS_WARNING, false);
            graphics.drawString(mc.font, "Orphan", getX() + 26, orphanY + 4, IsotopeColors.TEXT_PRIMARY, false);
            graphics.drawString(mc.font, "Not linked to any structure", getX() + 12, orphanY + 16,
                IsotopeColors.TEXT_SECONDARY, false);
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

            graphics.fill(getX() + width - 6, contentY, getX() + width - 2, getY() + height, 0xFF1a1a1a);
            graphics.fill(getX() + width - 5, scrollbarY, getX() + width - 3, scrollbarY + scrollbarHeight, IsotopeColors.TEXT_MUTED);
        }
    }

    private void renderIssue(GuiGraphics graphics, Minecraft mc, ValidationIssue issue, int index,
                             int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
        boolean selected = index == selectedIndex;
        boolean canFix = canAutoFix(issue);

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

        // Fix button (if issue can be auto-fixed)
        if (canFix) {
            int btnX = x + width - FIX_BUTTON_WIDTH - 4;
            int btnY = y + 2;
            int btnH = ROW_HEIGHT - 4;
            boolean fixHovered = mouseX >= btnX && mouseX < btnX + FIX_BUTTON_WIDTH &&
                                 mouseY >= btnY && mouseY < btnY + btnH;

            if (fixHovered) {
                hoveredFixButton = index;
                graphics.fill(btnX, btnY, btnX + FIX_BUTTON_WIDTH, btnY + btnH, 0xFF4ade80);
            } else {
                graphics.fill(btnX, btnY, btnX + FIX_BUTTON_WIDTH, btnY + btnH, 0xFF2d5a3d);
            }
            graphics.drawString(mc.font, "Fix", btnX + 8, y + 8, fixHovered ? 0xFF000000 : 0xFFFFFFFF, false);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
