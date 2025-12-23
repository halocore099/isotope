package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Styled button matching ISOTOPE design language.
 */
@Environment(EnvType.CLIENT)
public class IsotopeButton extends Button {

    private final ButtonStyle style;

    public IsotopeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, ButtonStyle.DEFAULT);
    }

    public IsotopeButton(int x, int y, int width, int height, Component message, OnPress onPress, ButtonStyle style) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        this.style = style;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int backgroundColor;
        int textColor;
        int borderColor;

        if (!this.active) {
            backgroundColor = style.disabledBackground;
            textColor = IsotopeColors.TEXT_DISABLED;
            borderColor = IsotopeColors.BORDER_DEFAULT;
        } else if (this.isHovered) {
            backgroundColor = style.hoverBackground;
            textColor = style.textColor;
            borderColor = style.hoverBorder;
        } else {
            backgroundColor = style.normalBackground;
            textColor = style.textColor;
            borderColor = IsotopeColors.BORDER_DEFAULT;
        }

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);

        // Border
        renderBorder(graphics, getX(), getY(), width, height, borderColor);

        // Text
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawCenteredString(
            minecraft.font,
            this.getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            textColor
        );
    }

    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        // Top
        graphics.fill(x, y, x + width, y + 1, color);
        // Bottom
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        // Left
        graphics.fill(x, y, x + 1, y + height, color);
        // Right
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /**
     * Button styles for different use cases.
     */
    public enum ButtonStyle {
        DEFAULT(
            IsotopeColors.BUTTON_BACKGROUND,
            IsotopeColors.BUTTON_HOVER,
            IsotopeColors.BUTTON_DISABLED,
            IsotopeColors.TEXT_PRIMARY,
            IsotopeColors.BORDER_HOVER
        ),
        PRIMARY(
            IsotopeColors.ACCENT_CYAN_DARK,
            IsotopeColors.ACCENT_CYAN,
            IsotopeColors.BUTTON_DISABLED,
            IsotopeColors.TEXT_PRIMARY,
            IsotopeColors.ACCENT_CYAN_HOVER
        ),
        DESTRUCTIVE(
            IsotopeColors.DESTRUCTIVE_BACKGROUND,
            IsotopeColors.DESTRUCTIVE_HOVER,
            IsotopeColors.BUTTON_DISABLED,
            IsotopeColors.DESTRUCTIVE_TEXT,
            IsotopeColors.STATUS_ERROR
        );

        final int normalBackground;
        final int hoverBackground;
        final int disabledBackground;
        final int textColor;
        final int hoverBorder;

        ButtonStyle(int normalBackground, int hoverBackground, int disabledBackground,
                    int textColor, int hoverBorder) {
            this.normalBackground = normalBackground;
            this.hoverBackground = hoverBackground;
            this.disabledBackground = disabledBackground;
            this.textColor = textColor;
            this.hoverBorder = hoverBorder;
        }
    }

    /**
     * Builder for creating IsotopeButtons.
     */
    public static class Builder {
        private int x, y, width = 200, height = 20;
        private Component message;
        private OnPress onPress;
        private ButtonStyle style = ButtonStyle.DEFAULT;

        public Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder style(ButtonStyle style) {
            this.style = style;
            return this;
        }

        public IsotopeButton build() {
            return new IsotopeButton(x, y, width, height, message, onPress, style);
        }
    }

    public static Builder isotopeBuilder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }
}
