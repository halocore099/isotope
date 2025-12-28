package dev.isotope.ui.widget;

import dev.isotope.data.BookmarkManager;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Bookmark listener
    private final BookmarkManager.BookmarkListener bookmarkListener = this::onBookmarksChanged;

    // Data
    private List<String> availableMods = new ArrayList<>();
    private Map<LootTableCategory, List<LootTableInfo>> tablesByCategory = new LinkedHashMap<>();
    private List<LootTableInfo> filteredTables = new ArrayList<>();

    // Selection
    @Nullable
    private ResourceLocation selectedTable;

    // Mod dropdown state
    private boolean modDropdownOpen = false;

    public LootTableBrowserWidget(int x, int y, int width, int height, Consumer<ResourceLocation> onTableSelected) {
        super(x, y, width, height, Component.empty());
        this.onTableSelected = onTableSelected;

        // Expand common categories by default
        expandedCategories.add(LootTableCategory.CHEST);
        expandedCategories.add(LootTableCategory.ENTITY);
    }

    public void loadData() {
        // Register bookmark listener
        BookmarkManager.getInstance().addListener(bookmarkListener);

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

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1e1e1e);

        int y = getY();

        // Search box area
        graphics.fill(getX(), y, getX() + width, y + SEARCH_HEIGHT + 4, 0xFF252525);
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
        graphics.fill(getX(), y, getX() + width, y + MOD_FILTER_HEIGHT + 4, 0xFF252525);
        graphics.drawString(font, "Mod:", getX() + 4, y + 6, IsotopeColors.TEXT_MUTED, false);

        // Mod dropdown button
        int dropdownX = getX() + 30;
        int dropdownWidth = width - 34;
        boolean dropdownHovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
            mouseY >= y + 2 && mouseY < y + MOD_FILTER_HEIGHT + 2;

        graphics.fill(dropdownX, y + 2, dropdownX + dropdownWidth, y + MOD_FILTER_HEIGHT + 2,
            dropdownHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(dropdownX, y + 2, dropdownWidth, MOD_FILTER_HEIGHT, 0xFF505050);

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
                graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, 0xFF2a2520);
                if (catHovered) {
                    graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, 0xFF3a3530);
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
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, 0xFF5a4a2a);
                        } else if (itemHovered) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, 0xFF3a3020);
                        }

                        // Star icon
                        graphics.drawString(font, "★", getX() + INDENT - 8, renderY + 4, IsotopeColors.ACCENT_GOLD, false);

                        String path = id.getPath();
                        if (font.width(path) > width - INDENT - 16) {
                            path = font.plainSubstrByWidth(path, width - INDENT - 24) + "...";
                        }
                        graphics.drawString(font, path, getX() + INDENT + 4, renderY + 4,
                            isSelected ? 0xFFFFFFFF : IsotopeColors.TEXT_SECONDARY, false);
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
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
                    graphics.fill(getX(), renderY, getX() + width, renderY + CATEGORY_HEIGHT, 0xFF303030);
                }

                String arrow = expanded ? "▼" : "▶";
                graphics.drawString(font, arrow, getX() + 4, renderY + 4, IsotopeColors.TEXT_MUTED, false);
                String catName = cat.name().charAt(0) + cat.name().substring(1).toLowerCase();
                graphics.drawString(font, catName, getX() + 16, renderY + 4,
                    IsotopeColors.TEXT_PRIMARY, false);
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
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, 0xFF3a5a8a);
                        } else if (itemHovered || starHovered) {
                            graphics.fill(getX(), renderY, getX() + width, renderY + ITEM_HEIGHT, 0xFF353535);
                        }

                        // Bookmark star (click target)
                        String star = isBookmarked ? "★" : (starHovered ? "☆" : "");
                        if (!star.isEmpty()) {
                            int starColor = isBookmarked ? IsotopeColors.ACCENT_GOLD : 0xFF666666;
                            graphics.drawString(font, star, getX() + 3, renderY + 4, starColor, false);
                        }

                        String path = table.id().getPath();
                        if (font.width(path) > width - INDENT - 8) {
                            path = font.plainSubstrByWidth(path, width - INDENT - 16) + "...";
                        }
                        graphics.drawString(font, path, getX() + INDENT + 4, renderY + 4,
                            isSelected ? 0xFFFFFFFF : IsotopeColors.TEXT_SECONDARY, false);
                    }
                    renderY += ITEM_HEIGHT;
                }
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = getX() + width - 4;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, (int) ((float) listHeight / (listHeight + maxScroll) * scrollbarHeight));
            int thumbY = listY + (int) ((float) scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

            graphics.fill(scrollbarX, listY, scrollbarX + 3, listY + scrollbarHeight, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF555555);
        }

        // Mod dropdown overlay (render last so it's on top)
        if (modDropdownOpen) {
            int dropY = getY() + SEARCH_HEIGHT + MOD_FILTER_HEIGHT + 6;
            int dropHeight = Math.min(availableMods.size() * 16 + 4, 200);

            graphics.fill(dropdownX - 1, dropY - 1, dropdownX + dropdownWidth + 1, dropY + dropHeight + 1, 0xFF606060);
            graphics.fill(dropdownX, dropY, dropdownX + dropdownWidth, dropY + dropHeight, 0xFF2a2a2a);

            int itemY = dropY + 2;
            for (String mod : availableMods) {
                if (itemY + 14 > dropY + dropHeight) break;

                boolean itemHovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                    mouseY >= itemY && mouseY < itemY + 14;

                if (itemHovered) {
                    graphics.fill(dropdownX, itemY, dropdownX + dropdownWidth, itemY + 14, 0xFF404040);
                }

                String display = mod.length() > 20 ? mod.substring(0, 18) + "..." : mod;
                int color = mod.equals(selectedMod) ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
                graphics.drawString(font, display, dropdownX + 4, itemY + 2, color, false);
                itemY += 14;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Search box
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            setFocused(true);  // Mark widget as focused for key events
            searchBox.setFocused(true);
            return searchBox.mouseClicked(mouseX, mouseY, button);
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
    public boolean charTyped(char chr, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(chr, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
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
}
