package dev.isotope.ui.widget;

import dev.isotope.analysis.AnalysisEngine;
import dev.isotope.analysis.LootContentsRegistry;
import dev.isotope.data.LootEntryInfo;
import dev.isotope.data.LootPoolInfo;
import dev.isotope.data.LootTableContents;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Right panel showing loot table details including pools and entries.
 */
@Environment(EnvType.CLIENT)
public class LootTableDetailPanel {

    private final int x, y, width, height;

    @Nullable
    private LootTableListWidget.LootTableEntry currentEntry;

    @Nullable
    private LootTableContents contents;

    @Nullable
    private AnalysisEngine.LootSampleResult sampleResult;

    private int scrollOffset = 0;

    public LootTableDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setLootTable(@Nullable LootTableListWidget.LootTableEntry entry) {
        this.currentEntry = entry;
        this.scrollOffset = 0;

        if (entry != null) {
            this.contents = LootContentsRegistry.getInstance().get(entry.info().id());
            this.sampleResult = AnalysisEngine.getInstance().getSample(entry.info().id()).orElse(null);
        } else {
            this.contents = null;
            this.sampleResult = null;
        }
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

        // Category
        graphics.drawString(font, "Category:", x + 10, contentY, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, currentEntry.info().category().name(), x + 65, contentY, IsotopeColors.TEXT_SECONDARY);
        contentY += 12;

        // Sample data if available
        if (sampleResult != null && !sampleResult.hasError()) {
            contentY += 5;
            graphics.fill(x + 5, contentY, x + width - 5, contentY + 1, IsotopeColors.BORDER_DEFAULT);
            contentY += 8;

            graphics.drawString(font, "Sample Results", x + 10, contentY, IsotopeColors.ACCENT_CYAN);
            contentY += 12;

            graphics.drawString(font, "Samples: " + sampleResult.sampleCount(), x + 10, contentY, IsotopeColors.TEXT_SECONDARY);
            contentY += 10;

            graphics.drawString(font, String.format("Avg items/roll: %.2f", sampleResult.averageItemsPerRoll()),
                x + 10, contentY, IsotopeColors.TEXT_SECONDARY);
            contentY += 12;

            // Top items by drop rate
            if (!sampleResult.itemDistribution().isEmpty()) {
                graphics.drawString(font, "Item Drop Rates:", x + 10, contentY, IsotopeColors.TEXT_MUTED);
                contentY += 12;

                List<AnalysisEngine.ItemSampleData> topItems = sampleResult.itemDistribution().stream()
                    .sorted((a, b) -> Integer.compare(b.getOccurrences(), a.getOccurrences()))
                    .limit(8)
                    .toList();

                for (AnalysisEngine.ItemSampleData item : topItems) {
                    float dropRate = item.getDropRate(sampleResult.sampleCount()) * 100;
                    String itemName = getItemDisplayName(item.getItemId());

                    // Item icon
                    renderItemIcon(graphics, item.getItemId(), x + 12, contentY - 1);

                    // Item name and rate
                    String text = String.format("%s: %.1f%%", itemName, dropRate);
                    if (font.width(text) > width - 40) {
                        text = String.format("%.1f%% (%.1f avg)", dropRate, item.getAverageCount());
                    }
                    graphics.drawString(font, text, x + 30, contentY, IsotopeColors.TEXT_SECONDARY);

                    contentY += 14;
                }
            }
        }

        // Pool analysis from LootContentsRegistry
        if (contents != null && contents.analyzed()) {
            contentY += 5;
            graphics.fill(x + 5, contentY, x + width - 5, contentY + 1, IsotopeColors.BORDER_DEFAULT);
            contentY += 8;

            graphics.drawString(font, "Structure Analysis", x + 10, contentY, IsotopeColors.ACCENT_CYAN);
            contentY += 12;

            graphics.drawString(font, "Pools: " + contents.poolCount(), x + 10, contentY, IsotopeColors.TEXT_SECONDARY);
            contentY += 10;

            graphics.drawString(font, "Total entries: " + contents.totalEntryCount(), x + 10, contentY, IsotopeColors.TEXT_SECONDARY);
            contentY += 14;

            // Show each pool
            for (LootPoolInfo pool : contents.pools()) {
                graphics.drawString(font, "Pool " + (pool.poolIndex() + 1) + ":", x + 10, contentY, IsotopeColors.TEXT_MUTED);
                graphics.drawString(font, pool.rollsDescription(), x + 55, contentY, IsotopeColors.TEXT_SECONDARY);
                contentY += 12;

                // Pool entries (limited)
                int entryCount = 0;
                for (LootEntryInfo entry : pool.entries()) {
                    if (entryCount >= 5) {
                        graphics.drawString(font, "  +" + (pool.entries().size() - 5) + " more...",
                            x + 15, contentY, IsotopeColors.TEXT_MUTED);
                        contentY += 10;
                        break;
                    }

                    String entryText = formatEntry(entry);
                    if (entry.itemId().isPresent()) {
                        renderItemIcon(graphics, entry.itemId().get(), x + 15, contentY - 1);
                        graphics.drawString(font, entryText, x + 33, contentY, IsotopeColors.TEXT_SECONDARY);
                    } else {
                        graphics.drawString(font, "  " + entryText, x + 15, contentY, IsotopeColors.TEXT_SECONDARY);
                    }
                    contentY += 12;
                    entryCount++;
                }

                contentY += 5;
            }
        } else if (contents != null && contents.errorMessage() != null) {
            contentY += 10;
            graphics.drawString(font, "Analysis error:", x + 10, contentY, IsotopeColors.STATUS_ERROR);
            contentY += 10;
            graphics.drawString(font, contents.errorMessage(), x + 10, contentY, IsotopeColors.TEXT_MUTED);
        }

        graphics.disableScissor();
    }

