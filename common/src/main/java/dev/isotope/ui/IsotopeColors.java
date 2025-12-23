package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Color palette for ISOTOPE UI.
 * Design: Neutral dark gray background, muted cyan accent, red for destructive actions.
 */
@Environment(EnvType.CLIENT)
public final class IsotopeColors {

    // Background colors
    public static final int BACKGROUND_DARK = 0xFF1A1A1A;
    public static final int BACKGROUND_MEDIUM = 0xFF2D2D2D;
    public static final int BACKGROUND_LIGHT = 0xFF3D3D3D;
    public static final int BACKGROUND_PANEL = 0xFF252525;

    // Accent colors
    public static final int ACCENT_CYAN = 0xFF4DD0E1;
    public static final int ACCENT_CYAN_DARK = 0xFF00ACC1;
    public static final int ACCENT_CYAN_HOVER = 0xFF80DEEA;

    // Text colors
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFB0B0B0;
    public static final int TEXT_MUTED = 0xFF808080;
    public static final int TEXT_DISABLED = 0xFF505050;

    // Status colors
    public static final int STATUS_SUCCESS = 0xFF4CAF50;
    public static final int STATUS_WARNING = 0xFFFF9800;
    public static final int STATUS_ERROR = 0xFFF44336;

    // Confidence level colors
    public static final int CONFIDENCE_EXACT = 0xFF4CAF50;      // Green
    public static final int CONFIDENCE_CONDITIONAL = 0xFFFFEB3B; // Yellow
    public static final int CONFIDENCE_OBSERVED = 0xFFFF9800;    // Orange
    public static final int CONFIDENCE_UNKNOWN = 0xFFF44336;     // Red

    // Badge colors
    public static final int BADGE_HAS_LOOT = 0xFF4CAF50;
    public static final int BADGE_NO_LOOT = 0xFF9E9E9E;
    public static final int BADGE_VANILLA = 0xFF2196F3;
    public static final int BADGE_MODIFIED = 0xFFFF9800;

    // Border colors
    public static final int BORDER_DEFAULT = 0xFF404040;
    public static final int BORDER_SELECTED = 0xFF4DD0E1;
    public static final int BORDER_HOVER = 0xFF606060;

    // Button colors
    public static final int BUTTON_BACKGROUND = 0xFF3D3D3D;
    public static final int BUTTON_HOVER = 0xFF4D4D4D;
    public static final int BUTTON_PRESSED = 0xFF2D2D2D;
    public static final int BUTTON_DISABLED = 0xFF2A2A2A;

    // Destructive action colors
    public static final int DESTRUCTIVE_BACKGROUND = 0xFF5D2A2A;
    public static final int DESTRUCTIVE_HOVER = 0xFF7D3A3A;
    public static final int DESTRUCTIVE_TEXT = 0xFFFF8A80;

    private IsotopeColors() {}

    /**
     * Get color with alpha component.
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Blend two colors.
     */
    public static int blend(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
