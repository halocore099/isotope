package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

/**
 * Simple dialog screen for entering a weight value for batch operations.
 */
@Environment(EnvType.CLIENT)
public class BatchWeightScreen extends Screen {

    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 100;

    @Nullable
    private final Screen parent;
    private final IntConsumer onConfirm;

    private EditBox weightInput;
    private Button confirmButton;
    private Button cancelButton;

    // Common weight presets
    private static final int[] PRESETS = {1, 5, 10, 15, 20, 25, 50};
    private int presetIndex = 2; // Default to 10

    public BatchWeightScreen(@Nullable Screen parent, IntConsumer onConfirm) {
        super(Component.literal("Set Weight"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        int dialogX = centerX - DIALOG_WIDTH / 2;
        int dialogY = centerY - DIALOG_HEIGHT / 2;

        // Weight input box
        weightInput = new EditBox(font, dialogX + 20, dialogY + 35, 60, 20, Component.literal("Weight"));
        weightInput.setValue(String.valueOf(PRESETS[presetIndex]));
        weightInput.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        addRenderableWidget(weightInput);

        // Preset buttons
        int presetX = dialogX + 90;
        for (int i = 0; i < PRESETS.length; i++) {
            int preset = PRESETS[i];
            int btnX = presetX + (i % 4) * 25;
            int btnY = dialogY + 30 + (i / 4) * 22;
            Button presetBtn = Button.builder(Component.literal(String.valueOf(preset)), btn -> {
                weightInput.setValue(String.valueOf(preset));
            }).pos(btnX, btnY).size(22, 18).build();
            addRenderableWidget(presetBtn);
        }

        // Confirm button
        confirmButton = Button.builder(Component.literal("Apply"), btn -> {
            try {
                int weight = Integer.parseInt(weightInput.getValue());
                if (weight > 0) {
                    onConfirm.accept(weight);
                    minecraft.setScreen(parent);
                }
            } catch (NumberFormatException ignored) {
            }
        }).pos(dialogX + 20, dialogY + DIALOG_HEIGHT - 30).size(70, 20).build();
        addRenderableWidget(confirmButton);

        // Cancel button
        cancelButton = Button.builder(Component.literal("Cancel"), btn -> {
            minecraft.setScreen(parent);
        }).pos(dialogX + DIALOG_WIDTH - 90, dialogY + DIALOG_HEIGHT - 30).size(70, 20).build();
        addRenderableWidget(cancelButton);

        // Focus input
        setInitialFocus(weightInput);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int dialogX = centerX - DIALOG_WIDTH / 2;
        int dialogY = centerY - DIALOG_HEIGHT / 2;

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xEE1a1a1a);
        graphics.renderOutline(dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF404040);

        // Title
        graphics.drawCenteredString(font, "Set Weight for Selected Entries", centerX, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Label
        graphics.drawString(font, "Weight:", dialogX + 20, dialogY + 40, IsotopeColors.TEXT_PRIMARY, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            minecraft.setScreen(parent);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
            confirmButton.onPress();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
