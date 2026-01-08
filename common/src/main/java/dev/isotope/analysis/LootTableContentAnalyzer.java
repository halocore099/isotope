package dev.isotope.analysis;

import com.google.gson.*;
import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Content-based loot table category analyzer.
 *
 * Instead of relying on path prefixes (which vary between mods),
 * this analyzer examines the actual JSON structure to determine
 * what type of loot table it is.
 *
 * Detection heuristics:
 * - ENTITY: Has entity-specific conditions (killed_by_player, entity_properties)
 *           or functions (looting_enchant, entity loot context)
 * - BLOCK: Has block-specific conditions (survives_explosion, match_tool, block_state_property)
 * - CHEST: Has multiple pools, item entries, no entity/block markers (container loot)
 * - ARCHAEOLOGY: Contains pottery sherds or archaeology-specific items
 * - GAMEPLAY: Specific mechanics (fishing, bartering) - uses path hints
 * - OTHER: Fallback for unclassifiable tables
 */
public final class LootTableContentAnalyzer {

    private static final Gson GSON = new GsonBuilder().create();

    // Entity-specific condition types
    private static final Set<String> ENTITY_CONDITIONS = Set.of(
        "minecraft:killed_by_player",
        "killed_by_player",
        "minecraft:entity_properties",
        "entity_properties",
        "minecraft:damage_source_properties",
        "damage_source_properties"
    );

    // Entity-specific function types
    private static final Set<String> ENTITY_FUNCTIONS = Set.of(
        "minecraft:looting_enchant",
        "looting_enchant",
        "minecraft:set_lore",  // Often used for mob drops
        "set_lore"
    );

    // Block-specific condition types
    private static final Set<String> BLOCK_CONDITIONS = Set.of(
        "minecraft:survives_explosion",
        "survives_explosion",
        "minecraft:match_tool",
        "match_tool",
        "minecraft:block_state_property",
        "block_state_property",
        "minecraft:table_bonus",
        "table_bonus"
    );

    // Block-specific function types
    private static final Set<String> BLOCK_FUNCTIONS = Set.of(
        "minecraft:explosion_decay",
        "explosion_decay",
        "minecraft:apply_bonus",
        "apply_bonus",
        "minecraft:copy_state",
        "copy_state",
        "minecraft:copy_name",
        "copy_name",
        "minecraft:copy_nbt",
        "copy_nbt"
    );

    // Archaeology-related items (pottery sherds)
    private static final Set<String> ARCHAEOLOGY_ITEMS = Set.of(
        "minecraft:angler_pottery_sherd",
        "minecraft:archer_pottery_sherd",
        "minecraft:arms_up_pottery_sherd",
        "minecraft:blade_pottery_sherd",
        "minecraft:brewer_pottery_sherd",
        "minecraft:burn_pottery_sherd",
        "minecraft:danger_pottery_sherd",
        "minecraft:explorer_pottery_sherd",
        "minecraft:friend_pottery_sherd",
        "minecraft:heart_pottery_sherd",
        "minecraft:heartbreak_pottery_sherd",
        "minecraft:howl_pottery_sherd",
        "minecraft:miner_pottery_sherd",
        "minecraft:mourner_pottery_sherd",
        "minecraft:plenty_pottery_sherd",
        "minecraft:prize_pottery_sherd",
        "minecraft:sheaf_pottery_sherd",
        "minecraft:shelter_pottery_sherd",
        "minecraft:skull_pottery_sherd",
        "minecraft:snort_pottery_sherd",
        "minecraft:flow_pottery_sherd",
        "minecraft:guster_pottery_sherd",
        "minecraft:scrape_pottery_sherd"
    );

    private LootTableContentAnalyzer() {}

