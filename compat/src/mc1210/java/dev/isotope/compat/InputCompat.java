package dev.isotope.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * MC 1.21.0-1.21.10 input compatibility.
 *
 * In MC 1.21.10 and earlier, InputConstants.isKeyDown() takes (long windowHandle, int key).
 */
@Environment(EnvType.CLIENT)
public final class InputCompat {

    private InputCompat() {}

    /**
     * Check if a key is currently pressed.
     * In MC 1.21.10 and earlier, uses Window.getWindow() to get the long handle.
     */
    public static boolean isKeyDown(int key) {
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(windowHandle, key);
    }
}
