package dev.isotope.editing;

import com.google.gson.*;
import dev.isotope.data.loot.*;

/**
 * Serializes LootTableStructure objects back to JSON format.
 *
 * Produces valid Minecraft loot table JSON that can be used in datapacks.
 */
public final class LootTableSerializer {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private LootTableSerializer() {}

    /**
     * Serialize a loot table structure to JSON string.
     *
     * @param table The loot table structure
     * @return The JSON string
     */
    public static String toJson(LootTableStructure table) {
        return GSON.toJson(toJsonObject(table));
    }

    /**
     * Serialize a loot table structure to a JsonObject.
     *
     * @param table The loot table structure
     * @return The JSON object
     */
    public static JsonObject toJsonObject(LootTableStructure table) {
        JsonObject json = new JsonObject();

        // Type (required)
        json.addProperty("type", table.type());

        // Random sequence (optional)
        table.randomSequence().ifPresent(seq ->
            json.addProperty("random_sequence", seq.toString())
        );

        // Pools
        if (!table.pools().isEmpty()) {
            JsonArray poolsArray = new JsonArray();
            for (LootPool pool : table.pools()) {
                poolsArray.add(serializePool(pool));
            }
            json.add("pools", poolsArray);
        }

        // Global functions
        if (!table.functions().isEmpty()) {
            JsonArray functionsArray = new JsonArray();
            for (LootFunction func : table.functions()) {
                functionsArray.add(serializeFunction(func));
            }
            json.add("functions", functionsArray);
        }

        return json;
    }

    /**
     * Serialize a loot pool to JSON.
     */
    private static JsonObject serializePool(LootPool pool) {
        JsonObject json = new JsonObject();

        // Rolls (required)
        json.add("rolls", serializeNumberProvider(pool.rolls()));

        // Bonus rolls (optional, skip if 0)
        if (pool.bonusRolls().getMax() > 0) {
            json.add("bonus_rolls", serializeNumberProvider(pool.bonusRolls()));
        }

        // Entries
        if (!pool.entries().isEmpty()) {
            JsonArray entriesArray = new JsonArray();
            for (LootEntry entry : pool.entries()) {
                entriesArray.add(serializeEntry(entry));
            }
            json.add("entries", entriesArray);
        }

        // Conditions
        if (!pool.conditions().isEmpty()) {
            JsonArray conditionsArray = new JsonArray();
            for (LootCondition cond : pool.conditions()) {
                conditionsArray.add(serializeCondition(cond));
            }
            json.add("conditions", conditionsArray);
        }

        // Functions
        if (!pool.functions().isEmpty()) {
            JsonArray functionsArray = new JsonArray();
            for (LootFunction func : pool.functions()) {
                functionsArray.add(serializeFunction(func));
            }
            json.add("functions", functionsArray);
        }

        return json;
    }

    /**
     * Serialize a loot entry to JSON.
     */
    private static JsonObject serializeEntry(LootEntry entry) {
        JsonObject json = new JsonObject();

        // Type (required)
        json.addProperty("type", entry.type());

        // Name (for item/loot_table/tag entries)
        entry.name().ifPresent(name ->
            json.addProperty("name", name.toString())
        );

        // Weight (optional, skip if 1)
        if (entry.weight() != 1) {
            json.addProperty("weight", entry.weight());
        }

        // Quality (optional, skip if 0)
        if (entry.quality() != 0) {
            json.addProperty("quality", entry.quality());
        }

        // Conditions
        if (!entry.conditions().isEmpty()) {
            JsonArray conditionsArray = new JsonArray();
            for (LootCondition cond : entry.conditions()) {
                conditionsArray.add(serializeCondition(cond));
            }
            json.add("conditions", conditionsArray);
        }

        // Functions
        if (!entry.functions().isEmpty()) {
            JsonArray functionsArray = new JsonArray();
            for (LootFunction func : entry.functions()) {
                functionsArray.add(serializeFunction(func));
            }
            json.add("functions", functionsArray);
        }

        // Children (for composite entries)
        if (!entry.children().isEmpty()) {
            JsonArray childrenArray = new JsonArray();
            for (LootEntry child : entry.children()) {
                childrenArray.add(serializeEntry(child));
            }
            json.add("children", childrenArray);
        }

        return json;
    }

    /**
     * Serialize a loot function to JSON.
     */
    private static JsonObject serializeFunction(LootFunction function) {
        // Start with the stored parameters
        JsonObject json = function.parameters().deepCopy();

        // Add the function type
        json.addProperty("function", function.function());

        // Add conditions if present
        if (!function.conditions().isEmpty()) {
            JsonArray conditionsArray = new JsonArray();
            for (LootCondition cond : function.conditions()) {
                conditionsArray.add(serializeCondition(cond));
            }
            json.add("conditions", conditionsArray);
        }

        return json;
    }

    /**
     * Serialize a loot condition to JSON.
     */
    private static JsonObject serializeCondition(LootCondition condition) {
        // Start with the stored parameters
        JsonObject json = condition.parameters().deepCopy();

        // Add the condition type
        json.addProperty("condition", condition.condition());

        return json;
    }

    /**
     * Serialize a number provider to JSON.
     */
    private static JsonElement serializeNumberProvider(NumberProvider provider) {
        return switch (provider) {
            case NumberProvider.Constant c -> {
                // For simple constants, just use the number directly
                if (c.value() == (int) c.value()) {
                    yield new JsonPrimitive((int) c.value());
                }
                yield new JsonPrimitive(c.value());
            }
            case NumberProvider.Uniform u -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", "minecraft:uniform");
                // Use integers for whole numbers
                if (u.min() == (int) u.min()) {
                    obj.addProperty("min", (int) u.min());
                } else {
                    obj.addProperty("min", u.min());
                }
                if (u.max() == (int) u.max()) {
                    obj.addProperty("max", (int) u.max());
                } else {
                    obj.addProperty("max", u.max());
                }
                yield obj;
            }
            case NumberProvider.Binomial b -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", "minecraft:binomial");
                obj.addProperty("n", b.n());
                obj.addProperty("p", b.p());
                yield obj;
            }
        };
    }

    /**
     * Minified JSON output (single line, no pretty printing).
     */
    public static String toMinifiedJson(LootTableStructure table) {
        return new Gson().toJson(toJsonObject(table));
    }
}
