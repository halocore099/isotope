package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Overlay screen showing all keyboard shortcuts.
 * Opened with F1.
 */
@Environment(EnvType.CLIENT)
public class ShortcutsScreen extends Screen {

    private static final int DIALOG_WIDTH = 320;
    private static final int DIALOG_HEIGHT = 380;

    private final Screen parent;

    // Shortcuts to display
    private static final String[][] SHORTCUTS = {
        // General
        {"F1", "Show this help"},
        {"Escape", "Close overlay / Clear selection"},
        {"Ctrl+F", "Focus browser search"},
        {"Ctrl+Shift+F", "Global search across all tables"},

        // Edit operations
        {"Ctrl+Z", "Undo last change"},
        {"Ctrl+Y", "Redo last change"},
        {"Ctrl+S", "Export as datapack"},

        // Entry operations
        {"Ctrl+N", "Add new item to selected pool"},
        {"Delete", "Remove selected entry(s)"},
        {"Ctrl+D", "Duplicate selected entry"},
        {"Ctrl+C", "Copy selected entry"},
        {"Ctrl+V", "Paste entry from clipboard"},

        // Multi-selection
        {"Ctrl+Click", "Toggle entry selection"},
        {"Shift+Click", "Select range of entries"},

        // Navigation
        {"Tab", "Next field"},
        {"Shift+Tab", "Previous field"},
    };

    public ShortcutsScreen(Screen parent) {
        super(Component.literal("Keyboard Shortcuts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .pos(dialogX + DIALOG_WIDTH / 2 - 40, dialogY + DIALOG_HEIGHT - 30)
            .size(80, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background with border
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, 0xFF333333);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title
        graphics.drawCenteredString(font, "Keyboard Shortcuts", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Shortcuts list
        int y = dialogY + 30;
        int keyX = dialogX + 20;
        int descX = dialogX + 130;

        // Section: General
        graphics.drawString(font, "General", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 14;

        for (int i = 0; i < 4; i++) {
            renderShortcut(graphics, keyX, y, SHORTCUTS[i][0], SHORTCUTS[i][1]);
            y += 12;
        }

        y += 8;
        graphics.drawString(font, "Editing", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 14;

        for (int i = 4; i < 7; i++) {
            renderShortcut(graphics, keyX, y, SHORTCUTS[i][0], SHORTCUTS[i][1]);
            y += 12;
        }

        y += 8;
        graphics.drawString(font, "Entry Operations", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 14;

        for (int i = 7; i < 12; i++) {
            renderShortcut(graphics, keyX, y, SHORTCUTS[i][0], SHORTCUTS[i][1]);
            y += 12;
        }

        y += 8;
        graphics.drawString(font, "Multi-Selection", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 14;

        for (int i = 12; i < 14; i++) {
            renderShortcut(graphics, keyX, y, SHORTCUTS[i][0], SHORTCUTS[i][1]);
            y += 12;
        }

        y += 8;
        graphics.drawString(font, "Navigation", dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 14;

        for (int i = 14; i < SHORTCUTS.length; i++) {
            renderShortcut(graphics, keyX, y, SHORTCUTS[i][0], SHORTCUTS[i][1]);
            y += 12;
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderShortcut(GuiGraphics graphics, int x, int y, String key, String description) {
        // Key badge
        int keyWidth = font.width(key) + 8;
        graphics.fill(x, y - 1, x + keyWidth, y + 10, 0xFF2a2a2a);
        graphics.renderOutline(x, y - 1, keyWidth, 11, 0xFF444444);
        graphics.drawString(font, key, x + 4, y + 1, 0xFFCCCCCC, false);

        // Description
        graphics.drawString(font, description, x + keyWidth + 10, y + 1, IsotopeColors.TEXT_PRIMARY, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Any key closes the overlay
        if (keyCode == 256 || keyCode == 290) { // Escape or F1
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
