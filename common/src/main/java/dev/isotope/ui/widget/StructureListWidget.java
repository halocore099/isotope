package dev.isotope.ui.widget;

import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Center panel widget showing structures for selected namespace.
 *
 * Shows ALL structures from the registry with heuristic link information.
 */
@Environment(EnvType.CLIENT)
public class StructureListWidget extends ScrollableListWidget<StructureListWidget.StructureEntry> {

    public record StructureEntry(
        StructureInfo info,
        List<StructureLootLink> links
    ) {
        public String displayName() {
            return info.path();
        }

        public boolean hasLoot() {
            return !links.isEmpty();
        }

        public int lootTableCount() {
            return links.size();
        }

        public boolean isVanilla() {
            return info.isVanilla();
        }

        /**
         * Get the highest confidence level among all links.
         */
        public StructureLootLink.Confidence getHighestConfidence() {
            return links.stream()
                .map(StructureLootLink::confidence)
                .max(Comparator.comparingInt(StructureLootLink.Confidence::getScore))
                .orElse(null);
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

        ClientDataProvider provider = ClientDataProvider.getInstance();
        List<StructureInfo> structures = provider.getStructuresByNamespace(namespace);

        List<StructureEntry> entries = new ArrayList<>();
        for (StructureInfo info : structures) {
            List<StructureLootLink> links = provider.getLinksForStructure(info.id());
            entries.add(new StructureEntry(info, links));
        }

        // Sort: structures with loot first, then alphabetically
        entries.sort(Comparator
            .comparing((StructureEntry e) -> !e.hasLoot())
            .thenComparing(StructureEntry::displayName));

        setItems(entries);
    }

    /**
     * Reload the currently selected entry with fresh link data.
     * Called after links are added or removed.
     */
    public void reloadCurrentEntry() {
        StructureEntry current = getSelected();
        if (current == null) return;

        // Get fresh link data
        ClientDataProvider provider = ClientDataProvider.getInstance();
        List<StructureLootLink> freshLinks = provider.getLinksForStructure(current.info().id());

        // Find and update the entry in the list
        List<StructureEntry> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).info().id().equals(current.info().id())) {
                StructureEntry updated = new StructureEntry(current.info(), freshLinks);
                items.set(i, updated);
                setSelected(updated);
                break;
            }
        }
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

        // Loot count badge with confidence color
        if (entry.hasLoot()) {
            StructureLootLink.Confidence confidence = entry.getHighestConfidence();
            String lootText = entry.lootTableCount() + " loot";
            int lootWidth = mc.font.width(lootText) + 6;

            // Use confidence color for the badge
            int badgeColor = confidence != null ? confidence.getColor() : IsotopeColors.BADGE_HAS_LOOT;
            graphics.fill(badgeX, badgeY, badgeX + lootWidth, badgeY + 10, badgeColor);
            graphics.drawString(mc.font, lootText, badgeX + 3, badgeY + 1, 0xFF000000, false);
            badgeX += lootWidth + 3;

            // Show confidence label
            if (confidence != null) {
                String confText = confidence.getLabel();
                int confWidth = mc.font.width(confText) + 4;
                graphics.fill(badgeX, badgeY, badgeX + confWidth, badgeY + 10, 0x80000000);
                graphics.drawString(mc.font, confText, badgeX + 2, badgeY + 1, confidence.getColor(), false);
                badgeX += confWidth + 3;
            }
        } else {
            String noLoot = "NO LINKS";
            int noLootWidth = mc.font.width(noLoot) + 6;
            graphics.fill(badgeX, badgeY, badgeX + noLootWidth, badgeY + 10, IsotopeColors.BADGE_NO_LOOT);
            graphics.drawString(mc.font, noLoot, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_MUTED, false);
            badgeX += noLootWidth + 3;
        }

        // VANILLA badge
        if (entry.isVanilla()) {
            String vanillaText = "VANILLA";
            int vanillaWidth = mc.font.width(vanillaText) + 6;
            graphics.fill(badgeX, badgeY, badgeX + vanillaWidth, badgeY + 10, IsotopeColors.BADGE_VANILLA);
            graphics.drawString(mc.font, vanillaText, badgeX + 3, badgeY + 1, IsotopeColors.TEXT_PRIMARY, false);
        }
    }
}
