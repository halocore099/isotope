package dev.isotope.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import dev.isotope.compat.Id;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses worldgen JSON files to discover features with loot tables.
 *
 * Scans data/<namespace>/worldgen/configured_feature/*.json for loot table
 * references, replacing the need for hardcoded feature-to-loot mappings.
 *
 * This enables automatic discovery of modded features with loot, improving
 * linking accuracy without requiring explicit mod support.
 *
 * Confidence: HIGH (70) - deterministic JSON parsing
 */
public final class WorldgenFeatureParser {

    private static final WorldgenFeatureParser INSTANCE = new WorldgenFeatureParser();
    private static final Gson GSON = new GsonBuilder().create();

    // Discovered feature -> loot tables mapping
    private final Map<Id, FeatureDiscovery> discoveries = new LinkedHashMap<>();

    // Statistics
    private int filesParsed = 0;
    private int featuresWithLoot = 0;
    private int totalLootReferences = 0;

    private boolean parsed = false;

    private WorldgenFeatureParser() {}

    public static WorldgenFeatureParser getInstance() {
        return INSTANCE;
    }

    /**
     * Record of a discovered feature with loot tables.
     */
    public record FeatureDiscovery(
        Id featureId,
        String featureType,
        Set<Id> lootTables,
        String sourcePath
    ) {}

    /**
     * Parse all worldgen JSON files from the server's resources.
     */
    public void parse(MinecraftServer server) {
        discoveries.clear();
        filesParsed = 0;
        featuresWithLoot = 0;
        totalLootReferences = 0;

        try {
            ResourceManager resourceManager = server.getResourceManager();

            // Scan for configured_feature JSON files
            // Path: data/<namespace>/worldgen/configured_feature/<path>.json
            var resources = resourceManager.listResources(
                "worldgen/configured_feature",
                path -> path.getPath().endsWith(".json")
            );

            Isotope.LOGGER.debug("[WorldgenFeatureParser] Found {} configured_feature files", resources.size());

            for (var entry : resources.entrySet()) {
                parseFeatureJson(Id.wrap(entry.getKey()), entry.getValue());
                filesParsed++;
            }

            // Also scan placed_feature for any loot references there
            var placedResources = resourceManager.listResources(
                "worldgen/placed_feature",
                path -> path.getPath().endsWith(".json")
            );

            for (var entry : placedResources.entrySet()) {
                parseFeatureJson(Id.wrap(entry.getKey()), entry.getValue());
                filesParsed++;
            }

            parsed = true;
            Isotope.LOGGER.info("[WorldgenFeatureParser] Parsed {} files, found {} features with {} loot references",
                filesParsed, featuresWithLoot, totalLootReferences);

        } catch (Exception e) {
            Isotope.LOGGER.error("[WorldgenFeatureParser] Failed to parse worldgen files", e);
        }
    }

    /**
     * Parse a single feature JSON file.
     */
    private void parseFeatureJson(Id path, Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            // Build the feature ID from the path
            // Path format: <namespace>:worldgen/configured_feature/<path>.json
            // We want: <namespace>:<path>
            Id featureId = extractFeatureId(path);
            if (featureId == null) return;

            // Get feature type if present
            String featureType = json.has("type") ? json.get("type").getAsString() : "unknown";

            // Recursively search for loot_table references
            Set<Id> foundLoot = new HashSet<>();
            searchForLootTables(json, foundLoot);

            if (!foundLoot.isEmpty()) {
                discoveries.put(featureId, new FeatureDiscovery(
                    featureId,
                    featureType,
                    foundLoot,
                    path.toString()
                ));
                featuresWithLoot++;
                totalLootReferences += foundLoot.size();

                Isotope.LOGGER.debug("[WorldgenFeatureParser] {} ({}) -> {}",
                    featureId, featureType, foundLoot);
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("[WorldgenFeatureParser] Failed to parse {}: {}", path, e.getMessage());
        }
    }

