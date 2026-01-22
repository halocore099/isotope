package dev.isotope.ui.screen;

import dev.isotope.testing.DropStatistics;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Dialog comparing drop statistics between original and edited loot tables.
 * Shows side-by-side comparison with difference highlighting.
 */
@Environment(EnvType.CLIENT)
public class CompareStatisticsDialog extends Screen {

    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 350;

    @Nullable
    private final Screen parent;
    private final DropStatistics originalStats;
    private final DropStatistics editedStats;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Combined entries for comparison
    private final List<CompareEntry> entries = new ArrayList<>();

    private record CompareEntry(
        String itemName,
        ResourceLocation itemId,
        int originalTotal,
        float originalAvg,
        float originalRate,
        int editedTotal,
        float editedAvg,
        float editedRate
    ) {
        float avgDiff() {
            return editedAvg - originalAvg;
        }

        float rateDiff() {
            return editedRate - originalRate;
        }

        boolean isNew() {
            return originalTotal == 0 && editedTotal > 0;
        }

        boolean isRemoved() {
            return originalTotal > 0 && editedTotal == 0;
        }

        boolean hasChanged() {
            return Math.abs(avgDiff()) > 0.01f || Math.abs(rateDiff()) > 0.5f;
        }
    }

    public CompareStatisticsDialog(@Nullable Screen parent, DropStatistics originalStats, DropStatistics editedStats) {
        super(Component.literal("Compare Drop Statistics"));
        this.parent = parent;
        this.originalStats = originalStats;
        this.editedStats = editedStats;
        buildEntries();
    }

