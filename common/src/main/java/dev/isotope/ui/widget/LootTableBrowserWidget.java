package dev.isotope.ui.widget;

import dev.isotope.analysis.OrphanDetector;
import dev.isotope.data.BookmarkManager;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import dev.isotope.data.RecentTablesManager;
import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.EntityLootRegistry;
import dev.isotope.registry.FeatureRegistry;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Left panel browser for loot tables.
 *
 * Features:
 * - Mod filter dropdown
 * - Search box
 * - Collapsible category tree
 */
@Environment(EnvType.CLIENT)
public class LootTableBrowserWidget extends AbstractWidget {

    private static final int SEARCH_HEIGHT = 20;
    private static final int MOD_FILTER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 16;
    private static final int CATEGORY_HEIGHT = 18;
    private static final int INDENT = 12;

    private final Consumer<ResourceLocation> onTableSelected;

    // UI State
    private EditBox searchBox;
    private String selectedMod = "All";
    private final Set<LootTableCategory> expandedCategories = new HashSet<>();
    private boolean bookmarksSectionExpanded = true;
    private boolean recentSectionExpanded = true;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Listeners
    private final BookmarkManager.BookmarkListener bookmarkListener = this::onBookmarksChanged;
    private final RecentTablesManager.RecentListener recentListener = this::onRecentChanged;

    // Data
    private List<String> availableMods = new ArrayList<>();
    private Map<LootTableCategory, List<LootTableInfo>> tablesByCategory = new LinkedHashMap<>();
    private List<LootTableInfo> filteredTables = new ArrayList<>();

    // Selection
    @Nullable
    private ResourceLocation selectedTable;

    // Mod dropdown state
    private boolean modDropdownOpen = false;

    // Orphan tooltip state
    @Nullable
    private ResourceLocation hoveredOrphanTable = null;
    private int hoveredOrphanX = 0;
    private int hoveredOrphanY = 0;

    // Mob tooltip state
    @Nullable
    private ResourceLocation hoveredMobTable = null;
    private int hoveredMobX = 0;
    private int hoveredMobY = 0;

    // Feature tooltip state
    @Nullable
    private ResourceLocation hoveredFeatureTable = null;
    private int hoveredFeatureX = 0;
    private int hoveredFeatureY = 0;

    public LootTableBrowserWidget(int x, int y, int width, int height, Consumer<ResourceLocation> onTableSelected) {
        super(x, y, width, height, Component.empty());
        this.onTableSelected = onTableSelected;

        // Expand common categories by default
        expandedCategories.add(LootTableCategory.CHEST);
        expandedCategories.add(LootTableCategory.ENTITY);
    }

    /**
     * Clean up resources when the widget is being disposed.
     * Call this from the parent screen's onClose().
     */
    public void cleanup() {
        BookmarkManager.getInstance().removeListener(bookmarkListener);
        RecentTablesManager.getInstance().removeListener(recentListener);
    }

    public void loadData() {
        // Register listeners (removed in cleanup())
        BookmarkManager.getInstance().addListener(bookmarkListener);
        RecentTablesManager.getInstance().addListener(recentListener);

        // Get all loot tables
        Collection<LootTableInfo> allTables = LootTableRegistry.getInstance().getAll();

        // Collect mods
        Set<String> mods = new TreeSet<>();
        for (LootTableInfo table : allTables) {
            mods.add(table.id().getNamespace());
        }
        availableMods = new ArrayList<>();
        availableMods.add("All");
        availableMods.addAll(mods);

        // Group by category
        tablesByCategory.clear();
        for (LootTableCategory cat : LootTableCategory.values()) {
            tablesByCategory.put(cat, new ArrayList<>());
        }
        for (LootTableInfo table : allTables) {
            tablesByCategory.get(table.category()).add(table);
        }

        // Sort each category
        for (List<LootTableInfo> tables : tablesByCategory.values()) {
            tables.sort(Comparator.comparing(t -> t.id().toString()));
        }

        applyFilters();
    }

    private void onBookmarksChanged() {
        calculateMaxScroll();
    }

    private void onRecentChanged() {
        calculateMaxScroll();
    }

