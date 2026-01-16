package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Color theme definition for ISOTOPE UI.
 * Contains all themeable colors with their values.
 */
@Environment(EnvType.CLIENT)
public record ColorTheme(
    String name,

    // === Backgrounds ===
    int backgroundDark,
    int backgroundMedium,
    int backgroundSolid,
    int backgroundPanel,
    int backgroundSlot,
    int backgroundSlotDark,

    // === Text ===
    int textPrimary,
    int textSecondary,
    int textMuted,
    int textTitle,
    int textDisabled,

    // === Borders ===
    int borderOuterDark,
    int borderOuterLight,
    int borderInner,
    int borderDefault,
    int borderHighlight,
    int borderSelected,
    int borderHover,

    // === Buttons ===
    int buttonBackground,
    int buttonHover,
    int buttonPressed,
    int buttonDisabled,
    int buttonBorderLight,
    int buttonBorderDark,

    // === Inputs ===
    int inputBackground,
    int inputBackgroundActive,
    int inputBackgroundHover,
    int inputBorder,

    // === Entries/Pools ===
    int entryBackground,
    int entryBackgroundHover,
    int entryBackgroundSelected,
    int poolHeaderBackground,
    int poolHeaderHover,

    // === Scrollbar ===
    int scrollbarTrack,
    int scrollbarThumb,
    int scrollbarThumbHover,

    // === Tabs ===
    int tabActive,
    int tabInactive,
    int tabHover,

    // === Tooltip ===
    int tooltipBackground,
    int tooltipBorder,

    // === Overlays ===
    int overlayDim,
    int overlayDark,
    int overlayDarker,

    // === List ===
    int listSelected,
    int listHover,
    int listItemBg,
    int listItemHover,
    int listItemSelected
) {
    /**
     * Dark theme - default ISOTOPE appearance.
     */
    public static final ColorTheme DARK = new ColorTheme(
        "Dark",
        // Backgrounds
        0xC0101010,  // backgroundDark
        0xFF1A1A1A,  // backgroundMedium
        0xFF0F0F0F,  // backgroundSolid
        0xFF1E1E1E,  // backgroundPanel
        0xFF8B8B8B,  // backgroundSlot
        0xFF373737,  // backgroundSlotDark
        // Text
        0xFFFFFFFF,  // textPrimary
        0xFFA0A0A0,  // textSecondary
        0xFF707070,  // textMuted
        0xFF404040,  // textTitle
        0xFF505050,  // textDisabled
        // Borders
        0xFF000000,  // borderOuterDark
        0xFF555555,  // borderOuterLight
        0xFF373737,  // borderInner
        0xFF404040,  // borderDefault
        0xFF606060,  // borderHighlight
        0xFFFFFFFF,  // borderSelected
        0xFF707070,  // borderHover
        // Buttons
        0xFF555555,  // buttonBackground
        0xFF666666,  // buttonHover
        0xFF444444,  // buttonPressed
        0xFF303030,  // buttonDisabled
        0xFFC6C6C6,  // buttonBorderLight
        0xFF2A2A2A,  // buttonBorderDark
        // Inputs
        0xFF303030,  // inputBackground
        0xFF252525,  // inputBackgroundActive
        0xFF404040,  // inputBackgroundHover
        0xFF505050,  // inputBorder
        // Entries/Pools
        0xFF2A2A2A,  // entryBackground
        0xFF3A3A3A,  // entryBackgroundHover
        0xFF3A3A3A,  // entryBackgroundSelected
        0xFF252525,  // poolHeaderBackground
        0xFF353535,  // poolHeaderHover
        // Scrollbar
        0xFF1E1E1E,  // scrollbarTrack
        0xFF8B8B8B,  // scrollbarThumb
        0xFFC6C6C6,  // scrollbarThumbHover
        // Tabs
        0xFFC6C6C6,  // tabActive
        0xFF555555,  // tabInactive
        0xFF7A7A7A,  // tabHover
        // Tooltip
        0xFF1A1A1A,  // tooltipBackground
        0xFF000000,  // tooltipBorder
        // Overlays
        0x90000000,  // overlayDim
        0x80000000,  // overlayDark
        0xC0000000,  // overlayDarker
        // List
        0x80FFFFFF,  // listSelected
        0x40FFFFFF,  // listHover
        0x00000000,  // listItemBg
        0x40FFFFFF,  // listItemHover
        0x60FFFFFF   // listItemSelected
    );

    /**
     * Light theme - lighter appearance for better visibility in bright environments.
     */
    public static final ColorTheme LIGHT = new ColorTheme(
        "Light",
        // Backgrounds
        0xC0FFFFFF,  // backgroundDark (inverted to light)
        0xFFE8E8E8,  // backgroundMedium
        0xFFF5F5F5,  // backgroundSolid
        0xFFEEEEEE,  // backgroundPanel
        0xFFCCCCCC,  // backgroundSlot
        0xFFDDDDDD,  // backgroundSlotDark
        // Text
        0xFF1A1A1A,  // textPrimary (dark text on light)
        0xFF555555,  // textSecondary
        0xFF888888,  // textMuted
        0xFF333333,  // textTitle
        0xFFAAAAAA,  // textDisabled
        // Borders
        0xFFCCCCCC,  // borderOuterDark
        0xFFAAAAAA,  // borderOuterLight
        0xFFDDDDDD,  // borderInner
        0xFFBBBBBB,  // borderDefault
        0xFF999999,  // borderHighlight
        0xFF3794FF,  // borderSelected (blue for visibility)
        0xFF888888,  // borderHover
        // Buttons
        0xFFDDDDDD,  // buttonBackground
        0xFFCCCCCC,  // buttonHover
        0xFFBBBBBB,  // buttonPressed
        0xFFEEEEEE,  // buttonDisabled
        0xFFFFFFFF,  // buttonBorderLight
        0xFFAAAAAA,  // buttonBorderDark
        // Inputs
        0xFFFFFFFF,  // inputBackground
        0xFFF5F5F5,  // inputBackgroundActive
        0xFFEEEEEE,  // inputBackgroundHover
        0xFFCCCCCC,  // inputBorder
        // Entries/Pools
        0xFFFFFFFF,  // entryBackground
        0xFFF0F0F0,  // entryBackgroundHover
        0xFFE8F4FF,  // entryBackgroundSelected (light blue tint)
        0xFFF5F5F5,  // poolHeaderBackground
        0xFFEEEEEE,  // poolHeaderHover
        // Scrollbar
        0xFFE8E8E8,  // scrollbarTrack
        0xFFAAAAAA,  // scrollbarThumb
        0xFF888888,  // scrollbarThumbHover
        // Tabs
        0xFF555555,  // tabActive (dark for contrast)
        0xFFCCCCCC,  // tabInactive
        0xFF999999,  // tabHover
        // Tooltip
        0xFFFFFFF0,  // tooltipBackground (cream)
        0xFFCCCCCC,  // tooltipBorder
        // Overlays
        0x60000000,  // overlayDim (lighter overlay)
        0x50000000,  // overlayDark
        0x80000000,  // overlayDarker
        // List
        0x403794FF,  // listSelected (blue tint)
        0x20000000,  // listHover
        0x00000000,  // listItemBg
        0x20000000,  // listItemHover
        0x303794FF   // listItemSelected
    );
}
