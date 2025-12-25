package dev.isotope.ui.widget;

import dev.isotope.Isotope;
import dev.isotope.data.StructureLootLink;
import dev.isotope.data.loot.*;
import dev.isotope.editing.ClipboardManager;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.editing.LootTableParser;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.screen.BatchWeightScreen;
import dev.isotope.ui.screen.TemplatePickerScreen;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.data.EntryTemplate;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import dev.isotope.ui.screen.ItemPickerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Right panel for inline loot table editing.
 *
 * Shows:
 * - Table header with name and linked structures
 * - List of entries with inline weight/qty editing
 * - Add item button
 * - Pool controls
 */
@Environment(EnvType.CLIENT)
public class LootTableEditPanel extends AbstractWidget {

    private static final int HEADER_HEIGHT = 50;
    private static final int ENTRY_HEIGHT = 24;
    private static final int POOL_HEADER_HEIGHT = 28;
    private static final int PADDING = 8;

    @Nullable
    private ResourceLocation tableId;
    @Nullable
    private LootTableStructure structure;
    @Nullable
    private LootTableStructure editedStructure;

    // Scroll state
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Inline edit fields (created dynamically)
    private final List<EntryEditRow> entryRows = new ArrayList<>();

    // Hover state for remove buttons
    private int hoveredRemovePool = -1;
    private int hoveredRemoveEntry = -1;
    private int hoveredRemoveEntryPool = -1;

    // Selection state for keyboard shortcuts
    private int selectedPoolIdx = -1;
    private int selectedEntryIdx = -1;

    // Multi-selection for batch operations
    private final Set<EntryKey> multiSelection = new LinkedHashSet<>();
    private EntryKey lastClickedEntry = null; // For shift-click range selection

    // Batch action hover state
    private boolean batchWeightHovered = false;
    private boolean batchDeleteHovered = false;

    /**
     * Key for identifying a specific entry (pool index, entry index).
     */
    public record EntryKey(int poolIdx, int entryIdx) implements Comparable<EntryKey> {
        @Override
        public int compareTo(EntryKey other) {
            int cmp = Integer.compare(this.poolIdx, other.poolIdx);
            return cmp != 0 ? cmp : Integer.compare(this.entryIdx, other.entryIdx);
        }
    }

    public LootTableEditPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public void setLootTable(@Nullable ResourceLocation tableId) {
        this.tableId = tableId;
        this.structure = null;
        this.editedStructure = null;
        this.scrollOffset = 0;
        this.selectedPoolIdx = -1;
        this.selectedEntryIdx = -1;
        this.multiSelection.clear();
        this.lastClickedEntry = null;
        entryRows.clear();

        if (tableId == null) return;

        // Get from cache (pre-parsed during registry scan)
        Optional<LootTableStructure> cached = LootEditManager.getInstance().getCachedOriginalStructure(tableId);
        if (cached.isPresent()) {
            structure = cached.get();
        } else {
            // Fallback: try to parse if server is available
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                Optional<LootTableStructure> parsed = LootTableParser.parse(server, tableId);
                if (parsed.isPresent()) {
                    structure = parsed.get();
                    LootEditManager.getInstance().cacheOriginalStructure(structure);
                }
            }
        }

        // Get edited version if exists
        if (LootEditManager.getInstance().hasEdits(tableId)) {
            editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(null);
        } else {
            editedStructure = structure;
        }

