package dev.isotope.data;

/**
 * Type of loot source in Minecraft.
 *
 * STRUCTURE - Real structures with persistent tracking, bounding boxes, and registry IDs
 *             (villages, ancient cities, trial chambers, etc.)
 *
 * FEATURE - Fire-and-forget decorations without persistent tracking
 *           (dungeons/monster_room, fossils, etc.)
 *           These generate loot but aren't tracked like structures.
 */
public enum LootSourceType {
    /**
     * Real structure with bounding boxes and persistent IDs.
     * Examples: villages, ancient cities, trial chambers, bastions
     */
    STRUCTURE("Structure", 0xFF55FFFF),  // Cyan

    /**
     * Feature: fire-and-forget decoration with loot but no tracking.
     * Examples: dungeons (monster_room), fossils
     */
    FEATURE("Feature", 0xFFFFAA00);      // Orange

    private final String label;
    private final int color;

    LootSourceType(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public int getColor() {
        return color;
    }
}
