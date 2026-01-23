package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * MC 1.21.11+ version-specific widget base class.
 *
 * In MC 1.21.11+, input methods natively use event objects.
 * This class simply extends AbstractWidget with no adaptations needed.
 */
@Environment(EnvType.CLIENT)
public abstract class VersionedWidget extends AbstractWidget {

    protected VersionedWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Default implementation - subclasses can override
    }

    // In MC 1.21.11+, the event-based methods are native to AbstractWidget,
    // so no adaptation is needed. Subclasses can directly override:
    // - keyPressed(KeyEvent)
    // - keyReleased(KeyEvent)
    // - mouseClicked(MouseButtonEvent)
    // - mouseReleased(MouseButtonEvent)
    // - charTyped(CharacterEvent)
}
