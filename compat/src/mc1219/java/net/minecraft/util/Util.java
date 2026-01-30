package net.minecraft.util;

/**
 * MC 1.21.0-1.21.10 shim for Util.
 *
 * In MC 1.21.10 and earlier, Util is at net.minecraft.Util.
 * This shim provides the same class at net.minecraft.util.Util
 * so shared code can import the 1.21.11+ location.
 */
public final class Util {
    private Util() {}

    /**
     * Get the platform (OS) utilities.
     */
    public static net.minecraft.Util.OS getPlatform() {
        return net.minecraft.Util.getPlatform();
    }
}
