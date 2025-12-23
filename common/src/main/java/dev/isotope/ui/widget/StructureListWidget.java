package dev.isotope.ui.widget;

import dev.isotope.analysis.StructureLootLinker;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Center panel widget showing structures for selected namespace.
 */
@Environment(EnvType.CLIENT)
public class StructureListWidget extends ScrollableListWidget<StructureListWidget.StructureEntry> {

    public record StructureEntry(
        StructureInfo structure,
        StructureLootLink link,
        boolean hasLoot
    ) {
        public String displayName() {
            return structure.path();
        }

        public int lootTableCount() {
            return link != null ? link.linkCount() : 0;
        }
    }

    public StructureListWidget(int x, int y, int width, int height, Consumer<StructureEntry> onSelect) {
        super(x, y, width, height, 24, StructureListWidget::renderEntry, onSelect);
    }

    public void loadForNamespace(String namespace) {
        if (namespace == null) {
            setItems(List.of());
            return;
        }

        Collection<StructureInfo> structures = ClientDataProvider.getInstance()
            .getStructuresByNamespace(namespace);

        List<StructureEntry> entries = new ArrayList<>();
        for (StructureInfo structure : structures) {
            StructureLootLink link = StructureLootLinker.getInstance()
                .getLink(structure.id())
                .orElse(null);
            boolean hasLoot = link != null && link.hasLinks();
            entries.add(new StructureEntry(structure, link, hasLoot));
        }

        // Sort: structures with loot first, then alphabetically
        entries.sort(Comparator
            .comparing((StructureEntry e) -> !e.hasLoot)
            .thenComparing(e -> e.structure.path()));

        setItems(entries);
    }

    private static void renderEntry(GuiGraphics graphics, StructureEntry entry, int x, int y,
                                     int width, int height, boolean selected, boolean hovered) {
        Minecraft mc = Minecraft.getInstance();

        // Structure name
        int textColor = selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY;
        graphics.drawString(mc.font, entry.displayName(), x, y + 2, textColor, false);

        // Badges row
        int badgeY = y + 12;
        int badgeX = x;

        // HAS LOOT / NO LOOT badge
        if (entry.hasLoot) {
            String lootText = entry.lootTableCount() + " loot";
            int lootWidth = mc.font.width(lootText) + 6;
            graphics.fill(badgeX, badgeY, badgeX + lootWidth, badgeY + 10, IsotopeColors.BADGE_HAS_LOOT);
            graphics.drawString(mc.font, lootText, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_PRIMARY, false);
            badgeX += lootWidth + 3;
        } else {
            String noLoot = "NO LOOT";
            int noLootWidth = mc.font.width(noLoot) + 6;
            graphics.fill(badgeX, badgeY, badgeX + noLootWidth, badgeY + 10, IsotopeColors.BADGE_NO_LOOT);
            graphics.drawString(mc.font, noLoot, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_MUTED, false);
            badgeX += noLootWidth + 3;
        }

        // VANILLA badge (for minecraft namespace)
        if (entry.structure.isVanilla()) {
            String vanillaText = "VANILLA";
            int vanillaWidth = mc.font.width(vanillaText) + 6;
            graphics.fill(badgeX, badgeY, badgeX + vanillaWidth, badgeY + 10, IsotopeColors.BADGE_VANILLA);
            graphics.drawString(mc.font, vanillaText, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_PRIMARY, false);
        }
    }
}
