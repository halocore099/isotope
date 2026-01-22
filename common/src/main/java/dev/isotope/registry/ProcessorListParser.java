package dev.isotope.registry;

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
 * Parses processor list JSON files to extract loot table references.
 *
 * Processor lists are used to modify structure templates during placement.
 * They can contain rules that set loot tables on containers, which gives us
 * another source of structure-to-loot-table mappings.
 *
 * Common processor types that can reference loot tables:
 * - rule processor: Can set output_nbt with LootTable key
 * - block_entity_modifier: Can modify container NBT
 * - capped/weighted: Delegates to other processors
 *
 * Path: data/<namespace>/worldgen/processor_list/<path>.json
 */
public final class ProcessorListParser {

    private static final ProcessorListParser INSTANCE = new ProcessorListParser();
    private static final Gson GSON = new GsonBuilder().create();

    // Processor ID -> loot tables found in that processor
    private final Map<ResourceLocation, Set<ResourceLocation>> processorToLootTables = new LinkedHashMap<>();

    // Reverse lookup: loot table -> processors that reference it
    private final Map<ResourceLocation, Set<ResourceLocation>> lootTableToProcessors = new LinkedHashMap<>();

    // Statistics
    private int filesParsed = 0;
    private int processorsWithLoot = 0;
    private int totalLootReferences = 0;
    private boolean parsed = false;

    private ProcessorListParser() {}

    public static ProcessorListParser getInstance() {
        return INSTANCE;
    }

    /**
     * Parse all processor list JSON files.
     */
    public void parse(MinecraftServer server) {
        clear();

        try {
            ResourceManager resourceManager = server.getResourceManager();

            // Scan for processor_list JSON files
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                "worldgen/processor_list",
                path -> path.getPath().endsWith(".json")
            );

            Isotope.LOGGER.debug("[ProcessorListParser] Found {} processor_list files", resources.size());

            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                parseProcessorJson(entry.getKey(), entry.getValue());
                filesParsed++;
            }

