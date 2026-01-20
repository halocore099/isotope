package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.analysis.LootTableContentAnalyzer;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
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
     * Version-specific: 1.21+ uses reloadableRegistries, 1.20.x uses getLootData.
     *
     * Uses content-based analysis to detect categories, with path-based fallback.
     */
    public void scan(MinecraftServer server) {
        lootTables.clear();

        int contentAnalyzed = 0;
        int pathFallback = 0;
        int stubsSkipped = 0;

        try {
            // Collect all IDs first
            List<ResourceLocation> tableIds = new ArrayList<>();

            // Try 1.21+ API first, fall back to 1.20.x if not available
            if (!scan1_21Plus(server, tableIds)) {
                scan1_20x(server, tableIds);
            }

            // Now analyze each table with content-based detection
            for (ResourceLocation id : tableIds) {
                // Skip stub/placeholder loot tables that have no pools
                // (e.g., trial_chambers stubs in 1.20.4 before the feature was released)
                if (isStubLootTable(server, id)) {
                    stubsSkipped++;
                    Isotope.LOGGER.debug("Skipping stub loot table: {}", id);
                    continue;
                }
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
            Isotope.LOGGER.info("LootTableRegistry: scanned {} loot tables ({} content-analyzed, {} path-fallback, {} stubs skipped)",
                lootTables.size(), contentAnalyzed, pathFallback, stubsSkipped);

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
     * Try 1.21+ API: reloadableRegistries().lookup().lookupOrThrow(LOOT_TABLE)
     * Uses reflection to avoid compile-time dependency on 1.21+ classes.
     * @return true if successful, false if API not available
     */
    private boolean scan1_21Plus(MinecraftServer server, List<ResourceLocation> tableIds) {
        try {
            // server.reloadableRegistries() - 1.21+ only
            java.lang.reflect.Method reloadableRegistries = MinecraftServer.class.getMethod("reloadableRegistries");
            Object holder = reloadableRegistries.invoke(server);

            // Try to get lookup from holder
            java.lang.reflect.Method lookupMethod = null;
            try {
                lookupMethod = holder.getClass().getMethod("lookup");
            } catch (NoSuchMethodException e) {
                // Use holder directly
            }

            Object lookup = lookupMethod != null ? lookupMethod.invoke(holder) : holder;

            // Get Registries.LOOT_TABLE via reflection
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Object lootTableKey = registriesClass.getField("LOOT_TABLE").get(null);

            // provider.lookupOrThrow(Registries.LOOT_TABLE)
            java.lang.reflect.Method lookupOrThrow = null;
            for (java.lang.reflect.Method m : lookup.getClass().getMethods()) {
                if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1) {
                    lookupOrThrow = m;
                    break;
                }
            }

            if (lookupOrThrow == null) {
                Isotope.LOGGER.debug("No lookupOrThrow method found");
                return false;
            }

            Object lootLookup = lookupOrThrow.invoke(lookup, lootTableKey);

            // Try multiple methods to get the keys from the registry
            // Method 1: keySet() - returns Set<ResourceLocation> in 1.21.x
            try {
                java.lang.reflect.Method keySetMethod = lootLookup.getClass().getMethod("keySet");
                Object keySet = keySetMethod.invoke(lootLookup);
                if (keySet instanceof java.util.Set<?>) {
                    for (Object key : (java.util.Set<?>) keySet) {
                        if (key instanceof ResourceLocation) {
                            ResourceLocation id = (ResourceLocation) key;
                            if (!id.getPath().equals("empty")) {
                                tableIds.add(id);
                            }
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                Isotope.LOGGER.debug("No keySet() method");
            }

            // Method 2: registryKeySet() - returns Set<ResourceKey<T>>
            if (tableIds.isEmpty()) {
                try {
                    java.lang.reflect.Method registryKeySet = lootLookup.getClass().getMethod("registryKeySet");
                    Object keySet = registryKeySet.invoke(lootLookup);
                    if (keySet instanceof java.util.Set<?>) {
                        for (Object key : (java.util.Set<?>) keySet) {
                            try {
                                java.lang.reflect.Method location = key.getClass().getMethod("location");
                                ResourceLocation id = (ResourceLocation) location.invoke(key);
                                if (!id.getPath().equals("empty")) {
                                    tableIds.add(id);
                                }
                            } catch (Exception ex) {
                                Isotope.LOGGER.debug("Failed to get location from registry key: {}", ex.getMessage());
                            }
                        }
                    }
                } catch (NoSuchMethodException e) {
                    Isotope.LOGGER.debug("No registryKeySet() method");
                }
            }

            // Method 3: listElementIds() - returns Stream<ResourceKey<T>>
            if (tableIds.isEmpty()) {
                try {
                    java.lang.reflect.Method listElementIds = lootLookup.getClass().getMethod("listElementIds");
                    Object stream = listElementIds.invoke(lootLookup);

                    java.lang.reflect.Method forEach = stream.getClass().getMethod("forEach", java.util.function.Consumer.class);
                    forEach.invoke(stream, (java.util.function.Consumer<Object>) key -> {
                        try {
                            java.lang.reflect.Method location = key.getClass().getMethod("location");
                            ResourceLocation id = (ResourceLocation) location.invoke(key);
                            if (!id.getPath().equals("empty")) {
                                tableIds.add(id);
                            }
                        } catch (Exception ex) {
                            Isotope.LOGGER.debug("Failed to get location from key: {}", ex.getMessage());
                        }
                    });
                } catch (NoSuchMethodException e) {
                    Isotope.LOGGER.debug("No listElementIds() method");
                }
            }

            if (tableIds.isEmpty()) {
                Isotope.LOGGER.warn("1.21+ API: Could not find any method to list loot tables");
                return false;
            }

            Isotope.LOGGER.info("1.21+ API: found {} loot tables", tableIds.size());
            return true;
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            // Expected on 1.20.x - method doesn't exist
            Isotope.LOGGER.debug("1.21+ API not available: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            Isotope.LOGGER.debug("1.21+ API failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Scan using 1.20.x API: getLootData().getKeys(LootDataType.TABLE)
     * Uses reflection to avoid compile-time dependency on 1.20.x classes.
     */
    private void scan1_20x(MinecraftServer server, List<ResourceLocation> tableIds) {
        try {
            // Use reflection for 1.20.x API
            java.lang.reflect.Method getLootData = MinecraftServer.class.getMethod("getLootData");
            Object lootData = getLootData.invoke(server);

            // Get LootDataType.TABLE
            Class<?> lootDataTypeClass = Class.forName("net.minecraft.world.level.storage.loot.LootDataType");
            Object tableType = lootDataTypeClass.getField("TABLE").get(null);

            // lootData.getKeys(LootDataType.TABLE)
            java.lang.reflect.Method getKeys = lootData.getClass().getMethod("getKeys", lootDataTypeClass);
            Object keys = getKeys.invoke(lootData, tableType);

            // keys is a Set<ResourceLocation>
            if (keys instanceof Iterable<?>) {
                for (Object key : (Iterable<?>) keys) {
                    ResourceLocation id = (ResourceLocation) key;
                    if (!id.getPath().equals("empty")) {
                        tableIds.add(id);
                    }
                }
            }
            Isotope.LOGGER.info("1.20.x API: found {} loot tables", tableIds.size());
        } catch (Exception e) {
            Isotope.LOGGER.error("1.20.x API scan failed: {}", e.getMessage());
        }
    }

    /**
     * Check if a loot table is a stub (placeholder with no pools).
     * Minecraft 1.20.4 includes stub loot tables for experimental features like trial_chambers
     * that have no actual content - just a type and random_sequence.
     *
     * @param server The server to look up the loot table
     * @param id The loot table ID to check
     * @return true if the table is a stub with no pools
     */
    private boolean isStubLootTable(MinecraftServer server, ResourceLocation id) {
        try {
            // Try 1.21+ API first
            Object lootTable = getLootTable1_21Plus(server, id);
            if (lootTable == null) {
                // Fall back to 1.20.x API
                lootTable = getLootTable1_20x(server, id);
            }

            if (lootTable == null) {
                return false; // Can't determine, assume not a stub
            }

            // Check if the loot table has pools
            // LootTable.pools is a List<LootPool>
            java.lang.reflect.Field poolsField = null;
            for (java.lang.reflect.Field f : lootTable.getClass().getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object value = f.get(lootTable);
                    if (value instanceof java.util.List<?> list) {
                        // Found a List field - check if it's the pools field
                        // Pools field should contain LootPool instances
                        if (list.isEmpty()) {
                            // Empty list - this is likely the pools field and it's a stub
                            return true;
                        } else if (!list.isEmpty()) {
                            Object first = list.get(0);
                            if (first != null && first.getClass().getSimpleName().contains("LootPool")) {
                                // Has pools - not a stub
                                return false;
                            }
                        }
                    }
                }
            }

            // Couldn't determine, assume not a stub
            return false;
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to check if {} is stub: {}", id, e.getMessage());
            return false;
        }
    }

    /**
     * Get a LootTable using 1.21+ API.
     */
    private Object getLootTable1_21Plus(MinecraftServer server, ResourceLocation id) {
        try {
            java.lang.reflect.Method reloadableRegistries = MinecraftServer.class.getMethod("reloadableRegistries");
            Object holder = reloadableRegistries.invoke(server);

            // Find getLootTable method
            for (java.lang.reflect.Method m : holder.getClass().getMethods()) {
                if (m.getName().equals("getLootTable") && m.getParameterCount() == 1) {
                    // Could take ResourceKey or ResourceLocation
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.getSimpleName().contains("ResourceKey")) {
                        // Create ResourceKey<LootTable>
                        Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
                        Object lootTableRegistry = registriesClass.getField("LOOT_TABLE").get(null);

                        Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
                        java.lang.reflect.Method create = resourceKeyClass.getMethod("create", resourceKeyClass, ResourceLocation.class);
                        Object resourceKey = create.invoke(null, lootTableRegistry, id);

                        return m.invoke(holder, resourceKey);
                    }
                }
            }
        } catch (Exception e) {
            // Expected on 1.20.x
        }
        return null;
    }

    /**
     * Get a LootTable using 1.20.x API.
     */
    private Object getLootTable1_20x(MinecraftServer server, ResourceLocation id) {
        try {
            java.lang.reflect.Method getLootData = MinecraftServer.class.getMethod("getLootData");
            Object lootData = getLootData.invoke(server);

            // getLootTable(ResourceLocation)
            java.lang.reflect.Method getLootTable = lootData.getClass().getMethod("getLootTable", ResourceLocation.class);
            return getLootTable.invoke(lootData, id);
        } catch (Exception e) {
            // Expected on 1.21+
        }
        return null;
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
