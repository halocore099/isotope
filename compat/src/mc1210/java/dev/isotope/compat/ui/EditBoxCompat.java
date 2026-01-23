package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * MC 1.21.0-1.21.10 EditBox compatibility.
 *
 * In these versions, EditBox uses primitive parameters for input methods.
 * We extract primitives from our event shim classes and pass them to EditBox.
 */
@Environment(EnvType.CLIENT)
public final class EditBoxCompat {

    private EditBoxCompat() {}

    /**
     * Forward mouse click to EditBox.
     * In MC 1.21.10 and earlier, uses mouseClicked(double, double, int).
     */
    public static boolean mouseClicked(EditBox editBox, MouseButtonEvent event) {
        return editBox.mouseClicked(event.x(), event.y(), event.button());
    }

    /**
     * Forward key press to EditBox.
     * In MC 1.21.10 and earlier, uses keyPressed(int, int, int).
     */
    public static boolean keyPressed(EditBox editBox, KeyEvent event) {
        return editBox.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    /**
     * Forward character typed to EditBox.
     * In MC 1.21.10 and earlier, uses charTyped(char, int).
     */
    public static boolean charTyped(EditBox editBox, CharacterEvent event) {
        return editBox.charTyped((char) event.codepoint(), event.modifiers());
    }
}