    private String formatEntry(LootEntryInfo entry) {
        StringBuilder sb = new StringBuilder();

        if (entry.itemId().isPresent()) {
            sb.append(getItemDisplayName(entry.itemId().get()));
        } else if (entry.tableRef().isPresent()) {
            sb.append("[").append(entry.tableRef().get().getPath()).append("]");
        } else if (entry.tagId().isPresent()) {
            sb.append("#").append(entry.tagId().get().getPath());
        } else {
            sb.append(entry.type().name().toLowerCase());
        }

        if (entry.weight() > 1) {
            sb.append(" w:").append(entry.weight());
        }

        if (entry.minCount() != entry.maxCount()) {
            sb.append(" ").append(entry.minCount()).append("-").append(entry.maxCount());
        } else if (entry.minCount() > 1) {
            sb.append(" x").append(entry.minCount());
        }

        return sb.toString();
    }

    private String getItemDisplayName(ResourceLocation itemId) {
        try {
            var itemOpt = BuiltInRegistries.ITEM.get(itemId);
            if (itemOpt.isPresent()) {
                ItemStack stack = new ItemStack(itemOpt.get());
                String name = stack.getHoverName().getString();
                if (name.length() > 20) {
                    return name.substring(0, 17) + "...";
                }
                return name;
            }
        } catch (Exception e) {
            // Fallback
        }
        return itemId.getPath();
    }

    private void renderItemIcon(GuiGraphics graphics, ResourceLocation itemId, int x, int y) {
        try {
            var itemOpt = BuiltInRegistries.ITEM.get(itemId);
            if (itemOpt.isPresent()) {
                ItemStack stack = new ItemStack(itemOpt.get());
                // Scale down to fit in text line
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(0.75f, 0.75f, 1f);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            }
        } catch (Exception e) {
            // Skip icon on error
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            scrollOffset = Math.max(0, scrollOffset - (int)(scrollY * 10));
            return true;
        }
        return false;
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
