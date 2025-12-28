package dev.isotope.ui.widget;

import dev.isotope.search.SearchHit;
import dev.isotope.search.SearchIndex;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.isotope.Isotope;
import dev.isotope.registry.LootTableRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Global search widget for searching items across all loot tables.
 */
@Environment(EnvType.CLIENT)
public class GlobalSearchWidget extends AbstractWidget {

    private static final int SEARCH_HEIGHT = 24;
    private static final int FILTER_HEIGHT = 16;
    private static final int RESULT_HEIGHT = 20;
    private static final int MAX_VISIBLE_RESULTS = 10;

    private EditBox searchBox;
    private List<SearchHit> results = new ArrayList<>();
    private List<SearchHit> filteredResults = new ArrayList<>();
    private int scrollOffset = 0;
    private int hoveredResult = -1;

    // Mod filter
    private String selectedNamespace = null; // null = all
    private List<String> availableNamespaces = new ArrayList<>();
    private int hoveredNamespace = -1;
    private boolean showNamespaceDropdown = false;

    private final Consumer<ResourceLocation> onTableSelected;

    public GlobalSearchWidget(int x, int y, int width, int height, Consumer<ResourceLocation> onTableSelected) {
        super(x, y, width, height, Component.literal("Global Search"));
        this.onTableSelected = onTableSelected;

        // Populate available namespaces from all loot tables
        availableNamespaces = new ArrayList<>(LootTableRegistry.getInstance().getNamespaces());
        availableNamespaces.sort((a, b) -> {
            if ("minecraft".equals(a)) return -1;
            if ("minecraft".equals(b)) return 1;
            return a.compareTo(b);
        });
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF1a1a1a);
        graphics.renderOutline(getX(), getY(), width, height, 0xFF333333);

        // Title
        graphics.drawString(font, "Global Search", getX() + 8, getY() + 8, IsotopeColors.ACCENT_GOLD, false);

        // Search box
        int searchY = getY() + 24;
        if (searchBox == null) {
            searchBox = new EditBox(font, getX() + 8, searchY, width - 16, SEARCH_HEIGHT - 4,
                Component.literal("Search"));
            searchBox.setHint(Component.literal("Search items..."));
            searchBox.setBordered(true);
            searchBox.setResponder(this::onSearch);
        }
        searchBox.setX(getX() + 8);
        searchBox.setY(searchY);
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // Mod filter
        int filterY = searchY + SEARCH_HEIGHT + 2;
        String filterLabel = selectedNamespace == null ? "All Mods" : selectedNamespace;
        int filterWidth = font.width(filterLabel) + 16;

        boolean filterHovered = mouseX >= getX() + 8 && mouseX < getX() + 8 + filterWidth &&
            mouseY >= filterY && mouseY < filterY + FILTER_HEIGHT;

        graphics.fill(getX() + 8, filterY, getX() + 8 + filterWidth, filterY + FILTER_HEIGHT,
            filterHovered ? 0xFF3a3a3a : 0xFF2a2a2a);
        graphics.renderOutline(getX() + 8, filterY, filterWidth, FILTER_HEIGHT, 0xFF404040);
        graphics.drawString(font, filterLabel, getX() + 12, filterY + 4, IsotopeColors.TEXT_PRIMARY, false);
        graphics.drawString(font, "▼", getX() + 8 + filterWidth - 10, filterY + 4, IsotopeColors.TEXT_MUTED, false);

        // Namespace dropdown (if open)
        if (showNamespaceDropdown && !availableNamespaces.isEmpty()) {
            renderNamespaceDropdown(graphics, font, mouseX, mouseY, filterY + FILTER_HEIGHT);
        }

        // Results area
        int resultsY = filterY + FILTER_HEIGHT + 4;
        int resultsHeight = height - (resultsY - getY()) - 8;
        int visibleCount = Math.min(filteredResults.size() - scrollOffset, resultsHeight / RESULT_HEIGHT);

        graphics.enableScissor(getX() + 4, resultsY, getX() + width - 4, getY() + height - 4);

