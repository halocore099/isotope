package dev.isotope.export;

import dev.isotope.Isotope;
import dev.isotope.data.loot.*;
import dev.isotope.editing.LootEditManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Exports edited loot tables as KubeJS server scripts.
 *
 * KubeJS is a popular modding tool that allows modpack creators to modify
 * game content via JavaScript without creating full mods.
 */
public class KubeJSExporter {

    private static final KubeJSExporter INSTANCE = new KubeJSExporter();

    private KubeJSExporter() {}

    public static KubeJSExporter getInstance() {
        return INSTANCE;
    }

    /**
     * Export edited loot tables as KubeJS scripts.
     *
     * @param progressCallback Progress callback for status updates
     * @return Export result with success status and location
     */
    public ExportManager.ExportResult export(Consumer<String> progressCallback) {
        try {
            LootEditManager editManager = LootEditManager.getInstance();
            Set<ResourceLocation> editedTables = editManager.getEditedTables();

            if (editedTables.isEmpty()) {
                return new ExportManager.ExportResult(false, "No edited loot tables to export", null, List.of());
            }

            progressCallback.accept("Found " + editedTables.size() + " edited loot table(s)");

            // Create KubeJS script directory
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path kubejsDir = gameDir.resolve("kubejs").resolve("server_scripts");
            Files.createDirectories(kubejsDir);

            // Generate timestamp for unique filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "isotope_loot_" + timestamp + ".js";
            Path scriptFile = kubejsDir.resolve(filename);

            progressCallback.accept("Generating KubeJS script...");

            // Generate the script content
            StringBuilder script = new StringBuilder();
            script.append("// ISOTOPE Loot Table Modifications\n");
            script.append("// Generated: ").append(LocalDateTime.now()).append("\n");
            script.append("// Tables modified: ").append(editedTables.size()).append("\n");
            script.append("//\n");
            script.append("// Place this file in: <minecraft>/kubejs/server_scripts/\n");
            script.append("// Requires KubeJS mod to be installed\n\n");

            script.append("ServerEvents.lootTables(event => {\n");

            List<String> exportedTables = new ArrayList<>();

            for (ResourceLocation tableId : editedTables) {
                progressCallback.accept("Processing: " + tableId);

                Optional<LootTableStructure> edited = editManager.getEditedStructure(tableId);
                if (edited.isEmpty()) {
                    Isotope.LOGGER.warn("Could not get edited structure for: {}", tableId);
                    continue;
                }

                script.append("\n    // ").append(tableId).append("\n");
                script.append(generateTableModification(tableId, edited.get()));
                exportedTables.add(tableId.toString());
            }

            script.append("});\n");

            // Write the script file
            Files.writeString(scriptFile, script.toString());

            progressCallback.accept("KubeJS export complete!");
            progressCallback.accept("Location: " + scriptFile);

            return new ExportManager.ExportResult(true, null, kubejsDir, List.of(filename));

        } catch (Exception e) {
            Isotope.LOGGER.error("KubeJS export failed", e);
            return new ExportManager.ExportResult(false, e.getMessage(), null, List.of());
        }
    }

    /**
     * Generate KubeJS code to modify a single loot table.
     */
    private String generateTableModification(ResourceLocation tableId, LootTableStructure structure) {
        StringBuilder sb = new StringBuilder();

        sb.append("    event.modify('").append(tableId).append("', loot => {\n");
        sb.append("        loot.clearPools();\n");

        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            sb.append(generatePoolCode(pool, poolIdx));
        }

        sb.append("    });\n");

