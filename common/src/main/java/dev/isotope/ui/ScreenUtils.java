package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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
}
