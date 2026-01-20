package dev.isotope.compat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adds 1.21-style disconnect() method to Minecraft for 1.20.x compatibility.
 *
 * In 1.21.x: minecraft.disconnect() closes the current world
 * In 1.20.x: This needs to be done via level.disconnect() or setScreen(null)
 */
@Mixin(Minecraft.class)
public abstract class MinecraftCompatMixin {

    @Shadow
    public abstract void setScreen(Screen screen);

    @Shadow
    public abstract void clearLevel(Screen screen);

    /**
     * Adds 1.21-style disconnect() method.
     * In 1.20.x, this clears the level and returns to the main menu.
     */
    public void disconnect() {
        // In 1.20.x, use clearLevel to disconnect
        // This is equivalent to what disconnect() does in 1.21.x
        this.clearLevel(null);
    }
}