        rebuildEntryRows();
        calculateMaxScroll();
    }

    private void rebuildEntryRows() {
        entryRows.clear();

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return;

        for (int poolIdx = 0; poolIdx < display.pools().size(); poolIdx++) {
            LootPool pool = display.pools().get(poolIdx);
            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);
                entryRows.add(new EntryEditRow(poolIdx, entryIdx, entry));
            }
        }
    }

    private void calculateMaxScroll() {
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) {
            maxScroll = 0;
            return;
        }

        int contentHeight = HEADER_HEIGHT;
        for (int i = 0; i < display.pools().size(); i++) {
            contentHeight += POOL_HEADER_HEIGHT;
            contentHeight += display.pools().get(i).entries().size() * ENTRY_HEIGHT;
            contentHeight += 24; // Add item button
        }
        contentHeight += 30; // Add pool button

        maxScroll = Math.max(0, contentHeight - height);
    }

    private void refreshFromEdits() {
        if (tableId == null) return;
        editedStructure = LootEditManager.getInstance().getEditedStructure(tableId).orElse(structure);
        rebuildEntryRows();
        calculateMaxScroll();
    }

    /**
     * Public method to refresh the panel from external changes (e.g., undo/redo).
     */
    public void refresh() {
        refreshFromEdits();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1a1a1a);

        if (tableId == null) {
            // Empty state with helpful messaging
            int centerX = getX() + width / 2;
            int centerY = getY() + height / 2;

            String title = "No Loot Table Selected";
            graphics.drawString(font, title, centerX - font.width(title) / 2, centerY - 30,
                IsotopeColors.TEXT_PRIMARY, false);

            String hint1 = "Select a loot table from the browser";
            graphics.drawString(font, hint1, centerX - font.width(hint1) / 2, centerY - 10,
                IsotopeColors.TEXT_MUTED, false);

            String hint2 = "or use Ctrl+Shift+F to search";
            graphics.drawString(font, hint2, centerX - font.width(hint2) / 2, centerY + 4,
                IsotopeColors.TEXT_MUTED, false);

            String hint3 = "Press F1 for keyboard shortcuts";
            graphics.drawString(font, hint3, centerX - font.width(hint3) / 2, centerY + 28,
                IsotopeColors.TEXT_MUTED, false);
            return;
        }

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) {
            graphics.drawString(font, "Failed to load loot table", getX() + 10, getY() + 10,
                IsotopeColors.STATUS_ERROR, false);
            return;
        }

        // Enable scissor for scrolling
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        int y = getY() - scrollOffset;

        // Header
        renderHeader(graphics, font, y, mouseX, mouseY);
        y += HEADER_HEIGHT;

        // Batch action bar (shown when multiple entries selected)
        if (multiSelection.size() > 1) {
            y = renderBatchActionBar(graphics, font, y, mouseX, mouseY);
        }

        // Pools and entries
        hoveredRemovePool = -1;
        hoveredRemoveEntry = -1;
        hoveredRemoveEntryPool = -1;

        for (int poolIdx = 0; poolIdx < display.pools().size(); poolIdx++) {
            LootPool pool = display.pools().get(poolIdx);
            y = renderPool(graphics, font, y, poolIdx, pool, mouseX, mouseY);
        }

        // Add pool button
        if (y > getY() && y < getY() + height) {
            int btnX = getX() + PADDING;
            int btnWidth = width - PADDING * 2;
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth &&
                mouseY >= y && mouseY < y + 22;

            graphics.fill(btnX, y + 2, btnX + btnWidth, y + 22, hovered ? 0xFF3a3a3a : 0xFF2a2a2a);
            graphics.renderOutline(btnX, y + 2, btnWidth, 20, 0xFF404040);

            String addPoolText = "+ Add Pool";
            int textX = btnX + (btnWidth - font.width(addPoolText)) / 2;
            graphics.drawString(font, addPoolText, textX, y + 7, IsotopeColors.TEXT_SECONDARY, false);
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = getX() + width - 5;
            int scrollbarHeight = height;
            int thumbHeight = Math.max(20, (int) ((float) height / (height + maxScroll) * scrollbarHeight));
            int thumbY = getY() + (int) ((float) scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

            graphics.fill(scrollbarX, getY(), scrollbarX + 4, getY() + height, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF555555);
        }
    }

    private void renderHeader(GuiGraphics graphics, Font font, int y, int mouseX, int mouseY) {
        // Table ID
        String tablePath = tableId.getPath();
        graphics.drawString(font, tablePath, getX() + PADDING, y + 6, IsotopeColors.TEXT_PRIMARY, false);

        // Namespace badge
        String ns = tableId.getNamespace();
        int nsWidth = font.width(ns) + 6;
        int nsX = getX() + width - PADDING - nsWidth;
        graphics.fill(nsX, y + 4, nsX + nsWidth, y + 16, 0xFF3a3a3a);
        graphics.drawString(font, ns, nsX + 3, y + 6, IsotopeColors.TEXT_MUTED, false);

        // Linked structures
        List<StructureLootLink> links = StructureLootLinker.getInstance().getLinksForLootTable(tableId);
        if (!links.isEmpty()) {
            StringBuilder sb = new StringBuilder("Structures: ");
            for (int i = 0; i < Math.min(links.size(), 3); i++) {
                if (i > 0) sb.append(", ");
                sb.append(links.get(i).structureId().getPath());
            }
            if (links.size() > 3) {
                sb.append(" +").append(links.size() - 3).append(" more");
            }
            graphics.drawString(font, sb.toString(), getX() + PADDING, y + 20,
                IsotopeColors.TEXT_MUTED, false);
        }

        // Edit indicator
        if (LootEditManager.getInstance().hasEdits(tableId)) {
            graphics.drawString(font, "●", getX() + PADDING + font.width(tablePath) + 4, y + 6,
                IsotopeColors.BADGE_MODIFIED, false);
        }

        // Separator
        graphics.fill(getX() + PADDING, y + HEADER_HEIGHT - 2, getX() + width - PADDING, y + HEADER_HEIGHT - 1,
            0xFF333333);
    }

    private static final int BATCH_BAR_HEIGHT = 28;

    private int renderBatchActionBar(GuiGraphics graphics, Font font, int y, int mouseX, int mouseY) {
        // Background
        graphics.fill(getX(), y, getX() + width, y + BATCH_BAR_HEIGHT, 0xFF2a2a3a);

        // Selection count
        String selectionText = multiSelection.size() + " entries selected";
        graphics.drawString(font, selectionText, getX() + PADDING, y + 9, 0xFF9966ff, false);

        // Buttons on the right
        int btnY = y + 4;
        int btnHeight = 20;

        // Clear selection button (X)
        int clearX = getX() + width - PADDING - 20;
        boolean clearHovered = mouseX >= clearX && mouseX < clearX + 18 &&
            mouseY >= btnY && mouseY < btnY + btnHeight;
        graphics.fill(clearX, btnY, clearX + 18, btnY + btnHeight, clearHovered ? 0xFF4a3a3a : 0xFF3a3a3a);
        graphics.drawString(font, "×", clearX + 5, btnY + 6, clearHovered ? 0xFFff6666 : IsotopeColors.TEXT_MUTED, false);

        // Delete All button
        int deleteX = clearX - 70;
        batchDeleteHovered = mouseX >= deleteX && mouseX < deleteX + 65 &&
            mouseY >= btnY && mouseY < btnY + btnHeight;
        graphics.fill(deleteX, btnY, deleteX + 65, btnY + btnHeight,
            batchDeleteHovered ? 0xFF5a2a2a : 0xFF3a3a3a);
        graphics.renderOutline(deleteX, btnY, 65, btnHeight, 0xFF505050);
        graphics.drawString(font, "Delete All", deleteX + 6, btnY + 6,
            batchDeleteHovered ? 0xFFff6666 : IsotopeColors.TEXT_PRIMARY, false);

        // Set Weight button
        int weightX = deleteX - 75;
        batchWeightHovered = mouseX >= weightX && mouseX < weightX + 70 &&
            mouseY >= btnY && mouseY < btnY + btnHeight;
        graphics.fill(weightX, btnY, weightX + 70, btnY + btnHeight,
            batchWeightHovered ? 0xFF3a4a5a : 0xFF3a3a3a);
        graphics.renderOutline(weightX, btnY, 70, btnHeight, 0xFF505050);
        graphics.drawString(font, "Set Weight", weightX + 6, btnY + 6,
            batchWeightHovered ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY, false);

        return y + BATCH_BAR_HEIGHT;
    }

    private int renderPool(GuiGraphics graphics, Font font, int y, int poolIdx, LootPool pool,
                           int mouseX, int mouseY) {

        // Pool header
        if (y + POOL_HEADER_HEIGHT > getY() && y < getY() + height) {
            graphics.fill(getX(), y, getX() + width, y + POOL_HEADER_HEIGHT, 0xFF252525);

            // Pool label
            String poolLabel = "Pool " + (poolIdx + 1);
            graphics.drawString(font, poolLabel, getX() + PADDING, y + 6, IsotopeColors.ACCENT_GOLD, false);

            // Rolls
            String rollsText = "Rolls: " + formatNumberProvider(pool.rolls());
            graphics.drawString(font, rollsText, getX() + PADDING + 60, y + 6, IsotopeColors.TEXT_SECONDARY, false);

            // Remove pool button (X)
            int removeX = getX() + width - PADDING - 16;
            boolean removeHovered = mouseX >= removeX && mouseX < removeX + 14 &&
                mouseY >= y + 4 && mouseY < y + 18;
            if (removeHovered) {
                hoveredRemovePool = poolIdx;
                graphics.fill(removeX, y + 4, removeX + 14, y + 18, 0xFF4a2a2a);
            }
            graphics.drawString(font, "×", removeX + 4, y + 5, removeHovered ? 0xFFff6666 : IsotopeColors.TEXT_MUTED, false);
        }
        y += POOL_HEADER_HEIGHT;

        // Entries
        for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
            LootEntry entry = pool.entries().get(entryIdx);
            if (y + ENTRY_HEIGHT > getY() && y < getY() + height) {
                renderEntry(graphics, font, y, poolIdx, entryIdx, entry, mouseX, mouseY);
            }
            y += ENTRY_HEIGHT;
        }

        // Add item and Template buttons
        if (y > getY() && y < getY() + height) {
            int totalWidth = width - PADDING * 2 - 20;
            int btnSpacing = 4;
            int addBtnWidth = (totalWidth - btnSpacing) / 2;
            int templateBtnWidth = totalWidth - addBtnWidth - btnSpacing;

            int addBtnX = getX() + PADDING + 20;
            int templateBtnX = addBtnX + addBtnWidth + btnSpacing;

            // Add Item button
            boolean addHovered = mouseX >= addBtnX && mouseX < addBtnX + addBtnWidth &&
                mouseY >= y && mouseY < y + 20;
            graphics.fill(addBtnX, y + 2, addBtnX + addBtnWidth, y + 20, addHovered ? 0xFF353535 : 0xFF2a2a2a);
            String addText = "+ Add Item";
            int addTextX = addBtnX + (addBtnWidth - font.width(addText)) / 2;
            graphics.drawString(font, addText, addTextX, y + 6, IsotopeColors.TEXT_MUTED, false);

            // Template button
            boolean templateHovered = mouseX >= templateBtnX && mouseX < templateBtnX + templateBtnWidth &&
                mouseY >= y && mouseY < y + 20;
            graphics.fill(templateBtnX, y + 2, templateBtnX + templateBtnWidth, y + 20,
                templateHovered ? 0xFF3a4a3a : 0xFF2a2a2a);
            String templateText = "☆ Template";
            int templateTextX = templateBtnX + (templateBtnWidth - font.width(templateText)) / 2;
            graphics.drawString(font, templateText, templateTextX, y + 6,
                templateHovered ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_MUTED, false);
        }
        y += 24;

        return y;
    }

    private void renderEntry(GuiGraphics graphics, Font font, int y, int poolIdx, int entryIdx,
                             LootEntry entry, int mouseX, int mouseY) {

        boolean rowHovered = mouseX >= getX() && mouseX < getX() + width &&
            mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        boolean isSelected = selectedPoolIdx == poolIdx && selectedEntryIdx == entryIdx;
        boolean isMultiSelected = multiSelection.contains(new EntryKey(poolIdx, entryIdx));

        if (isMultiSelected) {
            // Multi-selected entries (purple tint)
            graphics.fill(getX() + PADDING, y, getX() + width - PADDING, y + ENTRY_HEIGHT, 0xFF3a2a4a);
            graphics.renderOutline(getX() + PADDING, y, width - PADDING * 2, ENTRY_HEIGHT, 0xFF9966ff);
        } else if (isSelected) {
            // Single selected highlight (stronger blue tint)
            graphics.fill(getX() + PADDING, y, getX() + width - PADDING, y + ENTRY_HEIGHT, 0xFF2a3a4a);
            graphics.renderOutline(getX() + PADDING, y, width - PADDING * 2, ENTRY_HEIGHT, IsotopeColors.ACCENT_GOLD);
        } else if (rowHovered) {
            graphics.fill(getX() + PADDING, y, getX() + width - PADDING, y + ENTRY_HEIGHT, 0xFF2a2a2a);
        }

        int x = getX() + PADDING + 10;

        // Item icon
        if (entry.name().isPresent()) {
            ResourceLocation itemId = entry.name().get();
            var itemOpt = BuiltInRegistries.ITEM.get(itemId);
            if (itemOpt.isPresent()) {
                ItemStack stack = new ItemStack(itemOpt.get().value());
                graphics.renderItem(stack, x, y + 4);
            }
        }
        x += 20;

        // Item name
        String itemName = entry.name().map(ResourceLocation::getPath).orElse(entry.type());
        if (font.width(itemName) > 100) {
            itemName = font.plainSubstrByWidth(itemName, 95) + "...";
        }
        graphics.drawString(font, itemName, x, y + 8, IsotopeColors.TEXT_PRIMARY, false);
        x += 105;

        // Weight
        graphics.drawString(font, "W:", x, y + 8, IsotopeColors.TEXT_MUTED, false);
        x += 14;

        // Weight edit box
        int weightBoxWidth = 30;
        boolean weightHovered = mouseX >= x && mouseX < x + weightBoxWidth &&
            mouseY >= y + 3 && mouseY < y + 19;
        graphics.fill(x, y + 3, x + weightBoxWidth, y + 19, weightHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(x, y + 3, weightBoxWidth, 16, 0xFF505050);

        String weightStr = String.valueOf(entry.weight());
        int weightTextX = x + (weightBoxWidth - font.width(weightStr)) / 2;
        graphics.drawString(font, weightStr, weightTextX, y + 7, IsotopeColors.TEXT_PRIMARY, false);
        x += weightBoxWidth + 8;

        // Quantity (from set_count function)
        NumberProvider countProvider = getCountFromEntry(entry);
        graphics.drawString(font, "Qty:", x, y + 8, IsotopeColors.TEXT_MUTED, false);
        x += 22;

        // Min count
        int minCount = (int) countProvider.getMin();
        int countBoxWidth = 24;
        boolean minHovered = mouseX >= x && mouseX < x + countBoxWidth &&
            mouseY >= y + 3 && mouseY < y + 19;
        graphics.fill(x, y + 3, x + countBoxWidth, y + 19, minHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(x, y + 3, countBoxWidth, 16, 0xFF505050);
        String minStr = String.valueOf(minCount);
        graphics.drawString(font, minStr, x + (countBoxWidth - font.width(minStr)) / 2, y + 7,
            IsotopeColors.TEXT_PRIMARY, false);
        x += countBoxWidth + 2;

        graphics.drawString(font, "-", x, y + 7, IsotopeColors.TEXT_MUTED, false);
        x += 8;

        // Max count
        int maxCount = (int) countProvider.getMax();
        boolean maxHovered = mouseX >= x && mouseX < x + countBoxWidth &&
            mouseY >= y + 3 && mouseY < y + 19;
        graphics.fill(x, y + 3, x + countBoxWidth, y + 19, maxHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(x, y + 3, countBoxWidth, 16, 0xFF505050);
        String maxStr = String.valueOf(maxCount);
        graphics.drawString(font, maxStr, x + (countBoxWidth - font.width(maxStr)) / 2, y + 7,
            IsotopeColors.TEXT_PRIMARY, false);
        x += countBoxWidth + 10;

        // Remove button
        int removeX = getX() + width - PADDING - 20;
        boolean removeHovered = mouseX >= removeX && mouseX < removeX + 16 &&
            mouseY >= y + 4 && mouseY < y + 20;
        if (removeHovered) {
            hoveredRemoveEntry = entryIdx;
            hoveredRemoveEntryPool = poolIdx;
            graphics.fill(removeX, y + 4, removeX + 16, y + 20, 0xFF4a2a2a);
        }
        graphics.drawString(font, "×", removeX + 5, y + 7, removeHovered ? 0xFFff6666 : IsotopeColors.TEXT_MUTED, false);
    }

    private NumberProvider getCountFromEntry(LootEntry entry) {
        // Look for set_count function
        for (LootFunction func : entry.functions()) {
            if (func.function().equals("minecraft:set_count") || func.function().equals("set_count")) {
                if (func.parameters().has("count")) {
                    return parseNumberProvider(func.parameters().get("count"));
                }
            }
        }
        return new NumberProvider.Constant(1);
    }

    private NumberProvider parseNumberProvider(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new NumberProvider.Constant(element.getAsFloat());
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                return new NumberProvider.Uniform(
                    obj.get("min").getAsFloat(),
                    obj.get("max").getAsFloat()
                );
            }
        }
        return new NumberProvider.Constant(1);
    }

    private String formatNumberProvider(NumberProvider provider) {
        return switch (provider) {
            case NumberProvider.Constant c -> String.valueOf((int) c.value());
            case NumberProvider.Uniform u -> (int) u.min() + "-" + (int) u.max();
            case NumberProvider.Binomial b -> "B(" + b.n() + "," + b.p() + ")";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (tableId == null) return false;

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return false;

        // Check for batch action bar clicks when multi-selection is active
        if (multiSelection.size() > 1) {
            int batchBarY = getY() - scrollOffset + HEADER_HEIGHT;
            int btnY = batchBarY + 4;
            int btnHeight = 20;

            // Clear selection button (X)
            int clearX = getX() + width - PADDING - 20;
            if (mouseX >= clearX && mouseX < clearX + 18 && mouseY >= btnY && mouseY < btnY + btnHeight) {
                multiSelection.clear();
                lastClickedEntry = null;
                return true;
            }

            // Delete All button
            int deleteX = clearX - 70;
            if (mouseX >= deleteX && mouseX < deleteX + 65 && mouseY >= btnY && mouseY < btnY + btnHeight) {
                batchDelete();
                return true;
            }

            // Set Weight button
            int weightX = deleteX - 75;
            if (mouseX >= weightX && mouseX < weightX + 70 && mouseY >= btnY && mouseY < btnY + btnHeight) {
                openBatchWeightDialog();
                return true;
            }
        }

        // Check for remove pool click
        if (hoveredRemovePool >= 0) {
            LootEditOperation op = new LootEditOperation.RemovePool(hoveredRemovePool);
            LootEditManager.getInstance().applyOperation(tableId, op);
            multiSelection.clear();
            refreshFromEdits();
            return true;
        }

        // Check for remove entry click
        if (hoveredRemoveEntry >= 0 && hoveredRemoveEntryPool >= 0) {
            LootEditOperation op = new LootEditOperation.RemoveEntry(hoveredRemoveEntryPool, hoveredRemoveEntry);
            LootEditManager.getInstance().applyOperation(tableId, op);
            multiSelection.remove(new EntryKey(hoveredRemoveEntryPool, hoveredRemoveEntry));
            refreshFromEdits();
            return true;
        }

        // Check modifier keys for multi-selection
        boolean ctrlHeld = Screen.hasControlDown();
        boolean shiftHeld = Screen.hasShiftDown();

        // Check for weight/qty box clicks
        int y = getY() - scrollOffset + HEADER_HEIGHT;

        // Adjust y if batch bar is showing
        if (multiSelection.size() > 1) {
            y += BATCH_BAR_HEIGHT;
        }

        for (int poolIdx = 0; poolIdx < display.pools().size(); poolIdx++) {
            LootPool pool = display.pools().get(poolIdx);
            y += POOL_HEADER_HEIGHT;

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                // Check if clicking in entry row
                if (mouseY >= y && mouseY < y + ENTRY_HEIGHT) {
                    // Calculate field positions
                    int itemX = getX() + PADDING + 10;
                    int itemEndX = itemX + 20 + 105; // Icon + name width

                    // Item icon/name area - click to change item
                    if (mouseX >= itemX && mouseX < itemEndX) {
                        selectedPoolIdx = poolIdx;
                        selectedEntryIdx = entryIdx;
                        openChangeItemDialog(poolIdx, entryIdx);
                        return true;
                    }

                    int x = itemEndX + 14; // After "W:"

                    // Weight box
                    if (mouseX >= x && mouseX < x + 30) {
                        selectedPoolIdx = poolIdx;
                        selectedEntryIdx = entryIdx;
                        openWeightEditor(poolIdx, entryIdx, entry.weight());
                        return true;
                    }
                    x += 30 + 8 + 22; // After weight + gap + "Qty:"

                    // Min count box
                    if (mouseX >= x && mouseX < x + 24) {
                        selectedPoolIdx = poolIdx;
                        selectedEntryIdx = entryIdx;
                        NumberProvider count = getCountFromEntry(entry);
                        openCountEditor(poolIdx, entryIdx, (int) count.getMin(), (int) count.getMax(), true);
                        return true;
                    }
                    x += 24 + 10; // After min + dash

                    // Max count box
                    if (mouseX >= x && mouseX < x + 24) {
                        selectedPoolIdx = poolIdx;
                        selectedEntryIdx = entryIdx;
                        NumberProvider count = getCountFromEntry(entry);
                        openCountEditor(poolIdx, entryIdx, (int) count.getMin(), (int) count.getMax(), false);
                        return true;
                    }

                    // Clicking elsewhere in the row handles selection
                    EntryKey clickedKey = new EntryKey(poolIdx, entryIdx);

                    if (ctrlHeld) {
                        // Ctrl+Click: Toggle selection
                        if (multiSelection.contains(clickedKey)) {
                            multiSelection.remove(clickedKey);
                        } else {
                            multiSelection.add(clickedKey);
                        }
                        lastClickedEntry = clickedKey;
                    } else if (shiftHeld && lastClickedEntry != null) {
                        // Shift+Click: Range selection
                        selectRange(lastClickedEntry, clickedKey, display);
                    } else {
                        // Normal click: Clear multi-selection, set single selection
                        multiSelection.clear();
                        selectedPoolIdx = poolIdx;
                        selectedEntryIdx = entryIdx;
                        lastClickedEntry = clickedKey;
                    }
                    return true;
                }

                y += ENTRY_HEIGHT;
            }

            // Add item and Template buttons
            int totalWidth = width - PADDING * 2 - 20;
            int btnSpacing = 4;
            int addBtnWidth = (totalWidth - btnSpacing) / 2;
            int templateBtnWidth = totalWidth - addBtnWidth - btnSpacing;
            int addBtnX = getX() + PADDING + 20;
            int templateBtnX = addBtnX + addBtnWidth + btnSpacing;

            if (mouseY >= y + 2 && mouseY < y + 20) {
                if (mouseX >= addBtnX && mouseX < addBtnX + addBtnWidth) {
                    selectedPoolIdx = poolIdx;
                    selectedEntryIdx = -1;
                    openAddItemDialog(poolIdx);
                    return true;
                }
                if (mouseX >= templateBtnX && mouseX < templateBtnX + templateBtnWidth) {
                    selectedPoolIdx = poolIdx;
                    selectedEntryIdx = -1;
                    openTemplateDialog(poolIdx);
                    return true;
                }
            }
            y += 24;
        }

        // Add pool button
        int btnX = getX() + PADDING;
        int btnWidth = width - PADDING * 2;
        if (mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= y + 2 && mouseY < y + 22) {
            addNewPool();
            return true;
        }

        return false;
    }

    private void openWeightEditor(int poolIdx, int entryIdx, int currentWeight) {
        // For now, simple increment/decrement via keyboard
        // TODO: Open a small popup or use inline editing
        Isotope.LOGGER.info("Edit weight for pool {} entry {} (current: {})", poolIdx, entryIdx, currentWeight);

        // Simple: cycle through common weights
        int newWeight = currentWeight + 5;
        if (newWeight > 50) newWeight = 1;

        LootEditOperation op = new LootEditOperation.ModifyEntryWeight(poolIdx, entryIdx, newWeight);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();
    }

    private void openCountEditor(int poolIdx, int entryIdx, int min, int max, boolean editMin) {
        Isotope.LOGGER.info("Edit count for pool {} entry {} ({}-{})", poolIdx, entryIdx, min, max);

        // Simple: increment
        if (editMin) {
            min = Math.min(min + 1, max);
        } else {
            max = max + 1;
        }

        NumberProvider newCount = min == max
            ? new NumberProvider.Constant(min)
            : new NumberProvider.Uniform(min, max);

        LootEditOperation op = new LootEditOperation.SetItemCount(poolIdx, entryIdx, newCount);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();
    }

    private void openAddItemDialog(int poolIdx) {
        Isotope.LOGGER.info("Opening item picker for pool {}", poolIdx);

        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new ItemPickerScreen(currentScreen, itemId -> {
            // Create new entry with selected item
            LootEntry newEntry = new LootEntry(
                "minecraft:item",
                Optional.of(itemId),
                10, 0,
                List.of(), List.of(), List.of()
            );

            LootEditOperation op = new LootEditOperation.AddEntry(poolIdx, -1, newEntry);
            LootEditManager.getInstance().applyOperation(tableId, op);
            refreshFromEdits();
            Isotope.LOGGER.info("Added {} to pool {}", itemId, poolIdx);
        }));
    }

    private void openTemplateDialog(int poolIdx) {
        Isotope.LOGGER.info("Opening template picker for pool {}", poolIdx);

        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new TemplatePickerScreen(currentScreen, template -> {
            // If template has a default item, use it directly
            if (template.defaultItem().isPresent()) {
                LootEntry newEntry = template.createEntry();
                LootEditOperation op = new LootEditOperation.AddEntry(poolIdx, -1, newEntry);
                LootEditManager.getInstance().applyOperation(tableId, op);
                refreshFromEdits();
                IsotopeToast.success("Added", template.name() + " (" + template.defaultItem().get().getPath() + ")");
            } else {
                // No default item - open item picker
                Minecraft.getInstance().setScreen(new ItemPickerScreen(currentScreen, itemId -> {
                    LootEntry newEntry = template.createEntry(itemId);
                    LootEditOperation op = new LootEditOperation.AddEntry(poolIdx, -1, newEntry);
                    LootEditManager.getInstance().applyOperation(tableId, op);
                    refreshFromEdits();
                    IsotopeToast.success("Added", template.name() + " (" + itemId.getPath() + ")");
                }));
            }
        }));
    }

    private void openChangeItemDialog(int poolIdx, int entryIdx) {
        Isotope.LOGGER.info("Opening item picker to change pool {} entry {}", poolIdx, entryIdx);

        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new ItemPickerScreen(currentScreen, itemId -> {
            LootEditOperation op = new LootEditOperation.ModifyEntryItem(poolIdx, entryIdx, itemId);
            LootEditManager.getInstance().applyOperation(tableId, op);
            refreshFromEdits();
            Isotope.LOGGER.info("Changed pool {} entry {} to {}", poolIdx, entryIdx, itemId);
        }));
    }

    private void addNewPool() {
        Isotope.LOGGER.info("Add new pool");

        LootPool newPool = new LootPool(
            "",
            new NumberProvider.Constant(1),
            new NumberProvider.Constant(0),
            new ArrayList<>(),
            List.of(),
            List.of()
        );

        LootEditOperation op = new LootEditOperation.AddPool(-1, newPool);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();
    }

    // ===== Multi-Selection & Batch Operations =====

    /**
     * Select a range of entries between two keys (for Shift+Click).
     */
    private void selectRange(EntryKey from, EntryKey to, LootTableStructure display) {
        multiSelection.clear();

        // Build a flat list of all entry keys
        List<EntryKey> allEntries = new ArrayList<>();
        for (int poolIdx = 0; poolIdx < display.pools().size(); poolIdx++) {
            LootPool pool = display.pools().get(poolIdx);
            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                allEntries.add(new EntryKey(poolIdx, entryIdx));
            }
        }

        // Find indices
        int fromIndex = allEntries.indexOf(from);
        int toIndex = allEntries.indexOf(to);

        if (fromIndex < 0 || toIndex < 0) return;

        // Ensure correct order
        int start = Math.min(fromIndex, toIndex);
        int end = Math.max(fromIndex, toIndex);

        // Select the range
        for (int i = start; i <= end; i++) {
            multiSelection.add(allEntries.get(i));
        }
    }

    /**
     * Delete all multi-selected entries.
     * Entries are deleted in reverse order to preserve indices.
     */
    private void batchDelete() {
        if (tableId == null || multiSelection.isEmpty()) return;

        // Sort by pool then entry (descending) to delete from end first
        List<EntryKey> sorted = multiSelection.stream()
            .sorted(Comparator.reverseOrder())
            .toList();

        // Build operations
        List<LootEditOperation> ops = sorted.stream()
            .map(key -> new LootEditOperation.RemoveEntry(key.poolIdx(), key.entryIdx()))
            .collect(Collectors.toList());

        // Apply as batch
        int count = LootEditManager.getInstance().applyOperations(tableId, ops);

        IsotopeToast.success("Deleted", count + " entries removed");

        multiSelection.clear();
        lastClickedEntry = null;
        selectedPoolIdx = -1;
        selectedEntryIdx = -1;
        refreshFromEdits();
    }

    /**
     * Open a dialog to set weight for all multi-selected entries.
     */
    private void openBatchWeightDialog() {
        if (tableId == null || multiSelection.isEmpty()) return;

        // For now, use a simple cycling approach (similar to single weight edit)
        // In a full implementation, this would open a proper input dialog
        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new BatchWeightScreen(currentScreen, newWeight -> {
            List<LootEditOperation> ops = multiSelection.stream()
                .map(key -> new LootEditOperation.ModifyEntryWeight(key.poolIdx(), key.entryIdx(), newWeight))
                .collect(Collectors.toList());

            int count = LootEditManager.getInstance().applyOperations(tableId, ops);
            IsotopeToast.success("Updated", count + " entries set to weight " + newWeight);

            multiSelection.clear();
            lastClickedEntry = null;
            refreshFromEdits();
        }));
    }

    /**
     * Get the current multi-selection count.
     */
    public int getMultiSelectionCount() {
        return multiSelection.size();
    }

    /**
     * Check if an entry is multi-selected.
     */
    public boolean isMultiSelected(int poolIdx, int entryIdx) {
        return multiSelection.contains(new EntryKey(poolIdx, entryIdx));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return false;
    }

    // ===== Keyboard Shortcut Methods =====

    /**
     * Add a new item to the currently selected pool (Ctrl+N).
     */
    public void addItemToCurrentPool() {
        if (tableId == null) return;

        // If no pool selected, use pool 0 if it exists
        int poolIdx = selectedPoolIdx >= 0 ? selectedPoolIdx : 0;

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || display.pools().isEmpty()) return;

        if (poolIdx >= display.pools().size()) {
            poolIdx = 0;
        }

        openAddItemDialog(poolIdx);
    }

    /**
     * Delete the currently selected entry (Delete key).
     * If multiple entries are selected, performs batch delete.
     */
    public void deleteSelected() {
        if (tableId == null) return;

        // If multi-selection, batch delete
        if (multiSelection.size() > 1) {
            batchDelete();
            return;
        }

        // Single deletion
        if (selectedPoolIdx < 0 || selectedEntryIdx < 0) return;

        LootEditOperation op = new LootEditOperation.RemoveEntry(selectedPoolIdx, selectedEntryIdx);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();

        // Clear selection after delete
        selectedEntryIdx = -1;
    }

    /**
     * Duplicate the currently selected entry (Ctrl+D).
     */
    public void duplicateSelected() {
        if (tableId == null || selectedPoolIdx < 0 || selectedEntryIdx < 0) return;

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return;

        if (selectedPoolIdx >= display.pools().size()) return;
        LootPool pool = display.pools().get(selectedPoolIdx);
        if (selectedEntryIdx >= pool.entries().size()) return;

        LootEntry original = pool.entries().get(selectedEntryIdx);

        // Create a copy with same properties
        LootEntry copy = new LootEntry(
            original.type(),
            original.name(),
            original.weight(),
            original.quality(),
            new ArrayList<>(original.conditions()),
            new ArrayList<>(original.functions()),
            new ArrayList<>(original.children())
        );

        // Insert after the current entry
        LootEditOperation op = new LootEditOperation.AddEntry(selectedPoolIdx, selectedEntryIdx + 1, copy);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();

        // Select the new duplicate
        selectedEntryIdx = selectedEntryIdx + 1;
    }

    /**
     * Clear the current selection (Escape key).
     */
    public void clearSelection() {
        selectedPoolIdx = -1;
        selectedEntryIdx = -1;
        multiSelection.clear();
        lastClickedEntry = null;
    }

    /**
     * Copy the currently selected entry to clipboard (Ctrl+C).
     */
    public void copySelected() {
        if (tableId == null || selectedPoolIdx < 0 || selectedEntryIdx < 0) {
            IsotopeToast.info("Copy", "No entry selected");
            return;
        }

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return;

        if (selectedPoolIdx >= display.pools().size()) return;
        LootPool pool = display.pools().get(selectedPoolIdx);
        if (selectedEntryIdx >= pool.entries().size()) return;

        LootEntry entry = pool.entries().get(selectedEntryIdx);
        ClipboardManager.getInstance().copyEntry(entry, tableId);

        String itemName = entry.name().map(ResourceLocation::getPath).orElse(entry.type());
        IsotopeToast.info("Copied", itemName);
    }

    /**
     * Paste from clipboard into the current pool (Ctrl+V).
     */
    public void pasteFromClipboard() {
        if (tableId == null) {
            IsotopeToast.info("Paste", "No loot table selected");
            return;
        }

        ClipboardManager clipboard = ClipboardManager.getInstance();

        if (clipboard.hasEntry()) {
            pasteEntry();
        } else if (clipboard.hasPool()) {
            pastePool();
        } else {
            IsotopeToast.info("Paste", "Clipboard is empty");
        }
    }

    private void pasteEntry() {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        LootEntry entry = clipboard.getEntry().orElse(null);
        if (entry == null) return;

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || display.pools().isEmpty()) {
            IsotopeToast.error("Paste", "No pools to paste into");
            return;
        }

        // Paste into selected pool, or pool 0 if none selected
        int targetPool = selectedPoolIdx >= 0 ? selectedPoolIdx : 0;
        if (targetPool >= display.pools().size()) {
            targetPool = 0;
        }

        // Paste after selected entry, or at end of pool
        int targetIndex = selectedEntryIdx >= 0 ? selectedEntryIdx + 1 : -1;

        // Create a fresh copy
        LootEntry copy = new LootEntry(
            entry.type(),
            entry.name(),
            entry.weight(),
            entry.quality(),
            new ArrayList<>(entry.conditions()),
            new ArrayList<>(entry.functions()),
            new ArrayList<>(entry.children())
        );

        LootEditOperation op = new LootEditOperation.AddEntry(targetPool, targetIndex, copy);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();

        String itemName = entry.name().map(ResourceLocation::getPath).orElse(entry.type());
        IsotopeToast.success("Pasted", itemName);
    }

    private void pastePool() {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        LootPool pool = clipboard.getPool().orElse(null);
        if (pool == null) return;

        // Create a fresh copy
        LootPool copy = new LootPool(
            pool.name(),
            pool.rolls(),
            pool.bonusRolls(),
            new ArrayList<>(pool.entries()),
            new ArrayList<>(pool.conditions()),
            new ArrayList<>(pool.functions())
        );

        LootEditOperation op = new LootEditOperation.AddPool(-1, copy);
        LootEditManager.getInstance().applyOperation(tableId, op);
        refreshFromEdits();

        IsotopeToast.success("Pasted", "Pool with " + pool.entries().size() + " entries");
    }

    /**
     * Get the currently selected pool index.
     */
    public int getSelectedPoolIdx() {
        return selectedPoolIdx;
    }

    /**
     * Get the currently selected entry index.
     */
    public int getSelectedEntryIdx() {
        return selectedEntryIdx;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    // Helper record for entry rows
    private record EntryEditRow(int poolIdx, int entryIdx, LootEntry entry) {}
}
