package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.analysis.LootTableContentAnalyzer;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry of all discovered loot tables.
 *
 * Scans Minecraft's loot table registry to find all loot tables
 * (vanilla + modded) that exist in the current game session.
 */
public final class LootTableRegistry {

    private static final LootTableRegistry INSTANCE = new LootTableRegistry();

    private final Map<Identifier, LootTableInfo> lootTables = new LinkedHashMap<>();
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
            Isotope.LOGGER.debug("Holder type: {}", holder.getClass().getName());

            // Try lookup() with no args to see what it returns
            var lookup = holder.lookup();
            Isotope.LOGGER.debug("Lookup type: {}", lookup.getClass().getName());

            // Collect all IDs first
            List<Identifier> tableIds = new ArrayList<>();

            // The lookup should implement HolderLookup.Provider
            if (lookup instanceof net.minecraft.core.HolderLookup.Provider provider) {
                // Direct approach - works on NeoForge with Mojang mappings
                var lootLookup = provider.lookupOrThrow(Registries.LOOT_TABLE);

                lootLookup.listElementIds().forEach(key -> {
                    Identifier id = key.identifier();
                    if (!id.getPath().equals("empty")) {
                        tableIds.add(id);
                    }
                });
                Isotope.LOGGER.debug("Direct registry access successful");
            } else {
                // Fallback: use reflection for Fabric with intermediary mappings
                // The instanceof check fails on Fabric because class names differ at runtime
                Isotope.LOGGER.info("Using reflection fallback for loot table registry access");
                scanViaReflection(lookup, tableIds);
            }

