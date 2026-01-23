package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * MC 1.21.11+ version-specific button base class.
 *
 * In MC 1.21.11+, buttons override renderContents() for custom rendering.
 */
@Environment(EnvType.CLIENT)
public abstract class VersionedButton extends Button {

    protected VersionedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderButtonContents(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Implement this method to provide custom button rendering.
     * Called by the version-appropriate render method.
     */
    protected abstract void renderButtonContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}