        hoveredResult = -1;
        for (int i = 0; i < visibleCount; i++) {
            int index = scrollOffset + i;
            if (index >= filteredResults.size()) break;

            SearchHit hit = filteredResults.get(index);
            int y = resultsY + i * RESULT_HEIGHT;

            boolean hovered = mouseX >= getX() + 8 && mouseX < getX() + width - 8 &&
                mouseY >= y && mouseY < y + RESULT_HEIGHT;

            if (hovered) {
                hoveredResult = index;
                graphics.fill(getX() + 4, y, getX() + width - 4, y + RESULT_HEIGHT, 0xFF2a3a4a);
            }

            // Table icon
            graphics.drawString(font, "\u25A0", getX() + 10, y + 6, IsotopeColors.TEXT_MUTED, false);

            // Table path
            String tablePath = hit.table().getPath();
            if (font.width(tablePath) > width - 40) {
                tablePath = font.plainSubstrByWidth(tablePath, width - 50) + "...";
            }
            graphics.drawString(font, tablePath, getX() + 22, y + 2, IsotopeColors.TEXT_PRIMARY, false);

            // Context (pool/entry info)
            String context = hit.context();
            if (font.width(context) > width - 30) {
                context = font.plainSubstrByWidth(context, width - 40) + "...";
            }
            graphics.drawString(font, context, getX() + 22, y + 11, IsotopeColors.TEXT_MUTED, false);
        }

        graphics.disableScissor();

        // Result count or empty state
        if (!filteredResults.isEmpty()) {
            String countText = filteredResults.size() + " result" + (filteredResults.size() > 1 ? "s" : "");
            if (selectedNamespace != null) {
                countText += " in " + selectedNamespace;
            }
            graphics.drawString(font, countText, getX() + width - font.width(countText) - 8,
                getY() + height - 14, IsotopeColors.TEXT_MUTED, false);
        } else if (searchBox != null && !searchBox.getValue().isEmpty()) {
            // No results for search query
            graphics.drawString(font, "No results found", getX() + 8, resultsY + 10,
                IsotopeColors.TEXT_MUTED, false);
            if (selectedNamespace != null) {
                graphics.drawString(font, "Try 'All Mods' or different term", getX() + 8, resultsY + 22,
                    IsotopeColors.TEXT_MUTED, false);
            } else {
                graphics.drawString(font, "Try a different search term", getX() + 8, resultsY + 22,
                    IsotopeColors.TEXT_MUTED, false);
            }
        } else {
            // Empty search - show hint
            graphics.drawString(font, "Search for items by name or ID", getX() + 8, resultsY + 10,
                IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, "e.g. 'diamond', 'iron_ingot'", getX() + 8, resultsY + 22,
                IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, "Use filter to search by mod", getX() + 8, resultsY + 36,
                IsotopeColors.TEXT_MUTED, false);
        }

        // Scrollbar
        if (filteredResults.size() > visibleCount) {
            int scrollbarX = getX() + width - 6;
            int scrollbarHeight = resultsHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleCount / filteredResults.size());
            int thumbY = resultsY + (scrollbarHeight - thumbHeight) * scrollOffset /
                Math.max(1, filteredResults.size() - visibleCount);

