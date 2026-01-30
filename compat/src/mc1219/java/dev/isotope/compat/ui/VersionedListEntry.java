package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * MC 1.21.11+ list entry compatibility.
 *
 * In MC 1.21.11+, ObjectSelectionList.Entry already uses:
 * - renderContent(GuiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick)
 * - getContentX(), getContentY(), getContentWidth(), getContentHeight()
 * - mouseClicked(MouseButtonEvent, boolean)
 *
 * This adapter simply extends Entry with no modifications needed.
 */
@Environment(EnvType.CLIENT)
public abstract class VersionedListEntry<E extends VersionedListEntry<E>> extends ObjectSelectionList.Entry<E> {

    /**
     * Override this for version-agnostic rendering.
     * Maps directly to Entry.renderContent() in 1.21.11+.
     */
    @Override
    public abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick);

    /**
     * Override this for version-agnostic click handling.
     * Maps directly to Entry.mouseClicked() in 1.21.11+.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        return false;
    }

    // getContentX(), getContentY(), getContentWidth(), getContentHeight()
    // are inherited from ObjectSelectionList.Entry in 1.21.11+
}
