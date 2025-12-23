package dev.isotope.ui.widget;

import dev.isotope.data.LootTableInfo;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Left panel widget showing loot table categories with counts.
 */
@Environment(EnvType.CLIENT)
public class LootCategoryListWidget extends ScrollableListWidget<LootCategoryListWidget.CategoryEntry> {

    public record CategoryEntry(
        LootTableInfo.LootTableCategory category,
        int tableCount
    ) {
        public String displayName() {
            return switch (category) {
                case CHEST -> "Chests";
                case ENTITY -> "Entities";
                case BLOCK -> "Blocks";
                case GAMEPLAY -> "Gameplay";
                case ARCHAEOLOGY -> "Archaeology";
                case EQUIPMENT -> "Equipment";
                case SHEARING -> "Shearing";
                case OTHER -> "Other";
            };
        }
    }

    public LootCategoryListWidget(int x, int y, int width, int height, Consumer<CategoryEntry> onSelect) {
        super(x, y, width, height, 22, LootCategoryListWidget::renderEntry, onSelect);
    }

    public void loadData() {
        List<CategoryEntry> entries = new ArrayList<>();

        for (LootTableInfo.LootTableCategory category : LootTableInfo.LootTableCategory.values()) {
            int count = LootTableRegistry.getInstance().getByCategory(category).size();
            if (count > 0) {
                entries.add(new CategoryEntry(category, count));
            }
        }

        setItems(entries);

        // Select CHEST by default
        for (CategoryEntry entry : entries) {
            if (entry.category == LootTableInfo.LootTableCategory.CHEST) {
                setSelected(entry);
                break;
            }
        }
    }

    private static void renderEntry(GuiGraphics graphics, CategoryEntry entry, int x, int y,
                                     int width, int height, boolean selected, boolean hovered) {
        Minecraft mc = Minecraft.getInstance();

        // Category name
        int textColor = selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY;
        graphics.drawString(mc.font, entry.displayName(), x, y + 3, textColor, false);

        // Count badge
        String countText = String.valueOf(entry.tableCount);
        int countWidth = mc.font.width(countText) + 6;
        int badgeX = x + width - countWidth - 4;
        int badgeColor = getCategoryColor(entry.category);

        graphics.fill(badgeX, y + 2, badgeX + countWidth, y + height - 4, badgeColor);
        graphics.drawString(mc.font, countText, badgeX + 3, y + 4, IsotopeColors.TEXT_PRIMARY, false);
    }

    private static int getCategoryColor(LootTableInfo.LootTableCategory category) {
        return switch (category) {
            case CHEST -> IsotopeColors.BADGE_HAS_LOOT;
            case ENTITY -> 0xFF9C27B0;
            case BLOCK -> 0xFF795548;
            case GAMEPLAY -> 0xFF2196F3;
            case ARCHAEOLOGY -> 0xFFFF9800;
            case EQUIPMENT -> 0xFF607D8B;
            case SHEARING -> 0xFF8BC34A;
            default -> IsotopeColors.BADGE_NO_LOOT;
        };
    }
}
