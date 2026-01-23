package dev.isotope.visualization;

import dev.isotope.compat.Id;
import dev.isotope.data.LootSourceType;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a node in the loot flow graph.
 * Can be a source (structure/feature/mob), a loot table, or a nested table reference.
 */
public final class FlowNode {

    public static final int NODE_WIDTH = 120;
    public static final int NODE_HEIGHT = 24;
    public static final int COLUMN_SPACING = 160;
    public static final int ROW_SPACING = 8;

    /**
     * Type of node in the flow graph.
     */
    public enum NodeType {
        SOURCE,       // Structure, feature, or mob
        LOOT_TABLE,   // Primary loot table linked to a source
        NESTED_TABLE  // Referenced via minecraft:loot_table entry
    }

    private final Id id;
    private final NodeType type;
    private final String displayName;
    @Nullable
    private final LootSourceType sourceType; // Only for SOURCE type

    // Grid position (set by layout engine)
    private int column;
    private int row;

    // Screen coordinates (computed from grid position)
    private int x;
    private int y;
    private int width = NODE_WIDTH;
    private int height = NODE_HEIGHT;

    private FlowNode(Id id, NodeType type, String displayName, @Nullable LootSourceType sourceType) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.sourceType = sourceType;
        this.column = type == NodeType.SOURCE ? 0 : (type == NodeType.LOOT_TABLE ? 1 : 2);
    }

    // --- Factory methods ---

    /**
     * Create a source node (structure, feature, or mob).
     */
    public static FlowNode source(Id id, String name, LootSourceType sourceType) {
        return new FlowNode(id, NodeType.SOURCE, name, sourceType);
    }

    /**
     * Create a loot table node.
     */
    public static FlowNode lootTable(Id id, String name) {
        return new FlowNode(id, NodeType.LOOT_TABLE, name, null);
    }

    /**
     * Create a nested table reference node.
     */
    public static FlowNode nestedTable(Id id, String name) {
        return new FlowNode(id, NodeType.NESTED_TABLE, name, null);
    }

    // --- Getters ---

    public Id id() {
        return id;
    }

    public NodeType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    @Nullable
    public LootSourceType sourceType() {
        return sourceType;
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // --- Position setters (for layout engine) ---

    public void setGridPosition(int column, int row) {
        this.column = column;
        this.row = row;
    }

    public void setScreenPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // --- Hit testing ---

    /**
     * Check if a point is within this node's bounds.
     */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /**
     * Get the right edge center point (for edge start).
     */
    public int getRightEdgeX() {
        return x + width;
    }

    public int getRightEdgeY() {
        return y + height / 2;
    }

    /**
     * Get the left edge center point (for edge end).
     */
    public int getLeftEdgeX() {
        return x;
    }

    public int getLeftEdgeY() {
        return y + height / 2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowNode that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "FlowNode{" + type + ": " + id + " @ (" + x + "," + y + ")}";
    }
}
