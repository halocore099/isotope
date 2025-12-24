package dev.isotope.ui.widget;

import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import dev.isotope.data.StructureLootLink;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;
import java.util.function.Consumer;

/**
 * Widget showing all discovered loot tables for the Loot Tables tab.
 *
 * Shows ALL loot tables from the registry with link information.
 */
@Environment(EnvType.CLIENT)
public class LootTableListWidget extends ScrollableListWidget<LootTableListWidget.LootTableEntry> {

    public record LootTableEntry(
        LootTableInfo info,
        List<StructureLootLink> links
    ) {
        public String displayName() {
            return info.path();
        }

        public String namespace() {
            return info.namespace();
        }

        public LootTableCategory category() {
            return info.category();
        }

        public boolean hasLinks() {
            return !links.isEmpty();
        }

        public int structureCount() {
            return links.size();
        }

        public boolean isVanilla() {
            return info.isVanilla();
        }
    }

    private LootTableCategory currentFilter = null;

    public LootTableListWidget(int x, int y, int width, int height, Consumer<LootTableEntry> onSelect) {
        super(x, y, width, height, 26, LootTableListWidget::renderEntry, onSelect);
    }

    public void loadAll() {
        loadForCategory(null);
    }

    public void loadForCategory(LootTableCategory category) {
        currentFilter = category;

        ClientDataProvider provider = ClientDataProvider.getInstance();
        List<LootTableInfo> tables;

        if (category == null) {
            tables = provider.getAllLootTables();
        } else {
            tables = provider.getLootTablesByCategory(category);
        }

        List<LootTableEntry> entries = new ArrayList<>();
        for (LootTableInfo info : tables) {
            List<StructureLootLink> links = provider.getLinksForLootTable(info.id());
            entries.add(new LootTableEntry(info, links));
        }

        // Sort: tables with links first, then by category, then alphabetically
        entries.sort(Comparator
            .comparing((LootTableEntry e) -> !e.hasLinks())
            .thenComparing(e -> e.category().ordinal())
            .thenComparing(e -> e.info().id().toString()));

        setItems(entries);
    }

    private static void renderEntry(GuiGraphics graphics, LootTableEntry entry, int x, int y,
                                     int width, int height, boolean selected, boolean hovered) {
        Minecraft mc = Minecraft.getInstance();

        // Loot table name (truncate if needed)
        String name = entry.displayName();
        int maxNameWidth = width - 60;
        if (mc.font.width(name) > maxNameWidth) {
            while (mc.font.width(name + "...") > maxNameWidth && name.length() > 3) {
                name = name.substring(0, name.length() - 1);
            }
            name += "...";
        }

        int textColor = selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY;
        graphics.drawString(mc.font, name, x, y + 2, textColor, false);

        // Badges row
        int badgeY = y + 13;
        int badgeX = x;

        // Category badge
        String catText = entry.category().name();
        int catWidth = mc.font.width(catText) + 6;
        int catColor = getCategoryColor(entry.category());
        graphics.fill(badgeX, badgeY, badgeX + catWidth, badgeY + 10, catColor);
        graphics.drawString(mc.font, catText, badgeX + 3, badgeY + 1, 0xFF000000, false);
        badgeX += catWidth + 3;

        // Structure link count badge
        if (entry.hasLinks()) {
            String linkText = entry.structureCount() + " struct";
            int linkWidth = mc.font.width(linkText) + 6;
            graphics.fill(badgeX, badgeY, badgeX + linkWidth, badgeY + 10, IsotopeColors.BADGE_HAS_LOOT);
            graphics.drawString(mc.font, linkText, badgeX + 3, badgeY + 1, 0xFF000000, false);
            badgeX += linkWidth + 3;
        } else {
            String noLink = "UNLINKED";
            int noLinkWidth = mc.font.width(noLink) + 6;
            graphics.fill(badgeX, badgeY, badgeX + noLinkWidth, badgeY + 10, IsotopeColors.BADGE_NO_LOOT);
            graphics.drawString(mc.font, noLink, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_MUTED, false);
        }
    }

    private static int getCategoryColor(LootTableCategory category) {
        return switch (category) {
            case CHEST -> 0xFF8B4513;      // Brown
            case ENTITY -> 0xFFCC0000;     // Red
            case BLOCK -> 0xFF666666;      // Gray
            case GAMEPLAY -> 0xFF00AA00;   // Green
            case ARCHAEOLOGY -> 0xFFCC8800;// Orange
            case EQUIPMENT -> 0xFF4444FF;  // Blue
            case SHEARING -> 0xFFCCCCCC;   // Light gray
            case OTHER -> 0xFF888888;      // Gray
        };
    }
}
