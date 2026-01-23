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
 * Parses structure definition JSON files to extract loot table references.
 *
 * Structure definitions in worldgen/structure/*.json can contain:
 * - start_pool references (for jigsaw structures) - cross-reference with TemplatePoolParser
 * - Direct loot_table references in configuration
 * - Biome placement and other metadata
 *
 * This provides direct structure-to-pool mappings which improves accuracy over
 * name-based heuristics used elsewhere.
 *
 * Path: data/<namespace>/worldgen/structure/<path>.json
 */
public final class StructureConfigParser {

    private static final StructureConfigParser INSTANCE = new StructureConfigParser();
    private static final Gson GSON = new GsonBuilder().create();

    // Structure ID -> start pool ID (for jigsaw structures)
    private final Map<Id, Id> structureToStartPool = new LinkedHashMap<>();

    // Structure ID -> loot tables found directly in the config
    private final Map<Id, Set<Id>> structureToLootTables = new LinkedHashMap<>();

    // Structure ID -> structure type (e.g., "minecraft:jigsaw", "minecraft:buried_treasure")
    private final Map<Id, String> structureTypes = new LinkedHashMap<>();

    // Statistics
    private int filesParsed = 0;
    private int jigsawStructures = 0;
    private int structuresWithLoot = 0;
    private int totalLootReferences = 0;
    private boolean parsed = false;

    private StructureConfigParser() {}

    public static StructureConfigParser getInstance() {
        return INSTANCE;
    }

    /**
     * Parse all structure definition JSON files.
     */
    public void parse(MinecraftServer server) {
        clear();

        try {
            ResourceManager resourceManager = server.getResourceManager();

            // Scan for structure JSON files
            var resources = resourceManager.listResources(
                "worldgen/structure",
                path -> path.getPath().endsWith(".json")
            );

            Isotope.LOGGER.debug("[StructureConfigParser] Found {} structure files", resources.size());

            for (var entry : resources.entrySet()) {
                parseStructureJson(Id.wrap(entry.getKey()), entry.getValue());
                filesParsed++;
            }

            parsed = true;
            Isotope.LOGGER.info("[StructureConfigParser] Parsed {} files, {} jigsaw structures, {} with direct loot ({} refs)",
                filesParsed, jigsawStructures, structuresWithLoot, totalLootReferences);

        } catch (Exception e) {
            Isotope.LOGGER.error("[StructureConfigParser] Failed to parse structure configs", e);
        }
    }

    /**
     * Parse a single structure definition JSON file.
     */
    private void parseStructureJson(Id path, Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            // Build the structure ID from the path
            Id structureId = extractStructureId(path);
            if (structureId == null) return;

            // Get structure type
            String type = "unknown";
            if (json.has("type")) {
                type = json.get("type").getAsString();
                structureTypes.put(structureId, type);
            }

            Set<Id> foundLoot = new HashSet<>();

            // Handle jigsaw structures - they have a start_pool
            if (type.equals("minecraft:jigsaw") || json.has("start_pool")) {
                parseJigsawStructure(json, structureId, foundLoot);
                jigsawStructures++;
            }

            // Handle specific structure types that have known loot patterns
            parseStructureTypeLoot(type, json, structureId, foundLoot);

            // Generic recursive search for any loot_table references
            searchForLootTables(json, foundLoot);

            // Store results
            if (!foundLoot.isEmpty()) {
                structureToLootTables.put(structureId, foundLoot);
                structuresWithLoot++;
                totalLootReferences += foundLoot.size();
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("[StructureConfigParser] Failed to parse {}: {}", path, e.getMessage());
        }
    }

    /**
     * Parse a jigsaw structure for its start_pool reference.
     */
    private void parseJigsawStructure(JsonObject json, Id structureId, Set<Id> foundLoot) {
        if (json.has("start_pool")) {
            JsonElement startPool = json.get("start_pool");
            String poolStr = null;

            // Can be a string or an object with "value" key
            if (startPool.isJsonPrimitive()) {
                poolStr = startPool.getAsString();
            } else if (startPool.isJsonObject() && startPool.getAsJsonObject().has("value")) {
                poolStr = startPool.getAsJsonObject().get("value").getAsString();
            }

            if (poolStr != null) {
                // Remove # prefix if present (registry reference format)
                if (poolStr.startsWith("#")) {
                    poolStr = poolStr.substring(1);
                }
                try {
                    Id poolId = Id.parse(poolStr);
                    structureToStartPool.put(structureId, poolId);

                    // Cross-reference with TemplatePoolParser to get loot from this pool
                    TemplatePoolParser poolParser = TemplatePoolParser.getInstance();
                    if (poolParser.isParsed()) {
                        foundLoot.addAll(poolParser.getLootTablesForPool(poolId));
                    }
                } catch (Exception e) {
                    // Invalid pool reference
                }
            }
        }

        // Also check start_jigsaw_name if present (newer format)
        if (json.has("start_jigsaw_name")) {
            // This references a specific jigsaw block, less useful for loot
        }
    }

