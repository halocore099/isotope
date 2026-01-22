package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Overlay screen showing all keyboard shortcuts.
 * Opened with F1.
 */
@Environment(EnvType.CLIENT)
public class ShortcutsScreen extends Screen {

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 480;

    private final Screen parent;

    // Shortcut sections for display
    private static final String[][] GENERAL = {
        {"Ctrl+P", "Open command palette"},
        {"F1", "Show this help"},
        {"F5", "Toggle test mode"},
        {"Escape", "Close overlay / Clear selection"},
    };

    private static final String[][] PANELS = {
        {"1", "Browser panel"},
        {"2", "Search panel"},
        {"3", "Analysis panel"},
        {"4", "History panel"},
    };

    private static final String[][] EDITING = {
        {"Ctrl+Z", "Undo last change"},
        {"Ctrl+Y", "Redo last change"},
        {"Ctrl+S", "Export as datapack"},
    };

    private static final String[][] ENTRIES = {
        {"Ctrl+N", "Add new item to pool"},
        {"Delete", "Remove selected entry(s)"},
        {"Ctrl+D", "Duplicate entry"},
        {"Ctrl+C", "Copy entry"},
        {"Ctrl+V", "Paste entry"},
    };

    private static final String[][] NAVIGATION = {
        {"↑/↓", "Navigate entries"},
        {"Home", "Jump to first entry"},
        {"End", "Jump to last entry"},
        {"Enter", "Edit selected weight"},
    };

    private static final String[][] SELECTION = {
        {"Ctrl+Click", "Toggle selection"},
        {"Shift+Click", "Select range"},
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
        addRenderableWidget(Button.builder(Component.literal(UIConstants.LABEL_CLOSE), b -> onClose())
            .pos(dialogX + DIALOG_WIDTH / 2 - 40, dialogY + DIALOG_HEIGHT - 30)
            .size(80, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // MUST call super.render() first for content to be visible in MC 1.21
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render semi-transparent overlay
        graphics.fill(0, 0, width, height, 0x90000000);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background with vanilla-style border
        graphics.fill(dialogX - 3, dialogY - 3, dialogX + DIALOG_WIDTH + 3, dialogY + DIALOG_HEIGHT + 3, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, IsotopeColors.BUTTON_BACKGROUND);
        graphics.fill(dialogX - 1, dialogY - 1, dialogX + DIALOG_WIDTH + 1, dialogY + DIALOG_HEIGHT + 1, IsotopeColors.BACKGROUND_DARKEST);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Title
        graphics.drawCenteredString(font, "Keyboard Shortcuts", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Shortcuts list
        int y = dialogY + 30;
        int keyX = dialogX + 20;

        // Section: General
        y = renderSection(graphics, dialogX, keyX, y, "General", GENERAL);

        // Section: Panels
        y = renderSection(graphics, dialogX, keyX, y, "Panels", PANELS);

        // Section: Editing
        y = renderSection(graphics, dialogX, keyX, y, "Editing", EDITING);

        // Section: Entries
        y = renderSection(graphics, dialogX, keyX, y, "Entry Operations", ENTRIES);

        // Section: Navigation
        y = renderSection(graphics, dialogX, keyX, y, "Navigation", NAVIGATION);

        // Section: Selection
        y = renderSection(graphics, dialogX, keyX, y, "Multi-Selection", SELECTION);

        // Re-render button on top
        for (var widget : this.children()) {
            if (widget instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private int renderSection(GuiGraphics graphics, int dialogX, int keyX, int y, String title, String[][] shortcuts) {
        graphics.drawString(font, title, dialogX + 10, y, IsotopeColors.TEXT_SECONDARY, false);
        y += 14;
        for (String[] shortcut : shortcuts) {
            renderShortcut(graphics, keyX, y, shortcut[0], shortcut[1]);
            y += 12;
        }
        return y + 6;
    }

    private void renderShortcut(GuiGraphics graphics, int x, int y, String key, String description) {
        // Key badge
        int keyWidth = font.width(key) + 8;
        graphics.fill(x, y - 1, x + keyWidth, y + 10, IsotopeColors.ENTRY_BACKGROUND);
        ScreenUtils.renderOutline(graphics, x, y - 1, keyWidth, 11, IsotopeColors.BUTTON_PRESSED);
        graphics.drawString(font, key, x + 4, y + 1, IsotopeColors.TEXT_SECONDARY, false);

        // Description
        graphics.drawString(font, description, x + keyWidth + 10, y + 1, IsotopeColors.TEXT_PRIMARY, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key(); int scanCode = event.scancode(); int modifiers = event.modifiers();
        // Any key closes the overlay
        if (keyCode == UIConstants.KEY_ESCAPE || keyCode == UIConstants.KEY_F1) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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
