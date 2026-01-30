package dev.isotope.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * MC 1.21.9-1.21.10 input compatibility.
 *
 * In MC 1.21.9-1.21.10, InputConstants.isKeyDown() takes (Window, int key),
 * same as MC 1.21.11+.
 */
@Environment(EnvType.CLIENT)
public final class InputCompat {

    private InputCompat() {}

    /**
     * Check if a key is currently pressed.
     * In MC 1.21.9-1.21.10, uses Window directly (like 1.21.11+).
     */
    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }
}
