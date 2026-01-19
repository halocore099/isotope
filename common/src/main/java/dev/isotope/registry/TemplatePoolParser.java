package dev.isotope.registry;
import dev.isotope.compat.RegistryHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses template pool JSON files to extract loot table references.
 *
 * Template pools are used by jigsaw structures (villages, bastions, trial chambers, etc.)
 * to define which structure pieces can be used. The JSON files can contain:
 * - Direct loot_table references in element data
 * - Template references that we can cross-reference with NBT parsing
 * - Processor references that might modify loot
 *
 * This complements StructureTemplateParser by getting data from the JSON side
 * rather than just the NBT side.
 *
 * Path: data/<namespace>/worldgen/template_pool/<path>.json
 */
public final class TemplatePoolParser {

    private static final TemplatePoolParser INSTANCE = new TemplatePoolParser();
    private static final Gson GSON = new GsonBuilder().create();

    // Pool ID -> loot tables found in that pool
    private final Map<ResourceLocation, Set<ResourceLocation>> poolToLootTables = new LinkedHashMap<>();

    // Pool ID -> template references (for cross-referencing)
    private final Map<ResourceLocation, Set<ResourceLocation>> poolToTemplates = new LinkedHashMap<>();

    // Pool ID -> processor list references
    private final Map<ResourceLocation, Set<ResourceLocation>> poolToProcessors = new LinkedHashMap<>();

    // Pool hierarchy: pool ID -> pools it references (fallback pools, nested pools)
    private final Map<ResourceLocation, Set<ResourceLocation>> poolHierarchy = new LinkedHashMap<>();

    // Statistics
    private int filesParsed = 0;
    private int poolsWithLoot = 0;
    private int totalLootReferences = 0;
    private boolean parsed = false;

    private TemplatePoolParser() {}

    public static TemplatePoolParser getInstance() {
        return INSTANCE;
    }

    /**
     * Parse all template pool JSON files.
     */
    public void parse(MinecraftServer server) {
        clear();

        try {
            ResourceManager resourceManager = server.getResourceManager();

            // Scan for template_pool JSON files
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                "worldgen/template_pool",
                path -> path.getPath().endsWith(".json")
            );

            Isotope.LOGGER.debug("[TemplatePoolParser] Found {} template_pool files", resources.size());

            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                parsePoolJson(entry.getKey(), entry.getValue());
                filesParsed++;
            }

            // Resolve pool hierarchy to propagate loot tables
            resolvePoolHierarchy();

