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
        return switch (function) {
            case "minecraft:set_count" -> getCountSummary();
            case "minecraft:enchant_with_levels" -> getLevelsSummary();
            case "minecraft:enchant_randomly" -> getEnchantRandomlySummary();
            case "minecraft:set_enchantments" -> getSetEnchantmentsSummary();
            case "minecraft:set_damage" -> getDamageSummary();
            case "minecraft:set_attributes" -> getAttributesSummary();
            case "minecraft:set_potion" -> getPotionSummary();
            case "minecraft:looting_enchant" -> getLootingEnchantSummary();
            case "minecraft:set_name" -> getNameSummary();
            case "minecraft:exploration_map" -> getMapSummary();
            case "minecraft:set_instrument" -> getInstrumentSummary();
            case "minecraft:set_stew_effect" -> "random effect";
            case "minecraft:furnace_smelt" -> "smelts";
            case "minecraft:copy_name" -> "from " + (parameters.has("source") ? parameters.get("source").getAsString() : "block");
            case "minecraft:copy_nbt", "minecraft:copy_components" -> "from source";
            default -> "";
        };
    }

    private String getCountSummary() {
        if (!parameters.has("count")) return "";
        var count = parameters.get("count");
        if (count.isJsonPrimitive()) {
            return count.getAsString();
        } else if (count.isJsonObject()) {
            JsonObject countObj = count.getAsJsonObject();
            if (countObj.has("min") && countObj.has("max")) {
                return countObj.get("min").getAsInt() + "-" + countObj.get("max").getAsInt();
            }
        }
        return "";
    }

    private String getLevelsSummary() {
        if (!parameters.has("levels")) return "";
        var levels = parameters.get("levels");
        if (levels.isJsonPrimitive()) {
            return "Lvl " + levels.getAsInt();
        } else if (levels.isJsonObject()) {
            JsonObject levelsObj = levels.getAsJsonObject();
            if (levelsObj.has("min") && levelsObj.has("max")) {
                return "Lvl " + levelsObj.get("min").getAsInt() + "-" + levelsObj.get("max").getAsInt();
            }
        }
        return "";
    }

    private String getEnchantRandomlySummary() {
        if (parameters.has("options")) {
            // Specific enchantment pool
            var options = parameters.get("options");
            if (options.isJsonArray()) {
                int count = options.getAsJsonArray().size();
                return count + " option" + (count != 1 ? "s" : "");
            }
        }
        return "any";
    }

    private String getSetEnchantmentsSummary() {
        if (!parameters.has("enchantments")) return "";
        var enchants = parameters.get("enchantments");
        if (enchants.isJsonObject()) {
            int count = enchants.getAsJsonObject().size();
            if (count == 1) {
                // Show the enchantment name
                String first = enchants.getAsJsonObject().keySet().iterator().next();
                return formatId(first);
            }
            return count + " enchants";
        }
        return "";
    }

    private String getDamageSummary() {
        if (!parameters.has("damage")) return "";
        var damage = parameters.get("damage");
        if (damage.isJsonPrimitive()) {
            return (int)(damage.getAsFloat() * 100) + "%";
        } else if (damage.isJsonObject()) {
            JsonObject dmgObj = damage.getAsJsonObject();
            if (dmgObj.has("min") && dmgObj.has("max")) {
                return (int)(dmgObj.get("min").getAsFloat() * 100) + "-" + (int)(dmgObj.get("max").getAsFloat() * 100) + "%";
            }
        }
        return "";
    }

    private String getAttributesSummary() {
        if (!parameters.has("modifiers")) return "";
        var modifiers = parameters.get("modifiers");
        if (modifiers.isJsonArray()) {
            int count = modifiers.getAsJsonArray().size();
            return count + " modifier" + (count != 1 ? "s" : "");
        }
        return "";
    }

    private String getPotionSummary() {
        if (parameters.has("id")) {
            return formatId(parameters.get("id").getAsString());
        }
        return "";
    }

    private String getLootingEnchantSummary() {
        if (parameters.has("count")) {
            var count = parameters.get("count");
            if (count.isJsonPrimitive()) {
                return "+" + count.getAsInt() + "/lvl";
            } else if (count.isJsonObject()) {
                JsonObject countObj = count.getAsJsonObject();
                if (countObj.has("min") && countObj.has("max")) {
                    return "+" + countObj.get("min").getAsInt() + "-" + countObj.get("max").getAsInt() + "/lvl";
                }
            }
        }
        return "+1/lvl";
    }

    private String getNameSummary() {
        if (parameters.has("name")) {
            var name = parameters.get("name");
            if (name.isJsonPrimitive()) {
                String n = name.getAsString();
                return n.length() > 15 ? n.substring(0, 12) + "..." : n;
            }
        }
        return "";
    }

    private String getMapSummary() {
        if (parameters.has("destination")) {
            return formatId(parameters.get("destination").getAsString());
        }
        return "";
    }

    private String getInstrumentSummary() {
        if (parameters.has("options")) {
            return "goat horn";
        }
        return "";
    }

    /**
     * Format a namespaced ID for display.
     */
    private String formatId(String id) {
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        // Convert first char to uppercase, replace underscores
        if (id.isEmpty()) return id;
        return Character.toUpperCase(id.charAt(0)) + id.substring(1).replace("_", " ");
    }

    /**
     * Check if this is a set_count function.
     */
    public boolean isSetCount() {
        return function.equals("minecraft:set_count");
    }

    /**
     * Check if this is an enchantment-related function.
     */
    public boolean isEnchantment() {
        return function.equals("minecraft:enchant_randomly")
            || function.equals("minecraft:enchant_with_levels")
            || function.equals("minecraft:set_enchantments");
    }

    /**
     * Check if this is an attribute modifier function.
     */
    public boolean isAttribute() {
        return function.equals("minecraft:set_attributes");
    }

    /**
     * Check if this is a potion function.
     */
    public boolean isPotion() {
        return function.equals("minecraft:set_potion");
    }

    /**
     * Check if this is a damage/durability function.
     */
    public boolean isDamage() {
        return function.equals("minecraft:set_damage");
    }

    /**
     * Check if this is a looting enchant bonus function.
     */
    public boolean isLootingEnchant() {
        return function.equals("minecraft:looting_enchant");
    }

    /**
     * Get a short icon/symbol for this function type.
     */
    public String getIcon() {
        if (isEnchantment()) return "✦";      // Enchantment sparkle
        if (isAttribute()) return "◆";         // Attribute diamond
        if (isPotion()) return "⚗";            // Potion
        if (isDamage()) return "⚒";            // Durability
        if (isLootingEnchant()) return "⚗";    // Looting bonus
        if (function.equals("minecraft:set_name")) return "✎";  // Custom name
        if (function.equals("minecraft:exploration_map")) return "🗺"; // Map
        if (function.equals("minecraft:furnace_smelt")) return "🔥"; // Smelt
        return "";
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
