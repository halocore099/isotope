package dev.isotope.ui.widget;

import dev.isotope.analysis.StructureDiff;
import dev.isotope.analysis.StructureDiff.DiffEntry;
import dev.isotope.analysis.StructureDiff.DiffResult;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing changes compared to original loot table.
 */
@Environment(EnvType.CLIENT)
public class DiffPanel extends AbstractWidget {

    private static final int HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 14;
    private static final int PADDING = 6;

    @Nullable
    private ResourceLocation tableId;
    @Nullable
    private DiffResult diffResult;
    private List<DiffEntry> allChanges = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public DiffPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Changes"));
    }

    /**
     * Set the table to show diff for.
     */
    public void setTable(@Nullable ResourceLocation tableId) {
        this.tableId = tableId;
        this.scrollOffset = 0;
        recalculate();
    }

    /**
     * Recalculate the diff.
     */
    public void recalculate() {
        diffResult = null;
        allChanges.clear();
        maxScroll = 0;

        if (tableId == null) {
            return;
        }

        LootEditManager manager = LootEditManager.getInstance();

        // Get original
        var originalOpt = manager.getCachedOriginalStructure(tableId);
        if (originalOpt.isEmpty()) {
            return;
        }
        LootTableStructure original = originalOpt.get();

        // Get edited
        var editedOpt = manager.getEditedStructure(tableId);
        if (editedOpt.isEmpty() || !manager.hasEdits(tableId)) {
            return;
        }
        LootTableStructure edited = editedOpt.get();

        // Compute diff
        diffResult = StructureDiff.compare(original, edited);

        // Collect all changes into a single list
        allChanges.addAll(diffResult.removals());
        allChanges.addAll(diffResult.additions());
        allChanges.addAll(diffResult.modifications());

        // Calculate scroll
        int contentHeight = HEADER_HEIGHT + allChanges.size() * ROW_HEIGHT + 20;
        maxScroll = Math.max(0, contentHeight - height);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1a1a1a);
        graphics.renderOutline(getX(), getY(), width, height, 0xFF333333);

        // Title
        graphics.drawString(font, "Changes", getX() + PADDING, getY() + 7, IsotopeColors.ACCENT_GOLD, false);

        if (diffResult == null || !diffResult.hasChanges()) {
            String msg = tableId == null ? "No table selected" : "No changes";
            graphics.drawString(font, msg, getX() + PADDING, getY() + 30, IsotopeColors.TEXT_MUTED, false);
            return;
        }

        // Change count badge
        String countText = diffResult.totalChanges() + " change" + (diffResult.totalChanges() > 1 ? "s" : "");
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, getX() + width - PADDING - countWidth, getY() + 7,
            IsotopeColors.BADGE_MODIFIED, false);

        // Enable scissor for scrolling
        graphics.enableScissor(getX(), getY() + HEADER_HEIGHT, getX() + width, getY() + height);

        int y = getY() + HEADER_HEIGHT - scrollOffset;

        // Render changes
        for (DiffEntry entry : allChanges) {
            if (y + ROW_HEIGHT > getY() && y < getY() + height) {
                String text = StructureDiff.describe(entry);
                int color = StructureDiff.getColor(entry);

                // Truncate if needed
                if (font.width(text) > width - PADDING * 2) {
                    text = font.plainSubstrByWidth(text, width - PADDING * 2 - 10) + "...";
                }

                graphics.drawString(font, text, getX() + PADDING, y + 2, color, false);
            }
            y += ROW_HEIGHT;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = getX() + width - 5;
            int scrollbarHeight = height - HEADER_HEIGHT;
            int thumbHeight = Math.max(20, (int) ((float) (height - HEADER_HEIGHT) / (height - HEADER_HEIGHT + maxScroll) * scrollbarHeight));
            int thumbY = getY() + HEADER_HEIGHT + (int) ((float) scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

            graphics.fill(scrollbarX, getY() + HEADER_HEIGHT, scrollbarX + 4, getY() + height, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF555555);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
