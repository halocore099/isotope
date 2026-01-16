package dev.isotope.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * UI layout constants for ISOTOPE.
 * Centralizes common dimensions, paddings, and sizes used across the UI.
 */
@Environment(EnvType.CLIENT)
public final class UIConstants {

    private UIConstants() {}

    // === Text Positioning ===
    /** Small horizontal offset for text alignment (2 pixels). */
    public static final int TEXT_OFFSET_X = 2;

    /** Small vertical offset for text alignment (3 pixels). */
    public static final int TEXT_OFFSET_Y = 3;

    /** Common text X offset from left edge (6 pixels). */
    public static final int TEXT_PADDING_X = 6;

    // === Padding & Spacing ===
    /** Small padding between elements (2 pixels). */
    public static final int PADDING_TINY = 2;

    /** Standard small padding (4 pixels). */
    public static final int PADDING_SMALL = 4;

    /** Standard padding (5 pixels). */
    public static final int PADDING = 5;

    /** Medium padding (8 pixels). */
    public static final int PADDING_MEDIUM = 8;

    /** Standard spacing between elements (10 pixels). */
    public static final int SPACING = 10;

    // === Heights ===
    /** Small row height (14 pixels). */
    public static final int ROW_HEIGHT_SMALL = 14;

    /** Standard icon size (16 pixels). */
    public static final int ICON_SIZE = 16;

    /** Standard item/row height (18 pixels). */
    public static final int ROW_HEIGHT = 18;

    /** Standard button height (20 pixels). */
    public static final int BUTTON_HEIGHT = 20;

    /** Standard list item height (22 pixels). */
    public static final int ITEM_HEIGHT = 22;

    /** Larger list item height (24 pixels). */
    public static final int ITEM_HEIGHT_LARGE = 24;

    // === Widths ===
    /** Default button width (200 pixels). */
    public static final int BUTTON_WIDTH_DEFAULT = 200;

    /** Small button width (60 pixels). */
    public static final int BUTTON_WIDTH_SMALL = 60;

    /** Medium button width (80 pixels). */
    public static final int BUTTON_WIDTH_MEDIUM = 80;

    /** Scrollbar width (5 pixels). */
    public static final int SCROLLBAR_WIDTH = 5;

    /** Thin scrollbar width (6 pixels). */
    public static final int SCROLLBAR_WIDTH_THIN = 6;

    // === Borders & Lines ===
    /** Standard border thickness (1 pixel). */
    public static final int BORDER_THICKNESS = 1;

    /** Thick border/accent bar (2 pixels). */
    public static final int BORDER_THICK = 2;

    /** Focus indicator thickness (3 pixels). */
    public static final int FOCUS_INDICATOR = 3;

    // === Drag & Drop ===
    /** Drag threshold in pixels. */
    public static final int DRAG_THRESHOLD = 5;

    /** Drag indicator arrow offset for entries. */
    public static final int DRAG_ARROW_OFFSET = 4;

    // === Icons & Rendering ===
    /** Item icon scale factor. */
    public static final float ITEM_ICON_SCALE = 0.75f;

    /** Item icon display position X offset. */
    public static final int ITEM_ICON_OFFSET_X = 22;

    // === Dialog Sizes ===
    /** Small dialog width (280 pixels). */
    public static final int DIALOG_WIDTH_SMALL = 280;

    /** Standard dialog width (320 pixels). */
    public static final int DIALOG_WIDTH = 320;

    /** Medium dialog width (400 pixels). */
    public static final int DIALOG_WIDTH_MEDIUM = 400;

    /** Large dialog width (450 pixels). */
    public static final int DIALOG_WIDTH_LARGE = 450;

    // === Panel Sizes ===
    /** Standard left panel width (200 pixels). */
    public static final int LEFT_PANEL_WIDTH = 200;

    /** Tab bar height (22 pixels). */
    public static final int TAB_BAR_HEIGHT = 22;

    /** Header height (30 pixels). */
    public static final int HEADER_HEIGHT = 30;

    /** Status bar height (20 pixels). */
    public static final int STATUS_BAR_HEIGHT = 20;

    // === Keyboard Key Codes ===
    /** Escape key code (GLFW_KEY_ESCAPE). */
    public static final int KEY_ESCAPE = 256;

    /** Enter key code (GLFW_KEY_ENTER). */
    public static final int KEY_ENTER = 257;

    /** Tab key code (GLFW_KEY_TAB). */
    public static final int KEY_TAB = 258;

    /** Backspace key code (GLFW_KEY_BACKSPACE). */
    public static final int KEY_BACKSPACE = 259;

    /** Up arrow key code (GLFW_KEY_UP). */
    public static final int KEY_UP = 265;

    /** Down arrow key code (GLFW_KEY_DOWN). */
    public static final int KEY_DOWN = 264;

    /** Numpad Enter key code (GLFW_KEY_KP_ENTER). */
    public static final int KEY_NUMPAD_ENTER = 335;
}
