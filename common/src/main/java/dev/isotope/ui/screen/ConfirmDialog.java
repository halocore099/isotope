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
public class ConfirmDialog extends DialogScreen {

    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 120;

    private final String message;
    private final String confirmText;
    private final Runnable onConfirm;
    private final boolean destructive;
    private final boolean skipCloseAfterConfirm;

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
        this(parent, title, message, confirmText, onConfirm, destructive, false);
    }

    /**
     * Create a confirmation dialog with option to skip close after confirm.
     *
     * @param parent              The parent screen to return to
     * @param title               Dialog title
     * @param message             Message to display
     * @param confirmText         Text for confirm button
     * @param onConfirm           Action to run on confirm
     * @param destructive         If true, confirm button is red
     * @param skipCloseAfterConfirm If true, onClose() is not called after confirm (use when callback handles screen)
     */
    public ConfirmDialog(Screen parent, String title, String message, String confirmText,
                         Runnable onConfirm, boolean destructive, boolean skipCloseAfterConfirm) {
        super(parent, title);
        this.message = message;
        this.confirmText = confirmText;
        this.onConfirm = onConfirm;
        this.destructive = destructive;
        this.skipCloseAfterConfirm = skipCloseAfterConfirm;
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
    protected int getDialogWidth() {
        return DIALOG_WIDTH;
    }

    @Override
    protected int getDialogHeight() {
        return DIALOG_HEIGHT;
    }

    @Override
    protected int getTitleColor() {
        return destructive ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.ACCENT_GOLD;
    }

    @Override
    protected int getBorderColor() {
        return destructive ? IsotopeColors.DESTRUCTIVE_BORDER_LIGHT : IsotopeColors.BORDER_DEFAULT;
    }

    @Override
    protected void initDialog(int dialogX, int dialogY) {
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
            if (!skipCloseAfterConfirm) {
                onClose();
            }
        })
            .pos(dialogX + DIALOG_WIDTH / 2 + 10, buttonY)
            .size(buttonWidth, 20)
            .build());
    }

    @Override
    protected void renderDialogContent(GuiGraphics graphics, int dialogX, int dialogY, int mouseX, int mouseY, float partialTick) {
        // Message (supports newlines)
        String[] lines = message.split("\n");
        int lineY = dialogY + 35;
        for (String line : lines) {
            graphics.drawCenteredString(font, line, dialogX + DIALOG_WIDTH / 2, lineY, IsotopeColors.TEXT_PRIMARY);
            lineY += 12;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Use renderBackground for proper vanilla background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = getDialogX();
        int dialogY = getDialogY();
        int dialogWidth = getDialogWidth();
        int dialogHeight = getDialogHeight();

        // Dialog background with 2px border (thicker than standard for emphasis)
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, getBorderColor());
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, getBackgroundColor());

        // Title
        graphics.drawCenteredString(font, dialogTitle, dialogX + dialogWidth / 2, dialogY + 8, getTitleColor());

        // Render custom content
        renderDialogContent(graphics, dialogX, dialogY, mouseX, mouseY, partialTick);

        // Render widgets (buttons)
        for (var widget : this.children()) {
            if (widget instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }
}
