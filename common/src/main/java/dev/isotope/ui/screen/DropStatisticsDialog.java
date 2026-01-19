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
import dev.isotope.compat.RegistryHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog displaying drop statistics from loot table testing.
 */
@Environment(EnvType.CLIENT)
public class DropStatisticsDialog extends Screen {

    private static final int DIALOG_WIDTH = 350;
    private static final int DIALOG_HEIGHT = 300;

    @Nullable
    private final Screen parent;
    private final DropStatistics statistics;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Cached formatted entries for display
    private final List<StatEntry> entries = new ArrayList<>();

    private record StatEntry(
        ResourceLocation itemId,
        String itemName,
        int total,
        float average,
        float dropRate,
        int min,
        int max
    ) {}

    public DropStatisticsDialog(@Nullable Screen parent, DropStatistics statistics) {
        super(Component.literal("Drop Statistics"));
        this.parent = parent;
        this.statistics = statistics;
        buildEntries();
    }

    private void buildEntries() {
        entries.clear();

        // Sort items by total count descending
        List<ResourceLocation> sortedItems = new ArrayList<>(statistics.getDroppedItems());
        sortedItems.sort((a, b) -> Integer.compare(
            statistics.getTotalForItem(b),
            statistics.getTotalForItem(a)
        ));

        for (ResourceLocation itemId : sortedItems) {
            int total = statistics.getTotalForItem(itemId);
            float avg = statistics.getAverageForItem(itemId);
            float rate = statistics.getDropRateForItem(itemId);
            int[] minMax = statistics.getMinMaxForItem(itemId);

            entries.add(new StatEntry(
                itemId,
                ScreenUtils.formatItemName(itemId),
                total,
                avg,
                rate,
                minMax[0],
                minMax[1]
            ));
        }

        // Calculate max scroll
        int contentHeight = entries.size() * 24 + 20;
        int viewHeight = DIALOG_HEIGHT - 140;
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

        // Run Again button (returns to parent to run another test)
        addRenderableWidget(Button.builder(
            Component.literal("Run Again"),
            b -> onClose()
        ).pos(dialogX + 10, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());
    }

    private void copyToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append("Drop Statistics: ").append(statistics.getSourceName()).append("\n");
        sb.append("Condition: ").append(statistics.getTestCondition()).append("\n");
        sb.append("Tests: ").append(statistics.getTestCount()).append(" | Total drops: ").append(statistics.getTotalDrops()).append("\n");
        sb.append("\n");
        sb.append(String.format("%-25s %6s %6s %6s %8s\n", "Item", "Total", "Avg", "Rate", "Range"));
        sb.append("-".repeat(55)).append("\n");

        for (StatEntry entry : entries) {
            String range = entry.min == entry.max
                ? String.valueOf(entry.min)
                : entry.min + "-" + entry.max;
            sb.append(String.format("%-25s %6d %6.1f %5.0f%% %8s\n",
                ScreenUtils.truncate(entry.itemName, 25),
                entry.total,
                entry.average,
                entry.dropRate,
                range));
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        IsotopeToast.success("Copied", "Statistics copied to clipboard");
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
        graphics.drawString(font, "Drop Statistics", dialogX + 12, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Source info
        graphics.drawString(font, statistics.getSourceName(), dialogX + 12, dialogY + 22,
            IsotopeColors.TEXT_PRIMARY, false);
        graphics.drawString(font, statistics.getTestCondition(), dialogX + 12, dialogY + 34,
            IsotopeColors.SOURCE_MOB, false);

        // Summary stats on right
        String testInfo = statistics.getTestCount() + " tests";
        String dropInfo = statistics.getTotalDrops() + " total drops";
        int rightX = dialogX + DIALOG_WIDTH - 10;
        graphics.drawString(font, testInfo, rightX - font.width(testInfo), dialogY + 22,
            IsotopeColors.TEXT_SECONDARY, false);
        graphics.drawString(font, dropInfo, rightX - font.width(dropInfo), dialogY + 34,
            IsotopeColors.TEXT_SECONDARY, false);

        // Column headers
        int headerY = dialogY + 55;
        graphics.fill(dialogX, headerY, dialogX + DIALOG_WIDTH, headerY + 18, IsotopeColors.ENTRY_BACKGROUND);
        graphics.drawString(font, "Item", dialogX + 30, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Total", dialogX + 165, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Avg", dialogX + 210, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Rate", dialogX + 250, headerY + 5, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "Range", dialogX + 300, headerY + 5, IsotopeColors.TEXT_MUTED, false);

        // Content area
        int contentY = dialogY + 75;
        int contentHeight = DIALOG_HEIGHT - 140;

        // Scissor for scrolling
        graphics.enableScissor(dialogX, contentY, dialogX + DIALOG_WIDTH, contentY + contentHeight);

        if (entries.isEmpty()) {
            graphics.drawString(font, "No items dropped", dialogX + 12, contentY + 10,
                IsotopeColors.TEXT_MUTED, false);
        } else {
            int entryY = contentY - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                StatEntry entry = entries.get(i);

                if (entryY + 24 > contentY && entryY < contentY + contentHeight) {
                    // Alternating row background
                    if (i % 2 == 0) {
                        graphics.fill(dialogX + 5, entryY, dialogX + DIALOG_WIDTH - 5, entryY + 22, IsotopeColors.BACKGROUND_DARKER);
                    }

                    // Item icon
                    var itemOpt = RegistryHelper.getItem(entry.itemId);
                    if (itemOpt.isPresent()) {
                        ItemStack stack = new ItemStack(itemOpt.get());
                        graphics.renderItem(stack, dialogX + 10, entryY + 3);
                    }

                    // Item name (truncate if too long)
                    String name = entry.itemName;
                    if (font.width(name) > 115) {
                        name = font.plainSubstrByWidth(name, 110) + "...";
                    }
                    graphics.drawString(font, name, dialogX + 30, entryY + 6, IsotopeColors.TEXT_PRIMARY, false);

                    // Total
                    graphics.drawString(font, String.valueOf(entry.total), dialogX + 165, entryY + 6,
                        IsotopeColors.ACCENT_GOLD, false);

                    // Average
                    graphics.drawString(font, String.format("%.1f", entry.average), dialogX + 210, entryY + 6,
                        IsotopeColors.TEXT_SECONDARY, false);

                    // Drop rate (color coded)
                    int rateColor = entry.dropRate >= 75 ? IsotopeColors.STATUS_SUCCESS :
                                   entry.dropRate >= 25 ? IsotopeColors.STATUS_WARNING :
                                   IsotopeColors.STATUS_ERROR;
                    graphics.drawString(font, String.format("%.0f%%", entry.dropRate), dialogX + 250, entryY + 6,
                        rateColor, false);

                    // Range
                    String range = entry.min == entry.max
                        ? String.valueOf(entry.min)
                        : entry.min + "-" + entry.max;
                    graphics.drawString(font, range, dialogX + 300, entryY + 6,
                        IsotopeColors.TEXT_MUTED, false);
                }

                entryY += 24;
            }
        }

        graphics.disableScissor();

        // Scrollbar
        ScreenUtils.renderScrollbar(graphics, dialogX + DIALOG_WIDTH - 8, contentY, 4, contentHeight,
            scrollOffset, maxScroll, IsotopeColors.BORDER_DEFAULT, IsotopeColors.STAR_MUTED);

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
