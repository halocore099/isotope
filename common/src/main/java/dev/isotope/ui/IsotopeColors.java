package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Color palette for ISOTOPE UI - Vanilla Minecraft style.
 * Uses colors that match Minecraft's native UI aesthetic.
 */
@Environment(EnvType.CLIENT)
public final class IsotopeColors {

    // === Vanilla-style backgrounds ===
    // Based on inventory/creative menu colors
    public static final int BACKGROUND_DARK = 0xC0101010;       // Semi-transparent dark
    public static final int BACKGROUND_MEDIUM = 0xFF1A1A1A;     // Medium dark
    public static final int BACKGROUND_SOLID = 0xFF0F0F0F;      // Solid dark
    public static final int BACKGROUND_PANEL = 0xFF1E1E1E;      // Panel background
    public static final int BACKGROUND_SLOT = 0xFF8B8B8B;       // Slot gray (like inventory)
    public static final int BACKGROUND_SLOT_DARK = 0xFF373737;  // Darker slot

    // === Vanilla text colors ===
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;          // White
    public static final int TEXT_SECONDARY = 0xFFA0A0A0;        // Gray
    public static final int TEXT_MUTED = 0xFF707070;            // Dark gray
    public static final int TEXT_TITLE = 0xFF404040;            // Dark title (like inventory)
    public static final int TEXT_DISABLED = 0xFF505050;

    // === Vanilla accent colors ===
    public static final int ACCENT_GOLD = 0xFFFFD700;           // Gold/yellow
    public static final int ACCENT_GREEN = 0xFF55FF55;          // Minecraft green
    public static final int ACCENT_AQUA = 0xFF55FFFF;           // Aqua/cyan
    public static final int ACCENT_RED = 0xFFFF5555;            // Red

    // === Cyan variants (for backward compatibility) ===
    public static final int ACCENT_CYAN = ACCENT_AQUA;          // Alias for aqua
    public static final int ACCENT_CYAN_DARK = 0xFF2A8080;      // Darker cyan
    public static final int ACCENT_CYAN_HOVER = 0xFF77FFFF;     // Lighter cyan for hover

    // === Status colors (vanilla-style) ===
    public static final int STATUS_SUCCESS = 0xFF55FF55;        // Green
    public static final int STATUS_WARNING = 0xFFFFFF55;        // Yellow
    public static final int STATUS_ERROR = 0xFFFF5555;          // Red

    // === Confidence level colors ===
    public static final int CONFIDENCE_MANUAL = 0xFF55FF55;     // Green - author defined
    public static final int CONFIDENCE_VERIFIED = 0xFF55FFFF;   // Cyan - runtime verified
    public static final int CONFIDENCE_HIGH = 0xFFAAFFAA;       // Light green
    public static final int CONFIDENCE_MEDIUM = 0xFFFFFF55;     // Yellow
    public static final int CONFIDENCE_LOW = 0xFFFFAA00;        // Orange

    // === Badge colors ===
    public static final int BADGE_HAS_LOOT = 0xFF55AA55;        // Muted green
    public static final int BADGE_NO_LOOT = 0xFF555555;         // Gray
    public static final int BADGE_VANILLA = 0xFF5555FF;         // Blue
    public static final int BADGE_MODIFIED = 0xFFFFAA00;        // Orange

    // === Border colors (vanilla stone-like) ===
    public static final int BORDER_OUTER_DARK = 0xFF000000;     // Black
    public static final int BORDER_OUTER_LIGHT = 0xFF555555;    // Gray
    public static final int BORDER_INNER = 0xFF373737;          // Dark gray
    public static final int BORDER_DEFAULT = 0xFF404040;
    public static final int BORDER_HIGHLIGHT = 0xFF606060;
    public static final int BORDER_SELECTED = 0xFFFFFFFF;       // White outline
    public static final int BORDER_HOVER = 0xFF707070;          // Hover state

    // === Destructive action colors (for delete/remove) ===
    public static final int DESTRUCTIVE_BACKGROUND = 0xFF5A2020;
    public static final int DESTRUCTIVE_HOVER = 0xFF7A3030;
    public static final int DESTRUCTIVE_TEXT = 0xFFFF8888;

    // === List colors (vanilla-style selection) ===
    public static final int LIST_SELECTED = 0x80FFFFFF;         // Semi-transparent white
    public static final int LIST_HOVER = 0x40FFFFFF;            // Light hover
    public static final int LIST_ITEM_BG = 0x00000000;          // Transparent
    public static final int LIST_ITEM_HOVER = 0x40FFFFFF;
    public static final int LIST_ITEM_SELECTED = 0x60FFFFFF;

    // === Scrollbar colors ===
    public static final int SCROLLBAR_TRACK = 0xFF1E1E1E;
    public static final int SCROLLBAR_THUMB = 0xFF8B8B8B;
    public static final int SCROLLBAR_THUMB_HOVER = 0xFFC6C6C6;

    // === Button colors (vanilla style) ===
    public static final int BUTTON_BACKGROUND = 0xFF555555;
    public static final int BUTTON_HOVER = 0xFF666666;
    public static final int BUTTON_PRESSED = 0xFF444444;
    public static final int BUTTON_DISABLED = 0xFF303030;

    // === Tab colors ===
    public static final int TAB_ACTIVE = 0xFFC6C6C6;            // Light gray
    public static final int TAB_INACTIVE = 0xFF555555;          // Dark gray
    public static final int TAB_HOVER = 0xFF7A7A7A;

    private IsotopeColors() {}

    /**
     * Get color with alpha component.
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
