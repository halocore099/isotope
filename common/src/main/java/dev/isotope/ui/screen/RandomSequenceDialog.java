package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dialog for editing the random sequence of a loot table.
 *
 * Random sequence is a 1.20+ feature that allows for deterministic loot generation
 * based on world seed and block position.
 */
public class RandomSequenceDialog extends DialogScreen {

    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 140;

    private final Optional<ResourceLocation> currentSequence;
    private final Consumer<Optional<ResourceLocation>> onSequenceSelected;

    private String inputText;
    private boolean inputFocused = true;

    public RandomSequenceDialog(@Nullable Screen parent, Optional<ResourceLocation> currentSequence,
                                 Consumer<Optional<ResourceLocation>> onSequenceSelected) {
        super(parent, "Edit Random Sequence");
        this.currentSequence = currentSequence;
        this.onSequenceSelected = onSequenceSelected;
        this.inputText = currentSequence.map(ResourceLocation::toString).orElse("");
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
    protected void renderDialogContent(GuiGraphics graphics, int dialogX, int dialogY, int mouseX, int mouseY, float partialTick) {
        // Subtitle
        graphics.drawCenteredString(font, "1.20+ feature for deterministic loot", dialogX + DIALOG_WIDTH / 2, dialogY + 22, IsotopeColors.TEXT_MUTED);

        // Input label
        graphics.drawString(font, "Sequence ID:", dialogX + 10, dialogY + 42, IsotopeColors.TEXT_SECONDARY);

        // Input box
        int inputX = dialogX + 10;
        int inputY = dialogY + 54;
        int inputWidth = DIALOG_WIDTH - 20;
        int inputHeight = 18;
        ScreenUtils.renderInputBox(graphics, inputX, inputY, inputWidth, inputHeight, inputFocused);

        // Input text with cursor
        String displayText = inputText;
        if (inputFocused && ScreenUtils.shouldShowCursor()) {
            displayText += "_";
        }
        graphics.drawString(font, displayText, inputX + 4, inputY + 5, IsotopeColors.TEXT_PRIMARY);

        // Hint
        graphics.drawString(font, "e.g., minecraft:blocks/ancient_city", dialogX + 10, dialogY + 76, IsotopeColors.TEXT_MUTED);
        graphics.drawString(font, "Leave empty to remove sequence", dialogX + 10, dialogY + 88, IsotopeColors.TEXT_MUTED);

        // Buttons row
        int buttonY = dialogY + DIALOG_HEIGHT - 28;
        int buttonWidth = 70;
        int gap = 10;
        int totalWidth = buttonWidth * 2 + gap;
        int startX = dialogX + (DIALOG_WIDTH - totalWidth) / 2;

        // Apply button
        boolean applyHovered = mouseX >= startX && mouseX < startX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + 20;
        graphics.fill(startX, buttonY, startX + buttonWidth, buttonY + 20,
            applyHovered ? IsotopeColors.SUCCESS_BACKGROUND : IsotopeColors.SUCCESS_TINT);
        graphics.drawCenteredString(font, "Apply", startX + buttonWidth / 2, buttonY + 6,
            applyHovered ? IsotopeColors.ACCENT_GREEN : IsotopeColors.SUCCESS_LIGHT);

        // Cancel button
        int cancelX = startX + buttonWidth + gap;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + 20;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 20,
            cancelHovered ? IsotopeColors.BATCH_BUTTON_HOVER : IsotopeColors.ENTRY_BACKGROUND);
        graphics.drawCenteredString(font, "Cancel", cancelX + buttonWidth / 2, buttonY + 6,
            cancelHovered ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int dialogX = getDialogX();
            int dialogY = getDialogY();

            // Check input box click
            int inputX = dialogX + 10;
            int inputY = dialogY + 54;
            int inputWidth = DIALOG_WIDTH - 20;
            if (mouseX >= inputX && mouseX < inputX + inputWidth &&
                mouseY >= inputY && mouseY < inputY + 18) {
                inputFocused = true;
                return true;
            }

            // Check buttons
            int buttonY = dialogY + DIALOG_HEIGHT - 28;
            int buttonWidth = 70;
            int gap = 10;
            int totalWidth = buttonWidth * 2 + gap;
            int startX = dialogX + (DIALOG_WIDTH - totalWidth) / 2;

            // Apply button
            if (mouseX >= startX && mouseX < startX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + 20) {
                applySelection();
                return true;
            }

            // Cancel button
            int cancelX = startX + buttonWidth + gap;
            if (mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + 20) {
                onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (keyCode == UIConstants.KEY_ENTER || keyCode == UIConstants.KEY_NUMPAD_ENTER) {
            applySelection();
            return true;
        }

        if (inputFocused) {
            if (keyCode == UIConstants.KEY_BACKSPACE) {
                if (!inputText.isEmpty()) {
                    inputText = inputText.substring(0, inputText.length() - 1);
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused) {
            // Allow alphanumeric, underscore, colon, slash for ResourceLocation
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == '/' || chr == '.') {
                inputText += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private void applySelection() {
        Optional<ResourceLocation> result;
        if (inputText.isEmpty()) {
            result = Optional.empty();
        } else {
            String input = inputText;
            if (!input.contains(":")) {
                input = "minecraft:" + input;
            }
            result = Optional.of(ResourceLocation.parse(input));
        }
        onSequenceSelected.accept(result);
        onClose();
    }

    // keyPressed for escape, onClose, and isPauseScreen are provided by DialogScreen base class
}
