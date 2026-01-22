package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.analysis.LootTableContentAnalyzer;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all discovered loot tables.
 *
 * Scans Minecraft's loot table registry to find all loot tables
 * (vanilla + modded) that exist in the current game session.
 */
public final class LootTableRegistry {

    private static final LootTableRegistry INSTANCE = new LootTableRegistry();

    private final Map<ResourceLocation, LootTableInfo> lootTables = new LinkedHashMap<>();
    private boolean scanned = false;

    private LootTableRegistry() {}

    public static LootTableRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Scan the loot table registry from the server.
     * In 1.21.4, loot tables are in the reloadable registries.
     *
     * Uses content-based analysis to detect categories, with path-based fallback.
     */
    public void scan(MinecraftServer server) {
        lootTables.clear();

        int contentAnalyzed = 0;
        int pathFallback = 0;

        try {
            // In 1.21.4, reloadableRegistries() returns a Holder with a lookup() method
            var holder = server.reloadableRegistries();

            // Log the holder type for debugging
            Isotope.LOGGER.info("Holder type: {}", holder.getClass().getName());

            // Try lookup() with no args to see what it returns
            var lookup = holder.lookup();
            Isotope.LOGGER.info("Lookup type: {}", lookup.getClass().getName());

            // Collect all IDs first
            List<ResourceLocation> tableIds = new ArrayList<>();

            // Try to get loot table IDs - handle different API patterns across MC versions
            boolean found = false;

            // Method 1: Direct instanceof check (works for most versions)
            if (lookup instanceof net.minecraft.core.HolderLookup.Provider provider) {
                var lootLookup = provider.lookupOrThrow(Registries.LOOT_TABLE);
                lootLookup.listElementIds().forEach(key -> {
                    ResourceLocation id = key.location();
                    if (!id.getPath().equals("empty")) {
                        tableIds.add(id);
                    }
                });
                found = true;
            }

            // Method 2: Check if class implements Provider interface (handles anonymous classes in 1.21.1)
            if (!found) {
                for (Class<?> iface : lookup.getClass().getInterfaces()) {
                    if (iface.getName().contains("HolderLookup$Provider") ||
                        iface.getName().equals("net.minecraft.core.HolderLookup$Provider")) {
                        try {
                            // Cast and use - the interface is there, just not matching instanceof
                            var provider = (net.minecraft.core.HolderLookup.Provider) lookup;
                            var lootLookup = provider.lookupOrThrow(Registries.LOOT_TABLE);
                            lootLookup.listElementIds().forEach(key -> {
                                ResourceLocation id = key.location();
                                if (!id.getPath().equals("empty")) {
                                    tableIds.add(id);
                                }
                            });
                            found = true;
                            break;
                        } catch (ClassCastException e) {
                            Isotope.LOGGER.debug("Cast failed for interface {}: {}", iface.getName(), e.getMessage());
                        }
                    }
                }
            }

            // Method 3: Try direct cast anyway (last resort)
            if (!found) {
                try {
                    var provider = (net.minecraft.core.HolderLookup.Provider) lookup;
                    var lootLookup = provider.lookupOrThrow(Registries.LOOT_TABLE);
                    lootLookup.listElementIds().forEach(key -> {
                        ResourceLocation id = key.location();
                        if (!id.getPath().equals("empty")) {
                            tableIds.add(id);
                        }
                    });
                    found = true;
                } catch (ClassCastException e) {
                    Isotope.LOGGER.warn("Lookup is not a HolderLookup.Provider: {} (interfaces: {})",
                        lookup.getClass(), Arrays.toString(lookup.getClass().getInterfaces()));
                }
            }

            // Now analyze each table with content-based detection
            for (ResourceLocation id : tableIds) {
                LootTableCategory category = null;
                String path = id.getPath();

                // FIRST: Check if path clearly indicates category (authoritative)
                // This prevents content analysis from miscategorizing obvious paths
                LootTableCategory pathCategory = LootTableInfo.inferCategoryFromPath(path);
                if (pathCategory != LootTableCategory.OTHER) {
                    // Path is clear - use it directly (entities/, blocks/, chests/, etc.)
                    category = pathCategory;
                    pathFallback++;
                } else {
                    // Path is ambiguous - try content-based analysis
                    try {
                        category = LootTableContentAnalyzer.analyze(server, id);
                        if (category != null) {
                            contentAnalyzed++;
                        }
                    } catch (Exception e) {
                        Isotope.LOGGER.debug("Content analysis failed for {}: {}", id, e.getMessage());
                    }

                    // Final fallback to OTHER if content analysis also failed
                    if (category == null) {
                        category = LootTableCategory.OTHER;
                    }
                }

                LootTableInfo info = LootTableInfo.fromIdWithCategory(id, category);
                lootTables.put(id, info);
            }

            scanned = true;
            Isotope.LOGGER.info("LootTableRegistry: scanned {} loot tables ({} content-analyzed, {} path-fallback)",
                lootTables.size(), contentAnalyzed, pathFallback);

            // Log category breakdown
            Map<LootTableCategory, Long> byCategory = lootTables.values().stream()
                .collect(Collectors.groupingBy(LootTableInfo::category, Collectors.counting()));
            byCategory.forEach((cat, count) ->
                Isotope.LOGGER.info("  {} {} loot tables", count, cat));

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to scan loot table registry", e);
        }
    }

    /**
     * Get all discovered loot tables.
     */
    public Collection<LootTableInfo> getAll() {
        return Collections.unmodifiableCollection(lootTables.values());
    }

    /**
     * Get loot table by ID.
     */
    public Optional<LootTableInfo> get(ResourceLocation id) {
        return Optional.ofNullable(lootTables.get(id));
    }

    /**
     * Get loot tables filtered by category.
     */
    public List<LootTableInfo> getByCategory(LootTableCategory category) {
        return lootTables.values().stream()
            .filter(lt -> lt.category() == category)
            .toList();
    }

    /**
     * Get loot tables filtered by namespace.
     */
    public List<LootTableInfo> getByNamespace(String namespace) {
        if ("*".equals(namespace)) {
            return new ArrayList<>(lootTables.values());
        }
        return lootTables.values().stream()
            .filter(lt -> lt.namespace().equals(namespace))
            .toList();
    }

    /**
     * Get CHEST category loot tables (most relevant for structures).
     */
    public List<LootTableInfo> getChestLootTables() {
        return getByCategory(LootTableCategory.CHEST);
    }

    /**
     * Get all unique namespaces.
     */
    public Set<String> getNamespaces() {
        return lootTables.values().stream()
            .map(LootTableInfo::namespace)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get count of loot tables per category.
     */
    public Map<LootTableCategory, Integer> getCategoryCounts() {
        Map<LootTableCategory, Integer> counts = new EnumMap<>(LootTableCategory.class);
        for (LootTableInfo info : lootTables.values()) {
            counts.merge(info.category(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Get count of loot tables per namespace.
     */
    public Map<String, Integer> getNamespaceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LootTableInfo info : lootTables.values()) {
            counts.merge(info.namespace(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Total loot table count.
     */
    public int size() {
        return lootTables.size();
    }

    /**
     * Check if registry has been scanned.
     */
    public boolean isScanned() {
        return scanned;
    }

    /**
     * Reset the registry (for re-scanning).
     */
    public void reset() {
        lootTables.clear();
        scanned = false;
    }

    /**
     * Add a loot table from a loaded save file.
     */
    public void addFromSave(LootTableInfo info) {
        lootTables.put(info.id(), info);
        scanned = true; // Mark as having data
    }
}
