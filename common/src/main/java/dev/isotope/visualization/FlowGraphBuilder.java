package dev.isotope.visualization;

import dev.isotope.Isotope;
import dev.isotope.data.LootSource;
import dev.isotope.data.StructureLootLink;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.registry.LootSourceRegistry;
import dev.isotope.registry.StructureLootLinker;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Builds FlowGraph from registry data for a given namespace.
 */
public final class FlowGraphBuilder {

    private FlowGraphBuilder() {}

    /**
     * Build complete graph for a namespace.
     */
    public static FlowGraph build(String namespace) {
        FlowGraph graph = new FlowGraph(namespace);

        LootSourceRegistry sourceRegistry = LootSourceRegistry.getInstance();
        StructureLootLinker linker = StructureLootLinker.getInstance();

        if (!sourceRegistry.isCompiled()) {
            Isotope.LOGGER.warn("[FlowGraphBuilder] LootSourceRegistry not compiled");
            return graph;
        }

        Set<Identifier> addedTables = new HashSet<>();

        // 1. Add source nodes (structures, features, mobs for this namespace)
        List<LootSource> sources = sourceRegistry.getByNamespace(namespace);
        for (LootSource source : sources) {
            graph.addNode(FlowNode.source(source.id(), source.displayName(), source.type()));
        }

        // 2. Add loot table nodes and source->table edges
        for (LootSource source : sources) {
            List<StructureLootLink> links = linker.getLinksForStructure(source.id());
            for (StructureLootLink link : links) {
                Identifier tableId = link.lootTableId();

                // Add table node if not already added
                if (!addedTables.contains(tableId)) {
                    String tableName = formatTableName(tableId);
                    graph.addNode(FlowNode.lootTable(tableId, tableName));
                    addedTables.add(tableId);
                }

                // Add edge
                graph.addEdge(FlowEdge.fromLink(link));
            }
        }

        // 3. Find nested table references and add nodes/edges
        Set<Identifier> nestedTables = new HashSet<>();
        for (Identifier tableId : addedTables) {
            Set<Identifier> nested = findNestedTableReferences(tableId);
            for (Identifier nestedId : nested) {
                // Add nested table node if not already in graph
                if (!graph.getNode(nestedId).isPresent()) {
                    String nestedName = formatTableName(nestedId);
                    graph.addNode(FlowNode.nestedTable(nestedId, nestedName));
                    nestedTables.add(nestedId);
                }
                graph.addEdge(FlowEdge.nestedReference(tableId, nestedId));
            }
        }

        // 4. Compute layout
        FlowLayoutEngine.layout(graph, 0, 0);

        Isotope.LOGGER.debug("[FlowGraphBuilder] Built graph for {}: {}", namespace, graph.getSummary());

        return graph;
    }

    /**
     * Find all minecraft:loot_table references within a table.
     */
    private static Set<Identifier> findNestedTableReferences(Identifier tableId) {
        Set<Identifier> nested = new LinkedHashSet<>();

        // Try to get the cached structure
        Optional<LootTableStructure> structureOpt = LootEditManager.getInstance().getCachedOriginalStructure(tableId);
        if (structureOpt.isEmpty()) {
            return nested;
        }

        LootTableStructure structure = structureOpt.get();

        // Search all pools and entries for loot_table references
        for (LootPool pool : structure.pools()) {
            for (LootEntry entry : pool.entries()) {
                findNestedInEntry(entry, nested);
            }
        }

        return nested;
    }

    /**
     * Recursively find nested table references in an entry.
     */
    private static void findNestedInEntry(LootEntry entry, Set<Identifier> nested) {
        // Check if this entry is a loot table reference
        if (LootEntry.TYPE_LOOT_TABLE.equals(entry.type()) && entry.name().isPresent()) {
            nested.add(entry.name().get());
        }

        // Check children (for composite entries like alternatives, group, sequence)
        for (LootEntry child : entry.children()) {
            findNestedInEntry(child, nested);
        }
    }

    /**
     * Format a table ID into a display name.
     * "minecraft:chests/simple_dungeon" -> "simple_dungeon"
     */
    private static String formatTableName(Identifier id) {
        String path = id.getPath();
        // Get the last part of the path
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
}