    /**
     * Get bookmarked tables that match current filters.
     */
    private List<ResourceLocation> getFilteredBookmarks() {
        List<ResourceLocation> result = new ArrayList<>();
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        for (ResourceLocation id : BookmarkManager.getInstance().getAll()) {
            // Mod filter
            if (!selectedMod.equals("All") && !id.getNamespace().equals(selectedMod)) {
                continue;
            }
            // Search filter
            if (!searchText.isEmpty() && !id.toString().toLowerCase().contains(searchText)) {
                continue;
            }
            result.add(id);
        }
        return result;
    }

    /**
     * Get recent tables that match current filters.
     * Excludes tables that are already bookmarked (shown in bookmarks section).
     */
    private List<ResourceLocation> getFilteredRecent() {
        List<ResourceLocation> result = new ArrayList<>();
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        for (ResourceLocation id : RecentTablesManager.getInstance().getAll()) {
            // Skip if bookmarked (already shown in bookmarks)
            if (BookmarkManager.getInstance().isBookmarked(id)) {
                continue;
            }
            // Mod filter
            if (!selectedMod.equals("All") && !id.getNamespace().equals(selectedMod)) {
                continue;
            }
            // Search filter
            if (!searchText.isEmpty() && !id.toString().toLowerCase().contains(searchText)) {
                continue;
            }
            result.add(id);
        }
        return result;
    }