        return sb.toString();
    }

    /**
     * Generate KubeJS code for a single pool.
     */
    private String generatePoolCode(LootPool pool, int poolIdx) {
        StringBuilder sb = new StringBuilder();

        sb.append("        loot.addPool(pool => {\n");

        // Set rolls
        sb.append("            pool.rolls = ").append(numberProviderToJS(pool.rolls())).append(";\n");

        // Set bonus rolls if not zero
        if (pool.bonusRolls() != null) {
            float max = pool.bonusRolls().getMax();
            if (max > 0) {
                sb.append("            pool.bonusRolls = ").append(numberProviderToJS(pool.bonusRolls())).append(";\n");
            }
        }

        // Add entries
        for (LootEntry entry : pool.entries()) {
            sb.append(generateEntryCode(entry));
        }

        // Add pool conditions
        for (LootCondition condition : pool.conditions()) {
            sb.append(generateConditionCode(condition, "pool"));
        }

        // Add pool functions
        for (LootFunction function : pool.functions()) {
            sb.append(generateFunctionCode(function, "pool"));
        }

        sb.append("        });\n");

        return sb.toString();
    }

    /**
     * Generate KubeJS code for a single entry.
     */
    private String generateEntryCode(LootEntry entry) {
        StringBuilder sb = new StringBuilder();

        String entryType = entry.type();

        if (entryType.equals("minecraft:item") || entryType.equals("item")) {
            // Item entry
            if (entry.name().isPresent()) {
                ResourceLocation item = entry.name().get();
                sb.append("            pool.addItem('").append(item).append("'");

                // Add weight if not 1
                if (entry.weight() != 1) {
                    sb.append(", ").append(entry.weight());
                }

                sb.append(")");

                // Build chained function calls
                List<String> unhandledFunctions = new ArrayList<>();

                for (LootFunction func : entry.functions()) {
                    String funcName = func.function();

                    if (funcName.contains("set_count")) {
                        if (func.parameters().has("count")) {
                            NumberProvider count = parseNumberProvider(func.parameters().get("count"));
                            if (count != null) {
                                sb.append(".count(").append(numberProviderToJS(count)).append(")");
                            }
                        }
                    } else if (funcName.contains("set_damage")) {
                        if (func.parameters().has("damage")) {
                            NumberProvider damage = parseNumberProvider(func.parameters().get("damage"));
                            if (damage != null) {
                                sb.append(".damage(").append(numberProviderToJS(damage)).append(")");
                            }
                        }
                    } else if (funcName.contains("enchant_randomly")) {
                        sb.append(".enchantRandomly()");
                    } else if (funcName.contains("enchant_with_levels")) {
                        if (func.parameters().has("levels")) {
                            NumberProvider levels = parseNumberProvider(func.parameters().get("levels"));
                            if (levels != null) {
                                sb.append(".enchantWithLevels(").append(numberProviderToJS(levels)).append(")");
                            }
                        }
                    } else if (funcName.contains("looting_enchant")) {
                        int countMin = 0;
                        int countMax = 1;
                        if (func.parameters().has("count")) {
                            NumberProvider count = parseNumberProvider(func.parameters().get("count"));
                            if (count != null) {
                                countMin = (int) count.getMin();
                                countMax = (int) count.getMax();
                            }
                        }
                        sb.append(".lootingEnchant(").append(countMin).append(", ").append(countMax).append(")");
                    } else if (funcName.contains("furnace_smelt")) {
                        sb.append(".furnaceSmelt()");
                    } else if (funcName.contains("limit_count")) {
                        int min = 0;
                        int max = 64;
                        if (func.parameters().has("limit")) {
                            var limit = func.parameters().get("limit");
                            if (limit.isJsonObject()) {
                                var obj = limit.getAsJsonObject();
                                if (obj.has("min")) min = obj.get("min").getAsInt();
                                if (obj.has("max")) max = obj.get("max").getAsInt();
                            } else if (limit.isJsonPrimitive()) {
                                max = limit.getAsInt();
                            }
                        }
                        sb.append(".limitCount(").append(min).append(", ").append(max).append(")");
                    } else if (funcName.contains("set_nbt")) {
                        // set_nbt applies NBT tag to item
                        if (func.parameters().has("tag")) {
                            String tag = func.parameters().get("tag").toString();
                            sb.append(".nbt(").append(tag).append(")");
                        }
                    } else if (funcName.contains("set_name")) {
                        // set_name sets custom item name
                        if (func.parameters().has("name")) {
                            var nameEl = func.parameters().get("name");
                            String name = nameEl.isJsonPrimitive() ? nameEl.getAsString() : nameEl.toString();
                            sb.append(".customName(Component.literal(").append(escapeString(name)).append("))");
                        }
                    } else if (funcName.contains("set_lore")) {
                        // set_lore adds lore lines - KubeJS uses .lore()
                        if (func.parameters().has("lore")) {
                            var loreArr = func.parameters().getAsJsonArray("lore");
                            if (loreArr != null && !loreArr.isEmpty()) {
                                sb.append(".lore([");
                                for (int i = 0; i < loreArr.size(); i++) {
                                    if (i > 0) sb.append(", ");
                                    String line = loreArr.get(i).isJsonPrimitive() ?
                                        loreArr.get(i).getAsString() : loreArr.get(i).toString();
                                    sb.append("Component.literal(").append(escapeString(line)).append(")");
                                }
                                sb.append("])");
                            }
                        }
                    } else if (funcName.contains("set_potion")) {
                        // set_potion sets potion type
                        if (func.parameters().has("id")) {
                            String potionId = func.parameters().get("id").getAsString();
                            sb.append(".potion('").append(potionId).append("')");
                        }
                    } else if (funcName.contains("exploration_map")) {
                        // exploration_map creates treasure maps
                        String destination = "buried_treasure";
                        if (func.parameters().has("destination")) {
                            destination = func.parameters().get("destination").getAsString();
                        }
                        sb.append(".explorationMap('").append(destination).append("')");
                    } else if (funcName.contains("apply_bonus")) {
                        // apply_bonus for fortune/looting - add as comment since it's complex
                        String formula = "unknown";
                        if (func.parameters().has("formula")) {
                            formula = func.parameters().get("formula").getAsString();
                        }
                        // End the chain and add a comment
                        sb.append(";\n");
                        sb.append("            // apply_bonus (formula: ").append(formula).append(")\n");
                        continue; // Skip adding to unhandled
                    } else if (funcName.contains("copy_nbt")) {
                        // copy_nbt copies NBT from source
                        String source = "block_entity";
                        if (func.parameters().has("source")) {
                            source = func.parameters().get("source").getAsString();
                        }
                        sb.append(".copyNBT('").append(source).append("')");
                    } else if (funcName.contains("set_contents")) {
                        // set_contents for shulker boxes, bundles - add as comment
                        sb.append(";\n");
                        sb.append("            // set_contents (container contents)\n");
                        continue;
                    } else if (funcName.contains("set_banner_pattern")) {
                        // set_banner_pattern - add as comment
                        sb.append(";\n");
                        sb.append("            // set_banner_pattern\n");
                        continue;
                    } else {
                        // Track unhandled functions
                        unhandledFunctions.add(funcName);
                    }
                }

                sb.append(";\n");

                // Add comments for unhandled functions
                for (String funcName : unhandledFunctions) {
                    sb.append("            // TODO: ").append(funcName).append(" function\n");
                }

                // Add entry conditions
                for (LootCondition cond : entry.conditions()) {
                    sb.append(generateConditionComment(cond, "entry"));
                }
            }
        } else if (entryType.equals("minecraft:empty") || entryType.equals("empty")) {
            sb.append("            pool.addEmpty(").append(entry.weight()).append(");\n");
        } else if (entryType.equals("minecraft:loot_table") || entryType.equals("loot_table")) {
            if (entry.name().isPresent()) {
                sb.append("            pool.addLootTable('").append(entry.name().get()).append("'");
                if (entry.weight() != 1) {
                    sb.append(", ").append(entry.weight());
                }
                sb.append(");\n");
            }
        } else if (entryType.equals("minecraft:tag") || entryType.equals("tag")) {
            if (entry.name().isPresent()) {
                sb.append("            pool.addTag('#").append(entry.name().get()).append("'");
                if (entry.weight() != 1) {
                    sb.append(", ").append(entry.weight());
                }
                sb.append(");\n");
            }
        } else {
            // Unknown entry type - add comment
            sb.append("            // Unknown entry type: ").append(entryType).append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate a comment for entry-level conditions (KubeJS entry conditions are complex).
     */
    private String generateConditionComment(LootCondition condition, String context) {
        String condType = condition.condition();

        // Some conditions can be noted inline
        if (condType.contains("killed_by_player")) {
            return "            // Requires: killed_by_player\n";
        } else if (condType.contains("random_chance")) {
            float chance = 0.5f;
            if (condition.parameters().has("chance")) {
                chance = condition.parameters().get("chance").getAsFloat();
            }
            return "            // Requires: random_chance(" + (int)(chance * 100) + "%)\n";
        }

        return "";
    }

    /**
     * Generate KubeJS code for a condition.
     */
    private String generateConditionCode(LootCondition condition, String target) {
        StringBuilder sb = new StringBuilder();
        String condType = condition.condition();

        if (condType.contains("random_chance_with_looting")) {
            // random_chance_with_looting(chance, lootingMultiplier)
            float chance = 0.1f;
            float multiplier = 0.02f;
            if (condition.parameters().has("chance")) {
                chance = condition.parameters().get("chance").getAsFloat();
            }
            if (condition.parameters().has("looting_multiplier")) {
                multiplier = condition.parameters().get("looting_multiplier").getAsFloat();
            }
            sb.append("            ").append(target).append(".randomChanceWithLooting(")
              .append(chance).append(", ").append(multiplier).append(");\n");
        } else if (condType.contains("random_chance")) {
            // random_chance(chance)
            float chance = 0.5f;
            if (condition.parameters().has("chance")) {
                chance = condition.parameters().get("chance").getAsFloat();
            }
            sb.append("            ").append(target).append(".randomChance(").append(chance).append(");\n");
        } else if (condType.contains("killed_by_player")) {
            sb.append("            ").append(target).append(".killedByPlayer();\n");
        } else if (condType.contains("survives_explosion")) {
            sb.append("            ").append(target).append(".survivesExplosion();\n");
        } else if (condType.contains("entity_properties")) {
            // entity_properties is complex - add as comment with info
            String entity = "this";
            if (condition.parameters().has("entity")) {
                entity = condition.parameters().get("entity").getAsString();
            }
            sb.append("            // Condition: entity_properties (entity: ").append(entity).append(")\n");
        } else if (condType.contains("match_tool")) {
            // match_tool checks the tool used
            sb.append("            // Condition: match_tool (check tool properties)\n");
        } else if (condType.contains("table_bonus")) {
            // table_bonus is enchantment level based
            sb.append("            // Condition: table_bonus (enchantment level scaling)\n");
        } else if (condType.contains("inverted")) {
            // inverted wraps another condition
            sb.append("            // Condition: inverted (negates inner condition)\n");
        } else {
            // Unknown condition - add as comment
            sb.append("            // Condition: ").append(condType).append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate KubeJS code for a function.
     */
    private String generateFunctionCode(LootFunction function, String target) {
        StringBuilder sb = new StringBuilder();
        String funcName = function.function();

        // Common functions have KubeJS equivalents
        if (funcName.contains("enchant_randomly")) {
            sb.append("            ").append(target).append(".enchantRandomly();\n");
        } else if (funcName.contains("enchant_with_levels")) {
            if (function.parameters().has("levels")) {
                NumberProvider levels = parseNumberProvider(function.parameters().get("levels"));
                if (levels != null) {
                    sb.append("            ").append(target).append(".enchantWithLevels(")
                      .append(numberProviderToJS(levels)).append(");\n");
                }
            }
        } else if (funcName.contains("set_damage")) {
            if (function.parameters().has("damage")) {
                NumberProvider damage = parseNumberProvider(function.parameters().get("damage"));
                if (damage != null) {
                    sb.append("            ").append(target).append(".damage(")
                      .append(numberProviderToJS(damage)).append(");\n");
                }
            }
        } else if (funcName.contains("looting_enchant")) {
            // looting_enchant adds bonus items per looting level
            int countMin = 0;
            int countMax = 1;
            if (function.parameters().has("count")) {
                NumberProvider count = parseNumberProvider(function.parameters().get("count"));
                if (count != null) {
                    countMin = (int) count.getMin();
                    countMax = (int) count.getMax();
                }
            }
            sb.append("            ").append(target).append(".lootingEnchant(")
              .append(countMin).append(", ").append(countMax).append(");\n");
        } else if (funcName.contains("furnace_smelt")) {
            sb.append("            ").append(target).append(".furnaceSmelt();\n");
        } else if (funcName.contains("copy_name")) {
            String source = "block_entity";
            if (function.parameters().has("source")) {
                source = function.parameters().get("source").getAsString();
            }
            sb.append("            ").append(target).append(".copyName('").append(source).append("');\n");
        } else if (funcName.contains("limit_count")) {
            // limit_count restricts the stack size
            int min = 0;
            int max = 64;
            if (function.parameters().has("limit")) {
                var limit = function.parameters().get("limit");
                if (limit.isJsonObject()) {
                    var obj = limit.getAsJsonObject();
                    if (obj.has("min")) min = obj.get("min").getAsInt();
                    if (obj.has("max")) max = obj.get("max").getAsInt();
                } else if (limit.isJsonPrimitive()) {
                    max = limit.getAsInt();
                }
            }
            sb.append("            ").append(target).append(".limitCount(").append(min).append(", ").append(max).append(");\n");
        } else if (funcName.contains("set_count")) {
            // set_count - handled at entry level but also valid at pool level
            if (function.parameters().has("count")) {
                NumberProvider count = parseNumberProvider(function.parameters().get("count"));
                if (count != null) {
                    sb.append("            ").append(target).append(".count(")
                      .append(numberProviderToJS(count)).append(");\n");
                }
            }
        } else if (funcName.contains("set_nbt")) {
            // set_nbt applies NBT tag
            if (function.parameters().has("tag")) {
                String tag = function.parameters().get("tag").toString();
                sb.append("            ").append(target).append(".nbt(").append(tag).append(");\n");
            }
        } else if (funcName.contains("set_name")) {
            // set_name sets custom item name
            if (function.parameters().has("name")) {
                var nameEl = function.parameters().get("name");
                String name = nameEl.isJsonPrimitive() ? nameEl.getAsString() : nameEl.toString();
                sb.append("            ").append(target).append(".customName(Component.literal(")
                  .append(escapeString(name)).append("));\n");
            }
        } else if (funcName.contains("set_potion")) {
            // set_potion sets potion type
            if (function.parameters().has("id")) {
                String potionId = function.parameters().get("id").getAsString();
                sb.append("            ").append(target).append(".potion('").append(potionId).append("');\n");
            }
        } else if (funcName.contains("exploration_map")) {
            // exploration_map creates treasure maps
            String destination = "buried_treasure";
            if (function.parameters().has("destination")) {
                destination = function.parameters().get("destination").getAsString();
            }
            sb.append("            ").append(target).append(".explorationMap('").append(destination).append("');\n");
        } else if (funcName.contains("copy_nbt")) {
            // copy_nbt copies NBT from source
            String source = "block_entity";
            if (function.parameters().has("source")) {
                source = function.parameters().get("source").getAsString();
            }
            sb.append("            ").append(target).append(".copyNBT('").append(source).append("');\n");
        } else if (funcName.contains("apply_bonus")) {
            // apply_bonus for fortune/looting - add as comment
            String formula = "unknown";
            if (function.parameters().has("formula")) {
                formula = function.parameters().get("formula").getAsString();
            }
            sb.append("            // apply_bonus (formula: ").append(formula).append(")\n");
        } else if (funcName.contains("set_contents") || funcName.contains("set_banner_pattern")) {
            // Complex functions - add as comment
            sb.append("            // Function: ").append(funcName).append(" (complex, not supported)\n");
        } else {
            // Add comment for unhandled functions
            sb.append("            // Function: ").append(funcName).append("\n");
        }

        return sb.toString();
    }

    /**
     * Convert a NumberProvider to KubeJS syntax.
     */
    private String numberProviderToJS(NumberProvider provider) {
        if (provider instanceof NumberProvider.Constant c) {
            return String.valueOf((int) c.value());
        } else if (provider instanceof NumberProvider.Uniform u) {
            return "[" + (int) u.min() + ", " + (int) u.max() + "]";
        } else if (provider instanceof NumberProvider.Binomial b) {
            // KubeJS doesn't have direct binomial support, use range approximation
            return "[0, " + b.n() + "]";
        }
        return "1";
    }

    /**
     * Parse a JSON element into a NumberProvider.
     */
    private NumberProvider parseNumberProvider(com.google.gson.JsonElement element) {
        if (element == null) return null;

        if (element.isJsonPrimitive()) {
            return new NumberProvider.Constant(element.getAsFloat());
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                return new NumberProvider.Uniform(obj.get("min").getAsFloat(), obj.get("max").getAsFloat());
            }
            if (obj.has("n") && obj.has("p")) {
                return new NumberProvider.Binomial(obj.get("n").getAsInt(), obj.get("p").getAsFloat());
            }
            // Check for type field
            if (obj.has("type")) {
                String type = obj.get("type").getAsString();
                if (type.contains("constant") && obj.has("value")) {
                    return new NumberProvider.Constant(obj.get("value").getAsFloat());
                }
                if (type.contains("uniform")) {
                    float min = obj.has("min") ? obj.get("min").getAsFloat() : 0;
                    float max = obj.has("max") ? obj.get("max").getAsFloat() : 1;
                    return new NumberProvider.Uniform(min, max);
                }
            }
        }
        return null;
    }

    /**
     * Escape a string for use in JavaScript.
     */
    private String escapeString(String str) {
        if (str == null) return "''";
        return "'" + str.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
    }
}