    /**
     * Analyze a loot table and determine its category based on content.
     *
     * @param server The Minecraft server (for resource access)
     * @param tableId The loot table ID
     * @return The detected category, or null if unable to analyze
     */
    public static LootTableCategory analyze(MinecraftServer server, ResourceLocation tableId) {
        ResourceManager resourceManager = server.getResourceManager();

        // Loot tables are at: data/<namespace>/loot_table/<path>.json
        // Try both singular and plural paths (some mods differ)
        ResourceLocation jsonPath = ResourceLocation.fromNamespaceAndPath(
            tableId.getNamespace(),
            "loot_table/" + tableId.getPath() + ".json"
        );

        Optional<Resource> resource = resourceManager.getResource(jsonPath);
        if (resource.isEmpty()) {
            // Try plural form
            jsonPath = ResourceLocation.fromNamespaceAndPath(
                tableId.getNamespace(),
                "loot_tables/" + tableId.getPath() + ".json"
            );
            resource = resourceManager.getResource(jsonPath);
        }

        if (resource.isEmpty()) {
            Isotope.LOGGER.debug("Cannot analyze loot table (not found): {}", tableId);
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return analyzeJson(tableId, json);

        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to analyze loot table {}: {}", tableId, e.getMessage());
            return null;
        }
    }

    /**
     * Analyze a loot table JSON and determine its category.
     */
    public static LootTableCategory analyzeJson(ResourceLocation tableId, JsonObject json) {
        ContentAnalysis analysis = new ContentAnalysis();

        // Path-based hints for categories that can't be detected from content alone
        String path = tableId.getPath().toLowerCase();
        if (path.startsWith("gameplay/") || path.contains("/gameplay/")) {
            analysis.gameplayScore += 10;
        }
        if (path.startsWith("equipment/") || path.contains("/equipment/")) {
            analysis.equipmentScore += 10;
        }
        if (path.startsWith("shearing/") || path.contains("/shearing/")) {
            // Shearing is a type of gameplay
            analysis.gameplayScore += 10;
        }

        // Check the loot table type field
        if (json.has("type")) {
            String type = json.get("type").getAsString();
            if (type.contains("block")) {
                analysis.blockScore += 10;
            } else if (type.contains("entity")) {
                analysis.entityScore += 10;
            } else if (type.contains("chest") || type.contains("generic")) {
                analysis.chestScore += 5;
            }
        }

        // Analyze pools
        if (json.has("pools") && json.get("pools").isJsonArray()) {
            JsonArray pools = json.getAsJsonArray("pools");

            // Multiple pools often indicates chest loot
            if (pools.size() > 1) {
                analysis.chestScore += 3;
            }

            for (JsonElement poolElement : pools) {
                if (poolElement.isJsonObject()) {
                    analyzePool(poolElement.getAsJsonObject(), analysis);
                }
            }
        }

        // Analyze top-level functions
        if (json.has("functions") && json.get("functions").isJsonArray()) {
            for (JsonElement funcElement : json.getAsJsonArray("functions")) {
                if (funcElement.isJsonObject()) {
                    analyzeFunction(funcElement.getAsJsonObject(), analysis);
                }
            }
        }

        // Determine category based on scores
        LootTableCategory detected = analysis.getCategory();

        Isotope.LOGGER.debug("Content analysis for {}: entity={}, block={}, chest={}, arch={} -> {}",
            tableId, analysis.entityScore, analysis.blockScore, analysis.chestScore,
            analysis.archaeologyScore, detected);

        return detected;
    }

    /**
     * Analyze a pool for category indicators.
     */
    private static void analyzePool(JsonObject pool, ContentAnalysis analysis) {
        // Check pool conditions
        if (pool.has("conditions") && pool.get("conditions").isJsonArray()) {
            for (JsonElement condElement : pool.getAsJsonArray("conditions")) {
                if (condElement.isJsonObject()) {
                    analyzeCondition(condElement.getAsJsonObject(), analysis);
                }
            }
        }

        // Check pool functions
        if (pool.has("functions") && pool.get("functions").isJsonArray()) {
            for (JsonElement funcElement : pool.getAsJsonArray("functions")) {
                if (funcElement.isJsonObject()) {
                    analyzeFunction(funcElement.getAsJsonObject(), analysis);
                }
            }
        }

        // Analyze entries
        if (pool.has("entries") && pool.get("entries").isJsonArray()) {
            for (JsonElement entryElement : pool.getAsJsonArray("entries")) {
                if (entryElement.isJsonObject()) {
                    analyzeEntry(entryElement.getAsJsonObject(), analysis);
                }
            }
        }
    }