    /**
     * Extract a feature ID from a resource path.
     */
    private Id extractFeatureId(Id path) {
        // Path: <namespace>:worldgen/configured_feature/<path>.json
        // or: <namespace>:worldgen/placed_feature/<path>.json
        String pathStr = path.getPath();

        // Remove prefix and .json suffix
        String featurePath = null;
        if (pathStr.startsWith("worldgen/configured_feature/")) {
            featurePath = pathStr.substring("worldgen/configured_feature/".length());
        } else if (pathStr.startsWith("worldgen/placed_feature/")) {
            featurePath = pathStr.substring("worldgen/placed_feature/".length());
        }

        if (featurePath == null) return null;

        // Remove .json suffix
        if (featurePath.endsWith(".json")) {
            featurePath = featurePath.substring(0, featurePath.length() - 5);
        }

        return Id.of(path.getNamespace(), featurePath);
    }

    /**
     * Recursively search a JSON structure for loot_table references.
     */
    private void searchForLootTables(JsonElement element, Set<Id> found) {
        if (element == null) return;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Check for loot_table key
            if (obj.has("loot_table")) {
                JsonElement lootElement = obj.get("loot_table");
                if (lootElement.isJsonPrimitive()) {
                    String lootTableStr = lootElement.getAsString();
                    try {
                        Id lootId = Id.parse(lootTableStr);
                        found.add(lootId);
                    } catch (Exception e) {
                        // Invalid Id, skip
                    }
                }
            }

            // Also check for LootTable (capitalized, used in some structures)
            if (obj.has("LootTable")) {
                JsonElement lootElement = obj.get("LootTable");
                if (lootElement.isJsonPrimitive()) {
                    String lootTableStr = lootElement.getAsString();
                    try {
                        Id lootId = Id.parse(lootTableStr);
                        found.add(lootId);
                    } catch (Exception e) {
                        // Invalid Id, skip
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
        // Primitives don't need searching
    }

    /**
     * Get all discovered features with loot.
     */
    public Collection<FeatureDiscovery> getAllDiscoveries() {
        return Collections.unmodifiableCollection(discoveries.values());
    }

    /**
     * Get discovery for a specific feature.
     */
    public Optional<FeatureDiscovery> getDiscovery(Id featureId) {
        return Optional.ofNullable(discoveries.get(featureId));
    }

    /**
     * Get all loot tables associated with a feature.
     */
    public Set<Id> getLootTablesForFeature(Id featureId) {
        FeatureDiscovery discovery = discoveries.get(featureId);
        return discovery != null ? discovery.lootTables() : Set.of();
    }

    /**
     * Build a map of feature ID -> loot tables for linking.
     */
    public Map<Id, Set<Id>> buildFeatureLootMap() {
        Map<Id, Set<Id>> result = new LinkedHashMap<>();
        for (FeatureDiscovery discovery : discoveries.values()) {
            result.put(discovery.featureId(), discovery.lootTables());
        }
        return result;
    }

    /**
     * Get all unique namespaces from discovered features.
     */
    public Set<String> getNamespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        for (Id id : discoveries.keySet()) {
            namespaces.add(id.getNamespace());
        }
        return namespaces;
    }

    /**
     * Get parsing statistics.
     */
    public Stats getStats() {
        return new Stats(filesParsed, featuresWithLoot, totalLootReferences);
    }

    public record Stats(int filesParsed, int featuresWithLoot, int lootReferences) {}

    /**
     * Check if parsing has been done.
     */
    public boolean isParsed() {
        return parsed;
    }

    /**
     * Get total count of features discovered.
     */
    public int size() {
        return discoveries.size();
    }

    /**
     * Clear all discoveries.
     */
    public void clear() {
        discoveries.clear();
        filesParsed = 0;
        featuresWithLoot = 0;
        totalLootReferences = 0;
        parsed = false;
    }
}
