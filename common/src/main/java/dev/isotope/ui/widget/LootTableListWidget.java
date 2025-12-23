package dev.isotope.ui.widget;

import dev.isotope.analysis.AnalysisEngine;
import dev.isotope.data.LootTableInfo;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Widget showing loot tables for the Loot Tables tab.
 */
@Environment(EnvType.CLIENT)
public class LootTableListWidget extends ScrollableListWidget<LootTableListWidget.LootTableEntry> {

    public record LootTableEntry(
        LootTableInfo info,
        boolean sampled,
        float avgItemsPerRoll
    ) {
        public String displayName() {
            return info.path();
        }

        public String categoryName() {
            return info.category().name();
        }
    }

    public LootTableListWidget(int x, int y, int width, int height, Consumer<LootTableEntry> onSelect) {
        super(x, y, width, height, 26, LootTableListWidget::renderEntry, onSelect);
    }

    public void loadForNamespace(String namespace) {
        if (namespace == null) {
            setItems(List.of());
            return;
        }

        List<LootTableEntry> entries = new ArrayList<>();
        var samples = AnalysisEngine.getInstance().getAllSamples();

        for (LootTableInfo info : LootTableRegistry.getInstance().getByNamespace(namespace)) {
            var sample = samples.get(info.id());
            boolean sampled = sample != null && !sample.hasError();
            float avgItems = sample != null ? sample.averageItemsPerRoll() : 0;
            entries.add(new LootTableEntry(info, sampled, avgItems));
        }

        // Sort: sampled first, then by category, then alphabetically
        entries.sort(Comparator
            .comparing((LootTableEntry e) -> !e.sampled)
            .thenComparing(e -> e.info.category().ordinal())
            .thenComparing(e -> e.info.path()));

        setItems(entries);
    }

    public void loadForCategory(LootTableInfo.LootTableCategory category) {
        List<LootTableEntry> entries = new ArrayList<>();
        var samples = AnalysisEngine.getInstance().getAllSamples();

        for (LootTableInfo info : LootTableRegistry.getInstance().getByCategory(category)) {
            var sample = samples.get(info.id());
            boolean sampled = sample != null && !sample.hasError();
            float avgItems = sample != null ? sample.averageItemsPerRoll() : 0;
            entries.add(new LootTableEntry(info, sampled, avgItems));
        }

        entries.sort(Comparator
            .comparing((LootTableEntry e) -> !e.sampled)
            .thenComparing(e -> e.info.path()));

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

        // Category badge
        int badgeY = y + 13;
        int badgeX = x;

        String catText = entry.categoryName();
        int catWidth = mc.font.width(catText) + 6;
        int catColor = getCategoryColor(entry.info.category());
        graphics.fill(badgeX, badgeY, badgeX + catWidth, badgeY + 10, catColor);
        graphics.drawString(mc.font, catText, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_PRIMARY, false);
        badgeX += catWidth + 3;

        // Sampled badge
        if (entry.sampled) {
            String sampledText = String.format("%.1f avg", entry.avgItemsPerRoll);
            int sampledWidth = mc.font.width(sampledText) + 6;
            graphics.fill(badgeX, badgeY, badgeX + sampledWidth, badgeY + 10, IsotopeColors.BADGE_HAS_LOOT);
            graphics.drawString(mc.font, sampledText, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_PRIMARY, false);
        }
    }

    private static int getCategoryColor(LootTableInfo.LootTableCategory category) {
        return switch (category) {
            case CHEST -> IsotopeColors.BADGE_HAS_LOOT;
            case ENTITY -> 0xFF9C27B0; // Purple
            case BLOCK -> 0xFF795548; // Brown
            case GAMEPLAY -> 0xFF2196F3; // Blue
            case ARCHAEOLOGY -> 0xFFFF9800; // Orange
            case EQUIPMENT -> 0xFF607D8B; // Blue-gray
            case SHEARING -> 0xFF8BC34A; // Light green
            default -> IsotopeColors.BADGE_NO_LOOT;
        };
    }
}
