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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Global search widget for searching items across all loot tables.
 */
@Environment(EnvType.CLIENT)
public class GlobalSearchWidget extends AbstractWidget {

    private static final int SEARCH_HEIGHT = 24;
    private static final int RESULT_HEIGHT = 20;
    private static final int MAX_VISIBLE_RESULTS = 10;

    private EditBox searchBox;
    private List<SearchHit> results = new ArrayList<>();
    private int scrollOffset = 0;
    private int hoveredResult = -1;

    private final Consumer<ResourceLocation> onTableSelected;

    public GlobalSearchWidget(int x, int y, int width, int height, Consumer<ResourceLocation> onTableSelected) {
        super(x, y, width, height, Component.literal("Global Search"));
        this.onTableSelected = onTableSelected;
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

        // Results area
        int resultsY = searchY + SEARCH_HEIGHT + 4;
        int resultsHeight = height - (resultsY - getY()) - 8;
        int visibleCount = Math.min(results.size() - scrollOffset, resultsHeight / RESULT_HEIGHT);

        graphics.enableScissor(getX() + 4, resultsY, getX() + width - 4, getY() + height - 4);

        hoveredResult = -1;
        for (int i = 0; i < visibleCount; i++) {
            int index = scrollOffset + i;
            if (index >= results.size()) break;

            SearchHit hit = results.get(index);
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

        // Result count
        if (!results.isEmpty()) {
            String countText = results.size() + " result" + (results.size() > 1 ? "s" : "");
            graphics.drawString(font, countText, getX() + width - font.width(countText) - 8,
                getY() + height - 14, IsotopeColors.TEXT_MUTED, false);
        } else if (searchBox != null && !searchBox.getValue().isEmpty()) {
            graphics.drawString(font, "No results", getX() + 8, resultsY,
                IsotopeColors.TEXT_MUTED, false);
        }

        // Scrollbar
        if (results.size() > visibleCount) {
            int scrollbarX = getX() + width - 6;
            int scrollbarHeight = resultsHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleCount / results.size());
            int thumbY = resultsY + (scrollbarHeight - thumbHeight) * scrollOffset /
                Math.max(1, results.size() - visibleCount);

            graphics.fill(scrollbarX, resultsY, scrollbarX + 4, resultsY + scrollbarHeight, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF555555);
        }
    }

    private void onSearch(String query) {
        scrollOffset = 0;
        if (query.isBlank()) {
            results = new ArrayList<>();
        } else {
            results = new ArrayList<>(SearchIndex.getInstance().search(query));
        }
    }

    /**
     * Focus the search box.
     */
    public void focusSearch() {
        if (searchBox != null) {
            searchBox.setFocused(true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        // Check search box click
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        // Check result click
        if (hoveredResult >= 0 && hoveredResult < results.size()) {
            SearchHit hit = results.get(hoveredResult);
            onTableSelected.accept(hit.table());
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int maxScroll = Math.max(0, results.size() - MAX_VISIBLE_RESULTS);
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
