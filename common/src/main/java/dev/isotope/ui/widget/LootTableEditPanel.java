package dev.isotope.ui.widget;

import dev.isotope.Isotope;
import dev.isotope.analysis.OrphanDetector;
import dev.isotope.data.StructureLootLink;
import dev.isotope.data.loot.*;
import dev.isotope.editing.ClipboardManager;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootEditOperation;
import dev.isotope.editing.LootTableParser;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.screen.BatchWeightScreen;
import dev.isotope.ui.screen.TemplateEditorScreen;
import dev.isotope.ui.screen.TemplatePickerScreen;
import dev.isotope.registry.EntityLootRegistry;
import dev.isotope.registry.FeatureRegistry;
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

    // Orphan tooltip state
    private boolean orphanWarningHovered = false;
    private int orphanTooltipX = 0;
    private int orphanTooltipY = 0;
    private EntryKey lastClickedEntry = null; // For shift-click range selection

    // Batch action hover state
    private boolean batchWeightHovered = false;
    private boolean batchDeleteHovered = false;

    // Inline editing state
    private enum EditingField { NONE, WEIGHT, COUNT_MIN, COUNT_MAX }
    private EditingField editingField = EditingField.NONE;
    private int editingPoolIdx = -1;
    private int editingEntryIdx = -1;
    private String editText = "";
    private int editCursorPos = 0;
    private int editFieldX, editFieldY, editFieldWidth;  // For rendering

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

    /**
     * Context menu request data.
     */
    public record ContextMenuRequest(int x, int y, ContextMenuType type, int poolIdx, int entryIdx) {}

    /**
     * Types of context menus.
     */
    public enum ContextMenuType { ENTRY, POOL }

    /**
     * Listener for context menu requests.
     */
    @FunctionalInterface
    public interface ContextMenuListener {
        void onContextMenuRequested(ContextMenuRequest request);
    }

    @Nullable
    private ContextMenuListener contextMenuListener;

    public void setContextMenuListener(@Nullable ContextMenuListener listener) {
        this.contextMenuListener = listener;
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
            // Empty state with icon and helpful messaging
            int centerX = getX() + width / 2;
            int centerY = getY() + height / 2;

            // Large icon
            graphics.drawString(font, "◇", centerX - 4, centerY - 50, 0xFF4a4a4a, false);

            String title = "No Loot Table Selected";
            graphics.drawString(font, title, centerX - font.width(title) / 2, centerY - 24,
                IsotopeColors.TEXT_PRIMARY, false);

            // Separator line
            graphics.fill(centerX - 60, centerY - 8, centerX + 60, centerY - 7, 0xFF333333);

            // Tips
            String hint1 = "← Select a table from the browser";
            graphics.drawString(font, hint1, centerX - font.width(hint1) / 2, centerY + 4,
                IsotopeColors.TEXT_MUTED, false);

            String hint2 = "Press 2 for global search";
            graphics.drawString(font, hint2, centerX - font.width(hint2) / 2, centerY + 20,
                IsotopeColors.TEXT_MUTED, false);

            // Keyboard shortcuts hint at bottom
            String shortcutHint = "F1 - Keyboard shortcuts  |  Ctrl+P - Commands";
            graphics.drawString(font, shortcutHint, centerX - font.width(shortcutHint) / 2, centerY + 50,
                0xFF555555, false);
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

        // Orphan tooltip (render last so it's on top)
        if (orphanWarningHovered && tableId != null) {
            var font = Minecraft.getInstance().font;
            var orphanInfo = OrphanDetector.getInstance().getLootTableOrphanInfo(tableId);
            if (!orphanInfo.reasons().isEmpty()) {
                // Build tooltip lines
                List<String> lines = new ArrayList<>();
                lines.add("Orphan Reasons:");
                for (var reason : orphanInfo.reasons()) {
                    lines.add("• " + reason.getDescription());
                }

                // Calculate tooltip dimensions
                int tooltipWidth = 0;
                for (String line : lines) {
                    tooltipWidth = Math.max(tooltipWidth, font.width(line));
                }
                tooltipWidth += 12;
                int tooltipHeight = lines.size() * 10 + 6;

                // Position tooltip
                int tooltipX = orphanTooltipX;
                int tooltipY = orphanTooltipY;

                // Draw tooltip background
                graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, 0xFF000000);
                graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, 0xFF1a1a2e);
                graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF252540);

                // Draw lines
                int lineY = tooltipY + 4;
                for (int i = 0; i < lines.size(); i++) {
                    int color = i == 0 ? IsotopeColors.STATUS_WARNING : IsotopeColors.TEXT_MUTED;
                    graphics.drawString(font, lines.get(i), tooltipX + 4, lineY, color, false);
                    lineY += 10;
                }
            }
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

        // Check if this is an entity loot table
        var entityInfo = EntityLootRegistry.getInstance().getByLootTable(tableId);

        // Linked structures or orphan warning
        List<StructureLootLink> links = StructureLootLinker.getInstance().getLinksForLootTable(tableId);
        boolean isOrphan = OrphanDetector.getInstance().isOrphanLootTable(tableId);

        if (entityInfo.isPresent()) {
            // This is an entity/mob loot table - show mob indicator with looting info
            var entity = entityInfo.get();
            String mobText = "Mob: " + entity.displayName();
            if (entity.requiresPlayerKill()) {
                mobText += " ⚔";
            }
            graphics.drawString(font, mobText, getX() + PADDING, y + 20, IsotopeColors.SOURCE_MOB, false);

            // Show looting info if any entries have looting bonuses
            int lootingAffectedCount = countLootingAffectedEntries();
            if (lootingAffectedCount > 0) {
                int lootingX = getX() + PADDING + font.width(mobText) + 8;
                String lootingText = "⚗ Looting: " + lootingAffectedCount + " drop" + (lootingAffectedCount > 1 ? "s" : "");
                graphics.drawString(font, lootingText, lootingX, y + 20, IsotopeColors.ACCENT_AQUA, false);
            }
        } else if (!links.isEmpty()) {
            // Separate features from structures
            List<StructureLootLink> featureLinks = new ArrayList<>();
            List<StructureLootLink> structureLinks = new ArrayList<>();
            for (StructureLootLink link : links) {
                if (link.isFeatureMapping()) {
                    featureLinks.add(link);
                } else {
                    structureLinks.add(link);
                }
            }

            int textX = getX() + PADDING;
            int textY = y + 20;

            // Show features first (with orange color and display name)
            if (!featureLinks.isEmpty()) {
                StringBuilder sb = new StringBuilder("Features: ");
                for (int i = 0; i < Math.min(featureLinks.size(), 2); i++) {
                    if (i > 0) sb.append(", ");
                    StructureLootLink link = featureLinks.get(i);
                    // Use display name from FeatureRegistry if available
                    String displayName = FeatureRegistry.getInstance().get(link.structureId())
                        .map(f -> f.displayName())
                        .orElse(link.structureId().getPath());
                    sb.append(displayName);
                }
                if (featureLinks.size() > 2) {
                    sb.append(" +").append(featureLinks.size() - 2).append(" more");
                }
                graphics.drawString(font, sb.toString(), textX, textY, IsotopeColors.SOURCE_FEATURE, false);
                textX += font.width(sb.toString()) + 8;
            }

            // Show structures (with cyan/muted color)
            if (!structureLinks.isEmpty()) {
                StringBuilder sb = new StringBuilder(featureLinks.isEmpty() ? "Structures: " : "");
                for (int i = 0; i < Math.min(structureLinks.size(), 3); i++) {
                    if (i > 0 || !featureLinks.isEmpty()) sb.append(", ");
                    StructureLootLink link = structureLinks.get(i);
                    sb.append(link.structureId().getPath());
                    // Add confidence badge for non-high confidence links
                    if (link.confidence().getScore() < 70) {
                        sb.append(" (").append(link.confidence().getLabel().toLowerCase()).append(")");
                    }
                }
                if (structureLinks.size() > 3) {
                    sb.append(" +").append(structureLinks.size() - 3).append(" more");
                }
                int color = featureLinks.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.SOURCE_STRUCTURE;
                graphics.drawString(font, sb.toString(), textX, textY, color, false);
            }
        } else if (isOrphan) {
            // Show orphan warning
            String orphanText = "⚠ Orphan: Not linked to any structure";
            int textX = getX() + PADDING;
            int textY = y + 20;
            int textWidth = font.width(orphanText);
            graphics.drawString(font, orphanText, textX, textY, IsotopeColors.STATUS_WARNING, false);

            // Check if hovering over orphan warning
            orphanWarningHovered = mouseX >= textX && mouseX < textX + textWidth &&
                mouseY >= textY - 2 && mouseY < textY + 12;
            if (orphanWarningHovered) {
                orphanTooltipX = mouseX;
                orphanTooltipY = textY + 14;
            }
        } else {
            orphanWarningHovered = false;
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
        boolean weightEditing = editingField == EditingField.WEIGHT &&
            editingPoolIdx == poolIdx && editingEntryIdx == entryIdx;
        boolean weightHovered = mouseX >= x && mouseX < x + weightBoxWidth &&
            mouseY >= y + 3 && mouseY < y + 19;

        int weightBgColor = weightEditing ? 0xFF2a3a4a : (weightHovered ? 0xFF404040 : 0xFF303030);
        int weightBorderColor = weightEditing ? IsotopeColors.ACCENT_GOLD : 0xFF505050;
        graphics.fill(x, y + 3, x + weightBoxWidth, y + 19, weightBgColor);
        graphics.renderOutline(x, y + 3, weightBoxWidth, 16, weightBorderColor);

        if (weightEditing) {
            // Show edit text with cursor
            int textX = x + (weightBoxWidth - font.width(editText)) / 2;
            graphics.drawString(font, editText, textX, y + 7, IsotopeColors.TEXT_PRIMARY, false);
            // Blinking cursor
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                String beforeCursor = editText.substring(0, Math.min(editCursorPos, editText.length()));
                int cursorX = textX + font.width(beforeCursor);
                graphics.fill(cursorX, y + 5, cursorX + 1, y + 16, IsotopeColors.TEXT_PRIMARY);
            }
        } else {
            String weightStr = String.valueOf(entry.weight());
            int weightTextX = x + (weightBoxWidth - font.width(weightStr)) / 2;
            graphics.drawString(font, weightStr, weightTextX, y + 7, IsotopeColors.TEXT_PRIMARY, false);
        }
        x += weightBoxWidth + 8;

        // Quantity (from set_count function)
        NumberProvider countProvider = getCountFromEntry(entry);
        graphics.drawString(font, "Qty:", x, y + 8, IsotopeColors.TEXT_MUTED, false);
        x += 22;

        // Min count
        int minCount = (int) countProvider.getMin();
        int countBoxWidth = 24;
        boolean minEditing = editingField == EditingField.COUNT_MIN &&
            editingPoolIdx == poolIdx && editingEntryIdx == entryIdx;
        boolean minHovered = mouseX >= x && mouseX < x + countBoxWidth &&
            mouseY >= y + 3 && mouseY < y + 19;

        int minBgColor = minEditing ? 0xFF2a3a4a : (minHovered ? 0xFF404040 : 0xFF303030);
        int minBorderColor = minEditing ? IsotopeColors.ACCENT_GOLD : 0xFF505050;
        graphics.fill(x, y + 3, x + countBoxWidth, y + 19, minBgColor);
        graphics.renderOutline(x, y + 3, countBoxWidth, 16, minBorderColor);

        if (minEditing) {
            int textX = x + (countBoxWidth - font.width(editText)) / 2;
            graphics.drawString(font, editText, textX, y + 7, IsotopeColors.TEXT_PRIMARY, false);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                String beforeCursor = editText.substring(0, Math.min(editCursorPos, editText.length()));
                int cursorX = textX + font.width(beforeCursor);
                graphics.fill(cursorX, y + 5, cursorX + 1, y + 16, IsotopeColors.TEXT_PRIMARY);
            }
        } else {
            String minStr = String.valueOf(minCount);
            graphics.drawString(font, minStr, x + (countBoxWidth - font.width(minStr)) / 2, y + 7,
                IsotopeColors.TEXT_PRIMARY, false);
        }
        x += countBoxWidth + 2;

        graphics.drawString(font, "-", x, y + 7, IsotopeColors.TEXT_MUTED, false);
        x += 8;

        // Max count
        int maxCount = (int) countProvider.getMax();
        boolean maxEditing = editingField == EditingField.COUNT_MAX &&
            editingPoolIdx == poolIdx && editingEntryIdx == entryIdx;
        boolean maxHovered = mouseX >= x && mouseX < x + countBoxWidth &&
            mouseY >= y + 3 && mouseY < y + 19;

        int maxBgColor = maxEditing ? 0xFF2a3a4a : (maxHovered ? 0xFF404040 : 0xFF303030);
        int maxBorderColor = maxEditing ? IsotopeColors.ACCENT_GOLD : 0xFF505050;
        graphics.fill(x, y + 3, x + countBoxWidth, y + 19, maxBgColor);
        graphics.renderOutline(x, y + 3, countBoxWidth, 16, maxBorderColor);

        if (maxEditing) {
            int textX = x + (countBoxWidth - font.width(editText)) / 2;
            graphics.drawString(font, editText, textX, y + 7, IsotopeColors.TEXT_PRIMARY, false);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                String beforeCursor = editText.substring(0, Math.min(editCursorPos, editText.length()));
                int cursorX = textX + font.width(beforeCursor);
                graphics.fill(cursorX, y + 5, cursorX + 1, y + 16, IsotopeColors.TEXT_PRIMARY);
            }
        } else {
            String maxStr = String.valueOf(maxCount);
            graphics.drawString(font, maxStr, x + (countBoxWidth - font.width(maxStr)) / 2, y + 7,
                IsotopeColors.TEXT_PRIMARY, false);
        }
        x += countBoxWidth + 10;

        // Entry function/condition indicators
        boolean hasLooting = hasLootingFunction(entry) || hasRandomChanceWithLootingCondition(entry);
        boolean hasPlayerKill = hasKilledByPlayerCondition(entry);
        boolean hasEnchant = hasEnchantmentFunction(entry);
        boolean hasAttribute = hasAttributeFunction(entry);
        boolean hasPotion = hasPotionFunction(entry);
        boolean hasDamage = hasDamageFunction(entry);

        int removeX = getX() + width - PADDING - 20;
        int indicatorX = removeX - 6;

        // Player kill indicator (sword icon in purple)
        if (hasPlayerKill) {
            indicatorX -= 10;
            graphics.drawString(font, "⚔", indicatorX, y + 7, IsotopeColors.SOURCE_MOB, false);
        }

        // Looting indicator (potion icon in aqua)
        if (hasLooting) {
            indicatorX -= 10;
            graphics.drawString(font, "⚗", indicatorX, y + 7, IsotopeColors.ACCENT_AQUA, false);
        }

        // Enchantment indicator (sparkle in gold)
        if (hasEnchant) {
            indicatorX -= 10;
            graphics.drawString(font, "✦", indicatorX, y + 7, IsotopeColors.ACCENT_GOLD, false);
        }

        // Attribute indicator (diamond in green)
        if (hasAttribute) {
            indicatorX -= 10;
            graphics.drawString(font, "◆", indicatorX, y + 7, IsotopeColors.ACCENT_GREEN, false);
        }

        // Potion indicator
        if (hasPotion) {
            indicatorX -= 10;
            graphics.drawString(font, "⚗", indicatorX, y + 7, IsotopeColors.SOURCE_FEATURE, false);
        }

        // Damage/durability indicator
        if (hasDamage) {
            indicatorX -= 10;
            graphics.drawString(font, "⚒", indicatorX, y + 7, IsotopeColors.TEXT_SECONDARY, false);
        }

        // Remove button
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

    /**
     * Count entries that have looting_enchant functions (affected by Looting enchantment).
     */
    private int countLootingAffectedEntries() {
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return 0;

        int count = 0;
        for (LootPool pool : display.pools()) {
            for (LootEntry entry : pool.entries()) {
                if (hasLootingFunction(entry)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Check if an entry has a looting_enchant function.
     */
    private boolean hasLootingFunction(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            String funcType = func.function();
            if (funcType.equals("minecraft:looting_enchant") || funcType.equals("looting_enchant")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entry has a killed_by_player condition.
     */
    private boolean hasKilledByPlayerCondition(LootEntry entry) {
        for (LootCondition cond : entry.conditions()) {
            String condType = cond.condition();
            if (condType.equals("minecraft:killed_by_player") || condType.equals("killed_by_player")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entry has random_chance_with_looting condition.
     */
    private boolean hasRandomChanceWithLootingCondition(LootEntry entry) {
        for (LootCondition cond : entry.conditions()) {
            String condType = cond.condition();
            if (condType.equals("minecraft:random_chance_with_looting") || condType.equals("random_chance_with_looting")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entry has any enchantment function.
     */
    private boolean hasEnchantmentFunction(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            if (func.isEnchantment()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entry has attribute modifiers.
     */
    private boolean hasAttributeFunction(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            if (func.isAttribute()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entry has a potion function.
     */
    private boolean hasPotionFunction(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            if (func.isPotion()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an entry has a damage/durability function.
     */
    private boolean hasDamageFunction(LootEntry entry) {
        for (LootFunction func : entry.functions()) {
            if (func.isDamage()) {
                return true;
            }
        }
        return false;
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

            // Check for right-click on pool header
            if (button == 1 && mouseY >= y && mouseY < y + POOL_HEADER_HEIGHT) {
                selectedPoolIdx = poolIdx;
                selectedEntryIdx = -1;
                if (contextMenuListener != null) {
                    contextMenuListener.onContextMenuRequested(
                        new ContextMenuRequest((int) mouseX, (int) mouseY, ContextMenuType.POOL, poolIdx, -1)
                    );
                }
                return true;
            }

            y += POOL_HEADER_HEIGHT;

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                // Check if clicking in entry row
                if (mouseY >= y && mouseY < y + ENTRY_HEIGHT) {
                    // Right-click: Show context menu
                    if (button == 1) {
                        selectedPoolIdx = poolIdx;
                        selectedEntryIdx = entryIdx;
                        if (contextMenuListener != null) {
                            contextMenuListener.onContextMenuRequested(
                                new ContextMenuRequest((int) mouseX, (int) mouseY, ContextMenuType.ENTRY, poolIdx, entryIdx)
                            );
                        }
                        return true;
                    }

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
        // Start inline editing
        editingField = EditingField.WEIGHT;
        editingPoolIdx = poolIdx;
        editingEntryIdx = entryIdx;
        editText = String.valueOf(currentWeight);
        editCursorPos = editText.length();
    }

    private void openCountEditor(int poolIdx, int entryIdx, int min, int max, boolean editMin) {
        // Start inline editing
        editingField = editMin ? EditingField.COUNT_MIN : EditingField.COUNT_MAX;
        editingPoolIdx = poolIdx;
        editingEntryIdx = entryIdx;
        editText = String.valueOf(editMin ? min : max);
        editCursorPos = editText.length();
    }

    private void commitEdit() {
        if (editingField == EditingField.NONE || tableId == null) return;

        try {
            int value = Integer.parseInt(editText);
            if (value < 1) value = 1;
            if (value > 9999) value = 9999;

            LootTableStructure display = editedStructure != null ? editedStructure : structure;
            if (display == null) {
                cancelEdit();
                return;
            }

            if (editingField == EditingField.WEIGHT) {
                LootEditOperation op = new LootEditOperation.ModifyEntryWeight(editingPoolIdx, editingEntryIdx, value);
                LootEditManager.getInstance().applyOperation(tableId, op);
            } else {
                // Count editing
                LootPool pool = display.pools().get(editingPoolIdx);
                LootEntry entry = pool.entries().get(editingEntryIdx);
                NumberProvider currentCount = getCountFromEntry(entry);
                int min = (int) currentCount.getMin();
                int max = (int) currentCount.getMax();

                if (editingField == EditingField.COUNT_MIN) {
                    min = value;
                    if (min > max) max = min;  // Adjust max if min exceeds it
                } else {
                    max = value;
                    if (max < min) min = max;  // Adjust min if max is below it
                }

                NumberProvider newCount = min == max
                    ? new NumberProvider.Constant(min)
                    : new NumberProvider.Uniform(min, max);

                LootEditOperation op = new LootEditOperation.SetItemCount(editingPoolIdx, editingEntryIdx, newCount);
                LootEditManager.getInstance().applyOperation(tableId, op);
            }

            refreshFromEdits();
        } catch (NumberFormatException e) {
            // Invalid input, just cancel
        }

        cancelEdit();
    }

    private void cancelEdit() {
        editingField = EditingField.NONE;
        editingPoolIdx = -1;
        editingEntryIdx = -1;
        editText = "";
        editCursorPos = 0;
    }

    /**
     * Check if currently editing a field.
     */
    public boolean isEditing() {
        return editingField != EditingField.NONE;
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

    private void openSaveAsTemplateDialog(LootEntry entry) {
        Isotope.LOGGER.info("Opening template editor from entry: {}", entry.name().orElse(null));

        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(TemplateEditorScreen.fromEntry(currentScreen, entry, null));
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Inline editing mode - handle text input keys
        if (editingField != EditingField.NONE) {
            // Enter - commit
            if (keyCode == 257 || keyCode == 335) { // Enter or numpad enter
                commitEdit();
                return true;
            }

            // Escape - cancel
            if (keyCode == 256) {
                cancelEdit();
                return true;
            }

            // Backspace
            if (keyCode == 259 && editCursorPos > 0) {
                editText = editText.substring(0, editCursorPos - 1) + editText.substring(editCursorPos);
                editCursorPos--;
                return true;
            }

            // Delete
            if (keyCode == 261 && editCursorPos < editText.length()) {
                editText = editText.substring(0, editCursorPos) + editText.substring(editCursorPos + 1);
                return true;
            }

            // Left arrow
            if (keyCode == 263 && editCursorPos > 0) {
                editCursorPos--;
                return true;
            }

            // Right arrow
            if (keyCode == 262 && editCursorPos < editText.length()) {
                editCursorPos++;
                return true;
            }

            // Home
            if (keyCode == 268) {
                editCursorPos = 0;
                return true;
            }

            // End
            if (keyCode == 269) {
                editCursorPos = editText.length();
                return true;
            }

            return false;
        }

        // Navigation mode - handle arrow keys when not editing
        if (tableId == null) return false;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || display.pools().isEmpty()) return false;

        // Build flat list of entries for navigation
        List<EntryKey> allEntries = buildEntryList(display);
        if (allEntries.isEmpty()) return false;

        // Arrow Down - next entry
        if (keyCode == 264) { // Down
            navigateEntries(allEntries, 1);
            return true;
        }

        // Arrow Up - previous entry
        if (keyCode == 265) { // Up
            navigateEntries(allEntries, -1);
            return true;
        }

        // Home - first entry
        if (keyCode == 268) {
            if (!allEntries.isEmpty()) {
                EntryKey first = allEntries.get(0);
                selectedPoolIdx = first.poolIdx();
                selectedEntryIdx = first.entryIdx();
                ensureSelectionVisible();
            }
            return true;
        }

        // End - last entry
        if (keyCode == 269) {
            if (!allEntries.isEmpty()) {
                EntryKey last = allEntries.get(allEntries.size() - 1);
                selectedPoolIdx = last.poolIdx();
                selectedEntryIdx = last.entryIdx();
                ensureSelectionVisible();
            }
            return true;
        }

        // Enter - start editing weight of selected entry
        if (keyCode == 257 || keyCode == 335) {
            if (selectedPoolIdx >= 0 && selectedEntryIdx >= 0) {
                LootPool pool = display.pools().get(selectedPoolIdx);
                if (selectedEntryIdx < pool.entries().size()) {
                    LootEntry entry = pool.entries().get(selectedEntryIdx);
                    openWeightEditor(selectedPoolIdx, selectedEntryIdx, entry.weight());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Build a flat list of all entry keys for navigation.
     */
    private List<EntryKey> buildEntryList(LootTableStructure display) {
        List<EntryKey> allEntries = new ArrayList<>();
        for (int poolIdx = 0; poolIdx < display.pools().size(); poolIdx++) {
            LootPool pool = display.pools().get(poolIdx);
            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                allEntries.add(new EntryKey(poolIdx, entryIdx));
            }
        }
        return allEntries;
    }

    /**
     * Navigate entries by the given delta (-1 for up, +1 for down).
     */
    private void navigateEntries(List<EntryKey> allEntries, int delta) {
        if (allEntries.isEmpty()) return;

        // Find current index
        int currentIndex = -1;
        if (selectedPoolIdx >= 0 && selectedEntryIdx >= 0) {
            EntryKey current = new EntryKey(selectedPoolIdx, selectedEntryIdx);
            currentIndex = allEntries.indexOf(current);
        }

        // Calculate new index
        int newIndex;
        if (currentIndex < 0) {
            // Nothing selected - select first or last based on direction
            newIndex = delta > 0 ? 0 : allEntries.size() - 1;
        } else {
            newIndex = currentIndex + delta;
            // Clamp to valid range
            newIndex = Math.max(0, Math.min(allEntries.size() - 1, newIndex));
        }

        // Update selection
        EntryKey newSelection = allEntries.get(newIndex);
        selectedPoolIdx = newSelection.poolIdx();
        selectedEntryIdx = newSelection.entryIdx();

        // Clear multi-selection when navigating
        multiSelection.clear();
        lastClickedEntry = newSelection;

        ensureSelectionVisible();
    }

    /**
     * Scroll to ensure the selected entry is visible.
     */
    private void ensureSelectionVisible() {
        if (selectedPoolIdx < 0 || selectedEntryIdx < 0) return;

        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return;

        // Calculate Y position of selected entry
        int entryY = HEADER_HEIGHT;

        // Account for batch bar if showing
        if (multiSelection.size() > 1) {
            entryY += BATCH_BAR_HEIGHT;
        }

        for (int poolIdx = 0; poolIdx < display.pools().size(); poolIdx++) {
            LootPool pool = display.pools().get(poolIdx);
            entryY += POOL_HEADER_HEIGHT;

            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                if (poolIdx == selectedPoolIdx && entryIdx == selectedEntryIdx) {
                    // Found the selected entry - ensure it's visible
                    int entryTop = entryY - scrollOffset;
                    int entryBottom = entryTop + ENTRY_HEIGHT;

                    if (entryTop < 0) {
                        // Scroll up to show entry
                        scrollOffset = entryY;
                    } else if (entryBottom > height) {
                        // Scroll down to show entry
                        scrollOffset = entryY + ENTRY_HEIGHT - height;
                    }

                    scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                    return;
                }
                entryY += ENTRY_HEIGHT;
            }
            entryY += 24; // Add item button
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingField == EditingField.NONE) return false;

        // Only allow digits (no decimals)
        if (Character.isDigit(chr)) {
            if (editText.length() < 4) { // Max 4 digits (9999)
                editText = editText.substring(0, editCursorPos) + chr + editText.substring(editCursorPos);
                editCursorPos++;
            }
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
     * Check if any entries are currently selected.
     */
    public boolean hasSelection() {
        return selectedPoolIdx >= 0 || selectedEntryIdx >= 0 || !multiSelection.isEmpty();
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

    /**
     * Get the currently selected entry, or null if none selected.
     */
    @Nullable
    public LootEntry getSelectedEntry() {
        if (selectedPoolIdx < 0 || selectedEntryIdx < 0) return null;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return null;
        if (selectedPoolIdx >= display.pools().size()) return null;
        LootPool pool = display.pools().get(selectedPoolIdx);
        if (selectedEntryIdx >= pool.entries().size()) return null;
        return pool.entries().get(selectedEntryIdx);
    }

    /**
     * Select a specific pool and entry.
     * Used for navigation from validation issues.
     */
    public void selectPoolEntry(int poolIdx, int entryIdx) {
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null) return;

        // Validate indices
        if (poolIdx < 0 || poolIdx >= display.pools().size()) return;
        LootPool pool = display.pools().get(poolIdx);

        // Clear multi-selection
        multiSelection.clear();

        // Set selection
        selectedPoolIdx = poolIdx;
        if (entryIdx >= 0 && entryIdx < pool.entries().size()) {
            selectedEntryIdx = entryIdx;
        } else {
            selectedEntryIdx = -1;
        }

        // TODO: Could scroll to make selection visible if needed
    }

    /**
     * Open the save as template dialog for the selected entry.
     */
    public void saveSelectedAsTemplate() {
        LootEntry entry = getSelectedEntry();
        if (entry != null) {
            openSaveAsTemplateDialog(entry);
        }
    }

    /**
     * Open add item dialog for a specific pool.
     */
    public void openAddItem(int poolIdx) {
        openAddItemDialog(poolIdx);
    }

    /**
     * Open template picker for a specific pool.
     */
    public void openTemplatePicker(int poolIdx) {
        openTemplateDialog(poolIdx);
    }

    /**
     * Delete a specific pool.
     */
    public void deletePool(int poolIdx) {
        if (tableId == null) return;
        LootEditOperation op = new LootEditOperation.RemovePool(poolIdx);
        LootEditManager.getInstance().applyOperation(tableId, op);
        multiSelection.clear();
        refreshFromEdits();
    }

    /**
     * Move an entry up within its pool.
     */
    public void moveEntryUp(int poolIdx, int entryIdx) {
        if (tableId == null || entryIdx <= 0) return;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || poolIdx >= display.pools().size()) return;

        LootPool pool = display.pools().get(poolIdx);
        if (entryIdx >= pool.entries().size()) return;

        // Swap with previous entry using modify operations
        LootEntry entry = pool.entries().get(entryIdx);
        LootEntry prevEntry = pool.entries().get(entryIdx - 1);

        LootEditManager manager = LootEditManager.getInstance();
        // Remove entry, then add at new position
        manager.applyOperation(tableId, new LootEditOperation.RemoveEntry(poolIdx, entryIdx));
        manager.applyOperation(tableId, new LootEditOperation.AddEntry(poolIdx, entryIdx - 1, entry));

        selectedEntryIdx = entryIdx - 1;
        refreshFromEdits();
    }

    /**
     * Move an entry down within its pool.
     */
    public void moveEntryDown(int poolIdx, int entryIdx) {
        if (tableId == null) return;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || poolIdx >= display.pools().size()) return;

        LootPool pool = display.pools().get(poolIdx);
        if (entryIdx < 0 || entryIdx >= pool.entries().size() - 1) return;

        // Get entry
        LootEntry entry = pool.entries().get(entryIdx);

        LootEditManager manager = LootEditManager.getInstance();
        // Remove entry, then add at new position
        manager.applyOperation(tableId, new LootEditOperation.RemoveEntry(poolIdx, entryIdx));
        manager.applyOperation(tableId, new LootEditOperation.AddEntry(poolIdx, entryIdx + 1, entry));

        selectedEntryIdx = entryIdx + 1;
        refreshFromEdits();
    }

    /**
     * Add a new empty pool.
     */
    public void addPool() {
        if (tableId == null) return;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        int newPoolIdx = display != null ? display.pools().size() : 0;

        // Create an empty pool with 1 roll
        LootPool emptyPool = LootPool.simple(1, List.of());

        LootEditManager manager = LootEditManager.getInstance();
        manager.applyOperation(tableId, new LootEditOperation.AddPool(newPoolIdx, emptyPool));
        refreshFromEdits();
        IsotopeToast.success("Pool Added", "New pool created");
    }

    /**
     * Duplicate a pool with all its entries.
     */
    public void duplicatePool(int poolIdx) {
        if (tableId == null) return;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || poolIdx >= display.pools().size()) return;

        LootPool pool = display.pools().get(poolIdx);
        int newPoolIdx = display.pools().size();

        // Create a copy of the pool
        LootEditManager manager = LootEditManager.getInstance();
        manager.applyOperation(tableId, new LootEditOperation.AddPool(newPoolIdx, pool));

        refreshFromEdits();
        IsotopeToast.success("Pool Duplicated", "Pool copied with " + pool.entries().size() + " entries");
    }

    /**
     * Clear all entries from a pool.
     */
    public void clearPool(int poolIdx) {
        if (tableId == null) return;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || poolIdx >= display.pools().size()) return;

        LootPool pool = display.pools().get(poolIdx);
        LootEditManager manager = LootEditManager.getInstance();

        // Remove entries in reverse order
        for (int i = pool.entries().size() - 1; i >= 0; i--) {
            manager.applyOperation(tableId, new LootEditOperation.RemoveEntry(poolIdx, i));
        }

        multiSelection.clear();
        refreshFromEdits();
        IsotopeToast.success("Pool Cleared", "All entries removed");
    }

    /**
     * Check if an entry can move up.
     */
    public boolean canMoveUp() {
        return selectedEntryIdx > 0;
    }

    /**
     * Check if an entry can move down.
     */
    public boolean canMoveDown() {
        if (selectedPoolIdx < 0 || selectedEntryIdx < 0) return false;
        LootTableStructure display = editedStructure != null ? editedStructure : structure;
        if (display == null || selectedPoolIdx >= display.pools().size()) return false;
        return selectedEntryIdx < display.pools().get(selectedPoolIdx).entries().size() - 1;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    // Helper record for entry rows
    private record EntryEditRow(int poolIdx, int entryIdx, LootEntry entry) {}
}