            parsed = true;
            Isotope.LOGGER.info("[TemplatePoolParser] Parsed {} files, found {} pools with {} loot references",
                filesParsed, poolsWithLoot, totalLootReferences);

        } catch (Exception e) {
            Isotope.LOGGER.error("[TemplatePoolParser] Failed to parse template pools", e);
        }
    }

    /**
     * Parse a single template pool JSON file.
     */
    private void parsePoolJson(ResourceLocation path, Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            // Build the pool ID from the path
            ResourceLocation poolId = extractPoolId(path);
            if (poolId == null) return;

            Set<ResourceLocation> foundLoot = new HashSet<>();
            Set<ResourceLocation> foundTemplates = new HashSet<>();
            Set<ResourceLocation> foundProcessors = new HashSet<>();
            Set<ResourceLocation> referencedPools = new HashSet<>();

            // Check for fallback pool
            if (json.has("fallback")) {
                String fallback = json.get("fallback").getAsString();
                try {
                    referencedPools.add(RegistryHelper.parseLocation(fallback));
                } catch (Exception e) {
                    // Invalid fallback
                }
            }

            // Parse elements array
            if (json.has("elements")) {
                JsonArray elements = json.getAsJsonArray("elements");
                for (JsonElement elem : elements) {
                    if (elem.isJsonObject()) {
                        parsePoolElement(elem.getAsJsonObject(), foundLoot, foundTemplates, foundProcessors, referencedPools);
                    }
                }
            }

            // Store results
            if (!foundLoot.isEmpty()) {
                poolToLootTables.put(poolId, foundLoot);
                poolsWithLoot++;
                totalLootReferences += foundLoot.size();
            }
            if (!foundTemplates.isEmpty()) {
                poolToTemplates.put(poolId, foundTemplates);
            }
            if (!foundProcessors.isEmpty()) {
                poolToProcessors.put(poolId, foundProcessors);
            }
            if (!referencedPools.isEmpty()) {
                poolHierarchy.put(poolId, referencedPools);
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("[TemplatePoolParser] Failed to parse {}: {}", path, e.getMessage());
        }
    }

    /**
     * Parse a pool element for loot table references.
     */
    private void parsePoolElement(JsonObject element, Set<ResourceLocation> foundLoot,
                                   Set<ResourceLocation> foundTemplates,
                                   Set<ResourceLocation> foundProcessors,
                                   Set<ResourceLocation> referencedPools) {

        // Check for "element" sub-object
        if (element.has("element")) {
            JsonObject elemData = element.getAsJsonObject("element");
            parseElementData(elemData, foundLoot, foundTemplates, foundProcessors, referencedPools);
        }

        // Also check top level (some formats)
        parseElementData(element, foundLoot, foundTemplates, foundProcessors, referencedPools);
    }

    /**
     * Parse element data for various references.
     */
    private void parseElementData(JsonObject data, Set<ResourceLocation> foundLoot,
                                   Set<ResourceLocation> foundTemplates,
                                   Set<ResourceLocation> foundProcessors,
                                   Set<ResourceLocation> referencedPools) {

        // Check for template location
        if (data.has("location")) {
            String location = data.get("location").getAsString();
            try {
                foundTemplates.add(RegistryHelper.parseLocation(location));
            } catch (Exception e) {
                // Invalid location
            }
        }

        // Check for template (alternative key)
        if (data.has("template")) {
            String template = data.get("template").getAsString();
            try {
                foundTemplates.add(RegistryHelper.parseLocation(template));
            } catch (Exception e) {
                // Invalid template
            }
        }

        // Check for processors
        if (data.has("processors")) {
            JsonElement processors = data.get("processors");
            if (processors.isJsonPrimitive()) {
                try {
                    foundProcessors.add(RegistryHelper.parseLocation(processors.getAsString()));
                } catch (Exception e) {
                    // Invalid processor
                }
            }
        }

        // Check for pool reference (list pool elements)
        if (data.has("pool")) {
            String pool = data.get("pool").getAsString();
            try {
                referencedPools.add(RegistryHelper.parseLocation(pool));
            } catch (Exception e) {
                // Invalid pool
            }
        }

        // Recursively search for loot_table references
        searchForLootTables(data, foundLoot);
    }

    /**
     * Recursively search a JSON structure for loot_table references.
     */
    private void searchForLootTables(JsonElement element, Set<ResourceLocation> found) {
        if (element == null) return;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Check for loot_table key (various formats)
            for (String key : List.of("loot_table", "LootTable", "loot", "chest_loot")) {
                if (obj.has(key)) {
                    JsonElement lootElement = obj.get(key);
                    if (lootElement.isJsonPrimitive()) {
                        String lootTableStr = lootElement.getAsString();
                        try {
                            ResourceLocation lootId = RegistryHelper.parseLocation(lootTableStr);
                            found.add(lootId);
                        } catch (Exception e) {
                            // Invalid ResourceLocation
                        }
                    }
                }
            }

            // Recurse into all object values
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                searchForLootTables(entry.getValue(), found);
            }

        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement item : arr) {
                searchForLootTables(item, found);
            }
        }
    }

    /**
     * Resolve pool hierarchy to propagate loot tables from fallback pools.
     */
    private void resolvePoolHierarchy() {
        // For each pool that has referenced pools, inherit their loot tables
        Set<ResourceLocation> visited = new HashSet<>();

        for (ResourceLocation poolId : poolHierarchy.keySet()) {
            Set<ResourceLocation> inheritedLoot = new HashSet<>();
            collectLootFromHierarchy(poolId, inheritedLoot, visited);

            if (!inheritedLoot.isEmpty()) {
                poolToLootTables.computeIfAbsent(poolId, k -> new HashSet<>()).addAll(inheritedLoot);
            }
        }
    }

    /**
     * Recursively collect loot tables from pool hierarchy.
     */
    private void collectLootFromHierarchy(ResourceLocation poolId, Set<ResourceLocation> collected,
                                           Set<ResourceLocation> visited) {
        if (visited.contains(poolId)) return; // Prevent cycles
        visited.add(poolId);

        // Add direct loot
        Set<ResourceLocation> directLoot = poolToLootTables.get(poolId);
        if (directLoot != null) {
            collected.addAll(directLoot);
        }

        // Recurse into referenced pools
        Set<ResourceLocation> refs = poolHierarchy.get(poolId);
        if (refs != null) {
            for (ResourceLocation ref : refs) {
                collectLootFromHierarchy(ref, collected, visited);
            }
        }
    }

    /**
     * Extract pool ID from resource path.
     */
    private ResourceLocation extractPoolId(ResourceLocation path) {
        String pathStr = path.getPath();

        if (!pathStr.startsWith("worldgen/template_pool/")) {
            return null;
        }

        String poolPath = pathStr.substring("worldgen/template_pool/".length());
        if (poolPath.endsWith(".json")) {
            poolPath = poolPath.substring(0, poolPath.length() - 5);
        }

        return RegistryHelper.fromNamespaceAndPath(path.getNamespace(), poolPath);
    }

    // --- Query API ---

    /**
     * Get all loot tables referenced by a pool (including inherited).
     */
    public Set<ResourceLocation> getLootTablesForPool(ResourceLocation poolId) {
        return poolToLootTables.getOrDefault(poolId, Set.of());
    }

    /**
     * Get all templates referenced by a pool.
     */
    public Set<ResourceLocation> getTemplatesForPool(ResourceLocation poolId) {
        return poolToTemplates.getOrDefault(poolId, Set.of());
    }

    /**
     * Build a map of all pools to their loot tables.
     */
    public Map<ResourceLocation, Set<ResourceLocation>> buildPoolLootMap() {
        return Collections.unmodifiableMap(poolToLootTables);
    }

    /**
     * Get all pools that reference a specific loot table.
     */
    public Set<ResourceLocation> getPoolsForLootTable(ResourceLocation lootTableId) {
        Set<ResourceLocation> result = new HashSet<>();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : poolToLootTables.entrySet()) {
            if (entry.getValue().contains(lootTableId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get all processors referenced by a pool.
     */
    public Set<ResourceLocation> getProcessorsForPool(ResourceLocation poolId) {
        return poolToProcessors.getOrDefault(poolId, Set.of());
    }

    /**
     * Get all pools (for iteration).
     */
    public Set<ResourceLocation> getAllPoolIds() {
        Set<ResourceLocation> all = new HashSet<>();
        all.addAll(poolToLootTables.keySet());
        all.addAll(poolToTemplates.keySet());
        all.addAll(poolToProcessors.keySet());
        return all;
    }

    /**
     * Get parsing statistics.
     */
    public Stats getStats() {
        return new Stats(filesParsed, poolsWithLoot, totalLootReferences, poolToTemplates.size());
    }

    public record Stats(int filesParsed, int poolsWithLoot, int lootReferences, int templateReferences) {}

    public boolean isParsed() {
        return parsed;
    }

    public int size() {
        return poolToLootTables.size();
    }

    public void clear() {
        poolToLootTables.clear();
        poolToTemplates.clear();
        poolToProcessors.clear();
        poolHierarchy.clear();
        filesParsed = 0;
        poolsWithLoot = 0;
        totalLootReferences = 0;
        parsed = false;
    }
}