            graphics.fill(scrollbarX, resultsY, scrollbarX + 4, resultsY + scrollbarHeight, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF555555);
        }
    }

    private void renderNamespaceDropdown(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY) {
        int dropdownWidth = 120;
        int itemHeight = 14;
        int maxItems = Math.min(availableNamespaces.size() + 1, 8); // +1 for "All Mods"
        int dropdownHeight = maxItems * itemHeight + 4;

        // Dropdown background
        graphics.fill(getX() + 8, startY, getX() + 8 + dropdownWidth, startY + dropdownHeight, 0xFF252525);
        graphics.renderOutline(getX() + 8, startY, dropdownWidth, dropdownHeight, 0xFF404040);

        hoveredNamespace = -1;
        int y = startY + 2;

        // "All Mods" option
        boolean allHovered = mouseX >= getX() + 8 && mouseX < getX() + 8 + dropdownWidth &&
            mouseY >= y && mouseY < y + itemHeight;
        if (allHovered) {
            hoveredNamespace = -1; // Special value for "All"
            graphics.fill(getX() + 10, y, getX() + 8 + dropdownWidth - 2, y + itemHeight, 0xFF3a3a3a);
        }
        int textColor = selectedNamespace == null ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
        graphics.drawString(font, "All Mods", getX() + 12, y + 3, textColor, false);
        y += itemHeight;

        // Namespace options
        for (int i = 0; i < Math.min(availableNamespaces.size(), maxItems - 1); i++) {
            String ns = availableNamespaces.get(i);
            boolean hovered = mouseX >= getX() + 8 && mouseX < getX() + 8 + dropdownWidth &&
                mouseY >= y && mouseY < y + itemHeight;
            if (hovered) {
                hoveredNamespace = i;
                graphics.fill(getX() + 10, y, getX() + 8 + dropdownWidth - 2, y + itemHeight, 0xFF3a3a3a);
            }
            textColor = ns.equals(selectedNamespace) ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
            String displayNs = font.width(ns) > dropdownWidth - 10 ? font.plainSubstrByWidth(ns, dropdownWidth - 15) + ".." : ns;
            graphics.drawString(font, displayNs, getX() + 12, y + 3, textColor, false);
            y += itemHeight;
        }
    }

    private void onSearch(String query) {
        scrollOffset = 0;
        if (query.isBlank()) {
            results = new ArrayList<>();
        } else {
            results = new ArrayList<>(SearchIndex.getInstance().search(query));
        }

        // Extract available namespaces from results
        availableNamespaces = results.stream()
            .map(hit -> hit.table().getNamespace())
            .distinct()
            .sorted()
            .toList();

        applyFilter();
    }

    private void applyFilter() {
        if (selectedNamespace == null) {
            filteredResults = new ArrayList<>(results);
        } else {
            filteredResults = results.stream()
                .filter(hit -> hit.table().getNamespace().equals(selectedNamespace))
                .toList();
        }
        scrollOffset = 0;
    }

    /**
     * Focus the search box.
     */
    public void focusSearch() {
        // Focus both the widget (for key event routing) and the search box
        setFocused(true);

        // Create searchBox if it doesn't exist yet (called before first render)
        if (searchBox == null) {
            Font font = Minecraft.getInstance().font;
            int searchY = getY() + 24;
            searchBox = new EditBox(font, getX() + 8, searchY, width - 16, SEARCH_HEIGHT - 4,
                Component.literal("Search"));
            searchBox.setHint(Component.literal("Search items..."));
            searchBox.setBordered(true);
            searchBox.setResponder(this::onSearch);
        }

        searchBox.setFocused(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            showNamespaceDropdown = false;
            setFocused(false);
            if (searchBox != null) searchBox.setFocused(false);
            return false;
        }

        // Mark this widget as focused so it receives key events
        setFocused(true);

        // Check search box click
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            showNamespaceDropdown = false;
            searchBox.setFocused(true);
            searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        } else if (searchBox != null) {
            searchBox.setFocused(false);
        }

        // Check filter button click
        Font font = Minecraft.getInstance().font;
        int filterY = getY() + 24 + SEARCH_HEIGHT + 2;
        String filterLabel = selectedNamespace == null ? "All Mods" : selectedNamespace;
        int filterWidth = font.width(filterLabel) + 16;

        if (mouseX >= getX() + 8 && mouseX < getX() + 8 + filterWidth &&
            mouseY >= filterY && mouseY < filterY + FILTER_HEIGHT) {
            showNamespaceDropdown = !showNamespaceDropdown;
            return true;
        }

        // Check dropdown selection
        if (showNamespaceDropdown) {
            int dropdownWidth = 120;
            int itemHeight = 14;
            int dropdownStartY = filterY + FILTER_HEIGHT;

            if (mouseX >= getX() + 8 && mouseX < getX() + 8 + dropdownWidth) {
                int relativeY = (int) mouseY - dropdownStartY - 2;
                int clickedIndex = relativeY / itemHeight;

                if (clickedIndex == 0) {
                    // "All Mods" selected
                    selectedNamespace = null;
                    applyFilter();
                    showNamespaceDropdown = false;
                    return true;
                } else if (clickedIndex > 0 && clickedIndex <= availableNamespaces.size()) {
                    selectedNamespace = availableNamespaces.get(clickedIndex - 1);
                    applyFilter();
                    showNamespaceDropdown = false;
                    return true;
                }
            }
            showNamespaceDropdown = false;
            return true;
        }

        // Check result click
        if (hoveredResult >= 0 && hoveredResult < filteredResults.size()) {
            SearchHit hit = filteredResults.get(hoveredResult);
            onTableSelected.accept(hit.table());
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int maxScroll = Math.max(0, filteredResults.size() - MAX_VISIBLE_RESULTS);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (searchBox != null) {
            return searchBox.charTyped(c, modifiers);
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

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
