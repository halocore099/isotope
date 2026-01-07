package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * VS Code-style command palette for quick action access.
 *
 * Activated with Ctrl+P, shows a searchable list of all available commands.
 */
@Environment(EnvType.CLIENT)
public class CommandPalette extends AbstractWidget {

    private static final int PALETTE_WIDTH = 400;
    private static final int PALETTE_HEIGHT = 320;
    private static final int ITEM_HEIGHT = 24;
    private static final int MAX_VISIBLE_ITEMS = 10;
    private static final int HEADER_HEIGHT = 32;

    private final List<Command> allCommands = new ArrayList<>();
    private final List<Command> filteredCommands = new ArrayList<>();

    private EditBox searchBox;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    @Nullable
    private Consumer<Void> onClose;

    public CommandPalette(int screenWidth, int screenHeight) {
        super(
            (screenWidth - PALETTE_WIDTH) / 2,
            80, // Fixed distance from top
            PALETTE_WIDTH,
            PALETTE_HEIGHT,
            Component.literal("Command Palette")
        );

        // Create search box
        searchBox = new EditBox(
            Minecraft.getInstance().font,
            getX() + 8,
            getY() + 8,
            PALETTE_WIDTH - 16,
            18,
            Component.literal("Search commands...")
        );
        searchBox.setHint(Component.literal("Type to search commands..."));
        searchBox.setBordered(true);
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setFocused(true);
    }

    /**
     * Register a command in the palette.
     */
    public CommandPalette addCommand(String id, String icon, String label, String shortcut, Runnable action) {
        allCommands.add(new Command(id, icon, label, shortcut, action));
        updateFilteredCommands("");
        return this;
    }

    /**
     * Set callback for when palette is closed.
     */
    public CommandPalette onClose(Consumer<Void> callback) {
        this.onClose = callback;
        return this;
    }

    /**
     * Focus the search box.
     */
    public void focus() {
        searchBox.setFocused(true);
        searchBox.setValue("");
        selectedIndex = 0;
        scrollOffset = 0;
        updateFilteredCommands("");
    }

    /**
     * Reset state when shown.
     */
    public void reset() {
        searchBox.setValue("");
        selectedIndex = 0;
        scrollOffset = 0;
        updateFilteredCommands("");
    }

    private void onSearchChanged(String query) {
        updateFilteredCommands(query);
        selectedIndex = 0;
        scrollOffset = 0;
    }

    private void updateFilteredCommands(String query) {
        filteredCommands.clear();
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();

        if (lowerQuery.isEmpty()) {
            filteredCommands.addAll(allCommands);
        } else {
            for (Command cmd : allCommands) {
                if (cmd.label().toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                    cmd.id().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    filteredCommands.add(cmd);
                }
            }
        }
    }

    private void executeSelected() {
        if (selectedIndex >= 0 && selectedIndex < filteredCommands.size()) {
            Command cmd = filteredCommands.get(selectedIndex);
            close();
            cmd.action().run();
        }
    }

