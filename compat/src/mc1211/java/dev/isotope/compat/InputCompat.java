package dev.isotope.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * MC 1.21.11+ input compatibility.
 *
 * In MC 1.21.11+, InputConstants.isKeyDown() takes (Window, int key).
 */
@Environment(EnvType.CLIENT)
public final class InputCompat {

    private InputCompat() {}

    /**
     * Check if a key is currently pressed.
     * In MC 1.21.11+, uses Window directly.
     */
    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }
}
