package dev.isotope.ui.screen;

import dev.isotope.data.loot.NumberProvider;
import dev.isotope.ui.IsotopeColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Simple dialog for selecting bonus rolls (luck-based extra rolls) for a pool.
 */
public class BonusRollsDialog extends DialogScreen {

    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 160;

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
        super(parent, "Set Bonus Rolls");
        this.poolIdx = poolIdx;
        this.onBonusRollsSelected = onBonusRollsSelected;
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
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, IsotopeColors.ENTRY_BACKGROUND_EDITED_HOVER);
            } else {
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, IsotopeColors.ENTRY_BACKGROUND);
            }

            int textColor = hovered ? IsotopeColors.ACCENT_GREEN : IsotopeColors.TEXT_PRIMARY;
            graphics.drawCenteredString(font, PRESET_LABELS[i], dialogX + DIALOG_WIDTH / 2, btnY + 4, textColor);

            btnY += 18;
        }

        // Cancel button
        int cancelY = dialogY + DIALOG_HEIGHT - 24;
        int cancelX = dialogX + DIALOG_WIDTH / 2 - 30;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + 60 &&
            mouseY >= cancelY && mouseY < cancelY + 18;
        graphics.fill(cancelX, cancelY, cancelX + 60, cancelY + 18, cancelHovered ? IsotopeColors.BATCH_BUTTON_HOVER : IsotopeColors.ENTRY_BACKGROUND);
        graphics.drawCenteredString(font, "Cancel", cancelX + 30, cancelY + 5,
            cancelHovered ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int dialogX = getDialogX();
            int dialogY = getDialogY();

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

    // keyPressed, onClose, and isPauseScreen are provided by DialogScreen base class
}
