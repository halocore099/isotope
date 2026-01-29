package dev.isotope.ui.screen;

import dev.isotope.analysis.EnchantmentProbabilityCalculator;
import dev.isotope.analysis.EnchantmentProbabilityCalculator.EnchantmentAnalysis;
import dev.isotope.analysis.EnchantmentProbabilityCalculator.EnchantmentChance;
import dev.isotope.compat.Id;
import dev.isotope.compat.ui.VersionedScreen;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing enchantment probability analysis for enchantment functions.
 */
@Environment(EnvType.CLIENT)
public class EnchantmentAnalysisScreen extends VersionedScreen {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 360;
    private static final int ROW_HEIGHT = 18;
    private static final int BAR_WIDTH = 80;

    @Nullable
    private final Screen parent;
    private final Id itemId;
    private final List<LootFunction> enchantFunctions;

    private final List<EnchantmentAnalysis> analyses = new ArrayList<>();
    private int selectedFunctionIdx = 0;
    private int scrollOffset = 0;

    // Colors for probability bars based on rarity
    private static final int COLOR_COMMON = 0xFF66AA66;
    private static final int COLOR_UNCOMMON = 0xFF6688AA;
    private static final int COLOR_RARE = 0xFFAA66AA;
    private static final int COLOR_VERY_RARE = 0xFFAAAA66;
    private static final int COLOR_TREASURE = 0xFFAA6666;

    public EnchantmentAnalysisScreen(@Nullable Screen parent, Id itemId, List<LootFunction> enchantFunctions) {
        super(Component.literal("Enchantment Analysis"));
        this.parent = parent;
        this.itemId = itemId;
        this.enchantFunctions = enchantFunctions;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal("Close"),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH / 2 - 40, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());

