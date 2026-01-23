package dev.isotope.visualization;

import dev.isotope.compat.Id;

import java.util.*;

/**
 * Complete graph representation for a namespace's loot flow.
 */
public class FlowGraph {

    private final String namespace;
    private final Map<Id, FlowNode> nodes = new LinkedHashMap<>();
    private final List<FlowEdge> edges = new ArrayList<>();

    // Column-based node organization
    private final List<FlowNode> sourceNodes = new ArrayList<>();
    private final List<FlowNode> tableNodes = new ArrayList<>();
    private final List<FlowNode> nestedNodes = new ArrayList<>();

    // Computed bounds
    private int totalWidth;
    private int totalHeight;

    public FlowGraph(String namespace) {
        this.namespace = namespace;
    }

    // --- Node management ---

    /**
     * Add a node to the graph.
     */
    public void addNode(FlowNode node) {
        if (nodes.containsKey(node.id())) {
            return; // Already exists
        }

        nodes.put(node.id(), node);

        // Add to column list
        switch (node.type()) {
            case SOURCE -> sourceNodes.add(node);
            case LOOT_TABLE -> tableNodes.add(node);
            case NESTED_TABLE -> nestedNodes.add(node);
        }
    }

    /**
     * Add an edge to the graph.
     */
    public void addEdge(FlowEdge edge) {
        // Only add if both nodes exist
        if (nodes.containsKey(edge.fromId()) && nodes.containsKey(edge.toId())) {
            edges.add(edge);
        }
    }

    /**
     * Get a node by ID.
     */
    public Optional<FlowNode> getNode(Id id) {
        return Optional.ofNullable(nodes.get(id));
    }

    // --- Queries ---

    /**
     * Get all nodes.
     */
    public Collection<FlowNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Get all edges.
     */
    public List<FlowEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Get nodes by column.
     */
    public List<FlowNode> getNodesByColumn(int column) {
        return switch (column) {
            case 0 -> Collections.unmodifiableList(sourceNodes);
            case 1 -> Collections.unmodifiableList(tableNodes);
            case 2 -> Collections.unmodifiableList(nestedNodes);
            default -> Collections.emptyList();
        };
    }

    /**
     * Get edges originating from a node.
     */
    public List<FlowEdge> getEdgesFrom(Id id) {
        List<FlowEdge> result = new ArrayList<>();
        for (FlowEdge edge : edges) {
            if (edge.fromId().equals(id)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Get edges targeting a node.
     */
    public List<FlowEdge> getEdgesTo(Id id) {
        List<FlowEdge> result = new ArrayList<>();
        for (FlowEdge edge : edges) {
            if (edge.toId().equals(id)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Get the number of connections for a node (both incoming and outgoing).
     */
    public int getConnectionCount(Id id) {
        return getEdgesFrom(id).size() + getEdgesTo(id).size();
    }

    // --- Layout ---

    /**
     * Get source nodes list for layout.
     */
    public List<FlowNode> getSourceNodes() {
        return sourceNodes;
    }

    /**
     * Get table nodes list for layout.
     */
    public List<FlowNode> getTableNodes() {
        return tableNodes;
    }

    /**
     * Get nested nodes list for layout.
     */
    public List<FlowNode> getNestedNodes() {
        return nestedNodes;
    }

    /**
     * Set computed bounds.
     */
    public void setBounds(int width, int height) {
        this.totalWidth = width;
        this.totalHeight = height;
    }

    public int getTotalWidth() {
        return totalWidth;
    }

    public int getTotalHeight() {
        return totalHeight;
    }

    // --- Selection support ---

    /**
     * Find a node at the given screen coordinates.
     */
    public Optional<FlowNode> findNodeAt(int x, int y) {
        for (FlowNode node : nodes.values()) {
            if (node.contains(x, y)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    // --- Status ---

    public String getNamespace() {
        return namespace;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * Get a summary string for debugging/display.
     */
    public String getSummary() {
        return String.format("%d sources, %d tables, %d nested, %d edges",
            sourceNodes.size(), tableNodes.size(), nestedNodes.size(), edges.size());
    }
}
