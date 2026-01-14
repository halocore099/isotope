package dev.isotope.data.loot;

import com.google.gson.JsonObject;

/**
 * Represents a loot condition (e.g., random_chance, killed_by_player).
 *
 * Conditions are stored as raw JSON to preserve all parameters,
 * since the condition system is complex and extensible.
 */
public record LootCondition(
    String condition,        // e.g., "minecraft:random_chance"
    JsonObject parameters    // Raw JSON parameters
) {
    /**
     * Create a simple random chance condition.
     */
    public static LootCondition randomChance(float chance) {
        JsonObject params = new JsonObject();
        params.addProperty("chance", chance);
        return new LootCondition("minecraft:random_chance", params);
    }

    /**
     * Create a killed by player condition.
     */
    public static LootCondition killedByPlayer() {
        return new LootCondition("minecraft:killed_by_player", new JsonObject());
    }

    /**
     * Create a survives explosion condition.
     */
    public static LootCondition survivesExplosion() {
        return new LootCondition("minecraft:survives_explosion", new JsonObject());
    }

    /**
     * Create a random chance with looting condition.
     * Base chance is increased by lootingMultiplier for each level of Looting.
     */
    public static LootCondition randomChanceWithLooting(float chance, float lootingMultiplier) {
        JsonObject params = new JsonObject();
        params.addProperty("chance", chance);
        params.addProperty("looting_multiplier", lootingMultiplier);
        return new LootCondition("minecraft:random_chance_with_looting", params);
    }

    /**
     * Create a weather check condition.
     * @param raining If non-null, requires raining state to match
     * @param thundering If non-null, requires thundering state to match
     */
    public static LootCondition weatherCheck(Boolean raining, Boolean thundering) {
        JsonObject params = new JsonObject();
        if (raining != null) {
            params.addProperty("raining", raining);
        }
        if (thundering != null) {
            params.addProperty("thundering", thundering);
        }
        return new LootCondition("minecraft:weather_check", params);
    }

    /**
     * Create a time check condition.
     * Checks if the current day time is within the specified range.
     * @param min Minimum time (0-24000)
     * @param max Maximum time (0-24000)
     */
    public static LootCondition timeCheck(int min, int max) {
        JsonObject params = new JsonObject();
        JsonObject value = new JsonObject();
        value.addProperty("min", min);
        value.addProperty("max", max);
        params.add("value", value);
        return new LootCondition("minecraft:time_check", params);
    }

    /**
     * Create an inverted condition that negates another condition.
     */
    public static LootCondition inverted(LootCondition inner) {
        JsonObject params = new JsonObject();
        JsonObject term = new JsonObject();
        term.addProperty("condition", inner.condition());
        // Merge inner parameters
        for (var entry : inner.parameters().entrySet()) {
            term.add(entry.getKey(), entry.getValue());
        }
        params.add("term", term);
        return new LootCondition("minecraft:inverted", params);
    }

    /**
     * Get a display-friendly name for this condition.
     */
    public String getDisplayName() {
        String name = condition;
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        // Convert snake_case to Title Case
        return name.replace("_", " ");
    }

    /**
     * Get a short summary of the condition parameters.
     */
    public String getParameterSummary() {
        String condType = condition.replace("minecraft:", "");

        switch (condType) {
            case "random_chance" -> {
                if (parameters.has("chance")) {
                    float chance = parameters.get("chance").getAsFloat();
                    return String.format("%.0f%%", chance * 100);
                }
            }
            case "random_chance_with_looting" -> {
                float chance = parameters.has("chance") ? parameters.get("chance").getAsFloat() : 0;
                float mult = parameters.has("looting_multiplier") ? parameters.get("looting_multiplier").getAsFloat() : 0;
                return String.format("%.0f%% +%.0f%%/lvl", chance * 100, mult * 100);
            }
            case "weather_check" -> {
                boolean raining = parameters.has("raining") && parameters.get("raining").getAsBoolean();
                boolean thundering = parameters.has("thundering") && parameters.get("thundering").getAsBoolean();
                if (thundering) return "thunder";
                if (raining) return "rain";
                return "";
            }
            case "time_check" -> {
                if (parameters.has("value")) {
                    var value = parameters.getAsJsonObject("value");
                    int min = value.has("min") ? value.get("min").getAsInt() : 0;
                    int max = value.has("max") ? value.get("max").getAsInt() : 24000;
                    return min + "-" + max;
                }
            }
            case "inverted" -> {
                if (parameters.has("term")) {
                    var term = parameters.getAsJsonObject("term");
                    if (term.has("condition")) {
                        String inner = term.get("condition").getAsString().replace("minecraft:", "");
                        return "NOT " + inner.replace("_", " ");
                    }
                }
                return "inverted";
            }
            case "killed_by_player", "survives_explosion" -> {
                return "";  // No parameters to show
            }
        }

        if (parameters.size() == 0) {
            return "";
        }
        // Fallback: show raw JSON for unknown conditions
        String json = parameters.toString();
        if (json.length() > 30) {
            json = json.substring(0, 27) + "...";
        }
        return json;
    }
}