            // Now analyze each table with content-based detection
            for (Identifier id : tableIds) {
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
                Isotope.LOGGER.debug("  {} {} loot tables", count, cat));

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to scan loot table registry", e);
        }
    }

    /**
     * Scan loot tables using reflection.
     * This is a fallback for Fabric where the instanceof check fails due to
     * intermediary mappings causing different class names at runtime.
     */
    @SuppressWarnings("unchecked")
    private void scanViaReflection(Object lookup, List<Identifier> tableIds) {
        try {
            // Step 1: Find and invoke lookupOrThrow(ResourceKey) method
            // On Fabric intermediary, method names may be obfuscated like "method_46767"
            Method lookupOrThrowMethod = findMethod(lookup.getClass(),
                new String[]{"lookupOrThrow", "method_46767", "method_46763"},
                1, ResourceKey.class);

            if (lookupOrThrowMethod == null) {
                // Try finding any single-arg method that takes a ResourceKey-like parameter
                for (Method m : lookup.getClass().getMethods()) {
                    if (m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType.getName().contains("ResourceKey") ||
                            paramType.getName().contains("class_5321")) {
                            lookupOrThrowMethod = m;
                            Isotope.LOGGER.debug("Found lookupOrThrow candidate: {}", m.getName());
                            break;
                        }
                    }
                }
            }

            if (lookupOrThrowMethod == null) {
                Isotope.LOGGER.warn("Could not find lookupOrThrow method on {}", lookup.getClass().getName());
                logAvailableMethods(lookup.getClass());
                return;
            }

            lookupOrThrowMethod.setAccessible(true);
            Object lootLookup = lookupOrThrowMethod.invoke(lookup, Registries.LOOT_TABLE);

            if (lootLookup == null) {
                Isotope.LOGGER.warn("lookupOrThrow returned null");
                return;
            }

            Isotope.LOGGER.debug("Got loot lookup: {}", lootLookup.getClass().getName());

            // Step 2: Find and invoke listElementIds() method
            Method listElementIdsMethod = findMethod(lootLookup.getClass(),
                new String[]{"listElementIds", "method_46771", "method_40224"},
                0, null);

            if (listElementIdsMethod == null) {
                // Try finding any no-arg method that returns a Stream
                for (Method m : lootLookup.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 &&
                        m.getReturnType().getName().contains("Stream")) {
                        listElementIdsMethod = m;
                        Isotope.LOGGER.debug("Found listElementIds candidate: {}", m.getName());
                        break;
                    }
                }
            }

            if (listElementIdsMethod == null) {
                Isotope.LOGGER.warn("Could not find listElementIds method on {}", lootLookup.getClass().getName());
                logAvailableMethods(lootLookup.getClass());
                return;
            }

            listElementIdsMethod.setAccessible(true);
            Object streamObj = listElementIdsMethod.invoke(lootLookup);

            if (!(streamObj instanceof java.util.stream.Stream)) {
                Isotope.LOGGER.warn("listElementIds did not return a Stream: {}",
                    streamObj != null ? streamObj.getClass().getName() : "null");
                return;
            }

            java.util.stream.Stream<?> idStream = (java.util.stream.Stream<?>) streamObj;

            // Step 3: Process each ResourceKey to extract the Identifier
            idStream.forEach(key -> {
                try {
                    Identifier id = extractIdentifier(key);
                    if (id != null && !id.getPath().equals("empty")) {
                        tableIds.add(id);
                    }
                } catch (Exception e) {
                    Isotope.LOGGER.debug("Failed to extract ID from key: {}", e.getMessage());
                }
            });

            Isotope.LOGGER.info("Reflection-based scan found {} loot tables", tableIds.size());

        } catch (Exception e) {
            Isotope.LOGGER.error("Reflection-based loot table scan failed", e);
            Isotope.LOGGER.debug("Stack trace:", e);
        }
    }

    /**
     * Find a method by trying multiple possible names (for mapping compatibility).
     */
    private Method findMethod(Class<?> clazz, String[] possibleNames, int paramCount, Class<?> paramType) {
        for (String name : possibleNames) {
            try {
                for (Method m : clazz.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                        if (paramType == null ||
                            (paramCount > 0 && paramType.isAssignableFrom(m.getParameterTypes()[0]))) {
                            return m;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Extract an Identifier from a ResourceKey object using reflection.
     */
    private Identifier extractIdentifier(Object key) throws Exception {
        // Try common method names for getting the location/identifier
        String[] possibleMethods = {"identifier", "location", "method_41185", "method_29177"};

        for (String methodName : possibleMethods) {
            try {
                Method m = key.getClass().getMethod(methodName);
                Object result = m.invoke(key);
                if (result instanceof Identifier id) {
                    return id;
                }
                // On Fabric, it might return a ResourceLocation with a different class name
                if (result != null) {
                    // Try to extract namespace and path
                    Method getNamespace = result.getClass().getMethod("getNamespace");
                    Method getPath = result.getClass().getMethod("getPath");
                    String namespace = (String) getNamespace.invoke(result);
                    String path = (String) getPath.invoke(result);
                    return Identifier.fromNamespaceAndPath(namespace, path);
                }
            } catch (NoSuchMethodException ignored) {}
        }

        // Last resort: try any no-arg method that might return a location
        for (Method m : key.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && !m.getName().equals("toString") &&
                !m.getName().equals("hashCode") && !m.getName().equals("getClass")) {
                try {
                    Object result = m.invoke(key);
                    if (result instanceof Identifier id) {
                        return id;
                    }
                    if (result != null && result.getClass().getName().contains("ResourceLocation")) {
                        Method getNamespace = result.getClass().getMethod("getNamespace");
                        Method getPath = result.getClass().getMethod("getPath");
                        String namespace = (String) getNamespace.invoke(result);
                        String path = (String) getPath.invoke(result);
                        return Identifier.fromNamespaceAndPath(namespace, path);
                    }
                } catch (Exception ignored) {}
            }
        }

        Isotope.LOGGER.debug("Could not extract identifier from: {}", key.getClass().getName());
        return null;
    }

    /**
     * Log available methods on a class for debugging.
     */
    private void logAvailableMethods(Class<?> clazz) {
        Isotope.LOGGER.debug("Available methods on {}:", clazz.getName());
        for (Method m : clazz.getMethods()) {
            if (!m.getDeclaringClass().equals(Object.class)) {
                Isotope.LOGGER.debug("  {} ({} params)", m.getName(), m.getParameterCount());
            }
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
    public Optional<LootTableInfo> get(Identifier id) {
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
