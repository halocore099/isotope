package dev.isotope.visualization;

import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Computes hierarchical node positions for the flow graph.
 * Uses a 3-column layout with connection-based ordering within columns.
 */
public final class FlowLayoutEngine {

    private static final int PADDING = 20;

    private FlowLayoutEngine() {}

    /**
     * Compute layout positions for all nodes in the graph.
     *
     * @param graph   The graph to layout
     * @param offsetX Starting X offset
     * @param offsetY Starting Y offset
     */
    public static void layout(FlowGraph graph, int offsetX, int offsetY) {
        if (graph.isEmpty()) {
            graph.setBounds(0, 0);
            return;
        }

        // Get node lists by column
        List<FlowNode> sourceNodes = graph.getSourceNodes();
        List<FlowNode> tableNodes = graph.getTableNodes();
        List<FlowNode> nestedNodes = graph.getNestedNodes();

        // Sort nodes within columns to minimize edge crossings
        sortByConnections(sourceNodes, tableNodes, graph);
        sortByConnections(tableNodes, nestedNodes, graph);

        // Calculate column X positions
        int col0X = offsetX + PADDING;
        int col1X = col0X + FlowNode.NODE_WIDTH + FlowNode.COLUMN_SPACING;
        int col2X = col1X + FlowNode.NODE_WIDTH + FlowNode.COLUMN_SPACING;

        // Layout each column
        int maxHeight = 0;
        maxHeight = Math.max(maxHeight, layoutColumn(sourceNodes, col0X, offsetY + PADDING, 0));
        maxHeight = Math.max(maxHeight, layoutColumn(tableNodes, col1X, offsetY + PADDING, 1));
        maxHeight = Math.max(maxHeight, layoutColumn(nestedNodes, col2X, offsetY + PADDING, 2));

        // Compute total bounds
        int totalWidth = col2X + FlowNode.NODE_WIDTH + PADDING - offsetX;
        int totalHeight = maxHeight + PADDING;

        // If no nested nodes, reduce width
        if (nestedNodes.isEmpty()) {
            totalWidth = col1X + FlowNode.NODE_WIDTH + PADDING - offsetX;
        }

        graph.setBounds(totalWidth, totalHeight);
    }

    /**
     * Layout nodes in a single column.
     *
     * @param nodes  Nodes to layout
     * @param x      X position for this column
     * @param startY Starting Y position
     * @param column Column index for grid position
     * @return The Y position after the last node (max height used)
     */
    private static int layoutColumn(List<FlowNode> nodes, int x, int startY, int column) {
        int y = startY;

        for (int i = 0; i < nodes.size(); i++) {
            FlowNode node = nodes.get(i);
            node.setGridPosition(column, i);
            node.setScreenPosition(x, y);
            y += FlowNode.NODE_HEIGHT + FlowNode.ROW_SPACING;
        }

        return y;
    }

    /**
     * Sort nodes in the source column to minimize edge crossings with target column.
     * Uses barycenter heuristic: position source nodes near the average Y of their targets.
     */
    private static void sortByConnections(List<FlowNode> sourceCol, List<FlowNode> targetCol, FlowGraph graph) {
        if (sourceCol.isEmpty() || targetCol.isEmpty()) {
            return;
        }

        // Build index of target positions
        Map<Identifier, Integer> targetIndex = new HashMap<>();
        for (int i = 0; i < targetCol.size(); i++) {
            targetIndex.put(targetCol.get(i).id(), i);
        }

        // Calculate barycenter for each source node
        Map<FlowNode, Double> barycenters = new HashMap<>();
        for (FlowNode source : sourceCol) {
            List<FlowEdge> edges = graph.getEdgesFrom(source.id());
            if (edges.isEmpty()) {
                // No connections - put at end
                barycenters.put(source, Double.MAX_VALUE);
            } else {
                double sum = 0;
                int count = 0;
                for (FlowEdge edge : edges) {
                    Integer idx = targetIndex.get(edge.toId());
                    if (idx != null) {
                        sum += idx;
                        count++;
                    }
                }
                barycenters.put(source, count > 0 ? sum / count : Double.MAX_VALUE);
            }
        }

        // Sort by barycenter
        sourceCol.sort(Comparator.comparingDouble(n -> barycenters.getOrDefault(n, Double.MAX_VALUE)));
    }

    /**
     * Re-layout a graph after changes (e.g., filtering).
     */
    public static void relayout(FlowGraph graph) {
        layout(graph, 0, 0);
    }

    /**
     * Calculate the ideal viewport center to show a specific node.
     */
    public static int[] getNodeCenter(FlowNode node) {
        return new int[]{
            node.x() + node.width() / 2,
            node.y() + node.height() / 2
        };
    }

    /**
     * Calculate a bounding box that contains all nodes with padding.
     */
    public static int[] getContentBounds(FlowGraph graph) {
        if (graph.isEmpty()) {
            return new int[]{0, 0, 0, 0}; // minX, minY, maxX, maxY
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (FlowNode node : graph.getAllNodes()) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + node.width());
            maxY = Math.max(maxY, node.y() + node.height());
        }

        return new int[]{minX - PADDING, minY - PADDING, maxX + PADDING, maxY + PADDING};
    }
}
