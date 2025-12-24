package dev.isotope.data.loot;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * Represents a loot function (e.g., set_count, enchant_randomly).
 *
 * Functions are stored as raw JSON to preserve all parameters,
 * since the function system is complex and extensible.
 */
public record LootFunction(
    String function,                 // e.g., "minecraft:set_count"
    JsonObject parameters,           // Raw JSON parameters
    List<LootCondition> conditions   // Conditions for this function
) {
    /**
     * Create a set_count function with constant count.
     */
    public static LootFunction setCount(int count) {
        JsonObject params = new JsonObject();
        params.addProperty("count", count);
        return new LootFunction("minecraft:set_count", params, List.of());
    }

    /**
     * Create a set_count function with uniform range.
     */
    public static LootFunction setCount(int min, int max) {
        JsonObject params = new JsonObject();
        JsonObject countObj = new JsonObject();
        countObj.addProperty("type", "minecraft:uniform");
        countObj.addProperty("min", min);
        countObj.addProperty("max", max);
        params.add("count", countObj);
        return new LootFunction("minecraft:set_count", params, List.of());
    }

    /**
     * Create an enchant_randomly function.
     */
    public static LootFunction enchantRandomly() {
        return new LootFunction("minecraft:enchant_randomly", new JsonObject(), List.of());
    }

    /**
     * Create an enchant_with_levels function.
     */
    public static LootFunction enchantWithLevels(int minLevels, int maxLevels, boolean treasure) {
        JsonObject params = new JsonObject();
        JsonObject levels = new JsonObject();
        levels.addProperty("type", "minecraft:uniform");
        levels.addProperty("min", minLevels);
        levels.addProperty("max", maxLevels);
        params.add("levels", levels);
        params.addProperty("treasure", treasure);
        return new LootFunction("minecraft:enchant_with_levels", params, List.of());
    }

    /**
     * Create a set_damage function.
     */
    public static LootFunction setDamage(float min, float max) {
        JsonObject params = new JsonObject();
        JsonObject damage = new JsonObject();
        damage.addProperty("type", "minecraft:uniform");
        damage.addProperty("min", min);
        damage.addProperty("max", max);
        params.add("damage", damage);
        return new LootFunction("minecraft:set_damage", params, List.of());
    }

    /**
     * Get a display-friendly name for this function.
     */
    public String getDisplayName() {
        String name = function;
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        return name.replace("_", " ");
    }

    /**
     * Get a short summary of the function parameters.
     */
    public String getParameterSummary() {
        if (function.equals("minecraft:set_count")) {
            if (parameters.has("count")) {
                var count = parameters.get("count");
                if (count.isJsonPrimitive()) {
                    return count.getAsString();
                } else if (count.isJsonObject()) {
                    JsonObject countObj = count.getAsJsonObject();
                    if (countObj.has("min") && countObj.has("max")) {
                        return countObj.get("min").getAsInt() + "-" + countObj.get("max").getAsInt();
                    }
                }
            }
        }
        if (function.equals("minecraft:enchant_with_levels") && parameters.has("levels")) {
            var levels = parameters.get("levels");
            if (levels.isJsonObject()) {
                JsonObject levelsObj = levels.getAsJsonObject();
                if (levelsObj.has("min") && levelsObj.has("max")) {
                    return "Lvl " + levelsObj.get("min").getAsInt() + "-" + levelsObj.get("max").getAsInt();
                }
            }
        }
        if (parameters.size() == 0) {
            return "";
        }
        return "";
    }

    /**
     * Check if this is a set_count function.
     */
    public boolean isSetCount() {
        return function.equals("minecraft:set_count");
    }

    /**
     * Extract the count as a NumberProvider if this is a set_count function.
     */
    public NumberProvider getCountAsNumberProvider() {
        if (!isSetCount() || !parameters.has("count")) {
            return NumberProvider.constant(1);
        }

        var count = parameters.get("count");
        if (count.isJsonPrimitive()) {
            return NumberProvider.constant(count.getAsFloat());
        } else if (count.isJsonObject()) {
            JsonObject countObj = count.getAsJsonObject();
            String type = countObj.has("type") ? countObj.get("type").getAsString() : "minecraft:uniform";

            if (type.equals("minecraft:uniform") || type.equals("uniform")) {
                float min = countObj.has("min") ? countObj.get("min").getAsFloat() : 0;
                float max = countObj.has("max") ? countObj.get("max").getAsFloat() : 0;
                return NumberProvider.uniform(min, max);
            } else if (type.equals("minecraft:constant") || type.equals("constant")) {
                float value = countObj.has("value") ? countObj.get("value").getAsFloat() : 1;
                return NumberProvider.constant(value);
            }
        }
        return NumberProvider.constant(1);
    }
}
