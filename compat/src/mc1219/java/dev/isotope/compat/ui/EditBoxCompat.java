package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * MC 1.21.11+ EditBox compatibility.
 *
 * In MC 1.21.11+, EditBox uses event objects for input methods.
 * We pass the events directly to EditBox.
 */
@Environment(EnvType.CLIENT)
public final class EditBoxCompat {

    private EditBoxCompat() {}

    /**
     * Forward mouse click to EditBox.
     * In MC 1.21.11+, uses mouseClicked(MouseButtonEvent, boolean).
     */
    public static boolean mouseClicked(EditBox editBox, MouseButtonEvent event) {
        return editBox.mouseClicked(event, false);
    }

    /**
     * Forward key press to EditBox.
     * In MC 1.21.11+, uses keyPressed(KeyEvent).
     */
    public static boolean keyPressed(EditBox editBox, KeyEvent event) {
        return editBox.keyPressed(event);
    }

    /**
     * Forward character typed to EditBox.
     * In MC 1.21.11+, uses charTyped(CharacterEvent).
     */
    public static boolean charTyped(EditBox editBox, CharacterEvent event) {
        return editBox.charTyped(event);
    }
}
