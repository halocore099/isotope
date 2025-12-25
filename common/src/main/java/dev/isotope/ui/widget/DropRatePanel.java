package dev.isotope.ui.widget;

import dev.isotope.analysis.DropRateCalculator;
import dev.isotope.analysis.DropRateCalculator.DropRate;
import dev.isotope.analysis.DropRateCalculator.PoolStats;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing visual drop rate distribution for a loot table.
 */
@Environment(EnvType.CLIENT)
public class DropRatePanel extends AbstractWidget {

    private static final int HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 20;
    private static final int BAR_MAX_WIDTH = 100;
    private static final int PADDING = 6;

    @Nullable
    private LootTableStructure structure;
    private List<PoolStats> poolStats = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Which pool to show (-1 = all pools combined)
    private int selectedPool = -1;

    public DropRatePanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Drop Rates"));
    }

    /**
     * Set the loot table to display rates for.
     */
    public void setStructure(@Nullable LootTableStructure structure) {
        this.structure = structure;
        this.scrollOffset = 0;
        recalculate();
    }

    /**
     * Recalculate drop rates (call after edits).
     */
    public void recalculate() {
        poolStats.clear();

        if (structure == null) {
            maxScroll = 0;
            return;
        }

        for (int i = 0; i < structure.pools().size(); i++) {
            LootPool pool = structure.pools().get(i);
            poolStats.add(DropRateCalculator.calculatePoolStats(pool, i));
        }

        // Calculate max scroll
        int contentHeight = HEADER_HEIGHT;
        for (PoolStats ps : poolStats) {
            contentHeight += HEADER_HEIGHT; // Pool header
            contentHeight += ps.rates().size() * ROW_HEIGHT;
        }
        contentHeight += 30; // Summary
        maxScroll = Math.max(0, contentHeight - height);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1a1a1a);
        graphics.renderOutline(getX(), getY(), width, height, 0xFF333333);

        // Title
        graphics.drawString(font, "Drop Rates", getX() + PADDING, getY() + 7, IsotopeColors.ACCENT_GOLD, false);

        if (structure == null || poolStats.isEmpty()) {
            graphics.drawString(font, "No data", getX() + PADDING, getY() + 30, IsotopeColors.TEXT_MUTED, false);
            return;
        }

        // Enable scissor for scrolling
        graphics.enableScissor(getX(), getY() + HEADER_HEIGHT, getX() + width, getY() + height);

        int y = getY() + HEADER_HEIGHT - scrollOffset;

        // Render each pool
        for (PoolStats ps : poolStats) {
            y = renderPoolStats(graphics, font, y, ps, mouseX, mouseY);
        }

        // Summary
        y += 10;
        float totalExpected = 0;
        for (PoolStats ps : poolStats) {
            totalExpected += ps.expectedItemsPerRoll();
        }
        String summary = String.format("Expected items per chest: %.1f", totalExpected);
        graphics.drawString(font, summary, getX() + PADDING, y, IsotopeColors.TEXT_SECONDARY, false);

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

    private int renderPoolStats(GuiGraphics graphics, Font font, int y, PoolStats ps, int mouseX, int mouseY) {
        // Pool header
        if (y + HEADER_HEIGHT > getY() && y < getY() + height) {
            graphics.fill(getX() + PADDING, y, getX() + width - PADDING, y + HEADER_HEIGHT - 2, 0xFF252525);

            String poolLabel = String.format("Pool %d  (%.1f rolls, %d total weight)",
                ps.poolIndex() + 1, ps.avgRolls(), ps.totalWeight());
            graphics.drawString(font, poolLabel, getX() + PADDING + 4, y + 6, IsotopeColors.TEXT_PRIMARY, false);
        }
        y += HEADER_HEIGHT;

        // Entries
        for (DropRate rate : ps.rates()) {
            if (y + ROW_HEIGHT > getY() && y < getY() + height) {
                renderDropRate(graphics, font, y, rate, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }

        return y;
    }

    private void renderDropRate(GuiGraphics graphics, Font font, int y, DropRate rate, int mouseX, int mouseY) {
        int x = getX() + PADDING;

        // Item icon
        var itemOpt = BuiltInRegistries.ITEM.get(rate.item());
        if (itemOpt.isPresent()) {
            ItemStack stack = new ItemStack(itemOpt.get().value());
            graphics.renderItem(stack, x, y + 2);
        }
        x += 20;

        // Item name
        String itemName = rate.item().getPath();
        if (font.width(itemName) > 80) {
            itemName = font.plainSubstrByWidth(itemName, 75) + "...";
        }
        graphics.drawString(font, itemName, x, y + 6, IsotopeColors.TEXT_PRIMARY, false);
        x += 85;

        // Drop rate bar
        int barWidth = (int) (rate.probability() * BAR_MAX_WIDTH);
        int barColor = DropRateCalculator.getRarityColor(rate.probability());

        graphics.fill(x, y + 4, x + BAR_MAX_WIDTH, y + 14, 0xFF2a2a2a);
        if (barWidth > 0) {
            graphics.fill(x, y + 4, x + barWidth, y + 14, barColor);
        }
        x += BAR_MAX_WIDTH + 5;

        // Percentage
        String pctText = String.format("%.1f%%", rate.percentChance());
        graphics.drawString(font, pctText, x, y + 6, IsotopeColors.TEXT_SECONDARY, false);
        x += 40;

        // Average count
        if (rate.avgCount() != 1.0f) {
            String countText = String.format("(%.1f)", rate.avgCount());
            graphics.drawString(font, countText, x, y + 6, IsotopeColors.TEXT_MUTED, false);
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
