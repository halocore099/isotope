package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

/**
 * Simple dialog for setting entry quality (luck-based weight modifier).
 *
 * Quality affects drop chance based on the player's luck attribute:
 * - Positive quality: item becomes more likely with higher luck
 * - Negative quality: item becomes less likely with higher luck
 * - Zero: luck has no effect on this entry
 */
public class QualityDialog extends DialogScreen {

    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 200;

    private final int poolIdx;
    private final int entryIdx;
    private final int currentQuality;
    private final IntConsumer onQualitySelected;

    // Preset options
    private static final int[] PRESETS = {
        0,    // None (default)
        1,    // Slightly favored
        2,    // Favored
        5,    // Rare (strongly favored)
        10,   // Very rare (heavily favored)
        -1,   // Slightly disfavored
        -2,   // Disfavored
    };

    private static final String[] PRESET_LABELS = {
        "0 (No luck effect)",
        "+1 (Slightly favored)",
        "+2 (Favored)",
        "+5 (Rare, strongly favored)",
        "+10 (Very rare)",
        "-1 (Slightly disfavored)",
        "-2 (Disfavored)",
    };

    private int hoveredPreset = -1;

    public QualityDialog(@Nullable Screen parent, int poolIdx, int entryIdx, int currentQuality, IntConsumer onQualitySelected) {
        super(parent, "Set Quality");
        this.poolIdx = poolIdx;
        this.entryIdx = entryIdx;
        this.currentQuality = currentQuality;
        this.onQualitySelected = onQualitySelected;
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
        String subtitle = "Pool " + (poolIdx + 1) + ", Entry " + (entryIdx + 1);
        graphics.drawCenteredString(font, subtitle, dialogX + DIALOG_WIDTH / 2, dialogY + 22, IsotopeColors.TEXT_MUTED);

        // Current value
        String currentStr = "Current: " + currentQuality;
        graphics.drawCenteredString(font, currentStr, dialogX + DIALOG_WIDTH / 2, dialogY + 36, IsotopeColors.TEXT_SECONDARY);

        // Preset buttons
        int btnY = dialogY + 52;
        hoveredPreset = -1;

        for (int i = 0; i < PRESETS.length; i++) {
            int btnX = dialogX + 10;
            int btnWidth = DIALOG_WIDTH - 20;
            int btnHeight = 16;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth &&
                mouseY >= btnY && mouseY < btnY + btnHeight;
            boolean isCurrent = PRESETS[i] == currentQuality;

            if (hovered) {
                hoveredPreset = i;
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, IsotopeColors.ENTRY_BACKGROUND_EDITED_HOVER);
            } else if (isCurrent) {
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, IsotopeColors.SINGLE_SELECT_BACKGROUND);
            } else {
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, IsotopeColors.ENTRY_BACKGROUND);
            }

            int textColor;
            if (hovered) {
                textColor = IsotopeColors.ACCENT_GREEN;
            } else if (isCurrent) {
                textColor = IsotopeColors.ACCENT_BLUE;
            } else {
                textColor = IsotopeColors.TEXT_PRIMARY;
            }
            graphics.drawCenteredString(font, PRESET_LABELS[i], dialogX + DIALOG_WIDTH / 2, btnY + 4, textColor);

            // Current indicator
            if (isCurrent) {
                graphics.drawString(font, "◄", btnX + btnWidth - 12, btnY + 4, IsotopeColors.ACCENT_BLUE);
            }

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
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (button == 0) {
            int dialogX = getDialogX();
            int dialogY = getDialogY();

            // Check preset clicks
            if (hoveredPreset >= 0 && hoveredPreset < PRESETS.length) {
                onQualitySelected.accept(PRESETS[hoveredPreset]);
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

        return super.mouseClicked(event, focused);
    }

    // keyPressed, onClose, and isPauseScreen are provided by DialogScreen base class
}
