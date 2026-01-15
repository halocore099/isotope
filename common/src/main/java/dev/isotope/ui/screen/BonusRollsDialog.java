package dev.isotope.ui.screen;

import dev.isotope.data.loot.NumberProvider;
import dev.isotope.ui.IsotopeColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Simple dialog for selecting bonus rolls (luck-based extra rolls) for a pool.
 */
public class BonusRollsDialog extends Screen {

    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 160;

    @Nullable
    private final Screen parent;
    private final int poolIdx;
    private final Consumer<NumberProvider> onBonusRollsSelected;

    // Preset options
    private static final NumberProvider[] PRESETS = {
        NumberProvider.constant(0),      // None
        NumberProvider.constant(1),      // +1 per luck
        NumberProvider.constant(2),      // +2 per luck
        NumberProvider.uniform(0, 1),    // +0-1 per luck
        NumberProvider.uniform(0, 2),    // +0-2 per luck
        NumberProvider.uniform(1, 2),    // +1-2 per luck
    };

    private static final String[] PRESET_LABELS = {
        "None (0)",
        "+1 per luck",
        "+2 per luck",
        "+0-1 per luck",
        "+0-2 per luck",
        "+1-2 per luck",
    };

    private int hoveredPreset = -1;

    public BonusRollsDialog(@Nullable Screen parent, int poolIdx, Consumer<NumberProvider> onBonusRollsSelected) {
        super(Component.literal("Set Bonus Rolls"));
        this.parent = parent;
        this.poolIdx = poolIdx;
        this.onBonusRollsSelected = onBonusRollsSelected;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);
        graphics.renderOutline(dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF404040);

        // Title
        graphics.drawCenteredString(font, "Set Bonus Rolls", dialogX + DIALOG_WIDTH / 2, dialogY + 8, IsotopeColors.ACCENT_GOLD);

        // Subtitle
        graphics.drawCenteredString(font, "Pool " + (poolIdx + 1), dialogX + DIALOG_WIDTH / 2, dialogY + 22, IsotopeColors.TEXT_MUTED);

        // Preset buttons
        int btnY = dialogY + 40;
        hoveredPreset = -1;

        for (int i = 0; i < PRESETS.length; i++) {
            int btnX = dialogX + 10;
            int btnWidth = DIALOG_WIDTH - 20;
            int btnHeight = 16;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth &&
                mouseY >= btnY && mouseY < btnY + btnHeight;

            if (hovered) {
                hoveredPreset = i;
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF3a4a3a);
            } else {
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF2a2a2a);
            }

            int textColor = hovered ? 0xFF55FF55 : IsotopeColors.TEXT_PRIMARY;
            graphics.drawCenteredString(font, PRESET_LABELS[i], dialogX + DIALOG_WIDTH / 2, btnY + 4, textColor);

            btnY += 18;
        }

        // Cancel button
        int cancelY = dialogY + DIALOG_HEIGHT - 24;
        int cancelX = dialogX + DIALOG_WIDTH / 2 - 30;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + 60 &&
            mouseY >= cancelY && mouseY < cancelY + 18;
        graphics.fill(cancelX, cancelY, cancelX + 60, cancelY + 18, cancelHovered ? 0xFF4a3a3a : 0xFF2a2a2a);
        graphics.drawCenteredString(font, "Cancel", cancelX + 30, cancelY + 5,
            cancelHovered ? 0xFFff6666 : IsotopeColors.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int dialogX = (width - DIALOG_WIDTH) / 2;
            int dialogY = (height - DIALOG_HEIGHT) / 2;

            // Check preset clicks
            if (hoveredPreset >= 0 && hoveredPreset < PRESETS.length) {
                onBonusRollsSelected.accept(PRESETS[hoveredPreset]);
                onClose();
                return true;
            }

            // Check cancel button
            int cancelY = dialogY + DIALOG_HEIGHT - 24;
            int cancelX = dialogX + DIALOG_WIDTH / 2 - 30;
            if (mouseX >= cancelX && mouseX < cancelX + 60 &&
                mouseY >= cancelY && mouseY < cancelY + 18) {
                onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
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
