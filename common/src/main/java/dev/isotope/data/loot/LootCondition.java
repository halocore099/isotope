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
        if (condition.equals("minecraft:random_chance") && parameters.has("chance")) {
            float chance = parameters.get("chance").getAsFloat();
            return String.format("%.0f%%", chance * 100);
        }
        if (parameters.size() == 0) {
            return "";
        }
        return parameters.toString();
    }
}
