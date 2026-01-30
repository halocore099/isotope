package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * MC 1.21.11+ version-specific screen base class.
 *
 * In MC 1.21.11+, input methods natively use event objects.
 * This class simply extends Screen with no adaptations needed.
 */
@Environment(EnvType.CLIENT)
public abstract class VersionedScreen extends Screen {

    protected VersionedScreen(Component title) {
        super(title);
    }

    // In MC 1.21.11+, the event-based methods are native to Screen,
    // so no adaptation is needed. Subclasses can directly override:
    // - keyPressed(KeyEvent)
    // - keyReleased(KeyEvent)
    // - mouseClicked(MouseButtonEvent)
    // - mouseReleased(MouseButtonEvent)
    // - charTyped(CharacterEvent)
}