    /**
     * Analyze an entry for category indicators.
     */
    private static void analyzeEntry(JsonObject entry, ContentAnalysis analysis) {
        // Check entry type
        String type = entry.has("type") ? entry.get("type").getAsString() : "";

        // Item entries are common in chests
        if (type.equals("minecraft:item") || type.equals("item")) {
            analysis.chestScore += 1;

            // Check for archaeology items
            if (entry.has("name")) {
                String itemName = entry.get("name").getAsString();
                if (ARCHAEOLOGY_ITEMS.contains(itemName) || itemName.contains("pottery_sherd")) {
                    analysis.archaeologyScore += 10;
                }
            }
        }

        // Loot table references might indicate chest loot
        if (type.equals("minecraft:loot_table") || type.equals("loot_table")) {
            analysis.chestScore += 2;
        }

        // Check entry conditions
        if (entry.has("conditions") && entry.get("conditions").isJsonArray()) {
            for (JsonElement condElement : entry.getAsJsonArray("conditions")) {
                if (condElement.isJsonObject()) {
                    analyzeCondition(condElement.getAsJsonObject(), analysis);
                }
            }
        }

        // Check entry functions
        if (entry.has("functions") && entry.get("functions").isJsonArray()) {
            for (JsonElement funcElement : entry.getAsJsonArray("functions")) {
                if (funcElement.isJsonObject()) {
                    analyzeFunction(funcElement.getAsJsonObject(), analysis);
                }
            }
        }

        // Recurse into children (for alternatives, groups, etc.)
        if (entry.has("children") && entry.get("children").isJsonArray()) {
            for (JsonElement childElement : entry.getAsJsonArray("children")) {
                if (childElement.isJsonObject()) {
                    analyzeEntry(childElement.getAsJsonObject(), analysis);
                }
            }
        }
    }

    /**
     * Analyze a condition for category indicators.
     */
    private static void analyzeCondition(JsonObject condition, ContentAnalysis analysis) {
        String conditionType = condition.has("condition")
            ? condition.get("condition").getAsString()
            : "";

        if (ENTITY_CONDITIONS.contains(conditionType)) {
            analysis.entityScore += 5;
        }

        if (BLOCK_CONDITIONS.contains(conditionType)) {
            analysis.blockScore += 5;
        }

        // Random chance conditions are common in chests
        if (conditionType.contains("random_chance")) {
            analysis.chestScore += 1;
        }
    }

    /**
     * Analyze a function for category indicators.
     */
    private static void analyzeFunction(JsonObject function, ContentAnalysis analysis) {
        String functionType = function.has("function")
            ? function.get("function").getAsString()
            : "";

        if (ENTITY_FUNCTIONS.contains(functionType)) {
            analysis.entityScore += 5;
        }

        if (BLOCK_FUNCTIONS.contains(functionType)) {
            analysis.blockScore += 5;
        }

        // set_count is common in chests
        if (functionType.contains("set_count")) {
            analysis.chestScore += 1;
        }

        // enchant functions are common in chests
        if (functionType.contains("enchant")) {
            analysis.chestScore += 2;
        }
    }

    /**
     * Helper class to accumulate analysis scores.
     */
    private static class ContentAnalysis {
        int entityScore = 0;
        int blockScore = 0;
        int chestScore = 0;
        int archaeologyScore = 0;
        int gameplayScore = 0;
        int equipmentScore = 0;

        LootTableCategory getCategory() {
            // Archaeology has highest priority (specific items)
            if (archaeologyScore >= 10) {
                return LootTableCategory.ARCHAEOLOGY;
            }

            // Entity and block have strong signals from conditions/functions
            if (entityScore >= 5 && entityScore > blockScore && entityScore > chestScore) {
                return LootTableCategory.ENTITY;
            }

            if (blockScore >= 5 && blockScore > entityScore && blockScore > chestScore) {
                return LootTableCategory.BLOCK;
            }

            // Gameplay and equipment from path hints
            if (gameplayScore > 0) {
                return LootTableCategory.GAMEPLAY;
            }

            if (equipmentScore > 0) {
                return LootTableCategory.EQUIPMENT;
            }

            // CHEST requires positive evidence - multiple pools OR high chest score
            // Don't default to CHEST just because there's no entity/block markers
            if (chestScore >= 3) {
                return LootTableCategory.CHEST;
            }

            // Default to OTHER for ambiguous tables
            return LootTableCategory.OTHER;
        }
    }
}
