package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.Consumer;

/**
 * Item picker screen for selecting items to add to loot tables.
 *
 * Shows a search box, mod filter, and grid of matching items.
 */
@Environment(EnvType.CLIENT)
public class ItemPickerScreen extends Screen {

    private static final int GRID_COLS = 9;
    private static final int ITEM_SIZE = 18;
    private static final int ITEM_PADDING = 2;
    private static final int CELL_SIZE = ITEM_SIZE + ITEM_PADDING;

    private final Screen parent;
    private final Consumer<ResourceLocation> onItemSelected;

    private EditBox searchBox;
    private List<Item> filteredItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Mod filter
    private List<String> availableMods = new ArrayList<>();
    private String selectedMod = "All";
    private boolean modDropdownOpen = false;
    private int modDropdownScroll = 0;

    // Panel dimensions
    private int panelX, panelY, panelWidth, panelHeight;
    private int gridX, gridY, gridWidth, gridHeight;
    private int modDropdownX, modDropdownY, modDropdownWidth;

    public ItemPickerScreen(Screen parent, Consumer<ResourceLocation> onItemSelected) {
        super(Component.literal("Select Item"));
        this.parent = parent;
        this.onItemSelected = onItemSelected;
        collectMods();
    }

    private void collectMods() {
        Set<String> mods = new TreeSet<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            mods.add(entry.getKey().location().getNamespace());
        }
        availableMods.add("All");
        availableMods.addAll(mods);
    }

    @Override
    protected void init() {
        // Center panel
        panelWidth = Math.min(420, width - 40);
        panelHeight = Math.min(380, height - 40);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        // Search box (left side)
        int searchWidth = panelWidth - 150;
        searchBox = new EditBox(font, panelX + 10, panelY + 30, searchWidth, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        // Mod dropdown position (right of search)
        modDropdownX = panelX + searchWidth + 20;
        modDropdownY = panelY + 30;
        modDropdownWidth = panelWidth - searchWidth - 30;

        // Grid area
        gridX = panelX + 10;
        gridY = panelY + 60;
        gridWidth = panelWidth - 20;
        gridHeight = panelHeight - 100;

        // Cancel button
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
            .bounds(panelX + panelWidth - 70, panelY + panelHeight - 30, 60, 20)
            .build());

        // Initial filter
        filterItems();

        setInitialFocus(searchBox);
    }

    private void onSearchChanged(String text) {
        filterItems();
        scrollOffset = 0;
    }

    private void filterItems() {
        filteredItems.clear();
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Item item = entry.getValue();
            ResourceLocation id = entry.getKey().location();

            // Skip air
            if (item == Items.AIR) continue;

            // Mod filter
            if (!selectedMod.equals("All") && !id.getNamespace().equals(selectedMod)) {
                continue;
            }

            // Search filter
            if (!search.isEmpty()) {
                String idStr = id.toString().toLowerCase();
                String name = new ItemStack(item).getHoverName().getString().toLowerCase();
                if (!idStr.contains(search) && !name.contains(search)) {
                    continue;
                }
            }

            filteredItems.add(item);

            // Limit for performance
            if (filteredItems.size() >= 1000) break;
        }

        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int cols = gridWidth / CELL_SIZE;
        int rows = (filteredItems.size() + cols - 1) / cols;
        int contentHeight = rows * CELL_SIZE;
        maxScroll = Math.max(0, contentHeight - gridHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF404040);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF1a1a1a);

        // Title
        graphics.drawCenteredString(font, title, panelX + panelWidth / 2, panelY + 10, IsotopeColors.TEXT_PRIMARY);

        // Render widgets (search box, buttons)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Mod dropdown button
        boolean dropdownHovered = mouseX >= modDropdownX && mouseX < modDropdownX + modDropdownWidth &&
            mouseY >= modDropdownY && mouseY < modDropdownY + 18 && !modDropdownOpen;

        graphics.fill(modDropdownX, modDropdownY, modDropdownX + modDropdownWidth, modDropdownY + 18,
            dropdownHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(modDropdownX, modDropdownY, modDropdownWidth, 18, 0xFF505050);

        String modDisplay = selectedMod;
        if (font.width(modDisplay) > modDropdownWidth - 20) {
            modDisplay = font.plainSubstrByWidth(modDisplay, modDropdownWidth - 25) + "...";
        }
        graphics.drawString(font, modDisplay, modDropdownX + 4, modDropdownY + 5, IsotopeColors.TEXT_PRIMARY, false);
        graphics.drawString(font, "▼", modDropdownX + modDropdownWidth - 12, modDropdownY + 5, IsotopeColors.TEXT_MUTED, false);

        // Grid background
        graphics.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xFF252525);

        // Item count
        String countText = filteredItems.size() + " items" + (filteredItems.size() >= 1000 ? "+" : "");
        graphics.drawString(font, countText, gridX, gridY - 10, IsotopeColors.TEXT_MUTED, false);

        // Render items with scissor
        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);

        int cols = gridWidth / CELL_SIZE;

        for (int i = 0; i < filteredItems.size(); i++) {
            int row = i / cols;
            int col = i % cols;

            int itemX = gridX + col * CELL_SIZE;
            int itemY = gridY + row * CELL_SIZE - scrollOffset;

            if (itemY + CELL_SIZE < gridY || itemY > gridY + gridHeight) continue;

            Item item = filteredItems.get(i);
            ItemStack stack = new ItemStack(item);

            // Hover highlight
            boolean hovered = mouseX >= itemX && mouseX < itemX + ITEM_SIZE &&
                             mouseY >= itemY && mouseY < itemY + ITEM_SIZE &&
                             mouseY >= gridY && mouseY < gridY + gridHeight;

            if (hovered) {
                graphics.fill(itemX - 1, itemY - 1, itemX + ITEM_SIZE + 1, itemY + ITEM_SIZE + 1, 0xFF3a5a8a);
            }

            // Render item
            graphics.renderItem(stack, itemX, itemY);
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = gridX + gridWidth - 4;
            int thumbHeight = Math.max(20, (int) ((float) gridHeight / (gridHeight + maxScroll) * gridHeight));
            int thumbY = gridY + (int) ((float) scrollOffset / maxScroll * (gridHeight - thumbHeight));

            graphics.fill(scrollbarX, gridY, scrollbarX + 3, gridY + gridHeight, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF555555);
        }

        // Mod dropdown overlay (render on top)
        if (modDropdownOpen) {
            int maxVisible = 12;
            int dropHeight = Math.min(availableMods.size(), maxVisible) * 14 + 4;

            graphics.fill(modDropdownX - 1, modDropdownY + 18, modDropdownX + modDropdownWidth + 1, modDropdownY + 18 + dropHeight + 1, 0xFF606060);
            graphics.fill(modDropdownX, modDropdownY + 18, modDropdownX + modDropdownWidth, modDropdownY + 18 + dropHeight, 0xFF2a2a2a);

            graphics.enableScissor(modDropdownX, modDropdownY + 18, modDropdownX + modDropdownWidth, modDropdownY + 18 + dropHeight);

            int itemY = modDropdownY + 20 - modDropdownScroll;
            for (String mod : availableMods) {
                if (itemY + 14 > modDropdownY + 18 && itemY < modDropdownY + 18 + dropHeight) {
                    boolean itemHovered = mouseX >= modDropdownX && mouseX < modDropdownX + modDropdownWidth &&
                        mouseY >= itemY && mouseY < itemY + 14;

                    if (itemHovered) {
                        graphics.fill(modDropdownX, itemY, modDropdownX + modDropdownWidth, itemY + 14, 0xFF404040);
                    }

                    String display = mod;
                    if (font.width(display) > modDropdownWidth - 8) {
                        display = font.plainSubstrByWidth(display, modDropdownWidth - 14) + "...";
                    }
                    int color = mod.equals(selectedMod) ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_PRIMARY;
                    graphics.drawString(font, display, modDropdownX + 4, itemY + 2, color, false);
                }
                itemY += 14;
            }

            graphics.disableScissor();
        }

        // Tooltip for hovered item (render last)
        if (!modDropdownOpen) {
            int cols2 = gridWidth / CELL_SIZE;
            for (int i = 0; i < filteredItems.size(); i++) {
                int row = i / cols2;
                int col = i % cols2;
                int itemX = gridX + col * CELL_SIZE;
                int itemY = gridY + row * CELL_SIZE - scrollOffset;

                if (itemY + CELL_SIZE < gridY || itemY > gridY + gridHeight) continue;

                boolean hovered = mouseX >= itemX && mouseX < itemX + ITEM_SIZE &&
                                 mouseY >= itemY && mouseY < itemY + ITEM_SIZE &&
                                 mouseY >= gridY && mouseY < gridY + gridHeight;

                if (hovered) {
                    Item item = filteredItems.get(i);
                    ItemStack stack = new ItemStack(item);
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Mod dropdown
        if (modDropdownOpen) {
            int maxVisible = 12;
            int dropHeight = Math.min(availableMods.size(), maxVisible) * 14 + 4;

            if (mouseX >= modDropdownX && mouseX < modDropdownX + modDropdownWidth &&
                mouseY >= modDropdownY + 18 && mouseY < modDropdownY + 18 + dropHeight) {

                int itemY = modDropdownY + 20 - modDropdownScroll;
                for (String mod : availableMods) {
                    if (mouseY >= itemY && mouseY < itemY + 14) {
                        selectedMod = mod;
                        modDropdownOpen = false;
                        filterItems();
                        scrollOffset = 0;
                        return true;
                    }
                    itemY += 14;
                }
            }
            modDropdownOpen = false;
            return true;
        }

        // Mod dropdown button
        if (mouseX >= modDropdownX && mouseX < modDropdownX + modDropdownWidth &&
            mouseY >= modDropdownY && mouseY < modDropdownY + 18) {
            modDropdownOpen = !modDropdownOpen;
            modDropdownScroll = 0;
            return true;
        }

        // Grid clicks
        if (mouseX >= gridX && mouseX < gridX + gridWidth &&
            mouseY >= gridY && mouseY < gridY + gridHeight) {

            int cols = gridWidth / CELL_SIZE;
            int col = (int) (mouseX - gridX) / CELL_SIZE;
            int row = (int) (mouseY - gridY + scrollOffset) / CELL_SIZE;
            int index = row * cols + col;

            if (index >= 0 && index < filteredItems.size()) {
                Item item = filteredItems.get(index);
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                onItemSelected.accept(id);
                onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Mod dropdown scroll
        if (modDropdownOpen && mouseX >= modDropdownX && mouseX < modDropdownX + modDropdownWidth) {
            int maxVisible = 12;
            int maxDropScroll = Math.max(0, (availableMods.size() - maxVisible) * 14);
            modDropdownScroll = Math.max(0, Math.min(maxDropScroll, modDropdownScroll - (int) (scrollY * 28)));
            return true;
        }

        // Grid scroll
        if (mouseX >= gridX && mouseX < gridX + gridWidth &&
            mouseY >= gridY && mouseY < gridY + gridHeight) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 30)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            if (modDropdownOpen) {
                modDropdownOpen = false;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