    private void close() {
        if (onClose != null) {
            onClose.accept(null);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background with shadow
        int shadowOffset = 4;
        graphics.fill(getX() + shadowOffset, getY() + shadowOffset,
            getX() + width + shadowOffset, getY() + height + shadowOffset,
            0x80000000);

        // Main background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_SOLID);

        // Border
        renderBorder(graphics, getX(), getY(), width, height);

        // Render search box
        searchBox.setX(getX() + 8);
        searchBox.setY(getY() + 8);
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // Separator line
        int separatorY = getY() + HEADER_HEIGHT;
        graphics.fill(getX() + 1, separatorY, getX() + width - 1, separatorY + 1, IsotopeColors.BORDER_INNER);

        // Render command list
        int listY = separatorY + 4;
        int listHeight = height - HEADER_HEIGHT - 8;
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, filteredCommands.size());

        // Update hovered item based on mouse position
        int hoveredIndex = -1;
        if (mouseX >= getX() && mouseX < getX() + width &&
            mouseY >= listY && mouseY < listY + visibleItems * ITEM_HEIGHT) {
            hoveredIndex = (mouseY - listY) / ITEM_HEIGHT + scrollOffset;
        }

        // Render visible commands
        for (int i = 0; i < visibleItems && (i + scrollOffset) < filteredCommands.size(); i++) {
            int index = i + scrollOffset;
            Command cmd = filteredCommands.get(index);
            int itemY = listY + i * ITEM_HEIGHT;

            boolean isSelected = (index == selectedIndex);
            boolean isHovered = (index == hoveredIndex);

            // Background
            if (isSelected) {
                graphics.fill(getX() + 4, itemY, getX() + width - 4, itemY + ITEM_HEIGHT - 2,
                    IsotopeColors.withAlpha(IsotopeColors.ACCENT_GOLD, 0x40));
                // Left highlight bar
                graphics.fill(getX() + 4, itemY, getX() + 6, itemY + ITEM_HEIGHT - 2,
                    IsotopeColors.ACCENT_GOLD);
            } else if (isHovered) {
                graphics.fill(getX() + 4, itemY, getX() + width - 4, itemY + ITEM_HEIGHT - 2,
                    IsotopeColors.withAlpha(IsotopeColors.TEXT_PRIMARY, 0x15));
            }

            // Icon
            graphics.drawString(
                Minecraft.getInstance().font,
                cmd.icon(),
                getX() + 12,
                itemY + 6,
                isSelected ? IsotopeColors.ACCENT_GOLD : IsotopeColors.TEXT_SECONDARY,
                false
            );

            // Label
            graphics.drawString(
                Minecraft.getInstance().font,
                cmd.label(),
                getX() + 28,
                itemY + 6,
                isSelected ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY,
                false
            );

            // Shortcut (right-aligned)
            if (!cmd.shortcut().isEmpty()) {
                int shortcutWidth = Minecraft.getInstance().font.width(cmd.shortcut());
                graphics.drawString(
                    Minecraft.getInstance().font,
                    cmd.shortcut(),
                    getX() + width - shortcutWidth - 12,
                    itemY + 6,
                    IsotopeColors.TEXT_MUTED,
                    false
                );
            }
        }

        // "No results" message
        if (filteredCommands.isEmpty()) {
            String noResults = "No matching commands";
            int textWidth = Minecraft.getInstance().font.width(noResults);
            graphics.drawString(
                Minecraft.getInstance().font,
                noResults,
                getX() + (width - textWidth) / 2,
                listY + 20,
                IsotopeColors.TEXT_MUTED,
                false
            );
        }

        // Scrollbar (if needed)
        if (filteredCommands.size() > MAX_VISIBLE_ITEMS) {
            int scrollbarX = getX() + width - 6;
            int scrollbarHeight = listHeight - 4;
            int thumbHeight = Math.max(20, scrollbarHeight * MAX_VISIBLE_ITEMS / filteredCommands.size());
            int thumbY = listY + (scrollbarHeight - thumbHeight) * scrollOffset / (filteredCommands.size() - MAX_VISIBLE_ITEMS);

            // Track
            graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + scrollbarHeight,
                IsotopeColors.SCROLLBAR_TRACK);
            // Thumb
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight,
                IsotopeColors.SCROLLBAR_THUMB);
        }

        // Hint at bottom
        String hint = "↑↓ Navigate  Enter Select  Esc Close";
        int hintWidth = Minecraft.getInstance().font.width(hint);
        graphics.drawString(
            Minecraft.getInstance().font,
            hint,
            getX() + (width - hintWidth) / 2,
            getY() + height - 14,
            IsotopeColors.TEXT_MUTED,
            false
        );
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        // Outer border
        graphics.fill(x, y, x + w, y + 1, IsotopeColors.BORDER_OUTER_LIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(x, y, x + 1, y + h, IsotopeColors.BORDER_OUTER_LIGHT);
        graphics.fill(x + w - 1, y, x + w, y + h, IsotopeColors.BORDER_OUTER_DARK);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if click is outside palette
        if (!isMouseOver(mouseX, mouseY)) {
            close();
            return true;
        }

        // Handle search box click
        if (searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        // Handle command click
        int listY = getY() + HEADER_HEIGHT + 4;
        if (mouseY >= listY && button == 0) {
            int clickedIndex = (int) ((mouseY - listY) / ITEM_HEIGHT) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredCommands.size()) {
                selectedIndex = clickedIndex;
                executeSelected();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape to close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        // Enter to execute
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeSelected();
            return true;
        }

        // Arrow up
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                ensureVisible();
            }
            return true;
        }

        // Arrow down
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (selectedIndex < filteredCommands.size() - 1) {
                selectedIndex++;
                ensureVisible();
            }
            return true;
        }

        // Let search box handle other keys
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return searchBox.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int maxScroll = Math.max(0, filteredCommands.size() - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
        return true;
    }

    private void ensureVisible() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Component.literal("Command Palette"));
    }

    /**
     * Represents a command in the palette.
     */
    public record Command(String id, String icon, String label, String shortcut, Runnable action) {}
}
