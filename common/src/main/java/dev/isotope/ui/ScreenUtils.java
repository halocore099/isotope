package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

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
     * Format an item name from a Identifier.
     * Converts underscores to spaces and capitalizes words.
     * Example: "minecraft:golden_apple" -&gt; "Golden Apple"
     *
     * @param itemId The Identifier of the item
     * @return The formatted display name
     */
    public static String formatItemName(Identifier itemId) {
        return formatItemName(itemId != null ? itemId.getPath() : "");
    }

    /**
     * Format an item name from a Identifier path.
     * Converts underscores to spaces and capitalizes words.
     * Example: "golden_apple" -&gt; "Golden Apple"
     *
     * @param path The Identifier path
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
     * Ensure a string has a namespace prefix.
     * If the input doesn't contain ':', prepends "minecraft:".
     * Useful for parsing user input that might omit the namespace.
     * Example: "diamond" -&gt; "minecraft:diamond"
     * Example: "modid:item" -&gt; "modid:item" (unchanged)
     *
     * @param input The input string
     * @return The string with a namespace prefix
     */
    public static String ensureNamespace(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.contains(":") ? input : "minecraft:" + input;
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

    /**
     * Check if a blinking cursor should be visible based on 500ms blink cycle.
     * Use this for text input cursor animation.
     *
     * @return True if cursor should be visible (alternates every 500ms)
     */
    public static boolean shouldShowCursor() {
        return (System.currentTimeMillis() / 500) % 2 == 0;
    }

    /**
     * Render a blinking text cursor at the specified position.
     * Only renders if the blink cycle indicates the cursor should be visible.
     *
     * @param graphics The GuiGraphics context
     * @param x        Cursor X position
     * @param y        Cursor top Y position
     * @param height   Cursor height in pixels
     * @param color    Cursor color
     */
    public static void renderCursor(GuiGraphics graphics, int x, int y, int height, int color) {
        if (shouldShowCursor()) {
            graphics.fill(x, y, x + 1, y + height, color);
        }
    }

    /**
     * Render a blinking text cursor with default color (TEXT_PRIMARY).
     *
     * @param graphics The GuiGraphics context
     * @param x        Cursor X position
     * @param y        Cursor top Y position
     * @param height   Cursor height in pixels
     */
    public static void renderCursor(GuiGraphics graphics, int x, int y, int height) {
        renderCursor(graphics, x, y, height, IsotopeColors.TEXT_PRIMARY);
    }

    /**
     * Render a vertical scrollbar with track and thumb.
     * Only renders if maxScroll > 0.
     *
     * @param graphics     The GuiGraphics context
     * @param x            Scrollbar X position (left edge of track)
     * @param y            Scrollbar Y position (top of track)
     * @param trackWidth   Width of the scrollbar track
     * @param trackHeight  Height of the scrollbar track
     * @param scrollOffset Current scroll offset
     * @param maxScroll    Maximum scroll value
     * @param trackColor   Color for the track background
     * @param thumbColor   Color for the thumb
     */
    public static void renderScrollbar(GuiGraphics graphics, int x, int y, int trackWidth, int trackHeight,
                                        int scrollOffset, int maxScroll, int trackColor, int thumbColor) {
        if (maxScroll <= 0) return;

        // Draw track
        graphics.fill(x, y, x + trackWidth, y + trackHeight, trackColor);

        // Calculate thumb size and position
        int thumbHeight = Math.max(20, (int) ((float) trackHeight / (trackHeight + maxScroll) * trackHeight));
        int thumbY = y + (int) ((float) scrollOffset / maxScroll * (trackHeight - thumbHeight));

        // Draw thumb
        graphics.fill(x, thumbY, x + trackWidth, thumbY + thumbHeight, thumbColor);
    }

    /**
     * Render a vertical scrollbar with default colors (SCROLLBAR_TRACK and SCROLLBAR_THUMB).
     *
     * @param graphics     The GuiGraphics context
     * @param x            Scrollbar X position (left edge of track)
     * @param y            Scrollbar Y position (top of track)
     * @param trackWidth   Width of the scrollbar track
     * @param trackHeight  Height of the scrollbar track
     * @param scrollOffset Current scroll offset
     * @param maxScroll    Maximum scroll value
     */
    public static void renderScrollbar(GuiGraphics graphics, int x, int y, int trackWidth, int trackHeight,
                                        int scrollOffset, int maxScroll) {
        renderScrollbar(graphics, x, y, trackWidth, trackHeight, scrollOffset, maxScroll,
            IsotopeColors.SCROLLBAR_TRACK, IsotopeColors.SCROLLBAR_THUMB);
    }

    /**
     * Render an input box with background and border.
     * Colors change based on focus state.
     *
     * @param graphics    The GuiGraphics context
     * @param x           Input box X position
     * @param y           Input box Y position
     * @param width       Input box width
     * @param height      Input box height
     * @param focused     Whether the input is focused
     * @param bgNormal    Background color when not focused
     * @param bgFocused   Background color when focused
     * @param borderNormal Border color when not focused
     * @param borderFocused Border color when focused
     */
    public static void renderInputBox(GuiGraphics graphics, int x, int y, int width, int height, boolean focused,
                                       int bgNormal, int bgFocused, int borderNormal, int borderFocused) {
        int bgColor = focused ? bgFocused : bgNormal;
        int borderColor = focused ? borderFocused : borderNormal;
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.renderOutline(x, y, width, height, borderColor);
    }

    /**
     * Render an input box with default colors.
     * Uses ENTRY_BACKGROUND/ENTRY_BACKGROUND_HOVER for background
     * and BORDER_DEFAULT/ACCENT_GREEN for border.
     *
     * @param graphics The GuiGraphics context
     * @param x        Input box X position
     * @param y        Input box Y position
     * @param width    Input box width
     * @param height   Input box height
     * @param focused  Whether the input is focused
     */
    public static void renderInputBox(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        renderInputBox(graphics, x, y, width, height, focused,
            IsotopeColors.ENTRY_BACKGROUND, IsotopeColors.ENTRY_BACKGROUND_HOVER,
            IsotopeColors.BORDER_DEFAULT, IsotopeColors.ACCENT_GREEN);
    }
}
