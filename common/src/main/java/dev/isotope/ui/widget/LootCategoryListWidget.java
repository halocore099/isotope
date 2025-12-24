package dev.isotope.ui.widget;

import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;
import java.util.function.Consumer;

/**
 * Left panel widget showing loot table categories with counts.
 *
 * Shows all discovered loot table categories from the registry.
 */
@Environment(EnvType.CLIENT)
public class LootCategoryListWidget extends ScrollableListWidget<LootCategoryListWidget.CategoryEntry> {

    public record CategoryEntry(
        LootTableCategory category,
        int tableCount
    ) {
        public String displayName() {
            if (category == null) {
                return "All Categories";
            }
            // Capitalize and format
            String name = category.name();
            return name.charAt(0) + name.substring(1).toLowerCase();
        }

        public int getColor() {
            if (category == null) return IsotopeColors.ACCENT_CYAN;
            return switch (category) {
                case CHEST -> 0xFF8B4513;      // Brown
                case ENTITY -> 0xFFCC0000;     // Red
                case BLOCK -> 0xFF666666;      // Gray
                case GAMEPLAY -> 0xFF00AA00;   // Green
                case ARCHAEOLOGY -> 0xFFCC8800;// Orange
                case EQUIPMENT -> 0xFF4444FF;  // Blue
                case SHEARING -> 0xFFFFFFFF;   // White
                case OTHER -> 0xFF888888;      // Gray
            };
        }
    }

    public LootCategoryListWidget(int x, int y, int width, int height, Consumer<CategoryEntry> onSelect) {
        super(x, y, width, height, 22, LootCategoryListWidget::renderEntry, onSelect);
    }

    public void loadData() {
        Map<LootTableCategory, Integer> counts = ClientDataProvider.getInstance().getLootTableCountByCategory();

        List<CategoryEntry> entries = new ArrayList<>();

        // Add "All" entry first
        int totalCount = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalCount > 0) {
            entries.add(new CategoryEntry(null, totalCount)); // null means all
        }

        // Add each category
        for (LootTableCategory cat : LootTableCategory.values()) {
            int count = counts.getOrDefault(cat, 0);
            if (count > 0) {
                entries.add(new CategoryEntry(cat, count));
            }
        }

        setItems(entries);

        // Select "All" by default
        if (!entries.isEmpty()) {
            setSelected(entries.get(0));
        }
    }

    private static void renderEntry(GuiGraphics graphics, CategoryEntry entry, int x, int y,
                                     int width, int height, boolean selected, boolean hovered) {
        Minecraft mc = Minecraft.getInstance();

        // Display name
        int textColor = selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY;
        graphics.drawString(mc.font, entry.displayName(), x, y + 3, textColor, false);

        // Count badge with category color
        String countText = String.valueOf(entry.tableCount);
        int countWidth = mc.font.width(countText) + 6;
        int badgeX = x + width - countWidth - 4;

        graphics.fill(badgeX, y + 2, badgeX + countWidth, y + height - 4, entry.getColor());
        graphics.drawString(mc.font, countText, badgeX + 3, y + 4, IsotopeColors.TEXT_PRIMARY, false);
    }
}
