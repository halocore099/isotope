package dev.isotope.ui.widget;

import dev.isotope.data.StructureLootLink;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Right panel showing structure details and linked loot tables.
 */
@Environment(EnvType.CLIENT)
public class StructureDetailPanel {

    private final int x, y, width, height;
    private final Consumer<ResourceLocation> onViewLootTable;

    @Nullable
    private StructureListWidget.StructureEntry currentEntry;
    private final List<Button> lootTableButtons = new ArrayList<>();

    public StructureDetailPanel(int x, int y, int width, int height,
                                 Consumer<ResourceLocation> onViewLootTable) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onViewLootTable = onViewLootTable;
    }

    public void setStructure(@Nullable StructureListWidget.StructureEntry entry) {
        this.currentEntry = entry;
        rebuildButtons();
    }

    private void rebuildButtons() {
        lootTableButtons.clear();

        if (currentEntry == null || !currentEntry.hasLoot()) {
            return;
        }

        StructureLootLink link = currentEntry.link();
        int buttonY = y + 100;

        for (ResourceLocation tableId : link.linkedTables()) {
            if (buttonY + 20 > y + height - 10) break; // Don't overflow

            Button btn = IsotopeButton.isotopeBuilder(
                Component.literal(tableId.getPath()),
                b -> onViewLootTable.accept(tableId)
            )
            .pos(x + 10, buttonY)
            .size(width - 20, 18)
            .style(IsotopeButton.ButtonStyle.DEFAULT)
            .build();

            lootTableButtons.add(btn);
            buttonY += 22;
        }
    }

    public List<Button> getButtons() {
        return lootTableButtons;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Panel background
        graphics.fill(x, y, x + width, y + height, IsotopeColors.BACKGROUND_PANEL);

        // Border
        renderBorder(graphics, x, y, width, height, IsotopeColors.BORDER_DEFAULT);

        // Title
        graphics.drawString(font, "Details", x + 10, y + 8, IsotopeColors.TEXT_PRIMARY);

        // Separator
        graphics.fill(x + 5, y + 22, x + width - 5, y + 23, IsotopeColors.BORDER_DEFAULT);

        if (currentEntry == null) {
            // No selection message
            graphics.drawString(font, "Select a structure", x + 10, y + 35, IsotopeColors.TEXT_MUTED);
            graphics.drawString(font, "to view details", x + 10, y + 47, IsotopeColors.TEXT_MUTED);
            return;
        }

        // Structure info
        int infoY = y + 30;

        // Full ID
        graphics.drawString(font, "ID:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, currentEntry.structure().fullId(), x + 30, infoY, IsotopeColors.TEXT_SECONDARY);
        infoY += 14;

        // Namespace
        graphics.drawString(font, "Mod:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, currentEntry.structure().namespace(), x + 35, infoY, IsotopeColors.TEXT_SECONDARY);
        infoY += 14;

        // Loot status
        graphics.drawString(font, "Loot:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
        if (currentEntry.hasLoot()) {
            int lootCount = currentEntry.lootTableCount();
            graphics.drawString(font, lootCount + " table" + (lootCount != 1 ? "s" : ""),
                x + 40, infoY, IsotopeColors.BADGE_HAS_LOOT);
        } else {
            graphics.drawString(font, "None linked", x + 40, infoY, IsotopeColors.BADGE_NO_LOOT);
        }
        infoY += 14;

        // Link confidence (if has loot)
        if (currentEntry.hasLoot() && currentEntry.link() != null) {
            graphics.drawString(font, "Confidence:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
            StructureLootLink link = currentEntry.link();
            int confColor = getConfidenceColor(link.confidence());
            graphics.drawString(font, link.confidencePercent() + " (" + link.linkMethod().name() + ")",
                x + 75, infoY, confColor);
        }

        // Linked loot tables section
        if (currentEntry.hasLoot()) {
            int sectionY = y + 90;
            graphics.drawString(font, "Loot Tables:", x + 10, sectionY, IsotopeColors.TEXT_PRIMARY);

            // Buttons are rendered by MainScreen (added as renderables)
        }
    }

    private int getConfidenceColor(float confidence) {
        if (confidence >= 0.9f) return IsotopeColors.CONFIDENCE_EXACT;
        if (confidence >= 0.7f) return IsotopeColors.CONFIDENCE_CONDITIONAL;
        if (confidence >= 0.5f) return IsotopeColors.CONFIDENCE_OBSERVED;
        return IsotopeColors.CONFIDENCE_UNKNOWN;
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
