package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left panel widget showing namespaces (mods) with structure counts.
 */
@Environment(EnvType.CLIENT)
public class NamespaceListWidget extends ScrollableListWidget<NamespaceListWidget.NamespaceEntry> {

    public record NamespaceEntry(String namespace, int structureCount) {
        public String displayName() {
            if ("minecraft".equals(namespace)) {
                return "Minecraft";
            }
            // Capitalize first letter
            return namespace.substring(0, 1).toUpperCase() + namespace.substring(1);
        }
    }

    public NamespaceListWidget(int x, int y, int width, int height, Consumer<NamespaceEntry> onSelect) {
        super(x, y, width, height, 22, NamespaceListWidget::renderEntry, onSelect);
    }

    public void loadData() {
        Map<String, Integer> counts = ClientDataProvider.getInstance().getStructureCountByNamespace();
        List<NamespaceEntry> entries = counts.entrySet().stream()
            .map(e -> new NamespaceEntry(e.getKey(), e.getValue()))
            .toList();
        setItems(entries);

        // Select first by default if available
        if (!entries.isEmpty()) {
            setSelected(entries.get(0));
        }
    }

    private static void renderEntry(GuiGraphics graphics, NamespaceEntry entry, int x, int y,
                                     int width, int height, boolean selected, boolean hovered) {
        Minecraft mc = Minecraft.getInstance();

        // Namespace name
        int textColor = selected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY;
        graphics.drawString(mc.font, entry.displayName(), x, y + 3, textColor, false);

        // Structure count badge
        String countText = String.valueOf(entry.structureCount);
        int countWidth = mc.font.width(countText) + 6;
        int badgeX = x + width - countWidth - 4;
        int badgeColor = selected ? IsotopeColors.ACCENT_CYAN_DARK : IsotopeColors.BADGE_NO_LOOT;

        graphics.fill(badgeX, y + 2, badgeX + countWidth, y + height - 4, badgeColor);
        graphics.drawString(mc.font, countText, badgeX + 3, y + 4, IsotopeColors.TEXT_PRIMARY, false);
    }
}
