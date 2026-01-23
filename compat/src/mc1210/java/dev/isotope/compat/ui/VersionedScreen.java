package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * MC 1.21.0-1.21.10 version-specific screen base class.
 *
 * In MC 1.21.0-1.21.10, input methods use primitive parameters.
 * This class adapts them to the event-based API used in 1.21.11+.
 */
@Environment(EnvType.CLIENT)
public abstract class VersionedScreen extends Screen {

    protected VersionedScreen(Component title) {
        super(title);
    }

    // Adapt old primitive-parameter methods to new event-based methods

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
        return keyPressed(event) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
        return keyReleased(event) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
        return mouseClicked(event, false) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
        return mouseReleased(event, false) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        CharacterEvent event = new CharacterEvent(chr, modifiers);
        return charTyped(event) || super.charTyped(chr, modifiers);
    }

    // Event-based methods for subclasses to override
    // These match the 1.21.11+ method signatures

    /**
     * Handle key press events.
     * @param event The key event
     * @return true if the event was handled
     */
    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    /**
     * Handle key release events.
     * @param event The key event
     * @return true if the event was handled
     */
    public boolean keyReleased(KeyEvent event) {
        return false;
    }

    /**
     * Handle mouse click events.
     * @param event The mouse button event
     * @param focused Whether this screen is focused
     * @return true if the event was handled
     */
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        return false;
    }

    /**
     * Handle mouse release events.
     * @param event The mouse button event
     * @param focused Whether this screen is focused
     * @return true if the event was handled
     */
    public boolean mouseReleased(MouseButtonEvent event, boolean focused) {
        return false;
    }

    /**
     * Handle character typed events.
     * @param event The character event
     * @return true if the event was handled
     */
    public boolean charTyped(CharacterEvent event) {
        return false;
    }
}
