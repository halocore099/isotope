package dev.isotope.compat.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;

/**
 * MC 1.21.0-1.21.10 list entry compatibility.
 *
 * In MC 1.21.10 and earlier, ObjectSelectionList.Entry uses:
 * - render(GuiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick)
 * - mouseClicked(double mouseX, double mouseY, int button)
 *
 * This adapter provides the 1.21.11-style API (renderContent with getContent* helpers).
 */
@Environment(EnvType.CLIENT)
public abstract class VersionedListEntry<E extends VersionedListEntry<E>> extends ObjectSelectionList.Entry<E> {

    // Store dimensions passed in render() for use in renderContent()
    private int entryTop;
    private int entryLeft;
    private int entryWidth;
    private int entryHeight;

    /**
     * Override this instead of render() for version-agnostic rendering.
     */
    public abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovering, float partialTick);

    /**
     * Override this instead of mouseClicked(double, double, int) for version-agnostic click handling.
     */
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean focused) {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTick) {
        // Store entry dimensions for getContent* methods
        this.entryTop = top;
        this.entryLeft = left;
        this.entryWidth = width;
        this.entryHeight = height;

        // Delegate to version-agnostic method
        renderContent(graphics, mouseX, mouseY, hovered, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Delegate to version-agnostic method
        net.minecraft.client.input.MouseButtonEvent event =
            new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, button);
        return mouseClicked(event, false);
    }

    /**
     * Get the content X position.
     */
    public int getContentX() {
        return entryLeft;
    }

    /**
     * Get the content Y position.
     */
    public int getContentY() {
        return entryTop;
    }

    /**
     * Get the content width.
     */
    public int getContentWidth() {
        return entryWidth;
    }

    /**
     * Get the content height.
     */
    public int getContentHeight() {
        return entryHeight;
    }
}