    private void buildEntries() {
        entries.clear();

        // Collect all unique items from both stats
        Set<ResourceLocation> allItems = new LinkedHashSet<>();
        allItems.addAll(originalStats.getDroppedItems());
        allItems.addAll(editedStats.getDroppedItems());

        for (ResourceLocation itemId : allItems) {
            int origTotal = originalStats.getTotalForItem(itemId);
            float origAvg = originalStats.getAverageForItem(itemId);
            float origRate = originalStats.getDropRateForItem(itemId);

            int editTotal = editedStats.getTotalForItem(itemId);
            float editAvg = editedStats.getAverageForItem(itemId);
            float editRate = editedStats.getDropRateForItem(itemId);

            entries.add(new CompareEntry(
                ScreenUtils.formatItemName(itemId),
                itemId,
                origTotal,
                origAvg,
                origRate,
                editTotal,
                editAvg,
                editRate
            ));
        }

        // Sort: changed items first, then by difference magnitude
        entries.sort((a, b) -> {
            // New/removed items first
            if (a.isNew() != b.isNew()) return a.isNew() ? -1 : 1;
            if (a.isRemoved() != b.isRemoved()) return a.isRemoved() ? -1 : 1;
            // Then by magnitude of change
            float aDiff = Math.abs(a.avgDiff()) + Math.abs(a.rateDiff()) / 10;
            float bDiff = Math.abs(b.avgDiff()) + Math.abs(b.rateDiff()) / 10;
            return Float.compare(bDiff, aDiff);
        });

        // Calculate max scroll
        int contentHeight = entries.size() * 22 + 20;
        int viewHeight = DIALOG_HEIGHT - 160;
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal(UIConstants.LABEL_CLOSE),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 70, dialogY + DIALOG_HEIGHT - 30).size(60, 20).build());

        // Copy to clipboard button
        addRenderableWidget(Button.builder(
            Component.literal("Copy"),
            b -> copyToClipboard()
        ).pos(dialogX + DIALOG_WIDTH - 120, dialogY + DIALOG_HEIGHT - 30).size(45, 20).build());
    }

    private void copyToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append("Compare Statistics: ").append(originalStats.getSourceName()).append("\n");
        sb.append("Tests: ").append(originalStats.getTestCount()).append(" each\n");
        sb.append("\n");
        sb.append(String.format("%-25s %12s %12s %10s\n", "Item", "Original", "Edited", "Diff"));
        sb.append("-".repeat(62)).append("\n");

        for (CompareEntry entry : entries) {
            String status = entry.isNew() ? "[NEW]" :
                           entry.isRemoved() ? "[GONE]" :
                           "";
            String origText = String.format("%.1f (%.0f%%)", entry.originalAvg(), entry.originalRate());
            String editText = String.format("%.1f (%.0f%%)", entry.editedAvg(), entry.editedRate());
            String diffText = entry.isNew() || entry.isRemoved() ? status :
                             String.format("%+.1f", entry.avgDiff());

            sb.append(String.format("%-25s %12s %12s %10s\n",
                ScreenUtils.truncate(entry.itemName(), 25),
                origText,
                editText,
                diffText));
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        IsotopeToast.success("Copied", "Comparison copied to clipboard");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DARK);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        ScreenUtils.drawDialogBackground(graphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT);

        // Header
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 50, IsotopeColors.POOL_HEADER_BACKGROUND);

        // Title
        graphics.drawString(font, "Compare: Original vs Edited", dialogX + 12, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Source info
        graphics.drawString(font, originalStats.getSourceName(), dialogX + 12, dialogY + 22, IsotopeColors.TEXT_PRIMARY, false);

        // Test counts
        String testInfo = originalStats.getTestCount() + " tests each";
        graphics.drawString(font, testInfo, dialogX + DIALOG_WIDTH - font.width(testInfo) - 10, dialogY + 22,
            IsotopeColors.TEXT_SECONDARY, false);

        // Summary
        int newItems = (int) entries.stream().filter(CompareEntry::isNew).count();
        int removedItems = (int) entries.stream().filter(CompareEntry::isRemoved).count();
        int changedItems = (int) entries.stream().filter(e -> e.hasChanged() && !e.isNew() && !e.isRemoved()).count();

        String summary = "";
        if (newItems > 0) summary += "+" + newItems + " new  ";
        if (removedItems > 0) summary += "-" + removedItems + " removed  ";
        if (changedItems > 0) summary += "~" + changedItems + " changed";
        if (summary.isEmpty()) summary = "No changes detected";

        graphics.drawString(font, summary, dialogX + 12, dialogY + 36, IsotopeColors.TEXT_MUTED, false);

        // Column headers
        int headerY = dialogY + 55;
        graphics.fill(dialogX, headerY, dialogX + DIALOG_WIDTH, headerY + 18, IsotopeColors.ENTRY_BACKGROUND);

        graphics.drawString(font, "Item", dialogX + 30, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Original", dialogX + 180, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Edited", dialogX + 280, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Diff", dialogX + 380, headerY + 5, IsotopeColors.TEXT_MUTED, false);

        // Content area
        int contentY = dialogY + 75;
        int contentHeight = DIALOG_HEIGHT - 160;

        // Scissor for scrolling
        graphics.enableScissor(dialogX, contentY, dialogX + DIALOG_WIDTH, contentY + contentHeight);

        if (entries.isEmpty()) {
            graphics.drawString(font, "No items to compare", dialogX + 12, contentY + 10,
                IsotopeColors.TEXT_MUTED, false);
        } else {
            int entryY = contentY - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                CompareEntry entry = entries.get(i);

                if (entryY + 22 > contentY && entryY < contentY + contentHeight) {
                    // Row background based on change type
                    int bgColor = 0x00000000;
                    if (entry.isNew()) {
                        bgColor = 0x20005500; // Green tint for new
                    } else if (entry.isRemoved()) {
                        bgColor = 0x20550000; // Red tint for removed
                    } else if (entry.hasChanged()) {
                        bgColor = 0x20555500; // Yellow tint for changed
                    }
                    if (bgColor != 0) {
                        graphics.fill(dialogX + 5, entryY, dialogX + DIALOG_WIDTH - 5, entryY + 20, bgColor);
                    }

                    // Item icon
                    var itemOpt = BuiltInRegistries.ITEM.get(entry.itemId);
                    if (itemOpt.isPresent()) {
                        ItemStack stack = new ItemStack(itemOpt.get().value());
                        graphics.renderItem(stack, dialogX + 10, entryY + 2);
                    }

                    // Item name
                    String name = entry.itemName;
                    if (font.width(name) > 140) {
                        name = font.plainSubstrByWidth(name, 135) + "...";
                    }
                    int nameColor = entry.isNew() ? IsotopeColors.STATUS_SUCCESS :
                                   entry.isRemoved() ? IsotopeColors.STATUS_ERROR :
                                   IsotopeColors.TEXT_PRIMARY;
                    graphics.drawString(font, name, dialogX + 30, entryY + 5, nameColor, false);

                    // Original stats
                    String origText = String.format("%.1f (%.0f%%)", entry.originalAvg, entry.originalRate);
                    graphics.drawString(font, origText, dialogX + 180, entryY + 5,
                        entry.isRemoved() ? IsotopeColors.STATUS_ERROR : IsotopeColors.TEXT_SECONDARY, false);

                    // Edited stats
                    String editText = String.format("%.1f (%.0f%%)", entry.editedAvg, entry.editedRate);
                    graphics.drawString(font, editText, dialogX + 280, entryY + 5,
                        entry.isNew() ? IsotopeColors.STATUS_SUCCESS : IsotopeColors.TEXT_SECONDARY, false);

                    // Difference
                    if (!entry.isNew() && !entry.isRemoved()) {
                        float avgDiff = entry.avgDiff();
                        String diffText;
                        int diffColor;

                        if (Math.abs(avgDiff) < 0.01f) {
                            diffText = "=";
                            diffColor = IsotopeColors.TEXT_MUTED;
                        } else if (avgDiff > 0) {
                            diffText = String.format("+%.1f", avgDiff);
                            diffColor = IsotopeColors.STATUS_SUCCESS;
                        } else {
                            diffText = String.format("%.1f", avgDiff);
                            diffColor = IsotopeColors.STATUS_ERROR;
                        }

                        graphics.drawString(font, diffText, dialogX + 380, entryY + 5, diffColor, false);

                        // Rate diff as secondary info
                        float rateDiff = entry.rateDiff();
                        if (Math.abs(rateDiff) >= 1f) {
                            String rateText = rateDiff > 0 ? String.format("+%.0f%%", rateDiff) : String.format("%.0f%%", rateDiff);
                            graphics.drawString(font, rateText, dialogX + 430, entryY + 5, diffColor, false);
                        }
                    } else if (entry.isNew()) {
                        graphics.drawString(font, "NEW", dialogX + 380, entryY + 5, IsotopeColors.STATUS_SUCCESS, false);
                    } else {
                        graphics.drawString(font, "GONE", dialogX + 380, entryY + 5, IsotopeColors.STATUS_ERROR, false);
                    }
                }

                entryY += 22;
            }
        }

        graphics.disableScissor();

        // Scrollbar
        ScreenUtils.renderScrollbar(graphics, dialogX + DIALOG_WIDTH - 8, contentY, 4, contentHeight,
            scrollOffset, maxScroll, IsotopeColors.BORDER_DEFAULT, IsotopeColors.STAR_MUTED);

        // Legend
        int legendY = dialogY + DIALOG_HEIGHT - 55;
        graphics.drawString(font, "Legend:", dialogX + 12, legendY, IsotopeColors.TEXT_MUTED, false);
        graphics.fill(dialogX + 60, legendY - 1, dialogX + 72, legendY + 9, 0x40005500);
        graphics.drawString(font, "New", dialogX + 75, legendY, IsotopeColors.TEXT_SECONDARY, false);
        graphics.fill(dialogX + 110, legendY - 1, dialogX + 122, legendY + 9, 0x40550000);
        graphics.drawString(font, "Removed", dialogX + 125, legendY, IsotopeColors.TEXT_SECONDARY, false);
        graphics.fill(dialogX + 185, legendY - 1, dialogX + 197, legendY + 9, 0x40555500);
        graphics.drawString(font, "Changed", dialogX + 200, legendY, IsotopeColors.TEXT_SECONDARY, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
