package dev.isotope.ui.widget;

import dev.isotope.data.loot.LootPool;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Widget for displaying and selecting loot pools.
 */
@Environment(EnvType.CLIENT)
public class PoolListWidget extends ScrollableListWidget<LootPool> {

    private static final int ITEM_HEIGHT = 24;

    public PoolListWidget(int x, int y, int width, int height, Consumer<LootPool> onSelect) {
        super(x, y, width, height, ITEM_HEIGHT, PoolListWidget::renderPool, onSelect);
    }

    private static void renderPool(GuiGraphics graphics, LootPool pool, int x, int y,
                                   int width, int height, boolean selected, boolean hovered) {
        var font = Minecraft.getInstance().font;

        // Pool name/index
        String name = pool.name().isEmpty() ? "Pool" : pool.name();
        graphics.drawString(font, name, x + 2, y + 2, IsotopeColors.TEXT_PRIMARY, false);

        // Rolls info
        String rollsStr = "Rolls: " + pool.getRollsString();
        graphics.drawString(font, rollsStr, x + 2, y + 12, IsotopeColors.TEXT_SECONDARY, false);

        // Entry count badge
        int entryCount = pool.getEntryCount();
        String countStr = entryCount + " entries";
        int countWidth = font.width(countStr);
        graphics.drawString(font, countStr, x + width - countWidth - 4, y + 7,
            IsotopeColors.TEXT_MUTED, false);
    }

    public void setPools(List<LootPool> pools) {
        setItems(pools);
    }
}
