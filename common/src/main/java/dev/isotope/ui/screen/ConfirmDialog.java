package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple confirmation dialog for destructive actions.
 */
@Environment(EnvType.CLIENT)
public class ConfirmDialog extends Screen {

    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 120;

    private final Screen parent;
    private final String title;
    private final String message;
    private final String confirmText;
    private final Runnable onConfirm;
    private final boolean destructive;

    /**
     * Create a confirmation dialog.
     *
     * @param parent      The parent screen to return to
     * @param title       Dialog title
     * @param message     Message to display
     * @param confirmText Text for confirm button
     * @param onConfirm   Action to run on confirm
     * @param destructive If true, confirm button is red
     */
    public ConfirmDialog(Screen parent, String title, String message, String confirmText,
                         Runnable onConfirm, boolean destructive) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.confirmText = confirmText;
        this.onConfirm = onConfirm;
        this.destructive = destructive;
    }

    /**
     * Create a standard delete confirmation dialog.
     */
    public static ConfirmDialog delete(Screen parent, String itemName, Runnable onConfirm) {
        return new ConfirmDialog(
            parent,
            "Confirm Delete",
            "Are you sure you want to delete \"" + itemName + "\"?\nThis action cannot be undone.",
            "Delete",
            onConfirm,
            true
        );
    }

    /**
     * Create a standard clear confirmation dialog.
     */
    public static ConfirmDialog clear(Screen parent, String itemName, Runnable onConfirm) {
        return new ConfirmDialog(
            parent,
            "Confirm Clear",
            "Are you sure you want to clear " + itemName + "?\nThis action cannot be undone.",
            "Clear",
            onConfirm,
            true
        );
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        int buttonWidth = 80;
        int buttonY = dialogY + DIALOG_HEIGHT - 30;

        // Cancel button
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .pos(dialogX + DIALOG_WIDTH / 2 - buttonWidth - 10, buttonY)
            .size(buttonWidth, 20)
            .build());

        // Confirm button
        addRenderableWidget(Button.builder(Component.literal(confirmText), b -> {
            onConfirm.run();
            onClose();
        })
            .pos(dialogX + DIALOG_WIDTH / 2 + 10, buttonY)
            .size(buttonWidth, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background with border
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2,
            destructive ? 0xFF663333 : 0xFF333333);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title
        int titleColor = destructive ? 0xFFFF6666 : IsotopeColors.ACCENT_GOLD;
        graphics.drawCenteredString(font, title, width / 2, dialogY + 12, titleColor);

        // Message (supports newlines)
        String[] lines = message.split("\n");
        int lineY = dialogY + 35;
        for (String line : lines) {
            graphics.drawCenteredString(font, line, width / 2, lineY, IsotopeColors.TEXT_PRIMARY);
            lineY += 12;
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
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
