package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
public class QualityDialog extends Screen {

    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 200;

    @Nullable
    private final Screen parent;
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
        super(Component.literal("Set Quality"));
        this.parent = parent;
        this.poolIdx = poolIdx;
        this.entryIdx = entryIdx;
        this.currentQuality = currentQuality;
        this.onQualitySelected = onQualitySelected;
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
        graphics.drawCenteredString(font, "Set Quality", dialogX + DIALOG_WIDTH / 2, dialogY + 8, IsotopeColors.ACCENT_GOLD);

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
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF3a4a3a);
            } else if (isCurrent) {
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF2a3a4a);
            } else {
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF2a2a2a);
            }

            int textColor;
            if (hovered) {
                textColor = 0xFF55FF55;
            } else if (isCurrent) {
                textColor = 0xFF55AAFF;
            } else {
                textColor = IsotopeColors.TEXT_PRIMARY;
            }
            graphics.drawCenteredString(font, PRESET_LABELS[i], dialogX + DIALOG_WIDTH / 2, btnY + 4, textColor);

            // Current indicator
            if (isCurrent) {
                graphics.drawString(font, "◄", btnX + btnWidth - 12, btnY + 4, 0xFF55AAFF);
            }

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