    /**
     * Parse structure-type-specific loot references.
     */
    private void parseStructureTypeLoot(String type, JsonObject json, Id structureId,
                                         Set<Id> foundLoot) {
        switch (type) {
            case "minecraft:buried_treasure":
                // Buried treasure has a known loot table
                foundLoot.add(Id.of("minecraft", "chests/buried_treasure"));
                break;

            case "minecraft:desert_pyramid":
                foundLoot.add(Id.of("minecraft", "chests/desert_pyramid"));
                break;

            case "minecraft:jungle_pyramid":
                foundLoot.add(Id.of("minecraft", "chests/jungle_temple"));
                foundLoot.add(Id.of("minecraft", "chests/jungle_temple_dispenser"));
                break;

            case "minecraft:igloo":
                foundLoot.add(Id.of("minecraft", "chests/igloo_chest"));
                break;

            case "minecraft:shipwreck":
            case "minecraft:shipwreck_beached":
                foundLoot.add(Id.of("minecraft", "chests/shipwreck_map"));
                foundLoot.add(Id.of("minecraft", "chests/shipwreck_supply"));
                foundLoot.add(Id.of("minecraft", "chests/shipwreck_treasure"));
                break;

            case "minecraft:stronghold":
                foundLoot.add(Id.of("minecraft", "chests/stronghold_corridor"));
                foundLoot.add(Id.of("minecraft", "chests/stronghold_crossing"));
                foundLoot.add(Id.of("minecraft", "chests/stronghold_library"));
                break;

            case "minecraft:fortress":
                foundLoot.add(Id.of("minecraft", "chests/nether_bridge"));
                break;

            case "minecraft:ocean_ruin_cold":
            case "minecraft:ocean_ruin_warm":
                foundLoot.add(Id.of("minecraft", "chests/underwater_ruin_small"));
                foundLoot.add(Id.of("minecraft", "chests/underwater_ruin_big"));
                foundLoot.add(Id.of("minecraft", "archaeology/ocean_ruin_cold"));
                foundLoot.add(Id.of("minecraft", "archaeology/ocean_ruin_warm"));
                break;

            case "minecraft:mineshaft":
            case "minecraft:mineshaft_mesa":
                foundLoot.add(Id.of("minecraft", "chests/abandoned_mineshaft"));
                break;

            case "minecraft:woodland_mansion":
                foundLoot.add(Id.of("minecraft", "chests/woodland_mansion"));
                break;

            case "minecraft:end_city":
                foundLoot.add(Id.of("minecraft", "chests/end_city_treasure"));
                break;

            case "minecraft:ruined_portal":
            case "minecraft:ruined_portal_desert":
            case "minecraft:ruined_portal_jungle":
            case "minecraft:ruined_portal_mountain":
            case "minecraft:ruined_portal_nether":
            case "minecraft:ruined_portal_ocean":
            case "minecraft:ruined_portal_swamp":
                foundLoot.add(Id.of("minecraft", "chests/ruined_portal"));
                break;

            case "minecraft:pillager_outpost":
                foundLoot.add(Id.of("minecraft", "chests/pillager_outpost"));
                break;

            default:
                // For unknown types, rely on generic search
                break;
        }
    }

    /**
     * Recursively search a JSON structure for loot_table references.
     */
    private void searchForLootTables(JsonElement element, Set<Id> found) {
        if (element == null) return;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Check for loot_table key (various formats)
            for (String key : List.of("loot_table", "LootTable", "loot", "chest_loot", "loot_tables")) {
                if (obj.has(key)) {
                    JsonElement lootElement = obj.get(key);
                    if (lootElement.isJsonPrimitive()) {
                        String lootTableStr = lootElement.getAsString();
                        try {
                            Id lootId = Id.parse(lootTableStr);
                            found.add(lootId);
                        } catch (Exception e) {
                            // Invalid Id
                        }
                    } else if (lootElement.isJsonArray()) {
                        // Array of loot tables
                        for (JsonElement item : lootElement.getAsJsonArray()) {
                            if (item.isJsonPrimitive()) {
                                try {
                                    found.add(Id.parse(item.getAsString()));
                                } catch (Exception e) {
                                    // Invalid
                                }
                            }
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
     * Extract structure ID from resource path.
     */
    private Id extractStructureId(Id path) {
        String pathStr = path.getPath();

        if (!pathStr.startsWith("worldgen/structure/")) {
            return null;
        }

        String structurePath = pathStr.substring("worldgen/structure/".length());
        if (structurePath.endsWith(".json")) {
            structurePath = structurePath.substring(0, structurePath.length() - 5);
        }

        return Id.of(path.getNamespace(), structurePath);
    }

    // --- Query API ---

    /**
     * Get the start pool for a jigsaw structure.
     */
    public Optional<Id> getStartPoolForStructure(Id structureId) {
        return Optional.ofNullable(structureToStartPool.get(structureId));
    }

    /**
     * Get all loot tables directly referenced by a structure config.
     */
    public Set<Id> getLootTablesForStructure(Id structureId) {
        return structureToLootTables.getOrDefault(structureId, Set.of());
    }

    /**
     * Get the structure type for a structure.
     */
    public Optional<String> getStructureType(Id structureId) {
        return Optional.ofNullable(structureTypes.get(structureId));
    }

    /**
     * Build a map of all structures to their loot tables.
     */
    public Map<Id, Set<Id>> buildStructureLootMap() {
        return Collections.unmodifiableMap(structureToLootTables);
    }

    /**
     * Get all jigsaw structures and their start pools.
     */
    public Map<Id, Id> getJigsawStartPools() {
        return Collections.unmodifiableMap(structureToStartPool);
    }

    /**
     * Check if a structure is a jigsaw structure.
     */
    public boolean isJigsawStructure(Id structureId) {
        return structureToStartPool.containsKey(structureId);
    }

    /**
     * Get parsing statistics.
     */
    public Stats getStats() {
        return new Stats(filesParsed, jigsawStructures, structuresWithLoot, totalLootReferences);
    }

    public record Stats(int filesParsed, int jigsawStructures, int structuresWithLoot, int lootReferences) {}

    public boolean isParsed() {
        return parsed;
    }

    public int size() {
        return structureToLootTables.size();
    }

    public void clear() {
        structureToStartPool.clear();
        structureToLootTables.clear();
        structureTypes.clear();
        filesParsed = 0;
        jigsawStructures = 0;
        structuresWithLoot = 0;
        totalLootReferences = 0;
        parsed = false;
    }
}