        // Analyze all functions
        performAnalysis();
    }

    private void performAnalysis() {
        analyses.clear();

        RegistryAccess registryAccess = getRegistryAccess();
        if (registryAccess == null) {
            return;
        }

        for (LootFunction func : enchantFunctions) {
            EnchantmentAnalysis analysis = EnchantmentProbabilityCalculator.analyze(func, itemId, registryAccess);
            analyses.add(analysis);
        }
    }

    @Nullable
    private RegistryAccess getRegistryAccess() {
        if (minecraft == null) return null;

        // Try to get registry access from the client
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }

        // Fallback: try to get from connection
        if (minecraft.getConnection() != null) {
            return minecraft.getConnection().registryAccess();
        }

        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DARK);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        ScreenUtils.drawDialogBackground(graphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT);

        // Header
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 28, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.drawString(font, "Enchantment Analysis", dialogX + 12, dialogY + 10, IsotopeColors.ACCENT_GOLD, false);

        // Item name
        String itemName = itemId.getPath().replace("_", " ");
        itemName = Character.toUpperCase(itemName.charAt(0)) + itemName.substring(1);
        graphics.drawString(font, "Item: " + itemName, dialogX + DIALOG_WIDTH - font.width("Item: " + itemName) - 12, dialogY + 10, IsotopeColors.TEXT_MUTED, false);

        // Function tabs (if multiple)
        if (enchantFunctions.size() > 1) {
            int tabY = dialogY + 32;
            int tabX = dialogX + 10;

            for (int i = 0; i < enchantFunctions.size(); i++) {
                LootFunction func = enchantFunctions.get(i);
                String tabName = func.getDisplayName();
                int tabWidth = font.width(tabName) + 16;

                boolean selected = i == selectedFunctionIdx;
                boolean hovered = ScreenUtils.isMouseOver(mouseX, mouseY, tabX, tabY, tabWidth, 18);

                // Tab background
                int tabBg = selected ? IsotopeColors.SINGLE_SELECT_BACKGROUND : (hovered ? IsotopeColors.INPUT_BACKGROUND : IsotopeColors.BACKGROUND_DARKER);
                graphics.fill(tabX, tabY, tabX + tabWidth, tabY + 18, tabBg);
                ScreenUtils.renderOutline(graphics, tabX, tabY, tabWidth, 18, selected ? IsotopeColors.ACCENT_GOLD : IsotopeColors.BORDER_DEFAULT);

                graphics.drawString(font, tabName, tabX + 8, tabY + 5, selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);

                tabX += tabWidth + 4;
            }
        }

        // Analysis content
        int contentY = enchantFunctions.size() > 1 ? dialogY + 55 : dialogY + 35;
        int contentHeight = dialogY + DIALOG_HEIGHT - 40 - contentY;

        if (analyses.isEmpty()) {
            graphics.drawCenteredString(font, "No enchantment data available", dialogX + DIALOG_WIDTH / 2, contentY + 30, IsotopeColors.TEXT_MUTED);
            graphics.drawCenteredString(font, "(Requires game connection)", dialogX + DIALOG_WIDTH / 2, contentY + 45, IsotopeColors.TEXT_MUTED);
        } else if (selectedFunctionIdx < analyses.size()) {
            EnchantmentAnalysis analysis = analyses.get(selectedFunctionIdx);
            renderAnalysis(graphics, analysis, dialogX + 10, contentY, DIALOG_WIDTH - 20, contentHeight, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderAnalysis(GuiGraphics graphics, EnchantmentAnalysis analysis, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Function info header
        String info;
        if ("enchant_with_levels".equals(analysis.functionType())) {
            info = "Enchanting levels " + analysis.levelMin() + "-" + analysis.levelMax();
            if (analysis.includeTreasure()) {
                info += " (includes treasure)";
            }
            info += " | ~" + String.format("%.1f", analysis.avgEnchantmentsPerItem()) + " enchants/item";
        } else if ("enchant_randomly".equals(analysis.functionType())) {
            info = "Random enchantment from " + analysis.enchantments().size() + " options";
        } else {
            info = analysis.functionType();
        }
        graphics.drawString(font, info, x, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 16;

        if (!analysis.hasResults()) {
            graphics.drawString(font, "No applicable enchantments for this item", x, y + 10, IsotopeColors.TEXT_MUTED, false);
            return;
        }

        // Column headers
        graphics.drawString(font, "Enchantment", x, y, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Levels", x + 140, y, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Probability", x + 200, y, IsotopeColors.TEXT_MUTED, false);
        y += 14;

        // Divider
        graphics.fill(x, y, x + width, y + 1, IsotopeColors.BORDER_DEFAULT);
        y += 4;

        // Enchantment list
        int listHeight = height - 34;
        int maxVisibleItems = listHeight / ROW_HEIGHT;
        int maxScroll = Math.max(0, analysis.enchantments().size() - maxVisibleItems);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        graphics.enableScissor(x, y, x + width, y + listHeight);

        List<EnchantmentChance> enchantments = analysis.enchantments();
        for (int i = scrollOffset; i < enchantments.size() && (i - scrollOffset) < maxVisibleItems; i++) {
            EnchantmentChance ench = enchantments.get(i);
            int rowY = y + (i - scrollOffset) * ROW_HEIGHT;

            // Hover highlight
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 2, IsotopeColors.INPUT_BACKGROUND);
            }

            // Treasure indicator
            if (ench.isTreasure()) {
                graphics.drawString(font, "\u2605", x, rowY + 2, IsotopeColors.ACCENT_GOLD, false); // Star
            }

            // Name
            String name = ench.displayName();
            if (font.width(name) > 130) {
                while (font.width(name + "...") > 130 && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "...";
            }
            int nameColor = ench.isTreasure() ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
            graphics.drawString(font, name, x + 12, rowY + 2, nameColor, false);

            // Level range
            String levels = ench.maxLevel() > 1 ?
                toRoman(ench.minLevel()) + "-" + toRoman(ench.maxLevel()) :
                toRoman(ench.maxLevel());
            graphics.drawString(font, levels, x + 140, rowY + 2, IsotopeColors.TEXT_SECONDARY, false);

            // Probability bar
            int barX = x + 200;
            int barY = rowY + 3;
            int barHeight = ROW_HEIGHT - 6;

            graphics.fill(barX, barY, barX + BAR_WIDTH, barY + barHeight, IsotopeColors.INPUT_BACKGROUND);

            int fillWidth = (int) (BAR_WIDTH * ench.probability());
            if (fillWidth > 0) {
                int barColor = getBarColor(ench);
                graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);
            }

            ScreenUtils.renderOutline(graphics, barX, barY, BAR_WIDTH, barHeight, IsotopeColors.BORDER_DEFAULT);

            // Percentage
            String pct = String.format("%d%%", Math.round(ench.probability() * 100));
            graphics.drawString(font, pct, barX + BAR_WIDTH + 6, rowY + 2, IsotopeColors.TEXT_SECONDARY, false);
        }

        graphics.disableScissor();

        // Scrollbar if needed
        if (enchantments.size() > maxVisibleItems) {
            int scrollbarX = x + width - 6;
            int thumbHeight = Math.max(10, listHeight * maxVisibleItems / enchantments.size());
            int scrollRange = listHeight - thumbHeight;
            int thumbY = y + (maxScroll > 0 ? scrollOffset * scrollRange / maxScroll : 0);

            graphics.fill(scrollbarX, y, scrollbarX + 4, y + listHeight, IsotopeColors.SCROLLBAR_TRACK);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, IsotopeColors.SCROLLBAR_THUMB);
        }
    }

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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (button != 0) return super.mouseClicked(event, focused);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Check tab clicks
        if (enchantFunctions.size() > 1) {
            int tabY = dialogY + 32;
            int tabX = dialogX + 10;

            for (int i = 0; i < enchantFunctions.size(); i++) {
                LootFunction func = enchantFunctions.get(i);
                String tabName = func.getDisplayName();
                int tabWidth = font.width(tabName) + 16;

                if (ScreenUtils.isMouseOver(mouseX, mouseY, tabX, tabY, tabWidth, 18)) {
                    selectedFunctionIdx = i;
                    scrollOffset = 0;
                    return true;
                }

                tabX += tabWidth + 4;
            }
        }

        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedFunctionIdx < analyses.size()) {
            EnchantmentAnalysis analysis = analyses.get(selectedFunctionIdx);

            int dialogY = (height - DIALOG_HEIGHT) / 2;
            int contentY = enchantFunctions.size() > 1 ? dialogY + 55 : dialogY + 35;
            int contentHeight = dialogY + DIALOG_HEIGHT - 40 - contentY - 34;
            int maxVisibleItems = contentHeight / ROW_HEIGHT;
            int maxScroll = Math.max(0, analysis.enchantments().size() - maxVisibleItems);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
