package dev.isotope.ui.widget;

import dev.isotope.data.LootSourceType;
import dev.isotope.editing.LootEditManager;
import dev.isotope.registry.LootSourceRegistry;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.visualization.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Panel displaying loot flow visualization for a namespace.
 * Shows sources (structures/features/mobs) -> loot tables -> nested tables.
 */
@Environment(EnvType.CLIENT)
public class LootFlowPanel extends AbstractWidget {

    private static final int HEADER_HEIGHT = 28;
    private static final int PADDING = 6;

    // Zoom constraints
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float ZOOM_STEP = 0.1f;

    // Colors for node types
    private static final int COLOR_SOURCE_STRUCTURE = IsotopeColors.SOURCE_STRUCTURE;
    private static final int COLOR_SOURCE_FEATURE = IsotopeColors.SOURCE_FEATURE;
    private static final int COLOR_SOURCE_MOB = IsotopeColors.SOURCE_MOB;
    private static final int COLOR_LOOT_TABLE = IsotopeColors.ACCENT_GOLD;
    private static final int COLOR_NESTED_TABLE = IsotopeColors.ACCENT_AQUA;

    // Graph data
    @Nullable
    private FlowGraph graph;
    private List<String> availableNamespaces = new ArrayList<>();
    private int currentNamespaceIndex = 0;

    // View state
    private float zoom = 1.0f;
    private int panX = 0;
    private int panY = 0;

    // Interaction state
    private boolean dragging = false;
    private int dragStartX;
    private int dragStartY;
    private int dragStartPanX;
    private int dragStartPanY;

    @Nullable
    private FlowNode hoveredNode = null;

    // Callbacks
    @Nullable
    private Consumer<Identifier> onTableSelected;

