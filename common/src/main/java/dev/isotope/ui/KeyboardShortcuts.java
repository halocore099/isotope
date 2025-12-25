package dev.isotope.ui;

import org.lwjgl.glfw.GLFW;

/**
 * Centralized keyboard shortcut handling for ISOTOPE.
 *
 * Shortcuts:
 * - Ctrl+Z: Undo
 * - Ctrl+Y / Ctrl+Shift+Z: Redo
 * - Ctrl+S: Export/Save
 * - Ctrl+F: Focus search
 * - Ctrl+N: Add new item to selected pool
 * - Delete: Remove selected item
 * - Escape: Close picker / clear selection
 * - Ctrl+D: Duplicate selected entry
 * - Ctrl+C: Copy selected entry
 * - Ctrl+V: Paste entry
 * - F1: Show keyboard shortcuts help
 */
public final class KeyboardShortcuts {

    private KeyboardShortcuts() {}

    /**
     * Handle a key press and dispatch to the appropriate context method.
     *
     * @param keyCode   GLFW key code
     * @param modifiers GLFW modifier flags
     * @param context   The context to handle the shortcut
     * @return true if the shortcut was handled
     */
    public static boolean handle(int keyCode, int modifiers, ShortcutContext context) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;

        // Ctrl shortcuts
        if (ctrl && !alt) {
            return switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) {
                        context.redo();
                    } else {
                        context.undo();
                    }
                    yield true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    context.redo();
                    yield true;
                }
                case GLFW.GLFW_KEY_S -> {
                    context.save();
                    yield true;
                }
                case GLFW.GLFW_KEY_F -> {
                    if (shift) {
                        context.globalSearch();
                    } else {
                        context.focusSearch();
                    }
                    yield true;
                }
                case GLFW.GLFW_KEY_N -> {
                    context.addItem();
                    yield true;
                }
                case GLFW.GLFW_KEY_D -> {
                    context.duplicate();
                    yield true;
                }
                case GLFW.GLFW_KEY_C -> {
                    context.copy();
                    yield true;
                }
                case GLFW.GLFW_KEY_V -> {
                    context.paste();
                    yield true;
                }
                default -> false;
            };
        }

        // Non-Ctrl shortcuts
        return switch (keyCode) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                if (!ctrl) {
                    context.delete();
                    yield true;
                }
                yield false;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                context.escape();
                yield true;
            }
            case GLFW.GLFW_KEY_F1 -> {
                context.showHelp();
                yield true;
            }
            default -> false;
        };
    }

    /**
     * Context interface for handling keyboard shortcuts.
     * Implementers should override methods for shortcuts they support.
     */
    public interface ShortcutContext {
        /** Undo the last operation */
        default void undo() {}

        /** Redo the last undone operation */
        default void redo() {}

        /** Save/export the current work */
        default void save() {}

        /** Focus the search box */
        default void focusSearch() {}

        /** Open global search (Ctrl+Shift+F) */
        default void globalSearch() {}

        /** Add a new item to the current selection */
        default void addItem() {}

        /** Delete the currently selected item */
        default void delete() {}

        /** Duplicate the currently selected item */
        default void duplicate() {}

        /** Copy the currently selected item */
        default void copy() {}

        /** Paste from clipboard */
        default void paste() {}

        /** Handle escape key - close picker or clear selection */
        default void escape() {}

        /** Show keyboard shortcuts help (F1) */
        default void showHelp() {}
    }
}
