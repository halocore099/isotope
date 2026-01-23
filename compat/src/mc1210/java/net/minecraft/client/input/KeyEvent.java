package net.minecraft.client.input;

/**
 * Shim class for MC 1.21.10 and earlier.
 * In MC 1.21.11+, this is a real Minecraft class.
 * This allows common code to use the same event-based API across versions.
 */
public record KeyEvent(int key, int scancode, int modifiers) {
}
