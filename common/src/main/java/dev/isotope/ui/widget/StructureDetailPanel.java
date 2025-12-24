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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Right panel showing structure details and linked loot tables with confidence.
 *
 * Displays heuristic links with confidence badges and allows author overrides:
 * - Add new links via "+" button
 * - Remove links via "x" button next to each entry
 */
@Environment(EnvType.CLIENT)
public class StructureDetailPanel {

    private final int x, y, width, height;
    private final Consumer<ResourceLocation> onViewLootTable;
    private final Consumer<ResourceLocation> onAddLink;      // Called when user wants to add a link
    private final BiConsumer<ResourceLocation, ResourceLocation> onRemoveLink; // structure, lootTable

    @Nullable
    private StructureListWidget.StructureEntry currentEntry;
    private final List<Button> allButtons = new ArrayList<>();

    public StructureDetailPanel(int x, int y, int width, int height,
                                 Consumer<ResourceLocation> onViewLootTable,
                                 Consumer<ResourceLocation> onAddLink,
                                 BiConsumer<ResourceLocation, ResourceLocation> onRemoveLink) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onViewLootTable = onViewLootTable;
        this.onAddLink = onAddLink;
        this.onRemoveLink = onRemoveLink;
    }

    public void setStructure(@Nullable StructureListWidget.StructureEntry entry) {
        this.currentEntry = entry;
        rebuildButtons();
    }

    private void rebuildButtons() {
        allButtons.clear();

        if (currentEntry == null) {
            return;
        }

        int buttonY = y + 115;
        int lootButtonWidth = width - 45; // Leave room for remove button

        // Linked loot table buttons with remove buttons
        for (StructureLootLink link : currentEntry.links()) {
            if (buttonY + 20 > y + height - 35) break; // Leave room for Add button

            // Loot table button (clickable to view)
            Button lootBtn = Button.builder(
                Component.literal(truncatePath(link.lootTableId().getPath(), lootButtonWidth - 10)),
                b -> onViewLootTable.accept(link.lootTableId())
            )
            .pos(x + 10, buttonY)
            .size(lootButtonWidth, 18)
            .build();
            allButtons.add(lootBtn);

            // Remove button (X)
            Button removeBtn = Button.builder(
                Component.literal("X"),
                b -> onRemoveLink.accept(currentEntry.info().id(), link.lootTableId())
            )
            .pos(x + 10 + lootButtonWidth + 2, buttonY)
            .size(18, 18)
            .build();
            allButtons.add(removeBtn);

            buttonY += 22;
        }

        // "Add Link" button at bottom
        Button addButton = Button.builder(
            Component.literal("+ Add Link"),
            b -> onAddLink.accept(currentEntry.info().id())
        )
        .pos(x + 10, y + height - 30)
        .size(width - 20, 20)
        .build();
        allButtons.add(addButton);
    }

    private String truncatePath(String path, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        if (font.width(path) <= maxWidth) {
            return path;
        }
        // Truncate from the beginning to keep the meaningful end
        while (font.width("..." + path) > maxWidth && path.length() > 5) {
            path = path.substring(1);
        }
        return "..." + path;
    }

    public List<Button> getButtons() {
        return allButtons;
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
        String fullId = currentEntry.info().fullId();
        if (font.width(fullId) > width - 45) {
            fullId = fullId.substring(0, 20) + "...";
        }
        graphics.drawString(font, fullId, x + 30, infoY, IsotopeColors.TEXT_SECONDARY);
        infoY += 14;

        // Namespace
        graphics.drawString(font, "Mod:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, currentEntry.info().namespace(), x + 35, infoY, IsotopeColors.TEXT_SECONDARY);
        infoY += 14;

        // Data source - show if any links are manual
        graphics.drawString(font, "Source:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
        boolean hasManual = currentEntry.links().stream()
            .anyMatch(l -> l.confidence() == StructureLootLink.Confidence.MANUAL);
        if (hasManual) {
            graphics.drawString(font, "EDITED", x + 55, infoY, IsotopeColors.BADGE_HAS_LOOT);
        } else {
            graphics.drawString(font, "HEURISTIC", x + 55, infoY, IsotopeColors.STATUS_WARNING);
        }
        infoY += 14;

        // Loot status
        graphics.drawString(font, "Links:", x + 10, infoY, IsotopeColors.TEXT_MUTED);
        if (currentEntry.hasLoot()) {
            int lootCount = currentEntry.lootTableCount();
            StructureLootLink.Confidence conf = currentEntry.getHighestConfidence();
            String confLabel = conf != null ? " (" + conf.getLabel() + ")" : "";
            graphics.drawString(font, lootCount + " table" + (lootCount != 1 ? "s" : "") + confLabel,
                x + 45, infoY, conf != null ? conf.getColor() : IsotopeColors.BADGE_HAS_LOOT);
        } else {
            graphics.drawString(font, "None linked", x + 45, infoY, IsotopeColors.BADGE_NO_LOOT);
        }

        // Linked loot tables section header
        int sectionY = y + 100;
        graphics.drawString(font, "Linked Loot Tables:", x + 10, sectionY, IsotopeColors.TEXT_PRIMARY);

        // Draw confidence indicators next to each button
        int indicatorY = y + 115;
        for (StructureLootLink link : currentEntry.links()) {
            if (indicatorY + 20 > y + height - 35) break;

            // Draw small confidence dot to the left
            int dotX = x + 3;
            int dotY = indicatorY + 5;
            graphics.fill(dotX, dotY, dotX + 4, dotY + 8, link.confidence().getColor());

            indicatorY += 22;
        }

        // Buttons are rendered by MainScreen (added as renderables)
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