    public LootFlowPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Loot Flow"));
    }

    /**
     * Set callback for when a loot table is clicked.
     */
    public void setOnTableSelected(@Nullable Consumer<Identifier> callback) {
        this.onTableSelected = callback;
    }

    /**
     * Initialize the panel with available namespaces.
     */
    public void initialize() {
        // Get available namespaces from loot source registry
        LootSourceRegistry registry = LootSourceRegistry.getInstance();
        if (registry.isCompiled()) {
            availableNamespaces = new ArrayList<>(registry.getNamespaces());
            Collections.sort(availableNamespaces);

            // Default to minecraft if available
            int mcIndex = availableNamespaces.indexOf("minecraft");
            if (mcIndex >= 0) {
                currentNamespaceIndex = mcIndex;
            }

            rebuildGraph();
        }
    }

    /**
     * Rebuild the graph for the current namespace.
     */
    private void rebuildGraph() {
        if (availableNamespaces.isEmpty()) {
            graph = null;
            return;
        }

        String namespace = availableNamespaces.get(currentNamespaceIndex);
        graph = FlowGraphBuilder.build(namespace);

        // Reset view
        panX = 0;
        panY = 0;
        hoveredNode = null;
    }

    /**
     * Cycle to the next namespace.
     */
    public void nextNamespace() {
        if (availableNamespaces.isEmpty()) return;
        currentNamespaceIndex = (currentNamespaceIndex + 1) % availableNamespaces.size();
        rebuildGraph();
    }

    /**
     * Cycle to the previous namespace.
     */
    public void prevNamespace() {
        if (availableNamespaces.isEmpty()) return;
        currentNamespaceIndex = (currentNamespaceIndex - 1 + availableNamespaces.size()) % availableNamespaces.size();
        rebuildGraph();
    }

    /**
     * Get the current namespace.
     */
    public String getCurrentNamespace() {
        if (availableNamespaces.isEmpty()) return "";
        return availableNamespaces.get(currentNamespaceIndex);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_MEDIUM);
        ScreenUtils.renderOutline(graphics, getX(), getY(), width, height, IsotopeColors.BORDER_INNER);

        // Header
        renderHeader(graphics, font, mouseX, mouseY);

        // Content area
        int contentY = getY() + HEADER_HEIGHT;
        int contentHeight = height - HEADER_HEIGHT;

        if (graph == null || graph.isEmpty()) {
            // Empty state
            int centerX = getX() + width / 2;
            int centerY = contentY + contentHeight / 2;
            graphics.drawString(font, "No loot flow data", centerX - font.width("No loot flow data") / 2, centerY - 10, IsotopeColors.TEXT_MUTED, false);

            String hint = availableNamespaces.isEmpty() ? "Registry not compiled" : "Select a namespace above";
            graphics.drawString(font, hint, centerX - font.width(hint) / 2, centerY + 5, IsotopeColors.BUTTON_BACKGROUND, false);
            return;
        }

        // Enable scissor for content area
        graphics.enableScissor(getX(), contentY, getX() + width, getY() + height);

        // Transform for pan and zoom (MC 1.21.6+ uses Matrix3x2fStack with 2D methods)
        graphics.pose().pushMatrix();
        graphics.pose().translate(getX() + panX, contentY + panY);
        graphics.pose().scale(zoom, zoom);

        // Update hovered node
        updateHoveredNode(mouseX, mouseY, contentY);

        // Render edges first (below nodes)
        // Track edge index per source for curvature variation
        Map<Identifier, Integer> edgeIndexBySource = new HashMap<>();
        for (FlowEdge edge : graph.getEdges()) {
            int edgeIndex = edgeIndexBySource.getOrDefault(edge.fromId(), 0);
            edgeIndexBySource.put(edge.fromId(), edgeIndex + 1);
            boolean isHighlighted = hoveredNode != null &&
                (edge.fromId().equals(hoveredNode.id()) || edge.toId().equals(hoveredNode.id()));
            renderEdge(graphics, edge, edgeIndex, isHighlighted);
        }

        // Render nodes
        for (FlowNode node : graph.getAllNodes()) {
            renderNode(graphics, font, node);
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        // Render tooltip (outside scissor so it can extend beyond panel)
        if (hoveredNode != null) {
            renderTooltip(graphics, font, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int headerY = getY();

        // Header background
        graphics.fill(getX(), headerY, getX() + width, headerY + HEADER_HEIGHT, IsotopeColors.BACKGROUND_SOLID);
        graphics.fill(getX(), headerY + HEADER_HEIGHT - 1, getX() + width, headerY + HEADER_HEIGHT, IsotopeColors.BORDER_INNER);

        // Title
        graphics.drawString(font, "Loot Flow", getX() + PADDING, headerY + 9, IsotopeColors.ACCENT_GOLD, false);

        // Namespace selector (right side)
        int selectorX = getX() + width - 120;
        int selectorY = headerY + 5;

        // Prev button
        boolean hoverPrev = ScreenUtils.isMouseOver(mouseX, mouseY, selectorX, selectorY, 12, 16);
        graphics.drawString(font, "<", selectorX + 2, selectorY + 4, hoverPrev ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);

        // Namespace name
        String nsName = getCurrentNamespace();
        if (nsName.length() > 12) {
            nsName = nsName.substring(0, 10) + "...";
        }
        int nsWidth = font.width(nsName);
        graphics.drawString(font, nsName, selectorX + 60 - nsWidth / 2, selectorY + 4, IsotopeColors.TEXT_PRIMARY, false);

        // Next button
        boolean hoverNext = ScreenUtils.isMouseOver(mouseX, mouseY, selectorX + 108, selectorY, 12, 16);
        graphics.drawString(font, ">", selectorX + 110, selectorY + 4, hoverNext ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_SECONDARY, false);

        // Zoom indicator (left of namespace)
        String zoomText = String.format("%.0f%%", zoom * 100);
        graphics.drawString(font, zoomText, selectorX - 40, headerY + 9, IsotopeColors.TEXT_MUTED, false);

        // Node count
        if (graph != null) {
            String stats = graph.getSummary();
            int statsWidth = font.width(stats);
            graphics.drawString(font, stats, getX() + 70, headerY + 9, IsotopeColors.TEXT_MUTED, false);
        }
    }

    private void renderNode(GuiGraphics graphics, Font font, FlowNode node) {
        int x = node.x();
        int y = node.y();
        int w = node.width();
        int h = node.height();

        boolean hovered = node.equals(hoveredNode);

        // Check if this loot table has edits
        boolean hasEdits = (node.type() == FlowNode.NodeType.LOOT_TABLE ||
                           node.type() == FlowNode.NodeType.NESTED_TABLE) &&
                          LootEditManager.getInstance().hasEdits(node.id());

        // Node background - highlight edited tables
        int bgColor;
        if (hasEdits) {
            bgColor = hovered ? IsotopeColors.ENTRY_BACKGROUND_EDITED_HOVER : IsotopeColors.EDITED_TABLE_BG; // Green tint for edited
        } else {
            bgColor = hovered ? IsotopeColors.ENTRY_BACKGROUND_HOVER : IsotopeColors.ENTRY_BACKGROUND;
        }
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Left edge color indicator
        int indicatorColor = getNodeColor(node);
        graphics.fill(x, y, x + 3, y + h, indicatorColor);

        // Edit indicator - small green dot in top-right corner
        if (hasEdits) {
            int dotX = x + w - 8;
            int dotY = y + 4;
            graphics.fill(dotX, dotY, dotX + 5, dotY + 5, IsotopeColors.SUCCESS); // Green dot
        }

        // Border - use green for edited tables
        int borderColor;
        if (hasEdits) {
            borderColor = hovered ? IsotopeColors.SUCCESS : IsotopeColors.BADGE_HAS_LOOT; // Green border
        } else {
            borderColor = hovered ? IsotopeColors.BORDER_SELECTED : IsotopeColors.BORDER_DEFAULT;
        }
        ScreenUtils.renderOutline(graphics, x, y, w, h, borderColor);

        // Text label
        String label = node.displayName();
        int maxLabelWidth = hasEdits ? w - 18 : w - 10; // Less space if showing edit dot
        if (font.width(label) > maxLabelWidth) {
            label = font.plainSubstrByWidth(label, maxLabelWidth - 5) + "...";
        }
        graphics.drawString(font, label, x + 6, y + (h - 8) / 2, IsotopeColors.TEXT_PRIMARY, false);
    }

    private void renderEdge(GuiGraphics graphics, FlowEdge edge, int edgeIndex, boolean isHighlighted) {
        Optional<FlowNode> fromOpt = graph.getNode(edge.fromId());
        Optional<FlowNode> toOpt = graph.getNode(edge.toId());

        if (fromOpt.isEmpty() || toOpt.isEmpty()) return;

        FlowNode from = fromOpt.get();
        FlowNode to = toOpt.get();

        int x1 = from.getRightEdgeX();
        int y1 = from.getRightEdgeY();
        int x2 = to.getLeftEdgeX();
        int y2 = to.getLeftEdgeY();

        // Add curvature variation based on edge index to spread out overlapping lines
        // Alternate between positive and negative offsets
        int curveVariation = (edgeIndex % 2 == 0 ? 1 : -1) * ((edgeIndex / 2) + 1) * 8;

        // Calculate bezier control points with variation
        int ctrlOffset = (x2 - x1) / 2;
        int cx1 = x1 + ctrlOffset;
        int cy1 = y1 + curveVariation;
        int cx2 = x2 - ctrlOffset;
        int cy2 = y2 + curveVariation;

        // Edge color with confidence-based alpha (brighter when highlighted)
        int baseAlpha = edge.getAlpha();
        int alpha = isHighlighted ? 0xFF : baseAlpha;
        int baseColor = isHighlighted ? IsotopeColors.ACCENT_GOLD : getEdgeColor(edge);
        int color = IsotopeColors.withAlpha(baseColor, alpha);

        // Draw bezier curve (approximated with line segments)
        // Use thicker lines when highlighted
        int thickness = isHighlighted ? 2 : 1;
        drawBezierCurve(graphics, x1, y1, cx1, cy1, cx2, cy2, x2, y2, color, thickness);
    }

    private void drawBezierCurve(GuiGraphics graphics, int x1, int y1, int cx1, int cy1, int cx2, int cy2, int x2, int y2, int color, int thickness) {
        int segments = 16;
        int prevX = x1;
        int prevY = y1;

        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float u = 1 - t;

            // Cubic bezier formula
            int x = (int) (u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2);
            int y = (int) (u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2);

            // Draw line segment with thickness
            int halfThick = thickness / 2;
            graphics.fill(
                Math.min(prevX, x) - halfThick,
                Math.min(prevY, y) - thickness,
                Math.max(prevX, x) + 1 + halfThick,
                Math.max(prevY, y) + 1,
                color
            );

            prevX = x;
            prevY = y;
        }
    }

    private void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredNode == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(hoveredNode.id().toString());

        switch (hoveredNode.type()) {
            case SOURCE -> {
                LootSourceType sourceType = hoveredNode.sourceType();
                if (sourceType != null) {
                    lines.add("Type: " + sourceType.name().toLowerCase());
                }
                int edgeCount = graph.getEdgesFrom(hoveredNode.id()).size();
                lines.add("Loot tables: " + edgeCount);
            }
            case LOOT_TABLE -> {
                int inEdges = graph.getEdgesTo(hoveredNode.id()).size();
                int outEdges = graph.getEdgesFrom(hoveredNode.id()).size();
                lines.add("Sources: " + inEdges);
                if (outEdges > 0) {
                    lines.add("Nested refs: " + outEdges);
                }
                if (LootEditManager.getInstance().hasEdits(hoveredNode.id())) {
                    lines.add("Status: EDITED");
                }
                lines.add("");
                lines.add("Click to open");
            }
            case NESTED_TABLE -> {
                int inEdges = graph.getEdgesTo(hoveredNode.id()).size();
                lines.add("Referenced by: " + inEdges + " table(s)");
                if (LootEditManager.getInstance().hasEdits(hoveredNode.id())) {
                    lines.add("Status: EDITED");
                }
                lines.add("");
                lines.add("Click to open");
            }
        }

        // Calculate tooltip dimensions
        int tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, font.width(line));
        }
        tooltipWidth += 8;
        int tooltipHeight = lines.size() * 10 + 6;

        // Position tooltip
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;

        // Keep tooltip on screen
        if (tooltipX + tooltipWidth > getX() + width) {
            tooltipX = mouseX - tooltipWidth - 4;
        }
        if (tooltipY + tooltipHeight > getY() + height) {
            tooltipY = getY() + height - tooltipHeight;
        }

        // Draw tooltip background
        graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, IsotopeColors.BACKGROUND_MEDIUM);

        // Draw tooltip text
        int lineY = tooltipY + 3;
        for (String line : lines) {
            if (!line.isEmpty()) {
                graphics.drawString(font, line, tooltipX + 3, lineY, IsotopeColors.TEXT_PRIMARY, false);
            }
            lineY += 10;
        }
    }

    private void updateHoveredNode(int mouseX, int mouseY, int contentY) {
        hoveredNode = null;

        if (graph == null || !isMouseOver(mouseX, mouseY) || mouseY < contentY) {
            return;
        }

        // Transform mouse coordinates to graph space
        float graphX = (mouseX - getX() - panX) / zoom;
        float graphY = (mouseY - contentY - panY) / zoom;

        hoveredNode = graph.findNodeAt((int) graphX, (int) graphY).orElse(null);
    }

    private int getNodeColor(FlowNode node) {
        return switch (node.type()) {
            case SOURCE -> {
                LootSourceType sourceType = node.sourceType();
                if (sourceType == null) yield COLOR_SOURCE_STRUCTURE;
                yield switch (sourceType) {
                    case STRUCTURE -> COLOR_SOURCE_STRUCTURE;
                    case FEATURE -> COLOR_SOURCE_FEATURE;
                    case MOB -> COLOR_SOURCE_MOB;
                };
            }
            case LOOT_TABLE -> COLOR_LOOT_TABLE;
            case NESTED_TABLE -> COLOR_NESTED_TABLE;
        };
    }

    private int getEdgeColor(FlowEdge edge) {
        return switch (edge.type()) {
            case SOURCE_TO_TABLE -> IsotopeColors.TEXT_SECONDARY;
            case TABLE_TO_NESTED -> COLOR_NESTED_TABLE;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int contentY = getY() + HEADER_HEIGHT;

        // Header clicks
        if (mouseY < contentY) {
            int selectorX = getX() + width - 120;
            int selectorY = getY() + 5;

            // Prev button
            if (ScreenUtils.isMouseOver(mouseX, mouseY, selectorX, selectorY, 15, 16)) {
                prevNamespace();
                return true;
            }

            // Next button
            if (ScreenUtils.isMouseOver(mouseX, mouseY, selectorX + 105, selectorY, 15, 16)) {
                nextNamespace();
                return true;
            }

            return false;
        }

        // Content area clicks
        if (button == 0) {
            // Left click - check for node click or start drag
            if (hoveredNode != null) {
                // Open loot table
                if (hoveredNode.type() == FlowNode.NodeType.LOOT_TABLE ||
                    hoveredNode.type() == FlowNode.NodeType.NESTED_TABLE) {
                    if (onTableSelected != null) {
                        onTableSelected.accept(hoveredNode.id());
                    }
                    return true;
                }
            }

            // Start dragging for pan
            dragging = true;
            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            dragStartPanX = panX;
            dragStartPanY = panY;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            panX = dragStartPanX + (int) (event.x() - dragStartX);
            panY = dragStartPanY + (int) (event.y() - dragStartY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        // Zoom with scroll
        float oldZoom = zoom;
        zoom = (float) Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + scrollY * ZOOM_STEP));

        // Adjust pan to zoom towards mouse position
        if (zoom != oldZoom) {
            int contentY = getY() + HEADER_HEIGHT;
            float mouseGraphX = (float) (mouseX - getX() - panX) / oldZoom;
            float mouseGraphY = (float) (mouseY - contentY - panY) / oldZoom;

            panX = (int) (mouseX - getX() - mouseGraphX * zoom);
            panY = (int) (mouseY - contentY - mouseGraphY * zoom);
        }

        return true;
    }

    /**
     * Reset view to default.
     */
    public void resetView() {
        zoom = 1.0f;
        panX = 0;
        panY = 0;
    }

    /**
     * Fit the graph in the view.
     */
    public void fitToView() {
        if (graph == null || graph.isEmpty()) {
            resetView();
            return;
        }

        int[] bounds = FlowLayoutEngine.getContentBounds(graph);
        int contentWidth = bounds[2] - bounds[0];
        int contentHeight = bounds[3] - bounds[1];

        if (contentWidth <= 0 || contentHeight <= 0) {
            resetView();
            return;
        }

        int viewWidth = width - PADDING * 2;
        int viewHeight = height - HEADER_HEIGHT - PADDING * 2;

        float scaleX = (float) viewWidth / contentWidth;
        float scaleY = (float) viewHeight / contentHeight;
        zoom = Math.min(scaleX, scaleY);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));

        panX = (int) ((viewWidth - contentWidth * zoom) / 2) - (int) (bounds[0] * zoom);
        panY = (int) ((viewHeight - contentHeight * zoom) / 2) - (int) (bounds[1] * zoom);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
