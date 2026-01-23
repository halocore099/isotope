package dev.isotope.ui.widget;

import dev.isotope.compat.ui.VersionedWidget;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
public class InlineEditField extends VersionedWidget {

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
            int newValue = ScreenUtils.parseIntSafe(editText, value);
            newValue = Math.max(minValue, Math.min(maxValue, newValue));
            if (newValue != value) {
                value = newValue;
                if (onValueChanged != null) {
                    onValueChanged.accept(value);
                }
            }
        }
        editText = String.valueOf(value);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        boolean hovered = isHovered();

        // Background
        int bgColor = editing ? IsotopeColors.ENTRY_BACKGROUND : (hovered ? IsotopeColors.ENTRY_BACKGROUND_HOVER : IsotopeColors.INPUT_BACKGROUND);
        int borderColor = editing ? IsotopeColors.ACCENT_GOLD : (hovered ? IsotopeColors.INPUT_BORDER : IsotopeColors.BORDER_DEFAULT);

        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        ScreenUtils.renderOutline(graphics, getX(), getY(), width, height, borderColor);

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
            ScreenUtils.renderCursor(graphics, cursorX, textY - 1, 10);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (!editing) return false;

        // Enter - commit
        if (keyCode == UIConstants.KEY_ENTER || keyCode == UIConstants.KEY_NUMPAD_ENTER) {
            stopEditing(true);
            return true;
        }

        // Escape - cancel
        if (keyCode == UIConstants.KEY_ESCAPE) {
            stopEditing(false);
            return true;
        }

        // Backspace
        if (keyCode == UIConstants.KEY_BACKSPACE && cursorPos > 0) {
            editText = editText.substring(0, cursorPos - 1) + editText.substring(cursorPos);
            cursorPos--;
            return true;
        }

        // Delete
        if (keyCode == UIConstants.KEY_DELETE && cursorPos < editText.length()) {
            editText = editText.substring(0, cursorPos) + editText.substring(cursorPos + 1);
            return true;
        }

        // Left arrow
        if (keyCode == UIConstants.KEY_LEFT && cursorPos > 0) {
            cursorPos--;
            return true;
        }

        // Right arrow
        if (keyCode == UIConstants.KEY_RIGHT && cursorPos < editText.length()) {
            cursorPos++;
            return true;
        }

        // Home
        if (keyCode == UIConstants.KEY_HOME) {
            cursorPos = 0;
            return true;
        }

        // End
        if (keyCode == UIConstants.KEY_END) {
            cursorPos = editText.length();
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        int modifiers = event.modifiers();
        if (!editing) return false;

        // Only allow digits
        if (Character.isDigit(c)) {
            if (editText.length() < 4) { // Max 4 digits
                editText = editText.substring(0, cursorPos) + c + editText.substring(cursorPos);
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
