package dev.isotope.editing;

import com.google.gson.*;
import dev.isotope.Isotope;
import dev.isotope.data.loot.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses loot table JSON files into LootTableStructure objects.
 *
 * Uses the server's resource manager to read loot table JSON files,
 * providing a structured representation for the editor UI.
 */
public final class LootTableParser {

    private static final Gson GSON = new GsonBuilder().create();

    private LootTableParser() {}

    /**
     * Parse a loot table from the server's resources.
     *
     * @param server The Minecraft server (for resource access)
     * @param tableId The loot table ID (e.g., minecraft:chests/desert_pyramid)
     * @return The parsed structure, or empty if not found or parse error
     */
    public static Optional<LootTableStructure> parse(MinecraftServer server, ResourceLocation tableId) {
        ResourceManager resourceManager = server.getResourceManager();

        // Loot tables are at: data/<namespace>/loot_table/<path>.json
        ResourceLocation jsonPath = ResourceLocation.fromNamespaceAndPath(
            tableId.getNamespace(),
            "loot_table/" + tableId.getPath() + ".json"
        );

        try {
            Optional<Resource> resource = resourceManager.getResource(jsonPath);
            if (resource.isEmpty()) {
                Isotope.LOGGER.debug("Loot table not found: {}", tableId);
                return Optional.empty();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {

                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                return Optional.of(parseFromJson(tableId, json));
            }
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to parse loot table {}: {}", tableId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse a loot table from a JSON string.
     *
     * @param tableId The loot table ID
     * @param jsonString The JSON content
     * @return The parsed structure, or empty if parse error
     */
    public static Optional<LootTableStructure> parseFromString(ResourceLocation tableId, String jsonString) {
        try {
            JsonObject json = GSON.fromJson(jsonString, JsonObject.class);
            return Optional.of(parseFromJson(tableId, json));
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to parse loot table JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse a LootTableStructure from a JSON object.
     */
    public static LootTableStructure parseFromJson(ResourceLocation tableId, JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "minecraft:generic";

        List<LootPool> pools = new ArrayList<>();
        if (json.has("pools") && json.get("pools").isJsonArray()) {
            JsonArray poolsArray = json.getAsJsonArray("pools");
            for (int i = 0; i < poolsArray.size(); i++) {
                pools.add(parsePool(poolsArray.get(i).getAsJsonObject(), i));
            }
        }

        List<LootFunction> functions = new ArrayList<>();
        if (json.has("functions") && json.get("functions").isJsonArray()) {
            JsonArray functionsArray = json.getAsJsonArray("functions");
            for (JsonElement funcElem : functionsArray) {
                functions.add(parseFunction(funcElem.getAsJsonObject()));
            }
        }

        Optional<ResourceLocation> randomSequence = Optional.empty();
        if (json.has("random_sequence")) {
            randomSequence = Optional.of(ResourceLocation.parse(json.get("random_sequence").getAsString()));
        }

        return new LootTableStructure(tableId, type, pools, functions, randomSequence);
    }

    /**
     * Parse a loot pool from JSON.
     */
    private static LootPool parsePool(JsonObject json, int index) {
        String name = json.has("name") ? json.get("name").getAsString() : "Pool #" + (index + 1);

        NumberProvider rolls = parseNumberProvider(json.get("rolls"));
        NumberProvider bonusRolls = json.has("bonus_rolls")
            ? parseNumberProvider(json.get("bonus_rolls"))
            : NumberProvider.constant(0);

        List<LootEntry> entries = new ArrayList<>();
        if (json.has("entries") && json.get("entries").isJsonArray()) {
            JsonArray entriesArray = json.getAsJsonArray("entries");
            for (JsonElement entryElem : entriesArray) {
                entries.add(parseEntry(entryElem.getAsJsonObject()));
            }
        }

        List<LootCondition> conditions = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            JsonArray conditionsArray = json.getAsJsonArray("conditions");
            for (JsonElement condElem : conditionsArray) {
                conditions.add(parseCondition(condElem.getAsJsonObject()));
            }
        }

        List<LootFunction> functions = new ArrayList<>();
        if (json.has("functions") && json.get("functions").isJsonArray()) {
            JsonArray functionsArray = json.getAsJsonArray("functions");
            for (JsonElement funcElem : functionsArray) {
                functions.add(parseFunction(funcElem.getAsJsonObject()));
            }
        }

        return new LootPool(name, rolls, bonusRolls, entries, conditions, functions);
    }

    /**
     * Parse a loot entry from JSON.
     */
    private static LootEntry parseEntry(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "minecraft:item";

        Optional<ResourceLocation> name = Optional.empty();
        if (json.has("name")) {
            name = Optional.of(ResourceLocation.parse(json.get("name").getAsString()));
        }

        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        int quality = json.has("quality") ? json.get("quality").getAsInt() : 0;

        List<LootCondition> conditions = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            JsonArray conditionsArray = json.getAsJsonArray("conditions");
            for (JsonElement condElem : conditionsArray) {
                conditions.add(parseCondition(condElem.getAsJsonObject()));
            }
        }

        List<LootFunction> functions = new ArrayList<>();
        if (json.has("functions") && json.get("functions").isJsonArray()) {
            JsonArray functionsArray = json.getAsJsonArray("functions");
            for (JsonElement funcElem : functionsArray) {
                functions.add(parseFunction(funcElem.getAsJsonObject()));
            }
        }

        // Handle composite entries (alternatives, group, sequence)
        List<LootEntry> children = new ArrayList<>();
        if (json.has("children") && json.get("children").isJsonArray()) {
            JsonArray childrenArray = json.getAsJsonArray("children");
            for (JsonElement childElem : childrenArray) {
                children.add(parseEntry(childElem.getAsJsonObject()));
            }
        }

        return new LootEntry(type, name, weight, quality, conditions, functions, children);
    }

    /**
     * Parse a loot function from JSON.
     */
    private static LootFunction parseFunction(JsonObject json) {
        String function = json.has("function") ? json.get("function").getAsString() : "unknown";

        // Extract conditions if present
        List<LootCondition> conditions = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            JsonArray conditionsArray = json.getAsJsonArray("conditions");
            for (JsonElement condElem : conditionsArray) {
                conditions.add(parseCondition(condElem.getAsJsonObject()));
            }
        }

        // Create a copy of parameters without the function name and conditions
        JsonObject parameters = json.deepCopy();
        parameters.remove("function");
        parameters.remove("conditions");

        return new LootFunction(function, parameters, conditions);
    }

    /**
     * Parse a loot condition from JSON.
     */
    private static LootCondition parseCondition(JsonObject json) {
        String condition = json.has("condition") ? json.get("condition").getAsString() : "unknown";

        // Create a copy of parameters without the condition name
        JsonObject parameters = json.deepCopy();
        parameters.remove("condition");

        return new LootCondition(condition, parameters);
    }

    /**
     * Parse a number provider from JSON.
     */
    private static NumberProvider parseNumberProvider(JsonElement element) {
        if (element == null) {
            return NumberProvider.constant(1);
        }

        if (element.isJsonPrimitive()) {
            return NumberProvider.constant(element.getAsFloat());
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "minecraft:uniform";

            if (type.equals("minecraft:constant") || type.equals("constant")) {
                float value = obj.has("value") ? obj.get("value").getAsFloat() : 1;
                return NumberProvider.constant(value);
            }

            if (type.equals("minecraft:uniform") || type.equals("uniform")) {
                float min = obj.has("min") ? obj.get("min").getAsFloat() : 0;
                float max = obj.has("max") ? obj.get("max").getAsFloat() : 0;
                return NumberProvider.uniform(min, max);
            }

            if (type.equals("minecraft:binomial") || type.equals("binomial")) {
                int n = obj.has("n") ? obj.get("n").getAsInt() : 1;
                float p = obj.has("p") ? obj.get("p").getAsFloat() : 0.5f;
                return NumberProvider.binomial(n, p);
            }
        }

        return NumberProvider.constant(1);
    }
}
