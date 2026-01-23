package dev.isotope.visualization;

import dev.isotope.compat.Id;
import dev.isotope.data.StructureLootLink;

/**
 * Represents a directed edge in the loot flow graph.
 */
public record FlowEdge(
    Id fromId,
    Id toId,
    EdgeType type,
    int confidence,      // 0-100 for visual weight
    String sourceLabel   // e.g., "Template", "Verified", "Heuristic"
) {
    /**
     * Type of edge in the flow graph.
     */
    public enum EdgeType {
        SOURCE_TO_TABLE,    // Structure/feature/mob -> loot table
        TABLE_TO_NESTED     // Loot table -> nested table reference
    }

    /**
     * Create an edge from a StructureLootLink.
     */
    public static FlowEdge fromLink(StructureLootLink link) {
        return new FlowEdge(
            link.structureId(),
            link.lootTableId(),
            EdgeType.SOURCE_TO_TABLE,
            link.confidence().getScore(),
            link.confidence().getLabel()
        );
    }

    /**
     * Create an edge for a nested table reference.
     */
    public static FlowEdge nestedReference(Id fromTable, Id toTable) {
        return new FlowEdge(
            fromTable,
            toTable,
            EdgeType.TABLE_TO_NESTED,
            70, // HIGH confidence - deterministic from JSON
            "Reference"
        );
    }

    /**
     * Get alpha value for rendering based on confidence.
     * Higher confidence = more opaque.
     */
    public int getAlpha() {
        // Map 0-100 confidence to 0x40-0xFF alpha range
        return Math.max(0x40, (int) (0xFF * confidence / 100.0));
    }
}
