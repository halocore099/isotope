package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Inline numeric edit field.
 *
 * Shows value as text, becomes editable on click.
 * Commits on Enter or focus loss.
 */
@Environment(EnvType.CLIENT)
public class InlineEditField extends AbstractWidget {

    private int value;
    private int minValue = 0;
    private int maxValue = 999;
    private boolean editing = false;
    private String editText = "";
    private int cursorPos = 0;

    @Nullable
    private Consumer<Integer> onValueChanged;

    public InlineEditField(int x, int y, int width, int height, int initialValue) {
        super(x, y, width, height, Component.empty());
        this.value = initialValue;
    }

    public void setValue(int value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
        if (!editing) {
            editText = String.valueOf(this.value);
        }
    }

    public int getValue() {
        return value;
    }

    public void setRange(int min, int max) {
        this.minValue = min;
        this.maxValue = max;
        setValue(value); // Clamp to new range
    }

    public void setOnValueChanged(@Nullable Consumer<Integer> callback) {
        this.onValueChanged = callback;
    }

    private void startEditing() {
        editing = true;
        editText = String.valueOf(value);
        cursorPos = editText.length();
    }

    private void stopEditing(boolean commit) {
        if (!editing) return;
        editing = false;

        if (commit) {
            try {
                int newValue = Integer.parseInt(editText);
                newValue = Math.max(minValue, Math.min(maxValue, newValue));
                if (newValue != value) {
                    value = newValue;
                    if (onValueChanged != null) {
                        onValueChanged.accept(value);
                    }
                }
            } catch (NumberFormatException e) {
                // Revert to original value
            }
        }
        editText = String.valueOf(value);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        boolean hovered = isHovered();

        // Background
        int bgColor = editing ? 0xFF2a2a2a : (hovered ? 0xFF3a3a3a : 0xFF303030);
        int borderColor = editing ? IsotopeColors.ACCENT_GOLD : (hovered ? 0xFF505050 : 0xFF404040);

        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        graphics.renderOutline(getX(), getY(), width, height, borderColor);

        // Text
        String displayText = editing ? editText : String.valueOf(value);
        int textX = getX() + (width - font.width(displayText)) / 2;
        int textY = getY() + (height - 8) / 2;

        graphics.drawString(font, displayText, textX, textY, IsotopeColors.TEXT_PRIMARY, false);

        // Cursor when editing
        if (editing) {
            String beforeCursor = editText.substring(0, Math.min(cursorPos, editText.length()));
            int cursorX = getX() + (width - font.width(editText)) / 2 + font.width(beforeCursor);

            // Blink cursor
            long time = System.currentTimeMillis();
            if ((time / 500) % 2 == 0) {
                graphics.fill(cursorX, textY - 1, cursorX + 1, textY + 9, IsotopeColors.TEXT_PRIMARY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            if (!editing) {
                startEditing();
            }
            return true;
        } else if (editing) {
            stopEditing(true);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) return false;

        // Enter - commit
        if (keyCode == 257 || keyCode == 335) { // Enter or numpad enter
            stopEditing(true);
            return true;
        }

        // Escape - cancel
        if (keyCode == 256) {
            stopEditing(false);
            return true;
        }

        // Backspace
        if (keyCode == 259 && cursorPos > 0) {
            editText = editText.substring(0, cursorPos - 1) + editText.substring(cursorPos);
            cursorPos--;
            return true;
        }

        // Delete
        if (keyCode == 261 && cursorPos < editText.length()) {
            editText = editText.substring(0, cursorPos) + editText.substring(cursorPos + 1);
            return true;
        }

        // Left arrow
        if (keyCode == 263 && cursorPos > 0) {
            cursorPos--;
            return true;
        }

        // Right arrow
        if (keyCode == 262 && cursorPos < editText.length()) {
            cursorPos++;
            return true;
        }

        // Home
        if (keyCode == 268) {
            cursorPos = 0;
            return true;
        }

        // End
        if (keyCode == 269) {
            cursorPos = editText.length();
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!editing) return false;

        // Only allow digits
        if (Character.isDigit(chr)) {
            if (editText.length() < 4) { // Max 4 digits
                editText = editText.substring(0, cursorPos) + chr + editText.substring(cursorPos);
                cursorPos++;
            }
            return true;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && editing) {
            stopEditing(true);
        }
    }

    public boolean isEditing() {
        return editing;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
