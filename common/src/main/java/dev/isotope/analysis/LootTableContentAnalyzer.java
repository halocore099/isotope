package dev.isotope.analysis;
import dev.isotope.compat.RegistryHelper;

import com.google.gson.*;
import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import dev.isotope.data.StructureLootLink;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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

    // ===== STRUCTURE LINKING: Signature items that hint at specific structures =====

    /**
     * Signature items: items that strongly indicate a specific structure origin.
     * Maps item ID -> list of possible structure IDs with confidence scores.
     *
     * These are items that are unique to or highly characteristic of specific structures.
     */
    private static final Map<String, List<StructureHint>> SIGNATURE_ITEMS = buildSignatureItems();

    /**
     * A hint about which structure a loot table might belong to.
     */
    public record StructureHint(
        ResourceLocation structureId,
        int confidence,      // 0-100, how confident we are this item indicates this structure
        String reason        // Human-readable reason for the hint
    ) {}

    /**
     * Result of structure hint analysis.
     */
    public record StructureHintResult(
        ResourceLocation lootTableId,
        List<StructureHint> hints,
        Set<String> signatureItemsFound
    ) {
        public boolean hasHints() {
            return !hints.isEmpty();
        }

        public Optional<StructureHint> getBestHint() {
            return hints.stream().max(Comparator.comparingInt(StructureHint::confidence));
        }
    }

    private static Map<String, List<StructureHint>> buildSignatureItems() {
        Map<String, List<StructureHint>> items = new HashMap<>();

        // === Buried Treasure ===
        addSignatureItem(items, "minecraft:heart_of_the_sea", "minecraft:buried_treasure", 95,
            "Heart of the Sea is exclusive to buried treasure");

        // === Ancient City ===
        addSignatureItem(items, "minecraft:echo_shard", "minecraft:ancient_city", 90,
            "Echo Shards are exclusive to ancient cities");
        addSignatureItem(items, "minecraft:disc_fragment_5", "minecraft:ancient_city", 95,
            "Disc Fragment 5 is exclusive to ancient cities");
        addSignatureItem(items, "minecraft:ward_armor_trim_smithing_template", "minecraft:ancient_city", 85,
            "Ward trim template found in ancient cities");
        addSignatureItem(items, "minecraft:silence_armor_trim_smithing_template", "minecraft:ancient_city", 85,
            "Silence trim template found in ancient cities");
        addSignatureItem(items, "minecraft:swift_sneak", "minecraft:ancient_city", 80,
            "Swift Sneak enchanted books found in ancient cities");

        // === Woodland Mansion ===
        addSignatureItem(items, "minecraft:totem_of_undying", "minecraft:woodland_mansion", 75,
            "Totem of Undying is associated with Evokers in mansions");
        addSignatureItem(items, "minecraft:vex_armor_trim_smithing_template", "minecraft:woodland_mansion", 90,
            "Vex trim template is exclusive to woodland mansions");

        // === Bastion Remnant ===
        addSignatureItem(items, "minecraft:netherite_upgrade_smithing_template", "minecraft:bastion_remnant", 90,
            "Netherite upgrade template found in bastions");
        addSignatureItem(items, "minecraft:snout_armor_trim_smithing_template", "minecraft:bastion_remnant", 90,
            "Snout trim template found in bastions");
        addSignatureItem(items, "minecraft:piglin_banner_pattern", "minecraft:bastion_remnant", 85,
            "Piglin banner pattern found in bastions");
        addSignatureItem(items, "minecraft:music_disc_pigstep", "minecraft:bastion_remnant", 90,
            "Music Disc Pigstep is exclusive to bastions");
        addSignatureItem(items, "minecraft:ancient_debris", "minecraft:bastion_remnant", 60,
            "Ancient debris can be found in bastion loot");
        addSignatureItem(items, "minecraft:netherite_scrap", "minecraft:bastion_remnant", 65,
            "Netherite scrap found in bastion loot");

        // === End City ===
        addSignatureItem(items, "minecraft:elytra", "minecraft:end_city", 95,
            "Elytra are exclusive to end city ships");
        addSignatureItem(items, "minecraft:dragon_head", "minecraft:end_city", 95,
            "Dragon heads are exclusive to end city ships");
        addSignatureItem(items, "minecraft:spire_armor_trim_smithing_template", "minecraft:end_city", 90,
            "Spire trim template found in end cities");
        addSignatureItem(items, "minecraft:shulker_shell", "minecraft:end_city", 70,
            "Shulker shells associated with end cities");

        // === Trial Chambers ===
        addSignatureItem(items, "minecraft:trial_key", "minecraft:trial_chambers", 95,
            "Trial keys are exclusive to trial chambers");
        addSignatureItem(items, "minecraft:ominous_trial_key", "minecraft:trial_chambers", 95,
            "Ominous trial keys are exclusive to trial chambers");
        addSignatureItem(items, "minecraft:breeze_rod", "minecraft:trial_chambers", 85,
            "Breeze rods are associated with trial chambers");
        addSignatureItem(items, "minecraft:wind_charge", "minecraft:trial_chambers", 70,
            "Wind charges found in trial chambers");
        addSignatureItem(items, "minecraft:flow_armor_trim_smithing_template", "minecraft:trial_chambers", 90,
            "Flow trim template found in trial chambers");
        addSignatureItem(items, "minecraft:bolt_armor_trim_smithing_template", "minecraft:trial_chambers", 90,
            "Bolt trim template found in trial chambers");
        addSignatureItem(items, "minecraft:heavy_core", "minecraft:trial_chambers", 85,
            "Heavy core found in trial chambers");
        addSignatureItem(items, "minecraft:ominous_bottle", "minecraft:trial_chambers", 80,
            "Ominous bottles found in trial chambers");

        // === Stronghold ===
        addSignatureItem(items, "minecraft:ender_pearl", "minecraft:stronghold", 40,
            "Ender pearls found in stronghold loot");
        addSignatureItem(items, "minecraft:eye_armor_trim_smithing_template", "minecraft:stronghold", 90,
            "Eye trim template found in strongholds");

        // === Desert Pyramid ===
        addSignatureItem(items, "minecraft:dune_armor_trim_smithing_template", "minecraft:desert_pyramid", 90,
            "Dune trim template found in desert pyramids");

        // === Jungle Temple ===
        addSignatureItem(items, "minecraft:wild_armor_trim_smithing_template", "minecraft:jungle_pyramid", 90,
            "Wild trim template found in jungle temples");

        // === Ocean Monument ===
        addSignatureItem(items, "minecraft:sponge", "minecraft:monument", 85,
            "Sponges are associated with ocean monuments");
        addSignatureItem(items, "minecraft:tide_armor_trim_smithing_template", "minecraft:monument", 90,
            "Tide trim template found in ocean monuments");
        addSignatureItem(items, "minecraft:prismarine_shard", "minecraft:monument", 60,
            "Prismarine shards associated with ocean monuments");
        addSignatureItem(items, "minecraft:prismarine_crystals", "minecraft:monument", 60,
            "Prismarine crystals associated with ocean monuments");

        // === Shipwreck ===
        addSignatureItem(items, "minecraft:coast_armor_trim_smithing_template", "minecraft:shipwreck", 90,
            "Coast trim template found in shipwrecks");
        addSignatureItem(items, "minecraft:buried_treasure_map", "minecraft:shipwreck", 80,
            "Buried treasure maps found in shipwrecks");

        // === Ocean Ruins ===
        addSignatureItem(items, "minecraft:treasure_map", "minecraft:ocean_ruin_cold", 50,
            "Treasure maps can be found in ocean ruins");
        addSignatureItem(items, "minecraft:treasure_map", "minecraft:ocean_ruin_warm", 50,
            "Treasure maps can be found in ocean ruins");

        // === Nether Fortress ===
        addSignatureItem(items, "minecraft:blaze_rod", "minecraft:fortress", 50,
            "Blaze rods associated with nether fortress");
        addSignatureItem(items, "minecraft:wither_skeleton_skull", "minecraft:fortress", 60,
            "Wither skeleton skulls associated with nether fortress");
        addSignatureItem(items, "minecraft:rib_armor_trim_smithing_template", "minecraft:fortress", 90,
            "Rib trim template found in nether fortresses");

        // === Ruined Portal ===
        addSignatureItem(items, "minecraft:gold_block", "minecraft:ruined_portal", 40,
            "Gold blocks found in ruined portal loot");
        addSignatureItem(items, "minecraft:obsidian", "minecraft:ruined_portal", 50,
            "Obsidian found in ruined portal loot");
        addSignatureItem(items, "minecraft:crying_obsidian", "minecraft:ruined_portal", 70,
            "Crying obsidian found in ruined portal loot");
        addSignatureItem(items, "minecraft:enchanted_golden_apple", "minecraft:ruined_portal", 50,
            "Enchanted golden apples found in ruined portals");

        // === Pillager Outpost ===
        addSignatureItem(items, "minecraft:sentry_armor_trim_smithing_template", "minecraft:pillager_outpost", 90,
            "Sentry trim template found in pillager outposts");
        addSignatureItem(items, "minecraft:crossbow", "minecraft:pillager_outpost", 50,
            "Crossbows associated with pillager outposts");
        addSignatureItem(items, "minecraft:goat_horn", "minecraft:pillager_outpost", 60,
            "Goat horns found in pillager outposts");

        // === Mineshaft ===
        addSignatureItem(items, "minecraft:rail", "minecraft:mineshaft", 40,
            "Rails associated with mineshafts");
        addSignatureItem(items, "minecraft:powered_rail", "minecraft:mineshaft", 50,
            "Powered rails associated with mineshafts");
        addSignatureItem(items, "minecraft:name_tag", "minecraft:mineshaft", 40,
            "Name tags found in mineshaft chests");
        addSignatureItem(items, "minecraft:golden_apple", "minecraft:mineshaft", 30,
            "Golden apples can be found in mineshafts");

        // === Dungeon / Monster Room (feature) ===
        addSignatureItem(items, "minecraft:music_disc_13", "minecraft:monster_room", 60,
            "Music disc 13 found in dungeon chests");
        addSignatureItem(items, "minecraft:music_disc_cat", "minecraft:monster_room", 60,
            "Music disc cat found in dungeon chests");
        addSignatureItem(items, "minecraft:saddle", "minecraft:monster_room", 30,
            "Saddles can be found in dungeon chests");

        // === Village ===
        addSignatureItem(items, "minecraft:emerald", "minecraft:village_plains", 25,
            "Emeralds associated with village trading/loot");
        // Note: villages have low confidence because items are generic

        // === Igloo ===
        addSignatureItem(items, "minecraft:golden_apple", "minecraft:igloo", 40,
            "Golden apples found in igloo chests");
        addSignatureItem(items, "minecraft:splash_potion", "minecraft:igloo", 50,
            "Splash potions found in igloo basements");

        return items;
    }

    private static void addSignatureItem(Map<String, List<StructureHint>> items,
                                          String itemId, String structureId,
                                          int confidence, String reason) {
        items.computeIfAbsent(itemId, k -> new ArrayList<>())
            .add(new StructureHint(RegistryHelper.parseLocation(structureId), confidence, reason));
    }

    // ===== Analysis statistics =====
    private static int tablesAnalyzed = 0;
    private static int structureHintsFound = 0;
    private static final Map<ResourceLocation, StructureHintResult> cachedHints = new LinkedHashMap<>();

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
        ResourceLocation jsonPath = RegistryHelper.fromNamespaceAndPath(
            tableId.getNamespace(),
            "loot_table/" + tableId.getPath() + ".json"
        );

        Optional<Resource> resource = resourceManager.getResource(jsonPath);
        if (resource.isEmpty()) {
            // Try plural form
            jsonPath = RegistryHelper.fromNamespaceAndPath(
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

    // ===== STRUCTURE HINT ANALYSIS =====

    /**
     * Analyze a loot table for structure hints based on signature items.
     *
     * @param server The Minecraft server (for resource access)
     * @param tableId The loot table ID
     * @return Structure hint result, or null if unable to analyze
     */
    public static StructureHintResult analyzeForStructureHints(MinecraftServer server, ResourceLocation tableId) {
        // Check cache first
        if (cachedHints.containsKey(tableId)) {
            return cachedHints.get(tableId);
        }

        ResourceManager resourceManager = server.getResourceManager();

        // Try to load the loot table JSON
        ResourceLocation jsonPath = RegistryHelper.fromNamespaceAndPath(
            tableId.getNamespace(),
            "loot_table/" + tableId.getPath() + ".json"
        );

        Optional<Resource> resource = resourceManager.getResource(jsonPath);
        if (resource.isEmpty()) {
            // Try plural form
            jsonPath = RegistryHelper.fromNamespaceAndPath(
                tableId.getNamespace(),
                "loot_tables/" + tableId.getPath() + ".json"
            );
            resource = resourceManager.getResource(jsonPath);
        }

        if (resource.isEmpty()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            StructureHintResult result = analyzeJsonForStructureHints(tableId, json);

            // Cache the result
            cachedHints.put(tableId, result);
            tablesAnalyzed++;
            if (result.hasHints()) {
                structureHintsFound++;
            }

            return result;

        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to analyze loot table for structure hints {}: {}", tableId, e.getMessage());
            return null;
        }
    }

    /**
     * Analyze a loot table JSON for structure hints based on signature items.
     */
    public static StructureHintResult analyzeJsonForStructureHints(ResourceLocation tableId, JsonObject json) {
        Set<String> allItems = new LinkedHashSet<>();
        Set<String> signatureItemsFound = new LinkedHashSet<>();
        Map<ResourceLocation, List<StructureHint>> hintsByStructure = new LinkedHashMap<>();

        // Extract all items from the loot table
        extractItemsFromJson(json, allItems);

        // Check each item against signature items
        for (String itemId : allItems) {
            List<StructureHint> hints = SIGNATURE_ITEMS.get(itemId);
            if (hints != null) {
                signatureItemsFound.add(itemId);
                for (StructureHint hint : hints) {
                    hintsByStructure.computeIfAbsent(hint.structureId(), k -> new ArrayList<>()).add(hint);
                }
            }
        }

        // Aggregate hints by structure (multiple signature items for same structure = higher confidence)
        List<StructureHint> aggregatedHints = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<StructureHint>> entry : hintsByStructure.entrySet()) {
            List<StructureHint> structureHints = entry.getValue();
            if (structureHints.size() == 1) {
                aggregatedHints.add(structureHints.get(0));
            } else {
                // Multiple signature items for same structure - aggregate confidence
                int maxConfidence = structureHints.stream()
                    .mapToInt(StructureHint::confidence)
                    .max().orElse(0);
                // Boost confidence slightly for multiple matches (cap at 99)
                int boostedConfidence = Math.min(99, maxConfidence + (structureHints.size() - 1) * 5);
                String reasons = structureHints.stream()
                    .map(StructureHint::reason)
                    .collect(Collectors.joining("; "));
                aggregatedHints.add(new StructureHint(entry.getKey(), boostedConfidence, reasons));
            }
        }

        // Sort by confidence (highest first)
        aggregatedHints.sort(Comparator.comparingInt(StructureHint::confidence).reversed());

        if (!aggregatedHints.isEmpty()) {
            Isotope.LOGGER.debug("Structure hints for {}: {} hints from {} signature items",
                tableId, aggregatedHints.size(), signatureItemsFound.size());
        }

        return new StructureHintResult(tableId, aggregatedHints, signatureItemsFound);
    }

    /**
     * Extract all item IDs from a loot table JSON.
     */
    private static void extractItemsFromJson(JsonObject json, Set<String> items) {
        // Check pools
        if (json.has("pools") && json.get("pools").isJsonArray()) {
            for (JsonElement poolElement : json.getAsJsonArray("pools")) {
                if (poolElement.isJsonObject()) {
                    extractItemsFromPool(poolElement.getAsJsonObject(), items);
                }
            }
        }
    }

    /**
     * Extract item IDs from a pool.
     */
    private static void extractItemsFromPool(JsonObject pool, Set<String> items) {
        if (pool.has("entries") && pool.get("entries").isJsonArray()) {
            for (JsonElement entryElement : pool.getAsJsonArray("entries")) {
                if (entryElement.isJsonObject()) {
                    extractItemsFromEntry(entryElement.getAsJsonObject(), items);
                }
            }
        }
    }

    /**
     * Extract item IDs from an entry (recursively for alternatives/groups).
     */
    private static void extractItemsFromEntry(JsonObject entry, Set<String> items) {
        String type = entry.has("type") ? entry.get("type").getAsString() : "";

        // Item entry
        if ((type.equals("minecraft:item") || type.equals("item")) && entry.has("name")) {
            items.add(entry.get("name").getAsString());
        }

        // Recurse into children (alternatives, groups, sequences)
        if (entry.has("children") && entry.get("children").isJsonArray()) {
            for (JsonElement childElement : entry.getAsJsonArray("children")) {
                if (childElement.isJsonObject()) {
                    extractItemsFromEntry(childElement.getAsJsonObject(), items);
                }
            }
        }

        // Check for nested loot table reference
        if ((type.equals("minecraft:loot_table") || type.equals("loot_table")) && entry.has("value")) {
            // Could recursively analyze referenced table, but that's expensive
            // For now, just note it exists
        }
    }

    /**
     * Create StructureLootLinks from structure hints.
     * Only creates links for hints above the confidence threshold.
     */
    public static List<StructureLootLink> createLinksFromHints(
            StructureHintResult result, int minConfidence) {

        List<StructureLootLink> links = new ArrayList<>();

        for (StructureHint hint : result.hints()) {
            if (hint.confidence() >= minConfidence) {
                links.add(StructureLootLink.contentAnalysis(
                    hint.structureId(),
                    result.lootTableId(),
                    hint.confidence()
                ));
            }
        }

        return links;
    }

    /**
     * Analyze all loot tables in a registry and return structure hints.
     */
    public static Map<ResourceLocation, StructureHintResult> analyzeAllForStructureHints(
            MinecraftServer server,
            Collection<ResourceLocation> tableIds) {

        Map<ResourceLocation, StructureHintResult> results = new LinkedHashMap<>();

        for (ResourceLocation tableId : tableIds) {
            StructureHintResult result = analyzeForStructureHints(server, tableId);
            if (result != null && result.hasHints()) {
                results.put(tableId, result);
            }
        }

        Isotope.LOGGER.info("[ContentAnalyzer] Structure hint analysis: {} tables analyzed, {} with hints",
            tablesAnalyzed, structureHintsFound);

        return results;
    }

    /**
     * Get structure hints for orphan loot tables (tables without existing links).
     * This is useful for discovering links for tables that aren't matched by other methods.
     */
    public static Map<ResourceLocation, StructureHintResult> analyzeOrphansForStructureHints(
            MinecraftServer server,
            Collection<ResourceLocation> orphanTableIds,
            int minConfidence) {

        Map<ResourceLocation, StructureHintResult> results = new LinkedHashMap<>();

        for (ResourceLocation tableId : orphanTableIds) {
            StructureHintResult result = analyzeForStructureHints(server, tableId);
            if (result != null && result.hasHints()) {
                // Filter to only include hints above threshold
                Optional<StructureHint> bestHint = result.getBestHint();
                if (bestHint.isPresent() && bestHint.get().confidence() >= minConfidence) {
                    results.put(tableId, result);
                }
            }
        }

        return results;
    }

    /**
     * Get cached hints (for UI display).
     */
    public static Map<ResourceLocation, StructureHintResult> getCachedHints() {
        return Collections.unmodifiableMap(cachedHints);
    }

    /**
     * Get analysis statistics.
     */
    public static Stats getStats() {
        return new Stats(tablesAnalyzed, structureHintsFound, SIGNATURE_ITEMS.size(), cachedHints.size());
    }

    /**
     * Clear cached analysis data.
     */
    public static void clearCache() {
        cachedHints.clear();
        tablesAnalyzed = 0;
        structureHintsFound = 0;
    }

    /**
     * Statistics record.
     */
    public record Stats(
        int tablesAnalyzed,
        int tablesWithHints,
        int signatureItemCount,
        int cachedResults
    ) {
        public String summary() {
            return String.format("%d analyzed, %d with hints, %d signature items",
                tablesAnalyzed, tablesWithHints, signatureItemCount);
        }
    }
}
