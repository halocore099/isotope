package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Utility methods for screen-related operations.
 * Provides safe parsing, string manipulation, and other helpers.
 */
@Environment(EnvType.CLIENT)
public final class ScreenUtils {

    private ScreenUtils() {}

    /**
     * Safely parse an integer from a string, returning a default value on failure.
     *
     * @param s          The string to parse
     * @param defaultVal The default value if parsing fails
     * @return The parsed integer or the default value
     */
    public static int parseIntSafe(String s, int defaultVal) {
        if (s == null || s.isEmpty()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Safely parse a float from a string, returning a default value on failure.
     *
     * @param s          The string to parse
     * @param defaultVal The default value if parsing fails
     * @return The parsed float or the default value
     */
    public static float parseFloatSafe(String s, float defaultVal) {
        if (s == null || s.isEmpty()) {
            return defaultVal;
        }
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Safely parse a percentage from a string, clamping to 0-100 range.
     *
     * @param s          The string to parse
     * @param defaultVal The default value if parsing fails
     * @return The parsed percentage clamped to 0-100, or the default value
     */
    public static float parsePercentSafe(String s, float defaultVal) {
        float val = parseFloatSafe(s, defaultVal);
        return Math.max(0, Math.min(100, val));
    }

    /**
     * Truncate a string to a maximum length, adding ".." if truncated.
     *
     * @param s      The string to truncate
     * @param maxLen The maximum length
     * @return The truncated string or the original if shorter than maxLen
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }

    /**
     * Format an item name from a ResourceLocation.
     * Converts underscores to spaces and capitalizes words.
     * Example: "minecraft:golden_apple" -&gt; "Golden Apple"
     *
     * @param itemId The ResourceLocation of the item
     * @return The formatted display name
     */
    public static String formatItemName(ResourceLocation itemId) {
        return formatItemName(itemId != null ? itemId.getPath() : "");
    }

    /**
     * Format an item name from a ResourceLocation path.
     * Converts underscores to spaces and capitalizes words.
     * Example: "golden_apple" -&gt; "Golden Apple"
     *
     * @param path The ResourceLocation path
     * @return The formatted display name
     */
    public static String formatItemName(String path) {
        if (path == null || path.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : path.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Clamp an integer value to a range.
     *
     * @param value The value to clamp
     * @param min   The minimum value
     * @param max   The maximum value
     * @return The clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp a float value to a range.
     *
     * @param value The value to clamp
     * @param min   The minimum value
     * @param max   The maximum value
     * @return The clamped value
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Check if mouse coordinates are within a rectangular bounds.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param x      Rectangle X position
     * @param y      Rectangle Y position
     * @param width  Rectangle width
     * @param height Rectangle height
     * @return True if mouse is within bounds
     */
    public static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Draw a standard dialog background with border.
     * Renders a 2-pixel outer border and filled background.
     *
     * @param graphics The GuiGraphics context
     * @param x        Dialog X position
     * @param y        Dialog Y position
     * @param width    Dialog width
     * @param height   Dialog height
     */
    public static void drawDialogBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(x, y, x + width, y + height, IsotopeColors.BACKGROUND_MEDIUM);
    }
}
