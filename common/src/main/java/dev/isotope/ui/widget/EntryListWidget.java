package dev.isotope.ui.widget;

import dev.isotope.data.loot.LootEntry;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/**
 * Widget for displaying and selecting loot entries.
 */
@Environment(EnvType.CLIENT)
public class EntryListWidget extends ScrollableListWidget<LootEntry> {

    private static final int ITEM_HEIGHT = 22;

    public EntryListWidget(int x, int y, int width, int height, Consumer<LootEntry> onSelect) {
        super(x, y, width, height, ITEM_HEIGHT, EntryListWidget::renderEntry, onSelect);
    }

    private static void renderEntry(GuiGraphics graphics, LootEntry entry, int x, int y,
                                    int width, int height, boolean selected, boolean hovered) {
        var font = Minecraft.getInstance().font;

        // Item icon (if item entry)
        int textX = x + 2;
        if (entry.isItem() && entry.name().isPresent()) {
            ItemStack stack = getItemStack(entry.name().get());
            if (!stack.isEmpty()) {
                // Render small item icon
                graphics.pose().pushPose();
                graphics.pose().translate(x + 2, y + 3, 0);
                graphics.pose().scale(0.75f, 0.75f, 1.0f);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                textX = x + 16;
            }
        }

        // Entry name
        String displayName = entry.getDisplayName();
        if (displayName.length() > 20) {
            displayName = displayName.substring(0, 18) + "...";
        }
        graphics.drawString(font, displayName, textX, y + 3, IsotopeColors.TEXT_PRIMARY, false);

        // Weight badge
        String weightStr = "W:" + entry.weight();
        int weightColor = entry.weight() > 10 ? IsotopeColors.ACCENT_GREEN :
                         entry.weight() > 1 ? IsotopeColors.TEXT_SECONDARY :
                         IsotopeColors.TEXT_MUTED;
        graphics.drawString(font, weightStr, textX, y + 12, weightColor, false);

        // Count if has set_count function
        String countStr = entry.getCountString();
        if (!countStr.equals("1")) {
            int countWidth = font.width("x" + countStr);
            graphics.drawString(font, "x" + countStr, x + width - countWidth - 4, y + 7,
                IsotopeColors.ACCENT_AQUA, false);
        }

        // Entry type indicator for non-item entries
        if (!entry.isItem() && !entry.isEmpty()) {
            String typeIndicator = getTypeIndicator(entry.type());
            int typeWidth = font.width(typeIndicator);
            graphics.drawString(font, typeIndicator, x + width - typeWidth - 4, y + 3,
                IsotopeColors.TEXT_MUTED, false);
        }
    }

    private static ItemStack getItemStack(ResourceLocation itemId) {
        try {
            var itemOpt = BuiltInRegistries.ITEM.get(itemId);
            if (itemOpt.isPresent()) {
                return new ItemStack(itemOpt.get().value());
            }
        } catch (Exception e) {
            // Ignore
        }
        return ItemStack.EMPTY;
    }

    private static String getTypeIndicator(String type) {
        return switch (type) {
            case LootEntry.TYPE_LOOT_TABLE -> "[T]";
            case LootEntry.TYPE_TAG -> "[#]";
            case LootEntry.TYPE_ALTERNATIVES -> "[A]";
            case LootEntry.TYPE_GROUP -> "[G]";
            case LootEntry.TYPE_SEQUENCE -> "[S]";
            case LootEntry.TYPE_EMPTY -> "[-]";
            default -> "[?]";
        };
    }

    public void setEntries(List<LootEntry> entries) {
        setItems(entries);
    }
}