    private void applyFilters() {
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        filteredTables.clear();
        for (var entry : tablesByCategory.entrySet()) {
            for (LootTableInfo table : entry.getValue()) {
                // Mod filter
                if (!selectedMod.equals("All") && !table.id().getNamespace().equals(selectedMod)) {
                    continue;
                }
                // Search filter
                if (!searchText.isEmpty() && !table.id().toString().toLowerCase().contains(searchText)) {
                    continue;
                }
                filteredTables.add(table);
            }
        }

        // Reset scroll
        scrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int contentHeight = 0;

        // Bookmarks section
        List<ResourceLocation> bookmarks = getFilteredBookmarks();
        if (!bookmarks.isEmpty()) {
            contentHeight += CATEGORY_HEIGHT; // Header
            if (bookmarksSectionExpanded) {
                contentHeight += bookmarks.size() * ITEM_HEIGHT;
            }
        }

        // Recent section
        List<ResourceLocation> recent = getFilteredRecent();
        if (!recent.isEmpty()) {
            contentHeight += CATEGORY_HEIGHT; // Header
            if (recentSectionExpanded) {
                contentHeight += recent.size() * ITEM_HEIGHT;
            }
        }

        // Category sections
        for (LootTableCategory cat : LootTableCategory.values()) {
            if (getCategoryCount(cat) > 0) {
                contentHeight += CATEGORY_HEIGHT;
                if (expandedCategories.contains(cat)) {
                    contentHeight += getCategoryCount(cat) * ITEM_HEIGHT;
                }
            }
        }
        int viewHeight = height - SEARCH_HEIGHT - MOD_FILTER_HEIGHT - 10;
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    private int getCategoryCount(LootTableCategory category) {
        int count = 0;
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        for (LootTableInfo table : tablesByCategory.getOrDefault(category, List.of())) {
            if (!selectedMod.equals("All") && !table.id().getNamespace().equals(selectedMod)) {
                continue;
            }
            if (!searchText.isEmpty() && !table.id().toString().toLowerCase().contains(searchText)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private List<LootTableInfo> getFilteredTablesForCategory(LootTableCategory category) {
        List<LootTableInfo> result = new ArrayList<>();
        String searchText = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        for (LootTableInfo table : tablesByCategory.getOrDefault(category, List.of())) {
            if (!selectedMod.equals("All") && !table.id().getNamespace().equals(selectedMod)) {
                continue;
            }
            if (!searchText.isEmpty() && !table.id().toString().toLowerCase().contains(searchText)) {
                continue;
            }
            result.add(table);
        }
        return result;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        // Reset tooltip states
        hoveredOrphanTable = null;
        hoveredMobTable = null;
        hoveredFeatureTable = null;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_PANEL);

        int y = getY();

        // Search box area
        graphics.fill(getX(), y, getX() + width, y + SEARCH_HEIGHT + 4, IsotopeColors.POOL_HEADER_BACKGROUND);
        if (searchBox == null) {
            searchBox = new EditBox(font, getX() + 4, y + 2, width - 8, SEARCH_HEIGHT, Component.literal("Search"));
            searchBox.setHint(Component.literal("Search..."));
            searchBox.setBordered(true);
            searchBox.setResponder(text -> applyFilters());
        }
        searchBox.setX(getX() + 4);
        searchBox.setY(y + 2);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        y += SEARCH_HEIGHT + 4;

        // Mod filter
        graphics.fill(getX(), y, getX() + width, y + MOD_FILTER_HEIGHT + 4, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.drawString(font, "Mod:", getX() + 4, y + 6, IsotopeColors.TEXT_MUTED, false);

        // Mod dropdown button
        int dropdownX = getX() + 30;
        int dropdownWidth = width - 34;
        boolean dropdownHovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
            mouseY >= y + 2 && mouseY < y + MOD_FILTER_HEIGHT + 2;

        graphics.fill(dropdownX, y + 2, dropdownX + dropdownWidth, y + MOD_FILTER_HEIGHT + 2,
            dropdownHovered ? IsotopeColors.BORDER_DEFAULT : IsotopeColors.INPUT_BACKGROUND);
        ScreenUtils.renderOutline(graphics, dropdownX, y + 2, dropdownWidth, MOD_FILTER_HEIGHT, IsotopeColors.INPUT_BORDER);

        String modDisplay = selectedMod.length() > 18 ? selectedMod.substring(0, 16) + "..." : selectedMod;
        graphics.drawString(font, modDisplay, dropdownX + 4, y + 6, IsotopeColors.TEXT_PRIMARY, false);
        graphics.drawString(font, "▼", dropdownX + dropdownWidth - 12, y + 6, IsotopeColors.TEXT_MUTED, false);

        y += MOD_FILTER_HEIGHT + 6;

        // Category list with scroll
        int listY = y;
        int listHeight = getY() + height - listY;

        // Clip region for scrolling
        graphics.enableScissor(getX(), listY, getX() + width, getY() + height);

        int renderY = listY - scrollOffset;

        // Bookmarks section
        List<ResourceLocation> bookmarks = getFilteredBookmarks();
        if (!bookmarks.isEmpty()) {
            boolean catHovered = mouseX >= getX() && mouseX < getX() + width &&
                mouseY >= renderY && mouseY < renderY + CATEGORY_HEIGHT &&
                renderY >= listY && renderY < getY() + height;

            // Bookmarks header
            if (renderY + CATEGORY_HEIGHT > listY && renderY < getY() + height) {
                graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, IsotopeColors.CATEGORY_BOOKMARKS);
                if (catHovered) {
                    graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, IsotopeColors.CATEGORY_BOOKMARKS_HOVER);
                }

                String arrow = bookmarksSectionExpanded ? "▼" : "▶";
                graphics.drawString(font, arrow, getX() + 4, renderY + 4, IsotopeColors.ACCENT_GOLD, false);
                graphics.drawString(font, "★ Bookmarks", getX() + 16, renderY + 4,
                    IsotopeColors.ACCENT_GOLD, false);
                graphics.drawString(font, "(" + bookmarks.size() + ")", getX() + width - 30, renderY + 4,
                    IsotopeColors.TEXT_MUTED, false);
            }
            renderY += CATEGORY_HEIGHT;

            // Bookmark entries if expanded
            if (bookmarksSectionExpanded) {
                for (ResourceLocation id : bookmarks) {
                    if (renderY + ITEM_HEIGHT > listY && renderY < getY() + height) {
                        boolean isSelected = id.equals(selectedTable);
                        boolean itemHovered = mouseX >= getX() + INDENT && mouseX < getX() + width &&
                            mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                            renderY >= listY && renderY < getY() + height;

                        if (isSelected) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, IsotopeColors.ITEM_BOOKMARKS_SELECTED);
                        } else if (itemHovered) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, IsotopeColors.ITEM_BOOKMARKS_HOVER);
                        }

                        // Star icon
                        graphics.drawString(font, "★", getX() + INDENT - 8, renderY + 4, IsotopeColors.ACCENT_GOLD, false);

                        String path = id.getPath();
                        if (font.width(path) > width - INDENT - 16) {
                            path = font.plainSubstrByWidth(path, width - INDENT - 24) + "...";
                        }
                        graphics.drawString(font, path, getX() + INDENT + 4, renderY + 4,
                            isSelected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        // Recent section
        List<ResourceLocation> recent = getFilteredRecent();
        if (!recent.isEmpty()) {
            boolean catHovered = mouseX >= getX() && mouseX < getX() + width &&
                mouseY >= renderY && mouseY < renderY + CATEGORY_HEIGHT &&
                renderY >= listY && renderY < getY() + height;

            // Recent header
            if (renderY + CATEGORY_HEIGHT > listY && renderY < getY() + height) {
                graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, IsotopeColors.CATEGORY_RECENT);
                if (catHovered) {
                    graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, IsotopeColors.CATEGORY_RECENT_HOVER);
                }

                String arrow = recentSectionExpanded ? "▼" : "▶";
                graphics.drawString(font, arrow, getX() + 4, renderY + 4, IsotopeColors.ACCENT_AQUA, false);
                graphics.drawString(font, "⏱ Recent", getX() + 16, renderY + 4,
                    IsotopeColors.ACCENT_AQUA, false);
                graphics.drawString(font, "(" + recent.size() + ")", getX() + width - 30, renderY + 4,
                    IsotopeColors.TEXT_MUTED, false);
            }
            renderY += CATEGORY_HEIGHT;

            // Recent entries if expanded
            if (recentSectionExpanded) {
                for (ResourceLocation id : recent) {
                    if (renderY + ITEM_HEIGHT > listY && renderY < getY() + height) {
                        boolean isSelected = id.equals(selectedTable);
                        boolean itemHovered = mouseX >= getX() + INDENT && mouseX < getX() + width &&
                            mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                            renderY >= listY && renderY < getY() + height;

                        if (isSelected) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, IsotopeColors.ITEM_RECENT_SELECTED);
                        } else if (itemHovered) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, IsotopeColors.ITEM_RECENT_HOVER);
                        }

                        // Clock icon
                        graphics.drawString(font, "⏱", getX() + INDENT - 8, renderY + 4, IsotopeColors.ACCENT_AQUA, false);

                        String path = id.getPath();
                        if (font.width(path) > width - INDENT - 16) {
                            path = font.plainSubstrByWidth(path, width - INDENT - 24) + "...";
                        }
                        graphics.drawString(font, path, getX() + INDENT + 4, renderY + 4,
                            isSelected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        // Check if anything to show
        boolean hasContent = !bookmarks.isEmpty() || !recent.isEmpty();
        if (!hasContent) {
            for (LootTableCategory cat : LootTableCategory.values()) {
                if (getCategoryCount(cat) > 0) {
                    hasContent = true;
                    break;
                }
            }
        }

        // Empty state when no results
        if (!hasContent) {
            String searchText = searchBox != null ? searchBox.getValue() : "";
            int centerX = getX() + width / 2;
            int centerY = listY + 60;

            if (!searchText.isEmpty()) {
                // No search results
                graphics.drawString(font, "⌕", centerX - 4, centerY - 20, IsotopeColors.TEXT_MUTED, false);
                String noResults = "No results found";
                graphics.drawString(font, noResults, centerX - font.width(noResults) / 2, centerY,
                    IsotopeColors.TEXT_SECONDARY, false);
                String hint = "Try a different search term";
                graphics.drawString(font, hint, centerX - font.width(hint) / 2, centerY + 14,
                    IsotopeColors.TEXT_MUTED, false);
            } else if (!selectedMod.equals("All")) {
                // No tables for selected mod
                graphics.drawString(font, "∅", centerX - 4, centerY - 20, IsotopeColors.TEXT_MUTED, false);
                String noTables = "No loot tables";
                graphics.drawString(font, noTables, centerX - font.width(noTables) / 2, centerY,
                    IsotopeColors.TEXT_SECONDARY, false);
                String modHint = "for " + selectedMod;
                graphics.drawString(font, modHint, centerX - font.width(modHint) / 2, centerY + 14,
                    IsotopeColors.TEXT_MUTED, false);
            } else {
                // No tables at all (shouldn't happen normally)
                graphics.drawString(font, "∅", centerX - 4, centerY - 20, IsotopeColors.TEXT_MUTED, false);
                String noTables = "No loot tables found";
                graphics.drawString(font, noTables, centerX - font.width(noTables) / 2, centerY,
                    IsotopeColors.TEXT_SECONDARY, false);
                String hint = "Join a world first";
                graphics.drawString(font, hint, centerX - font.width(hint) / 2, centerY + 14,
                    IsotopeColors.TEXT_MUTED, false);
            }
            graphics.disableScissor();
            return;
        }

        for (LootTableCategory cat : LootTableCategory.values()) {
            int count = getCategoryCount(cat);
            if (count == 0) continue;

            boolean expanded = expandedCategories.contains(cat);
            boolean catHovered = mouseX >= getX() && mouseX < getX() + width &&
                mouseY >= renderY && mouseY < renderY + CATEGORY_HEIGHT &&
                renderY >= listY && renderY < getY() + height;

            // Category header
            if (renderY + CATEGORY_HEIGHT > listY && renderY < getY() + height) {
                if (catHovered) {
                    graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, IsotopeColors.INPUT_BACKGROUND);
                }

                String arrow = expanded ? "▼" : "▶";
                // Use purple for Entity category (mob drops)
                int catColor = (cat == LootTableCategory.ENTITY) ? IsotopeColors.SOURCE_MOB : IsotopeColors.TEXT_PRIMARY;
                int arrowColor = (cat == LootTableCategory.ENTITY) ? IsotopeColors.SOURCE_MOB : IsotopeColors.TEXT_MUTED;
                graphics.drawString(font, arrow, getX() + 4, renderY + 4, arrowColor, false);
                String catName = cat.name().charAt(0) + cat.name().substring(1).toLowerCase();
                graphics.drawString(font, catName, getX() + 16, renderY + 4, catColor, false);
                graphics.drawString(font, "(" + count + ")", getX() + width - 30, renderY + 4,
                    IsotopeColors.TEXT_MUTED, false);
            }
            renderY += CATEGORY_HEIGHT;

            // Entries if expanded
            if (expanded) {
                for (LootTableInfo table : getFilteredTablesForCategory(cat)) {
                    if (renderY + ITEM_HEIGHT > listY && renderY < getY() + height) {
                        boolean isSelected = table.id().equals(selectedTable);
                        boolean itemHovered = mouseX >= getX() + INDENT && mouseX < getX() + width &&
                            mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                            renderY >= listY && renderY < getY() + height;
                        boolean isBookmarked = BookmarkManager.getInstance().isBookmarked(table.id());
                        boolean starHovered = mouseX >= getX() + 2 && mouseX < getX() + INDENT &&
                            mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                            renderY >= listY && renderY < getY() + height;

                        if (isSelected) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, IsotopeColors.ITEM_ENTITY_SELECTED);
                        } else if (itemHovered || starHovered) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, IsotopeColors.POOL_HEADER_HOVER);
                        }

                        // Bookmark star (click target)
                        String star = isBookmarked ? "★" : (starHovered ? "☆" : "");
                        if (!star.isEmpty()) {
                            int starColor = isBookmarked ? IsotopeColors.ACCENT_GOLD : IsotopeColors.STAR_MUTED;
                            graphics.drawString(font, star, getX() + 3, renderY + 4, starColor, false);
                        }

                        // Right-side indicators (player kill, orphan)
                        int textRightPadding = 8;
                        int nextIconX = getX() + width - 12;

                        // Orphan indicator (rightmost)
                        boolean isOrphan = OrphanDetector.getInstance().isOrphanLootTable(table.id());
                        if (isOrphan) {
                            graphics.drawString(font, "⚠", nextIconX, renderY + 4, IsotopeColors.STATUS_WARNING, false);
                            textRightPadding += 10;

                            // Check if hovering over orphan icon
                            boolean orphanHovered = mouseX >= nextIconX - 2 && mouseX < nextIconX + 10 &&
                                mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                                renderY >= listY && renderY < getY() + height;
                            if (orphanHovered) {
                                hoveredOrphanTable = table.id();
                                hoveredOrphanX = mouseX;
                                hoveredOrphanY = renderY + ITEM_HEIGHT;
                            }
                            nextIconX -= 10;
                        }

                        // Player kill indicator for entity loot (sword icon)
                        var entityInfo = EntityLootRegistry.getInstance().getByLootTable(table.id());
                        if (entityInfo.isPresent() && entityInfo.get().requiresPlayerKill()) {
                            graphics.drawString(font, "⚔", nextIconX, renderY + 4, IsotopeColors.SOURCE_MOB, false);
                            textRightPadding += 10;

                            // Check if hovering over mob icon
                            boolean mobHovered = mouseX >= nextIconX - 2 && mouseX < nextIconX + 10 &&
                                mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                                renderY >= listY && renderY < getY() + height;
                            if (mobHovered) {
                                hoveredMobTable = table.id();
                                hoveredMobX = mouseX;
                                hoveredMobY = renderY + ITEM_HEIGHT;
                            }
                            nextIconX -= 10;
                        }

                        // Feature indicator (dungeon icon for features like monster_room)
                        var featureLink = StructureLootLinker.getInstance().getLinksForLootTable(table.id()).stream()
                            .filter(StructureLootLink::isFeatureMapping)
                            .findFirst();
                        if (featureLink.isPresent()) {
                            graphics.drawString(font, "⚙", nextIconX, renderY + 4, IsotopeColors.SOURCE_FEATURE, false);
                            textRightPadding += 10;

                            // Check if hovering over feature icon
                            boolean featureHovered = mouseX >= nextIconX - 2 && mouseX < nextIconX + 10 &&
                                mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT &&
                                renderY >= listY && renderY < getY() + height;
                            if (featureHovered) {
                                hoveredFeatureTable = table.id();
                                hoveredFeatureX = mouseX;
                                hoveredFeatureY = renderY + ITEM_HEIGHT;
                            }
                        }

                        String path = table.id().getPath();
                        if (font.width(path) > width - INDENT - textRightPadding) {
                            path = font.plainSubstrByWidth(path, width - INDENT - textRightPadding - 8) + "...";
                        }
                        graphics.drawString(font, path, getX() + INDENT + 4, renderY + 4,
                            isSelected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        graphics.disableScissor();

        // Scrollbar
        ScreenUtils.renderScrollbar(graphics, getX() + width - 4, listY, 3, listHeight,
            scrollOffset, maxScroll, IsotopeColors.ENTRY_BACKGROUND, IsotopeColors.BUTTON_BACKGROUND);

        // Mod dropdown overlay (render last so it's on top)
        if (modDropdownOpen) {
            int dropY = getY() + SEARCH_HEIGHT + MOD_FILTER_HEIGHT + 6;
            int dropHeight = Math.min(availableMods.size() * 16 + 4, 200);

            graphics.fill(dropdownX - 1, dropY - 1, dropdownX + dropdownWidth + 1, dropY + dropHeight + 1, IsotopeColors.BORDER_HIGHLIGHT);
            graphics.fill(dropdownX, dropY, dropdownX + dropdownWidth, dropY + dropHeight, IsotopeColors.ENTRY_BACKGROUND);

            int itemY = dropY + 2;
            for (String mod : availableMods) {
                if (itemY + 14 > dropY + dropHeight) break;

                boolean itemHovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                    mouseY >= itemY && mouseY < itemY + 14;

                if (itemHovered) {
                    graphics.fill(dropdownX, itemY, dropdownX + dropdownWidth, itemY + 14, IsotopeColors.BORDER_DEFAULT);
                }

                String display = mod.length() > 20 ? mod.substring(0, 18) + "..." : mod;
                int color = mod.equals(selectedMod) ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
                graphics.drawString(font, display, dropdownX + 4, itemY + 2, color, false);
                itemY += 14;
            }
        }

        // Orphan tooltip (render last so it's on top)
        if (hoveredOrphanTable != null) {
            var orphanInfo = OrphanDetector.getInstance().getLootTableOrphanInfo(hoveredOrphanTable);
            if (!orphanInfo.reasons().isEmpty()) {
                List<String> lines = new ArrayList<>();
                lines.add("§eOrphan Reasons:");
                for (var reason : orphanInfo.reasons()) {
                    lines.add("§7• " + reason.getDescription());
                }
                renderTooltip(graphics, lines, hoveredOrphanX, hoveredOrphanY, TOOLTIP_ORPHAN);
            }
        }

        // Mob tooltip (player kill info)
        if (hoveredMobTable != null) {
            var entityInfo = EntityLootRegistry.getInstance().getByLootTable(hoveredMobTable);
            if (entityInfo.isPresent()) {
                var entity = entityInfo.get();
                List<String> lines = new ArrayList<>();
                lines.add("§p" + entity.displayName());
                if (entity.requiresPlayerKill()) {
                    lines.add("§7⚔ Player kill required");
                    lines.add("§7for rare drops");
                }
                renderTooltip(graphics, lines, hoveredMobX, hoveredMobY, TOOLTIP_MOB);
            }
        }

        // Feature tooltip
        if (hoveredFeatureTable != null) {
            var featureLinks = StructureLootLinker.getInstance().getLinksForLootTable(hoveredFeatureTable).stream()
                .filter(StructureLootLink::isFeatureMapping)
                .toList();
            if (!featureLinks.isEmpty()) {
                var link = featureLinks.get(0);
                var featureDef = FeatureRegistry.getInstance().get(link.structureId());
                List<String> lines = new ArrayList<>();
                if (featureDef.isPresent()) {
                    lines.add("§f" + featureDef.get().displayName());
                    lines.add("§7" + featureDef.get().description());
                } else {
                    lines.add("§fFeature: " + link.structureId().getPath());
                }
                lines.add("§7⚙ Not a tracked structure");
                renderTooltip(graphics, lines, hoveredFeatureX, hoveredFeatureY, TOOLTIP_FEATURE);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // Search box
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            setFocused(true);  // Mark widget as focused for key events
            searchBox.setFocused(true);
            return searchBox.mouseClicked(event, focused);
        } else if (searchBox != null) {
            searchBox.setFocused(false);
        }

        // If clicking elsewhere in this widget, still focus it but not the search box
        if (isMouseOver(mouseX, mouseY)) {
            setFocused(true);
        }

        int y = getY();

        // Mod dropdown click
        int dropdownX = getX() + 30;
        int dropdownWidth = width - 34;
        int dropdownY = y + SEARCH_HEIGHT + 6;

        if (modDropdownOpen) {
            // Check if clicking on dropdown item
            int dropY = getY() + SEARCH_HEIGHT + MOD_FILTER_HEIGHT + 6;
            int itemY = dropY + 2;
            for (String mod : availableMods) {
                if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                    mouseY >= itemY && mouseY < itemY + 14) {
                    selectedMod = mod;
                    modDropdownOpen = false;
                    applyFilters();
                    return true;
                }
                itemY += 14;
            }
            modDropdownOpen = false;
            return true;
        }

        // Mod dropdown button
        if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
            mouseY >= dropdownY && mouseY < dropdownY + MOD_FILTER_HEIGHT) {
            modDropdownOpen = !modDropdownOpen;
            return true;
        }

        // Category/item clicks
        int listY = getY() + SEARCH_HEIGHT + MOD_FILTER_HEIGHT + 10;
        if (mouseY < listY) return false;

        int renderY = listY - scrollOffset;

        // Bookmarks section click handling
        List<ResourceLocation> bookmarks = getFilteredBookmarks();
        if (!bookmarks.isEmpty()) {
            // Bookmarks header click
            if (mouseY >= renderY && mouseY < renderY + CATEGORY_HEIGHT) {
                bookmarksSectionExpanded = !bookmarksSectionExpanded;
                calculateMaxScroll();
                return true;
            }
            renderY += CATEGORY_HEIGHT;

            // Bookmark items if expanded
            if (bookmarksSectionExpanded) {
                for (ResourceLocation id : bookmarks) {
                    if (mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT) {
                        selectedTable = id;
                        RecentTablesManager.getInstance().recordView(id);
                        onTableSelected.accept(id);
                        return true;
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        // Recent section click handling
        List<ResourceLocation> recent = getFilteredRecent();
        if (!recent.isEmpty()) {
            // Recent header click
            if (mouseY >= renderY && mouseY < renderY + CATEGORY_HEIGHT) {
                recentSectionExpanded = !recentSectionExpanded;
                calculateMaxScroll();
                return true;
            }
            renderY += CATEGORY_HEIGHT;

            // Recent items if expanded
            if (recentSectionExpanded) {
                for (ResourceLocation id : recent) {
                    if (mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT) {
                        selectedTable = id;
                        RecentTablesManager.getInstance().recordView(id);
                        onTableSelected.accept(id);
                        return true;
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        for (LootTableCategory cat : LootTableCategory.values()) {
            int count = getCategoryCount(cat);
            if (count == 0) continue;

            // Category header click
            if (mouseY >= renderY && mouseY < renderY + CATEGORY_HEIGHT) {
                if (expandedCategories.contains(cat)) {
                    expandedCategories.remove(cat);
                } else {
                    expandedCategories.add(cat);
                }
                calculateMaxScroll();
                return true;
            }
            renderY += CATEGORY_HEIGHT;

            // Items if expanded
            if (expandedCategories.contains(cat)) {
                for (LootTableInfo table : getFilteredTablesForCategory(cat)) {
                    if (mouseY >= renderY && mouseY < renderY + ITEM_HEIGHT) {
                        // Check if clicking on star area (bookmark toggle)
                        if (mouseX >= getX() + 2 && mouseX < getX() + INDENT) {
                            BookmarkManager.getInstance().toggle(table.id());
                            return true;
                        }
                        // Otherwise select the item
                        selectedTable = table.id();
                        RecentTablesManager.getInstance().recordView(table.id());
                        onTableSelected.accept(table.id());
                        return true;
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY) && !modDropdownOpen) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        int modifiers = event.modifiers();
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.keyPressed(event);
        }
        return false;
    }

    /**
     * Focus the search box (for Ctrl+F shortcut).
     */
    public void focusSearch() {
        if (searchBox != null) {
            searchBox.setFocused(true);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    /**
     * Tooltip color scheme for different tooltip types.
     */
    private record TooltipColors(int outer, int middle, int inner, String primaryCode, int primaryColor) {}

    private static final TooltipColors TOOLTIP_ORPHAN = new TooltipColors(
        IsotopeColors.BORDER_OUTER_DARK, IsotopeColors.TOOLTIP_BACKGROUND, IsotopeColors.TOOLTIP_BACKGROUND_WARNING,
        "§e", IsotopeColors.STATUS_WARNING
    );
    private static final TooltipColors TOOLTIP_MOB = new TooltipColors(
        IsotopeColors.BORDER_OUTER_DARK, IsotopeColors.TOOLTIP_ENTITY_BG, IsotopeColors.TOOLTIP_ENTITY_INNER,
        "§p", IsotopeColors.SOURCE_MOB
    );
    private static final TooltipColors TOOLTIP_FEATURE = new TooltipColors(
        IsotopeColors.BORDER_OUTER_DARK, IsotopeColors.TOOLTIP_FEATURE_BG, IsotopeColors.TOOLTIP_FEATURE_INNER,
        "§f", IsotopeColors.SOURCE_FEATURE
    );

    /**
     * Render a styled tooltip with the given lines and colors.
     */
    private void renderTooltip(GuiGraphics graphics, List<String> lines, int anchorX, int anchorY, TooltipColors colors) {
        var font = Minecraft.getInstance().font;

        // Calculate tooltip dimensions
        int tooltipWidth = 0;
        for (String line : lines) {
            String stripped = line.replace(colors.primaryCode, "").replace("§7", "");
            tooltipWidth = Math.max(tooltipWidth, font.width(stripped));
        }
        tooltipWidth += 12;
        int tooltipHeight = lines.size() * 10 + 6;

        // Position tooltip (prefer right side, fall back to left if overflow)
        int tooltipX = anchorX + 8;
        if (tooltipX + tooltipWidth > getX() + width + 100) {
            tooltipX = anchorX - tooltipWidth - 8;
        }
        int tooltipY = anchorY;

        // Draw tooltip background (3-layer effect)
        graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, colors.outer);
        graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, colors.middle);
        graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, colors.inner);

        // Draw lines with color code parsing
        int lineY = tooltipY + 4;
        for (String line : lines) {
            int color = IsotopeColors.TEXT_PRIMARY;
            String text = line;
            if (line.startsWith(colors.primaryCode)) {
                color = colors.primaryColor;
                text = line.substring(2);
            } else if (line.startsWith("§7")) {
                color = IsotopeColors.TEXT_MUTED;
                text = line.substring(2);
            }
            graphics.drawString(font, text, tooltipX + 4, lineY, color, false);
            lineY += 10;
        }
    }
}
