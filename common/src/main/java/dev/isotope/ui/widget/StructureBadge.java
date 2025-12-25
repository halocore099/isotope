package dev.isotope.ui.widget;

import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Widget showing structures that use a loot table.
 * Displays as clickable badges.
 */
@Environment(EnvType.CLIENT)
public class StructureBadge extends AbstractWidget {

    private static final int BADGE_HEIGHT = 14;
    private static final int BADGE_PADDING = 4;
    private static final int BADGE_SPACING = 4;

    @Nullable
    private ResourceLocation tableId;
    private List<StructureLootLink> links = new ArrayList<>();
    private List<BadgeRect> badgeRects = new ArrayList<>();

    @Nullable
    private Consumer<ResourceLocation> onStructureClicked;

    public StructureBadge(int x, int y, int width) {
        super(x, y, width, BADGE_HEIGHT, Component.empty());
    }

    /**
     * Set the loot table to show structures for.
     */
    public void setLootTable(@Nullable ResourceLocation tableId) {
        this.tableId = tableId;
        refreshLinks();
    }

    /**
     * Set click handler for structure badges.
     */
    public void setOnStructureClicked(@Nullable Consumer<ResourceLocation> handler) {
        this.onStructureClicked = handler;
    }

    private void refreshLinks() {
        links.clear();
        badgeRects.clear();

        if (tableId == null) {
            return;
        }

        links = StructureLootLinker.getInstance().getLinksForLootTable(tableId);

        // Pre-calculate badge positions
        Font font = Minecraft.getInstance().font;
        int x = getX();

        for (StructureLootLink link : links) {
            String name = getShortName(link.structureId());
            int textWidth = font.width(name);
            int badgeWidth = textWidth + BADGE_PADDING * 2;

            if (x + badgeWidth > getX() + width) {
                break; // No more space
            }

            badgeRects.add(new BadgeRect(x, getY(), badgeWidth, link));
            x += badgeWidth + BADGE_SPACING;
        }
    }

    private String getShortName(ResourceLocation id) {
        String path = id.getPath();
        // Remove common prefixes
        if (path.startsWith("structure/")) {
            path = path.substring(10);
        }
        // Shorten if too long
        if (path.length() > 20) {
            path = path.substring(0, 17) + "...";
        }
        return path;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (links.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;

        // Label
        graphics.drawString(font, "Used by:", getX(), getY() + 3, IsotopeColors.TEXT_MUTED, false);

        int labelWidth = font.width("Used by: ");
        int badgeStartX = getX() + labelWidth + 4;

        for (BadgeRect badge : badgeRects) {
            boolean hovered = mouseX >= badge.x + labelWidth + 4 && mouseX < badge.x + labelWidth + 4 + badge.width &&
                              mouseY >= badge.y && mouseY < badge.y + BADGE_HEIGHT;

            // Badge background
            int bgColor = hovered ? 0xFF4a4a4a : 0xFF333333;
            int borderColor = getConfidenceColor(badge.link.confidence());

            graphics.fill(badge.x + labelWidth + 4, badge.y,
                badge.x + labelWidth + 4 + badge.width, badge.y + BADGE_HEIGHT, bgColor);
            graphics.renderOutline(badge.x + labelWidth + 4, badge.y,
                badge.width, BADGE_HEIGHT, borderColor);

            // Structure name
            String name = getShortName(badge.link.structureId());
            graphics.drawString(font, name, badge.x + labelWidth + 4 + BADGE_PADDING, badge.y + 3,
                IsotopeColors.TEXT_PRIMARY, false);
        }

        // Show count if more structures exist
        if (links.size() > badgeRects.size()) {
            int remaining = links.size() - badgeRects.size();
            String moreText = "+" + remaining + " more";
            int lastBadge = badgeRects.isEmpty() ? badgeStartX :
                badgeRects.get(badgeRects.size() - 1).x + labelWidth + 4 +
                badgeRects.get(badgeRects.size() - 1).width + BADGE_SPACING;
            graphics.drawString(font, moreText, lastBadge, getY() + 3, IsotopeColors.TEXT_MUTED, false);
        }
    }

    private int getConfidenceColor(StructureLootLink.Confidence confidence) {
        return switch (confidence) {
            case MANUAL, VERIFIED -> 0xFF55FF55;  // Green
            case HIGH -> 0xFF5555FF;               // Blue
            case MEDIUM -> 0xFFFFFF55;             // Yellow
            case LOW -> 0xFFFF8800;                // Orange
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (onStructureClicked == null) {
            return false;
        }

        Font font = Minecraft.getInstance().font;
        int labelWidth = font.width("Used by: ");

        for (BadgeRect badge : badgeRects) {
            if (mouseX >= badge.x + labelWidth + 4 && mouseX < badge.x + labelWidth + 4 + badge.width &&
                mouseY >= badge.y && mouseY < badge.y + BADGE_HEIGHT) {
                onStructureClicked.accept(badge.link.structureId());
                return true;
            }
        }

        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    private record BadgeRect(int x, int y, int width, StructureLootLink link) {}
}
