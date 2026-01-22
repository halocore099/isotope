package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Color palette for ISOTOPE UI - Vanilla Minecraft style.
 * Uses colors that match Minecraft's native UI aesthetic.
 *
 * <p>This class provides two ways to access colors:</p>
 * <ul>
 *   <li><b>Constants</b> (e.g., {@code BACKGROUND_MEDIUM}) - Fixed values for dark theme.
 *       Use these for backward compatibility or when theme support isn't needed.</li>
 *   <li><b>Methods</b> (e.g., {@code background()}) - Theme-aware accessors that return
 *       different values based on the current theme. Prefer these for new code.</li>
 * </ul>
 *
 * @see ThemeManager
 * @see ColorTheme
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

    // === Loot source type colors ===
    public static final int SOURCE_STRUCTURE = 0xFF55FFFF;      // Cyan - real structures
    public static final int SOURCE_FEATURE = 0xFFFFAA00;        // Orange - features (dungeons, etc.)
    public static final int SOURCE_MOB = 0xFFAA55FF;            // Purple - mob/entity drops

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
    public static final int DESTRUCTIVE_BORDER_LIGHT = 0xFF8A4040;
    public static final int DESTRUCTIVE_BORDER_DARK = 0xFF3A1010;

    // === Success/Add button colors ===
    public static final int SUCCESS_BACKGROUND = 0xFF3A5A3A;
    public static final int SUCCESS_HOVER = 0xFF4A6A4A;
    public static final int SUCCESS_BORDER_LIGHT = 0xFF5A7A5A;
    public static final int SUCCESS_BORDER_DARK = 0xFF1A3A1A;

    // === Button 3D border colors ===
    public static final int BUTTON_BORDER_LIGHT = 0xFFC6C6C6;
    public static final int BUTTON_BORDER_DARK = 0xFF2A2A2A;

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

    // === Drag and drop indicators ===
    public static final int DRAG_ENTRY_INDICATOR = 0xFF55FF55;  // Green line for entry drag
    public static final int DRAG_POOL_INDICATOR = 0xFF55FFFF;   // Cyan line for pool drag
    public static final int DRAG_GHOST_ENTRY = 0x8055FF55;      // Semi-transparent green
    public static final int DRAG_GHOST_POOL = 0x8055FFFF;       // Semi-transparent cyan

    // === Tooltip colors ===
    public static final int TOOLTIP_BACKGROUND = 0xFF1A1A1A;
    public static final int TOOLTIP_BORDER = 0xFF000000;
    public static final int TOOLTIP_BACKGROUND_WARNING = 0xFF252540;

    // === Entry/Pool edit panel colors ===
    public static final int ENTRY_BACKGROUND = 0xFF2A2A2A;
    public static final int ENTRY_BACKGROUND_HOVER = 0xFF3A3A3A;
    public static final int ENTRY_BACKGROUND_SELECTED = 0xFF3A3A3A;
    public static final int ENTRY_BACKGROUND_EDITED = 0xFF2A3A2A;      // Green tint for edited
    public static final int ENTRY_BACKGROUND_EDITED_HOVER = 0xFF3A4A3A;
    public static final int POOL_HEADER_BACKGROUND = 0xFF252525;
    public static final int POOL_HEADER_HOVER = 0xFF353535;

    // === Input field colors ===
    public static final int INPUT_BACKGROUND = 0xFF303030;
    public static final int INPUT_BACKGROUND_ACTIVE = 0xFF252525;
    public static final int INPUT_BORDER = 0xFF505050;
    public static final int INPUT_BORDER_FOCUS = ACCENT_AQUA;

    // === Success color alias ===
    public static final int SUCCESS = STATUS_SUCCESS;

    // === Multi-select/batch colors ===
    public static final int BATCH_BAR_BACKGROUND = 0xFF2A2A3A;          // Purple tint background
    public static final int BATCH_ACCENT = 0xFF9966FF;                  // Purple accent for selection
    public static final int BATCH_BUTTON_BACKGROUND = 0xFF3A3A3A;
    public static final int BATCH_BUTTON_HOVER = 0xFF4A3A3A;

    // === Function/Condition section colors ===
    public static final int FUNC_COND_BACKGROUND = 0xFF252530;          // Dark blue tint for function sections
    public static final int CONDITION_KEYWORD = 0xFFCC9900;             // "if" keyword color (gold/orange)
    public static final int CONDITION_TEXT = 0xFF88BBDD;                // Condition name color (light blue)
    public static final int FUNCTION_COND_BTN_BG = 0xFF303040;          // "+ if" button background
    public static final int FUNCTION_COND_BTN_TEXT = 0xFF8899AA;        // "+ if" button text

    // === Selection/editing colors ===
    public static final int MULTI_SELECT_BACKGROUND = 0xFF3A2A4A;       // Purple tint for multi-selected
    public static final int MULTI_SELECT_BORDER = 0xFF9966FF;           // Purple outline for multi-selected
    public static final int SINGLE_SELECT_BACKGROUND = 0xFF2A3A4A;      // Blue tint for selected

    // === Input field state colors ===
    public static final int INPUT_BACKGROUND_HOVER = 0xFF404040;

    // === Browser category colors ===
    public static final int CATEGORY_BOOKMARKS = 0xFF2A2520;           // Gold tint
    public static final int CATEGORY_BOOKMARKS_HOVER = 0xFF3A3530;
    public static final int CATEGORY_RECENT = 0xFF202530;              // Cyan tint
    public static final int CATEGORY_RECENT_HOVER = 0xFF303540;
    public static final int CATEGORY_ENTITIES = 0xFF303030;            // Neutral
    public static final int CATEGORY_ENTITIES_HOVER = 0xFF353535;

    // === Browser item selection colors ===
    public static final int ITEM_BOOKMARKS_SELECTED = 0xFF5A4A2A;
    public static final int ITEM_BOOKMARKS_HOVER = 0xFF3A3020;
    public static final int ITEM_RECENT_SELECTED = 0xFF2A4A5A;
    public static final int ITEM_RECENT_HOVER = 0xFF253035;
    public static final int ITEM_ENTITY_SELECTED = 0xFF3A5A8A;
    public static final int STAR_MUTED = 0xFF666666;

    // === Feature tooltip colors ===
    public static final int TOOLTIP_FEATURE_BG = 0xFF3E2A1A;
    public static final int TOOLTIP_FEATURE_INNER = 0xFF402520;
    public static final int TOOLTIP_ENTITY_BG = 0xFF2A1A3E;
    public static final int TOOLTIP_ENTITY_INNER = 0xFF352540;

    // === Additional UI colors ===
    public static final int BACKGROUND_DARKER = 0xFF222222;
    public static final int BACKGROUND_DARKEST = 0xFF2D2D2D;
    public static final int ERROR_BRIGHT = 0xFFF14C4C;
    public static final int ACCENT_BLUE = 0xFF55AAFF;
    public static final int SYNTAX_CYAN = 0xFF4EC9B0;
    public static final int SYNTAX_BLUE = 0xFF5A8AB8;
    public static final int SYNTAX_BLUE_DARK = 0xFF3D5C7A;
    public static final int SUCCESS_LIGHT = 0xFF88FF88;
    public static final int SUCCESS_MUTED = 0xFF88CC88;
    public static final int SUCCESS_TINT = 0xFF2A4A2A;

    // === Dialog overlay ===
    public static final int OVERLAY_DIM = 0x90000000;
    public static final int OVERLAY_DARK = 0x80000000;
    public static final int OVERLAY_DARKER = 0xC0000000;

    // === Toggle button colors ===
    public static final int TOGGLE_OFF_BG = 0xFF3A3A2A;
    public static final int TOGGLE_ON_TEXT = 0xFF66AA66;
    public static final int TOGGLE_OFF_TEXT = 0xFFAAAA66;

    // === Function/template editor colors ===
    public static final int FUNC_ADD_HOVER = 0xFF3A6A4A;
    public static final int FUNC_ADD_BORDER = 0xFF5A9A6A;
    public static final int FUNC_ADD_BORDER_DEFAULT = 0xFF4A7A5A;
    public static final int FUNC_ICON_BG = 0xFF3A5A4A;

    // === Compare/diff highlight colors ===
    public static final int DIFF_HIGHLIGHT = 0xFF3A2A1A;

    // === Test mode colors ===
    public static final int TEST_INDICATOR = 0xFF44AA44;
    public static final int MOB_LOOT_BG = 0xFF2A1A3A;
    public static final int MOB_LOOT_BORDER = 0xFF4A3A5A;
    public static final int STRUCTURE_INFO = 0xFF88AA88;

    // === Loading screen colors ===
    public static final int LOADING_BG = 0xFF0A0A0A;

    // === Template picker colors ===
    public static final int CATEGORY_CUSTOM = 0xFFC9A656;
    public static final int CATEGORY_BUILTIN = 0xFF8899AA;
    public static final int CUSTOM_TEMPLATE_BG = 0xFF2E2A24;
    public static final int CUSTOM_TEMPLATE_BORDER = 0xFF3D3A30;
    public static final int CUSTOM_BADGE_BG = 0xFF4A3D20;
    public static final int CUSTOM_BADGE_BORDER = 0xFF6A5A30;
    public static final int WEIGHT_BADGE_OUTLINE = 0xFF484848;

    // === Mob badge colors ===
    public static final int MOB_BADGE_BG = 0xFF3A2A4A;

    // === Template manager colors ===
    public static final int SELECTED_BORDER = 0xFF5A7A9A;
    public static final int EDIT_BUTTON_HOVER = 0xFF4A5A6A;
    public static final int DELETE_BUTTON = 0xFF5A3A3A;
    public static final int DELETE_BUTTON_HOVER = 0xFF6A4A4A;
    public static final int CONFIRM_DELETE_HOVER = 0xFF8A4A4A;
    public static final int CONFIRM_DELETE = 0xFF6A3A3A;

    // === Validation panel colors ===
    public static final int ORPHAN_HOVER = 0xFF4A4030;
    public static final int FIX_BUTTON_BG = 0xFF2D5A3D;

    // === Loot flow panel colors ===
    public static final int EDITED_TABLE_BG = 0xFF2A3A2A;

    // === Toast colors ===
    public static final int TOAST_INFO = 0xFF3794FF;
    public static final int TOAST_BACKGROUND = 0xFF1E1E1E;
    public static final int TOAST_BORDER = 0xFF454545;
    public static final int TOAST_TITLE = 0xFFE0E0E0;
    public static final int TOAST_MESSAGE = 0xFF969696;

    private IsotopeColors() {}

    /**
     * Get color with alpha component.
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    // ========================================================================
    // THEME-AWARE ACCESSORS
    // These methods return colors based on the current theme.
    // Prefer these over constants for new code.
    // ========================================================================

    private static ThemeManager theme() {
        return ThemeManager.getInstance();
    }

    /** Theme-aware background (semi-transparent dark/light). */
    public static int backgroundDark() {
        return theme().getTheme().backgroundDark();
    }

    /** Theme-aware medium background. */
    public static int backgroundMedium() {
        return theme().getTheme().backgroundMedium();
    }

    /** Theme-aware solid background. */
    public static int backgroundSolid() {
        return theme().getTheme().backgroundSolid();
    }

    /** Theme-aware panel background. */
    public static int backgroundPanel() {
        return theme().getTheme().backgroundPanel();
    }

    /** Theme-aware primary text color. */
    public static int textPrimary() {
        return theme().getTheme().textPrimary();
    }

    /** Theme-aware secondary text color. */
    public static int textSecondary() {
        return theme().getTheme().textSecondary();
    }

    /** Theme-aware muted text color. */
    public static int textMuted() {
        return theme().getTheme().textMuted();
    }

    /** Theme-aware title text color. */
    public static int textTitle() {
        return theme().getTheme().textTitle();
    }

    /** Theme-aware disabled text color. */
    public static int textDisabled() {
        return theme().getTheme().textDisabled();
    }

    /** Theme-aware outer dark border. */
    public static int borderOuterDark() {
        return theme().getTheme().borderOuterDark();
    }

    /** Theme-aware inner border. */
    public static int borderInner() {
        return theme().getTheme().borderInner();
    }

    /** Theme-aware default border. */
    public static int borderDefault() {
        return theme().getTheme().borderDefault();
    }

    /** Theme-aware highlight border. */
    public static int borderHighlight() {
        return theme().getTheme().borderHighlight();
    }

    /** Theme-aware selected border. */
    public static int borderSelected() {
        return theme().getTheme().borderSelected();
    }

    /** Theme-aware hover border. */
    public static int borderHover() {
        return theme().getTheme().borderHover();
    }

    /** Theme-aware button background. */
    public static int buttonBackground() {
        return theme().getTheme().buttonBackground();
    }

    /** Theme-aware button hover background. */
    public static int buttonHover() {
        return theme().getTheme().buttonHover();
    }

    /** Theme-aware button pressed background. */
    public static int buttonPressed() {
        return theme().getTheme().buttonPressed();
    }

    /** Theme-aware disabled button background. */
    public static int buttonDisabled() {
        return theme().getTheme().buttonDisabled();
    }

    /** Theme-aware input background. */
    public static int inputBackground() {
        return theme().getTheme().inputBackground();
    }

    /** Theme-aware active input background. */
    public static int inputBackgroundActive() {
        return theme().getTheme().inputBackgroundActive();
    }

    /** Theme-aware input border. */
    public static int inputBorder() {
        return theme().getTheme().inputBorder();
    }

    /** Theme-aware entry background. */
    public static int entryBackground() {
        return theme().getTheme().entryBackground();
    }

    /** Theme-aware entry hover background. */
    public static int entryBackgroundHover() {
        return theme().getTheme().entryBackgroundHover();
    }

    /** Theme-aware entry selected background. */
    public static int entryBackgroundSelected() {
        return theme().getTheme().entryBackgroundSelected();
    }

    /** Theme-aware pool header background. */
    public static int poolHeaderBackground() {
        return theme().getTheme().poolHeaderBackground();
    }

    /** Theme-aware pool header hover background. */
    public static int poolHeaderHover() {
        return theme().getTheme().poolHeaderHover();
    }

    /** Theme-aware scrollbar track. */
    public static int scrollbarTrack() {
        return theme().getTheme().scrollbarTrack();
    }

    /** Theme-aware scrollbar thumb. */
    public static int scrollbarThumb() {
        return theme().getTheme().scrollbarThumb();
    }

    /** Theme-aware scrollbar thumb hover. */
    public static int scrollbarThumbHover() {
        return theme().getTheme().scrollbarThumbHover();
    }

    /** Theme-aware active tab. */
    public static int tabActive() {
        return theme().getTheme().tabActive();
    }

    /** Theme-aware inactive tab. */
    public static int tabInactive() {
        return theme().getTheme().tabInactive();
    }

    /** Theme-aware tab hover. */
    public static int tabHover() {
        return theme().getTheme().tabHover();
    }

    /** Theme-aware tooltip background. */
    public static int tooltipBackground() {
        return theme().getTheme().tooltipBackground();
    }

    /** Theme-aware tooltip border. */
    public static int tooltipBorder() {
        return theme().getTheme().tooltipBorder();
    }

    /** Theme-aware dim overlay. */
    public static int overlayDim() {
        return theme().getTheme().overlayDim();
    }

    /** Theme-aware dark overlay. */
    public static int overlayDark() {
        return theme().getTheme().overlayDark();
    }

    /** Theme-aware darker overlay. */
    public static int overlayDarker() {
        return theme().getTheme().overlayDarker();
    }

    /** Theme-aware list selected background. */
    public static int listSelected() {
        return theme().getTheme().listSelected();
    }

    /** Theme-aware list hover background. */
    public static int listHover() {
        return theme().getTheme().listHover();
    }

    /** Theme-aware list item background. */
    public static int listItemBg() {
        return theme().getTheme().listItemBg();
    }

    /** Theme-aware list item hover background. */
    public static int listItemHover() {
        return theme().getTheme().listItemHover();
    }

    /** Theme-aware list item selected background. */
    public static int listItemSelected() {
        return theme().getTheme().listItemSelected();
    }
}
