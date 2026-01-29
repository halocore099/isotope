package dev.isotope.ui.widget;

import dev.isotope.analysis.EnchantmentProbabilityCalculator.EnchantmentAnalysis;
import dev.isotope.analysis.EnchantmentProbabilityCalculator.EnchantmentChance;
import dev.isotope.compat.ui.VersionedWidget;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Panel that displays enchantment probabilities for enchantment functions.
 * Shows a visual breakdown of what enchantments can appear and their chances.
 */
@Environment(EnvType.CLIENT)
public class EnchantmentProbabilityPanel extends VersionedWidget {

    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int PROBABILITY_BAR_WIDTH = 60;

    @Nullable
    private EnchantmentAnalysis analysis;

    private int scrollOffset = 0;
    private boolean expanded = true;

    // Colors for probability bars based on rarity
    private static final int COLOR_COMMON = 0xFF66AA66;     // Green
    private static final int COLOR_UNCOMMON = 0xFF6688AA;   // Blue
    private static final int COLOR_RARE = 0xFFAA66AA;       // Purple
    private static final int COLOR_VERY_RARE = 0xFFAAAA66;  // Gold
    private static final int COLOR_TREASURE = 0xFFAA6666;   // Red-ish

    public EnchantmentProbabilityPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Enchantments"));
    }

    /**
     * Set the analysis data to display.
     */
    public void setAnalysis(@Nullable EnchantmentAnalysis analysis) {
        this.analysis = analysis;
        this.scrollOffset = 0;
    }

    /**
     * Clear the analysis.
     */
    public void clear() {
        this.analysis = null;
        this.scrollOffset = 0;
    }

    /**
     * Check if there's data to display.
     */
    public boolean hasData() {
        return analysis != null && analysis.hasResults();
    }

    /**
     * Get the preferred height based on content.
     */
    public int getPreferredHeight() {
        if (!hasData()) {
            return HEADER_HEIGHT;
        }
        int rows = Math.min(analysis.enchantments().size(), MAX_VISIBLE_ROWS);
        return HEADER_HEIGHT + rows * ROW_HEIGHT + 8; // +8 for padding
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_DARKER);
        ScreenUtils.renderOutline(graphics, getX(), getY(), width, height, IsotopeColors.BORDER_DEFAULT);

        // Header
        graphics.fill(getX(), getY(), getX() + width, getY() + HEADER_HEIGHT, IsotopeColors.POOL_HEADER_BACKGROUND);

        // Expand/collapse indicator
        String indicator = expanded ? "\u25BC" : "\u25B6"; // ▼ or ▶
        graphics.drawString(font, indicator, getX() + 4, getY() + 6, IsotopeColors.TEXT_MUTED, false);

        // Title
        graphics.drawString(font, "Possible Enchantments", getX() + 16, getY() + 6, IsotopeColors.ACCENT_GOLD, false);

        if (analysis == null || !analysis.hasResults()) {
            // No data message
            graphics.drawString(font, "No enchantments available", getX() + 8, getY() + HEADER_HEIGHT + 4, IsotopeColors.TEXT_MUTED, false);
            return;
        }

        if (!expanded) {
            // Show summary when collapsed
            String summary = analysis.enchantments().size() + " possible";
            graphics.drawString(font, summary, getX() + width - font.width(summary) - 8, getY() + 6, IsotopeColors.TEXT_SECONDARY, false);
            return;
        }

        // Level info in header
        String levelInfo;
        if ("enchant_with_levels".equals(analysis.functionType())) {
            levelInfo = "Lvl " + analysis.levelMin() + "-" + analysis.levelMax();
            if (analysis.includeTreasure()) {
                levelInfo += " +T";
            }
        } else {
            levelInfo = analysis.enchantments().size() + " options";
        }
        graphics.drawString(font, levelInfo, getX() + width - font.width(levelInfo) - 8, getY() + 6, IsotopeColors.TEXT_SECONDARY, false);

        // Enchantment list
        int listY = getY() + HEADER_HEIGHT + 2;
        int listHeight = height - HEADER_HEIGHT - 4;
        int maxVisibleItems = listHeight / ROW_HEIGHT;

        List<EnchantmentChance> enchantments = analysis.enchantments();
        int maxScroll = Math.max(0, enchantments.size() - maxVisibleItems);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        graphics.enableScissor(getX() + 2, listY, getX() + width - 2, listY + listHeight);

        for (int i = scrollOffset; i < enchantments.size() && (i - scrollOffset) < maxVisibleItems; i++) {
            EnchantmentChance ench = enchantments.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;

            renderEnchantmentRow(graphics, font, ench, getX() + 4, rowY, width - 8, mouseX, mouseY);
        }

        graphics.disableScissor();

        // Scroll indicator if needed
        if (enchantments.size() > maxVisibleItems) {
            int thumbHeight = Math.max(10, listHeight * maxVisibleItems / enchantments.size());
            int scrollRange = listHeight - thumbHeight;
            int thumbY = listY + (maxScroll > 0 ? scrollOffset * scrollRange / maxScroll : 0);

            // Track
            graphics.fill(getX() + width - 6, listY, getX() + width - 2, listY + listHeight, IsotopeColors.SCROLLBAR_TRACK);
            // Thumb
            graphics.fill(getX() + width - 5, thumbY, getX() + width - 3, thumbY + thumbHeight, IsotopeColors.SCROLLBAR_THUMB);
        }

        // Show "(X more)" if truncated
        if (enchantments.size() > maxVisibleItems) {
            int remaining = enchantments.size() - maxVisibleItems - scrollOffset;
            if (remaining > 0) {
                String moreText = "(" + remaining + " more)";
                int moreY = getY() + height - 12;
                graphics.drawString(font, moreText, getX() + width / 2 - font.width(moreText) / 2, moreY, IsotopeColors.TEXT_MUTED, false);
            }
        }
    }

    private void renderEnchantmentRow(GuiGraphics graphics, Font font, EnchantmentChance ench,
                                      int x, int y, int rowWidth, int mouseX, int mouseY) {
        // Name (truncate if too long)
        String name = ench.displayName();
        int maxNameWidth = rowWidth - PROBABILITY_BAR_WIDTH - 45;
        if (font.width(name) > maxNameWidth) {
            while (font.width(name + "...") > maxNameWidth && name.length() > 3) {
                name = name.substring(0, name.length() - 1);
            }
            name += "...";
        }

        // Level range
        String levels = "";
        if (ench.maxLevel() > 1) {
            if (ench.minLevel() == ench.maxLevel()) {
                levels = toRoman(ench.minLevel());
            } else {
                levels = toRoman(ench.minLevel()) + "-" + toRoman(ench.maxLevel());
            }
        }

        // Name color based on treasure status
        int nameColor = ench.isTreasure() ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
        graphics.drawString(font, name, x, y + 2, nameColor, false);

        // Level range
        if (!levels.isEmpty()) {
            int levelX = x + font.width(name) + 4;
            graphics.drawString(font, levels, levelX, y + 2, IsotopeColors.TEXT_MUTED, false);
        }

        // Probability bar
        int barX = x + rowWidth - PROBABILITY_BAR_WIDTH - 30;
        int barY = y + 3;
        int barHeight = ROW_HEIGHT - 6;

        // Bar background
        graphics.fill(barX, barY, barX + PROBABILITY_BAR_WIDTH, barY + barHeight, IsotopeColors.INPUT_BACKGROUND);

        // Bar fill
        int fillWidth = (int) (PROBABILITY_BAR_WIDTH * ench.probability());
        if (fillWidth > 0) {
            int barColor = getBarColor(ench);
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);
        }

        // Bar border
        ScreenUtils.renderOutline(graphics, barX, barY, PROBABILITY_BAR_WIDTH, barHeight, IsotopeColors.BORDER_DEFAULT);

        // Percentage
        String pct = String.format("%d%%", Math.round(ench.probability() * 100));
        graphics.drawString(font, pct, barX + PROBABILITY_BAR_WIDTH + 4, y + 2, IsotopeColors.TEXT_SECONDARY, false);

        // Hover tooltip (for full name)
        if (mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT) {
            // Could show tooltip here in the future
        }
    }

    /**
     * Get bar color based on rarity.
     */
    private int getBarColor(EnchantmentChance ench) {
        if (ench.isTreasure()) {
            return COLOR_TREASURE;
        }
        return switch (ench.rarityWeight()) {
            case 10 -> COLOR_COMMON;
            case 5 -> COLOR_UNCOMMON;
            case 2 -> COLOR_RARE;
            case 1 -> COLOR_VERY_RARE;
            default -> COLOR_COMMON;
        };
    }

    /**
     * Convert level to Roman numeral.
     */
    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (button != 0) return false;

        // Check header click to toggle expand/collapse
        if (mouseY >= getY() && mouseY < getY() + HEADER_HEIGHT) {
            expanded = !expanded;
            return true;
        }

        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (analysis == null || !expanded) return false;

        if (isMouseOver(mouseX, mouseY)) {
            int listHeight = height - HEADER_HEIGHT - 4;
            int maxVisibleItems = listHeight / ROW_HEIGHT;
            int maxScroll = Math.max(0, analysis.enchantments().size() - maxVisibleItems);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        // Accessibility narration
        if (analysis != null && analysis.hasResults()) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT,
                analysis.enchantments().size() + " possible enchantments");
        }
    }
}
