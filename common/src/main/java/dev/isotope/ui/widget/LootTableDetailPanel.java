package dev.isotope.ui.widget;

import dev.isotope.data.StructureLootLink;
import dev.isotope.editing.LootEditManager;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.data.ClientDataProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Right panel showing loot table details based on registry data.
 *
 * Shows loot table info and which structures link to it with confidence levels.
 */
@Environment(EnvType.CLIENT)
public class LootTableDetailPanel {

    private final int x, y, width, height;

    @Nullable
    private LootTableListWidget.LootTableEntry currentEntry;

    private int scrollOffset = 0;

    // Edit button callback
    @Nullable
    private Consumer<ResourceLocation> onEditClicked;

    // Edit button bounds (for click handling)
    private int editButtonX, editButtonY, editButtonWidth, editButtonHeight;
    private boolean editButtonVisible = false;

    public LootTableDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setOnEditClicked(@Nullable Consumer<ResourceLocation> callback) {
        this.onEditClicked = callback;
    }

    public void setLootTable(@Nullable LootTableListWidget.LootTableEntry entry) {
        this.scrollOffset = 0;
        this.currentEntry = entry;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Panel background
        graphics.fill(x, y, x + width, y + height, IsotopeColors.BACKGROUND_PANEL);
        renderBorder(graphics, x, y, width, height, IsotopeColors.BORDER_DEFAULT);

        // Title
        graphics.drawString(font, "Loot Table Details", x + 10, y + 8, IsotopeColors.TEXT_PRIMARY);
        graphics.fill(x + 5, y + 22, x + width - 5, y + 23, IsotopeColors.BORDER_DEFAULT);

        if (currentEntry == null) {
            graphics.drawString(font, "Select a loot table", x + 10, y + 35, IsotopeColors.TEXT_MUTED);
            graphics.drawString(font, "to view details", x + 10, y + 47, IsotopeColors.TEXT_MUTED);
            return;
        }

        // Enable scissor for scrolling
        graphics.enableScissor(x + 1, y + 25, x + width - 1, y + height - 1);

        int contentY = y + 30 - scrollOffset;

        // Table ID
        graphics.drawString(font, "ID:", x + 10, contentY, IsotopeColors.TEXT_MUTED);
        String fullId = currentEntry.info().fullId();
        if (font.width(fullId) > width - 35) {
            fullId = "..." + fullId.substring(fullId.length() - 25);
        }
        graphics.drawString(font, fullId, x + 25, contentY, IsotopeColors.TEXT_SECONDARY);
        contentY += 12;

        // Namespace
        graphics.drawString(font, "Mod:", x + 10, contentY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, currentEntry.namespace(), x + 35, contentY, IsotopeColors.TEXT_SECONDARY);
        contentY += 12;

        // Category
        graphics.drawString(font, "Category:", x + 10, contentY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, currentEntry.category().name(), x + 65, contentY, IsotopeColors.TEXT_SECONDARY);
        contentY += 12;

        // Data source
        graphics.drawString(font, "Source:", x + 10, contentY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, "REGISTRY", x + 55, contentY, IsotopeColors.BADGE_VANILLA);
        contentY += 15;

        // Edit button (only show when in-world)
        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            editButtonVisible = true;
            editButtonWidth = 50;
            editButtonHeight = 16;
            editButtonX = x + width - editButtonWidth - 10;
            editButtonY = y + 30;

            // Button background
            boolean hasEdits = LootEditManager.getInstance().hasEdits(
                ResourceLocation.parse(currentEntry.info().fullId()));
            int buttonColor = hasEdits ? IsotopeColors.BADGE_MODIFIED : IsotopeColors.BUTTON_BACKGROUND;
            graphics.fill(editButtonX, editButtonY, editButtonX + editButtonWidth, editButtonY + editButtonHeight, buttonColor);

            // Button border
            graphics.fill(editButtonX, editButtonY, editButtonX + editButtonWidth, editButtonY + 1, 0xFFC6C6C6);
            graphics.fill(editButtonX, editButtonY, editButtonX + 1, editButtonY + editButtonHeight, 0xFFC6C6C6);
            graphics.fill(editButtonX, editButtonY + editButtonHeight - 1, editButtonX + editButtonWidth, editButtonY + editButtonHeight, 0xFF2A2A2A);
            graphics.fill(editButtonX + editButtonWidth - 1, editButtonY, editButtonX + editButtonWidth, editButtonY + editButtonHeight, 0xFF2A2A2A);

            // Button text
            String editLabel = hasEdits ? "Edit*" : "Edit";
            int textWidth = font.width(editLabel);
            graphics.drawString(font, editLabel, editButtonX + (editButtonWidth - textWidth) / 2,
                editButtonY + 4, IsotopeColors.TEXT_PRIMARY, false);
        } else {
            editButtonVisible = false;
        }

        // Linked structures section
        List<StructureLootLink> links = currentEntry.links();

        graphics.fill(x + 5, contentY, x + width - 5, contentY + 1, IsotopeColors.BORDER_DEFAULT);
        contentY += 8;

        if (!links.isEmpty()) {
            graphics.drawString(font, "Linked Structures (" + links.size() + ")",
                x + 10, contentY, IsotopeColors.ACCENT_CYAN);
            contentY += 14;

            for (StructureLootLink link : links) {
                // Confidence dot
                graphics.fill(x + 10, contentY + 2, x + 14, contentY + 8, link.confidence().getColor());

                // Structure path
                String structPath = link.structureId().getPath();
                if (font.width(structPath) > width - 50) {
                    structPath = structPath.substring(0, 15) + "...";
                }
                graphics.drawString(font, structPath, x + 18, contentY, IsotopeColors.TEXT_SECONDARY);

                // Confidence label
                String confLabel = link.confidence().getLabel();
                int labelX = x + width - font.width(confLabel) - 10;
                graphics.drawString(font, confLabel, labelX, contentY, link.confidence().getColor());

                contentY += 12;
            }
        } else {
            graphics.drawString(font, "No Linked Structures", x + 10, contentY, IsotopeColors.TEXT_MUTED);
            contentY += 14;

            graphics.drawString(font, "This loot table is not", x + 10, contentY, IsotopeColors.TEXT_MUTED);
            contentY += 10;
            graphics.drawString(font, "linked to any structure.", x + 10, contentY, IsotopeColors.TEXT_MUTED);
            contentY += 12;

            // Hint about manual linking
            contentY += 5;
            graphics.drawString(font, "You can manually link it", x + 10, contentY, IsotopeColors.ACCENT_CYAN);
            contentY += 10;
            graphics.drawString(font, "from the Structures tab.", x + 10, contentY, IsotopeColors.ACCENT_CYAN);
        }

        graphics.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check Edit button click
        if (editButtonVisible && currentEntry != null) {
            if (mouseX >= editButtonX && mouseX < editButtonX + editButtonWidth
                && mouseY >= editButtonY && mouseY < editButtonY + editButtonHeight) {
                if (onEditClicked != null) {
                    onEditClicked.accept(ResourceLocation.parse(currentEntry.info().fullId()));
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            scrollOffset = Math.max(0, scrollOffset - (int)(scrollY * 10));
            return true;
        }
        return false;
    }

    @Nullable
    public ResourceLocation getCurrentTableId() {
        return currentEntry != null ? ResourceLocation.parse(currentEntry.info().fullId()) : null;
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