            parsed = true;
            Isotope.LOGGER.info("[ProcessorListParser] Parsed {} files, found {} processors with {} loot references",
                filesParsed, processorsWithLoot, totalLootReferences);

        } catch (Exception e) {
            Isotope.LOGGER.error("[ProcessorListParser] Failed to parse processor lists", e);
        }
    }

    /**
     * Parse a single processor list JSON file.
     */
    private void parseProcessorJson(ResourceLocation path, Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            // Build the processor ID from the path
            ResourceLocation processorId = extractProcessorId(path);
            if (processorId == null) return;

            Set<ResourceLocation> foundLoot = new HashSet<>();

            // Parse processors array
            if (json.has("processors")) {
                JsonArray processors = json.getAsJsonArray("processors");
                for (JsonElement proc : processors) {
                    if (proc.isJsonObject()) {
                        parseProcessor(proc.getAsJsonObject(), foundLoot);
                    }
                }
            }

            // Store results
            if (!foundLoot.isEmpty()) {
                processorToLootTables.put(processorId, foundLoot);
                processorsWithLoot++;
                totalLootReferences += foundLoot.size();

                // Build reverse lookup
                for (ResourceLocation tableId : foundLoot) {
                    lootTableToProcessors.computeIfAbsent(tableId, k -> new HashSet<>()).add(processorId);
                }
            }

        } catch (Exception e) {
            Isotope.LOGGER.debug("[ProcessorListParser] Failed to parse {}: {}", path, e.getMessage());
        }
    }

    /**
     * Parse a processor element for loot table references.
     */
    private void parseProcessor(JsonObject processor, Set<ResourceLocation> foundLoot) {
        String type = processor.has("processor_type") ?
            processor.get("processor_type").getAsString() : "";

        // Handle different processor types
        switch (type) {
            case "minecraft:rule":
                parseRuleProcessor(processor, foundLoot);
                break;
            case "minecraft:capped":
            case "minecraft:weighted_processor":
                // Delegate processors - recurse into delegate
                if (processor.has("delegate")) {
                    JsonElement delegate = processor.get("delegate");
                    if (delegate.isJsonObject()) {
                        parseProcessor(delegate.getAsJsonObject(), foundLoot);
                    }
                }
                // Weighted processor has processors array
                if (processor.has("processors")) {
                    JsonArray procs = processor.getAsJsonArray("processors");
                    for (JsonElement p : procs) {
                        if (p.isJsonObject()) {
                            parseProcessor(p.getAsJsonObject(), foundLoot);
                        }
                    }
                }
                break;
            default:
                // For any other processor type, do a deep search for loot tables
                searchForLootTables(processor, foundLoot);
                break;
        }
    }

    /**
     * Parse a rule processor which can contain output_nbt with LootTable.
     */
    private void parseRuleProcessor(JsonObject processor, Set<ResourceLocation> foundLoot) {
        if (!processor.has("rules")) return;

        JsonArray rules = processor.getAsJsonArray("rules");
        for (JsonElement ruleElem : rules) {
            if (!ruleElem.isJsonObject()) continue;
            JsonObject rule = ruleElem.getAsJsonObject();

            // Check output_nbt for LootTable
            if (rule.has("output_nbt")) {
                JsonElement nbt = rule.get("output_nbt");
                if (nbt.isJsonObject()) {
                    searchForLootTables(nbt.getAsJsonObject(), foundLoot);
                } else if (nbt.isJsonPrimitive()) {
                    // NBT can be a string (SNBT format)
                    String snbt = nbt.getAsString();
                    extractLootFromSnbt(snbt, foundLoot);
                }
            }

            // Also check block_entity_modifier if present
            if (rule.has("block_entity_modifier")) {
                JsonElement modifier = rule.get("block_entity_modifier");
                if (modifier.isJsonObject()) {
                    searchForLootTables(modifier.getAsJsonObject(), foundLoot);
                }
            }
        }
    }

    /**
     * Extract loot table references from SNBT string.
     * Looks for patterns like: LootTable:"minecraft:chests/simple_dungeon"
     */
    private void extractLootFromSnbt(String snbt, Set<ResourceLocation> found) {
        // Pattern: LootTable:"namespace:path"
        int idx = snbt.indexOf("LootTable:");
        while (idx >= 0) {
            int start = snbt.indexOf('"', idx);
            if (start < 0) break;
            int end = snbt.indexOf('"', start + 1);
            if (end < 0) break;

            String tableStr = snbt.substring(start + 1, end);
            try {
                ResourceLocation tableId = ResourceLocation.parse(tableStr);
                found.add(tableId);
            } catch (Exception e) {
                // Invalid resource location
            }

            idx = snbt.indexOf("LootTable:", end);
        }
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
                            ResourceLocation lootId = ResourceLocation.parse(lootTableStr);
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
     * Extract processor ID from resource path.
     */
    private ResourceLocation extractProcessorId(ResourceLocation path) {
        String pathStr = path.getPath();

        if (!pathStr.startsWith("worldgen/processor_list/")) {
            return null;
        }

        String processorPath = pathStr.substring("worldgen/processor_list/".length());
        if (processorPath.endsWith(".json")) {
            processorPath = processorPath.substring(0, processorPath.length() - 5);
        }

        return ResourceLocation.fromNamespaceAndPath(path.getNamespace(), processorPath);
    }

    // --- Query API ---

    /**
     * Get all loot tables referenced by a processor.
     */
    public Set<ResourceLocation> getLootTablesForProcessor(ResourceLocation processorId) {
        return processorToLootTables.getOrDefault(processorId, Set.of());
    }

    /**
     * Get all processors that reference a specific loot table.
     */
    public Set<ResourceLocation> getProcessorsForLootTable(ResourceLocation lootTableId) {
        return lootTableToProcessors.getOrDefault(lootTableId, Set.of());
    }

    /**
     * Build a map of all processors to their loot tables.
     */
    public Map<ResourceLocation, Set<ResourceLocation>> buildProcessorLootMap() {
        return Collections.unmodifiableMap(processorToLootTables);
    }

    /**
     * Get all loot tables found in any processor.
     */
    public Set<ResourceLocation> getAllReferencedLootTables() {
        return Collections.unmodifiableSet(lootTableToProcessors.keySet());
    }

    /**
     * Get parsing statistics.
     */
    public Stats getStats() {
        return new Stats(filesParsed, processorsWithLoot, totalLootReferences);
    }

    public record Stats(int filesParsed, int processorsWithLoot, int lootReferences) {}

    public boolean isParsed() {
        return parsed;
    }

    public int size() {
        return processorToLootTables.size();
    }

    public void clear() {
        processorToLootTables.clear();
        lootTableToProcessors.clear();
        filesParsed = 0;
        processorsWithLoot = 0;
        totalLootReferences = 0;
        parsed = false;
    }
}
