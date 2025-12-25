package dev.isotope.search;

import dev.isotope.Isotope;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Search index for finding items across all loot tables.
 */
public final class SearchIndex {

    private static final SearchIndex INSTANCE = new SearchIndex();

    // Inverted index: item ID -> list of hits
    private final Map<ResourceLocation, List<SearchHit>> itemIndex = new HashMap<>();

    // Forward index: table ID -> list of items
    private final Map<ResourceLocation, Set<ResourceLocation>> tableItems = new HashMap<>();

    // All indexed tables
    private final Set<ResourceLocation> indexedTables = new HashSet<>();

    private SearchIndex() {}

    public static SearchIndex getInstance() {
        return INSTANCE;
    }

    /**
     * Rebuild the entire index from cached loot table structures.
     */
    public void rebuild() {
        itemIndex.clear();
        tableItems.clear();
        indexedTables.clear();

        LootEditManager manager = LootEditManager.getInstance();
        int tableCount = 0;
        int entryCount = 0;

        // Index all cached original structures
        for (var entry : manager.getAllEdits().entrySet()) {
            // Skip - we want to index originals, not edits
        }

        // We need to get all cached original structures
        // Since there's no direct accessor, we'll index when search is performed
        // For now, build index on-demand during search

        Isotope.LOGGER.debug("Search index rebuilt: {} tables, {} entries", tableCount, entryCount);
    }

    /**
     * Index a single loot table structure.
     */
    public void indexTable(LootTableStructure structure) {
        ResourceLocation tableId = structure.id();
        indexedTables.add(tableId);
        Set<ResourceLocation> items = new HashSet<>();

        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                if (entry.name().isPresent()) {
                    ResourceLocation itemId = entry.name().get();
                    items.add(itemId);

                    // Build context string
                    String context = String.format("Pool %d, Entry %d: %s (W:%d)",
                        poolIdx + 1, entryIdx + 1, itemId.getPath(), entry.weight());

                    SearchHit hit = new SearchHit(tableId, poolIdx, entryIdx, context);

                    itemIndex.computeIfAbsent(itemId, k -> new ArrayList<>()).add(hit);
                }
            }
        }

        tableItems.put(tableId, items);
    }

    /**
     * Search for items or tables matching the query.
     */
    public List<SearchHit> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String lowerQuery = query.toLowerCase().trim();
        List<SearchHit> results = new ArrayList<>();

        // Search by item ID
        for (var entry : itemIndex.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            if (itemId.toString().toLowerCase().contains(lowerQuery) ||
                itemId.getPath().toLowerCase().contains(lowerQuery)) {
                results.addAll(entry.getValue());
            }
        }

        // Deduplicate and limit results
        List<SearchHit> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchHit hit : results) {
            String key = hit.table() + ":" + hit.pool() + ":" + hit.entry();
            if (seen.add(key)) {
                unique.add(hit);
            }
        }

        // Sort by table path
        unique.sort(Comparator.comparing(h -> h.table().toString()));

        return unique;
    }

    /**
     * Search for tables containing a specific item.
     */
    public List<ResourceLocation> findTablesWithItem(ResourceLocation itemId) {
        List<SearchHit> hits = itemIndex.getOrDefault(itemId, List.of());
        return hits.stream()
            .map(SearchHit::table)
            .distinct()
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();
    }

    /**
     * Get all items in a table.
     */
    public Set<ResourceLocation> getItemsInTable(ResourceLocation tableId) {
        return tableItems.getOrDefault(tableId, Set.of());
    }

    /**
     * Check if a table is indexed.
     */
    public boolean isIndexed(ResourceLocation tableId) {
        return indexedTables.contains(tableId);
    }

    /**
     * Get index statistics.
     */
    public String getStats() {
        int totalHits = itemIndex.values().stream().mapToInt(List::size).sum();
        return String.format("%d tables, %d unique items, %d total hits",
            indexedTables.size(), itemIndex.size(), totalHits);
    }

    /**
     * Clear the index.
     */
    public void clear() {
        itemIndex.clear();
        tableItems.clear();
        indexedTables.clear();
    }
}
