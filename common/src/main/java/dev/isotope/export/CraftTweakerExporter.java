package dev.isotope.export;

import dev.isotope.Isotope;
import dev.isotope.data.loot.*;
import dev.isotope.editing.LootEditManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Exports edited loot tables as CraftTweaker ZenScript files.
 *
 * CraftTweaker is a popular modding tool that allows modpack creators to modify
 * game content via ZenScript without creating full mods.
 */
public class CraftTweakerExporter {

    private static final CraftTweakerExporter INSTANCE = new CraftTweakerExporter();

    private CraftTweakerExporter() {}

    public static CraftTweakerExporter getInstance() {
        return INSTANCE;
    }

    /**
     * Export edited loot tables as CraftTweaker scripts.
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

            // Create CraftTweaker script directory
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path scriptsDir = gameDir.resolve("scripts");
            Files.createDirectories(scriptsDir);

            // Generate timestamp for unique filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "isotope_loot_" + timestamp + ".zs";
            Path scriptFile = scriptsDir.resolve(filename);

            progressCallback.accept("Generating CraftTweaker script...");

            // Generate the script content
            StringBuilder script = new StringBuilder();
            script.append("// ISOTOPE Loot Table Modifications\n");
            script.append("// Generated: ").append(LocalDateTime.now()).append("\n");
            script.append("// Tables modified: ").append(editedTables.size()).append("\n");
            script.append("//\n");
            script.append("// Place this file in: <minecraft>/scripts/\n");
            script.append("// Requires CraftTweaker mod to be installed\n\n");

            // Imports
            script.append("import crafttweaker.api.loot.LootManager;\n");
            script.append("import crafttweaker.api.loot.condition.LootConditions;\n");
            script.append("import crafttweaker.api.loot.condition.builder.LootConditionBuilder;\n\n");

            List<String> exportedTables = new ArrayList<>();

            for (ResourceLocation tableId : editedTables) {
                progressCallback.accept("Processing: " + tableId);

                Optional<LootTableStructure> edited = editManager.getEditedStructure(tableId);
                if (edited.isEmpty()) {
                    Isotope.LOGGER.warn("Could not get edited structure for: {}", tableId);
                    continue;
                }

                script.append("\n// ").append(tableId).append("\n");
                script.append(generateTableModification(tableId, edited.get()));
                exportedTables.add(tableId.toString());
            }

            // Write the script file
            Files.writeString(scriptFile, script.toString());

            progressCallback.accept("CraftTweaker export complete!");
            progressCallback.accept("Location: " + scriptFile);

            return new ExportManager.ExportResult(true, null, scriptsDir, List.of(filename));

        } catch (Exception e) {
            Isotope.LOGGER.error("CraftTweaker export failed", e);
            return new ExportManager.ExportResult(false, e.getMessage(), null, List.of());
        }
    }

    /**
     * Generate CraftTweaker code to modify a single loot table.
     */
    private String generateTableModification(ResourceLocation tableId, LootTableStructure structure) {
        StringBuilder sb = new StringBuilder();

        sb.append("LootManager.getTable(<resource:").append(tableId).append(">).removeAll();\n");

        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            sb.append(generatePoolCode(tableId, pool, poolIdx));
        }

        return sb.toString();
    }

    /**
     * Generate CraftTweaker code for a single pool.
     */
    private String generatePoolCode(ResourceLocation tableId, LootPool pool, int poolIdx) {
        StringBuilder sb = new StringBuilder();

        sb.append("LootManager.getTable(<resource:").append(tableId).append(">).addPool((builder) => {\n");

        // Set rolls
        NumberProvider rolls = pool.rolls();
        if (rolls instanceof NumberProvider.Constant c) {
            sb.append("    builder.rolls(").append((int) c.value()).append(");\n");
        } else if (rolls instanceof NumberProvider.Uniform u) {
            sb.append("    builder.rolls(").append((int) u.min()).append(", ").append((int) u.max()).append(");\n");
        }

        // Set bonus rolls if not zero
        if (pool.bonusRolls() != null) {
            float max = pool.bonusRolls().getMax();
            if (max > 0) {
                NumberProvider bonus = pool.bonusRolls();
                if (bonus instanceof NumberProvider.Constant c) {
                    sb.append("    builder.bonusRolls(").append((int) c.value()).append(");\n");
                } else if (bonus instanceof NumberProvider.Uniform u) {
                    sb.append("    builder.bonusRolls(").append((int) u.min()).append(", ").append((int) u.max()).append(");\n");
                }
            }
        }

        // Add entries
        for (LootEntry entry : pool.entries()) {
            sb.append(generateEntryCode(entry));
        }

        // Add pool conditions
        for (LootCondition condition : pool.conditions()) {
            sb.append(generateConditionCode(condition, "builder"));
        }

        // Add pool functions
        for (LootFunction function : pool.functions()) {
            sb.append(generateFunctionCode(function, "builder"));
        }

        sb.append("});\n");

        return sb.toString();
    }

    /**
     * Generate CraftTweaker code for a single entry.
     */
    private String generateEntryCode(LootEntry entry) {
        StringBuilder sb = new StringBuilder();

        String entryType = entry.type();

        if (entryType.equals("minecraft:item") || entryType.equals("item")) {
            // Item entry
            if (entry.name().isPresent()) {
                ResourceLocation item = entry.name().get();
                sb.append("    builder.addLootTableEntry(<item:").append(item).append(">, (entryBuilder) => {\n");

                // Set weight
                if (entry.weight() != 1) {
                    sb.append("        entryBuilder.setWeight(").append(entry.weight()).append(");\n");
                }

                // Set quality
                if (entry.quality() != 0) {
                    sb.append("        entryBuilder.setQuality(").append(entry.quality()).append(");\n");
                }

                // Add functions
                for (LootFunction func : entry.functions()) {
                    String funcCode = generateEntryFunctionCode(func);
                    if (!funcCode.isEmpty()) {
                        sb.append(funcCode);
                    }
                }

                // Add conditions
                for (LootCondition cond : entry.conditions()) {
                    sb.append(generateConditionCode(cond, "entryBuilder"));
                }

                sb.append("    });\n");
            }
        } else if (entryType.equals("minecraft:empty") || entryType.equals("empty")) {
            sb.append("    builder.addEmptyEntry((entryBuilder) => {\n");
            if (entry.weight() != 1) {
                sb.append("        entryBuilder.setWeight(").append(entry.weight()).append(");\n");
            }
            sb.append("    });\n");
        } else if (entryType.equals("minecraft:loot_table") || entryType.equals("loot_table")) {
            if (entry.name().isPresent()) {
                sb.append("    builder.addLootTableEntry(<resource:").append(entry.name().get()).append(">, (entryBuilder) => {\n");
                if (entry.weight() != 1) {
                    sb.append("        entryBuilder.setWeight(").append(entry.weight()).append(");\n");
                }
                sb.append("    });\n");
            }
        } else if (entryType.equals("minecraft:tag") || entryType.equals("tag")) {
            if (entry.name().isPresent()) {
                sb.append("    builder.addTagEntry(<tag:items:").append(entry.name().get()).append(">, (entryBuilder) => {\n");
                if (entry.weight() != 1) {
                    sb.append("        entryBuilder.setWeight(").append(entry.weight()).append(");\n");
                }
                sb.append("    });\n");
            }
        } else if (entry.isComposite()) {
            // Composite entries - add children
            sb.append("    // Composite entry: ").append(entryType).append("\n");
            for (LootEntry child : entry.children()) {
                sb.append(generateEntryCode(child));
            }
        } else {
            // Unknown entry type - add comment
            sb.append("    // Unknown entry type: ").append(entryType).append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate CraftTweaker code for an entry function.
     */
    private String generateEntryFunctionCode(LootFunction function) {
        StringBuilder sb = new StringBuilder();
        String funcName = function.function();

        if (funcName.contains("set_count")) {
            if (function.parameters().has("count")) {
                NumberProvider count = parseNumberProvider(function.parameters().get("count"));
                if (count != null) {
                    if (count instanceof NumberProvider.Constant c) {
                        sb.append("        entryBuilder.apply(SetCount.lootFunction().setCount(")
                          .append((int) c.value()).append("));\n");
                    } else if (count instanceof NumberProvider.Uniform u) {
                        sb.append("        entryBuilder.apply(SetCount.lootFunction().setCount(")
                          .append((int) u.min()).append(", ").append((int) u.max()).append("));\n");
                    }
                }
            }
        } else if (funcName.contains("set_damage")) {
            if (function.parameters().has("damage")) {
                NumberProvider damage = parseNumberProvider(function.parameters().get("damage"));
                if (damage != null) {
                    if (damage instanceof NumberProvider.Constant c) {
                        sb.append("        entryBuilder.apply(SetDamage.lootFunction().setDamage(")
                          .append(c.value()).append("));\n");
                    } else if (damage instanceof NumberProvider.Uniform u) {
                        sb.append("        entryBuilder.apply(SetDamage.lootFunction().setDamage(")
                          .append(u.min()).append(", ").append(u.max()).append("));\n");
                    }
                }
            }
        } else if (funcName.contains("enchant_randomly")) {
            sb.append("        entryBuilder.apply(EnchantRandomly.lootFunction());\n");
        } else if (funcName.contains("enchant_with_levels")) {
            if (function.parameters().has("levels")) {
                NumberProvider levels = parseNumberProvider(function.parameters().get("levels"));
                if (levels != null) {
                    if (levels instanceof NumberProvider.Constant c) {
                        sb.append("        entryBuilder.apply(EnchantWithLevels.lootFunction().setLevels(")
                          .append((int) c.value()).append("));\n");
                    } else if (levels instanceof NumberProvider.Uniform u) {
                        sb.append("        entryBuilder.apply(EnchantWithLevels.lootFunction().setLevels(")
                          .append((int) u.min()).append(", ").append((int) u.max()).append("));\n");
                    }
                }
            }
        } else if (funcName.contains("looting_enchant")) {
            int countMin = 0;
            int countMax = 1;
            if (function.parameters().has("count")) {
                NumberProvider count = parseNumberProvider(function.parameters().get("count"));
                if (count != null) {
                    countMin = (int) count.getMin();
                    countMax = (int) count.getMax();
                }
            }
            sb.append("        entryBuilder.apply(LootingEnchant.lootFunction().setCount(")
              .append(countMin).append(", ").append(countMax).append("));\n");
        } else if (funcName.contains("furnace_smelt")) {
            sb.append("        entryBuilder.apply(FurnaceSmelt.lootFunction());\n");
        } else if (funcName.contains("limit_count")) {
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
            sb.append("        entryBuilder.apply(LimitCount.lootFunction().setLimit(")
              .append(min).append(", ").append(max).append("));\n");
        } else if (funcName.contains("copy_name")) {
            sb.append("        entryBuilder.apply(CopyName.lootFunction());\n");
        } else if (funcName.contains("copy_nbt")) {
            sb.append("        // TODO: copy_nbt function\n");
        } else if (funcName.contains("set_nbt")) {
            sb.append("        // TODO: set_nbt function\n");
        } else if (funcName.contains("set_name")) {
            if (function.parameters().has("name")) {
                var nameEl = function.parameters().get("name");
                String name = nameEl.isJsonPrimitive() ? nameEl.getAsString() : nameEl.toString();
                sb.append("        entryBuilder.apply(SetName.lootFunction().setName(")
                  .append(escapeString(name)).append("));\n");
            }
        } else if (funcName.contains("set_potion")) {
            if (function.parameters().has("id")) {
                String potionId = function.parameters().get("id").getAsString();
                sb.append("        entryBuilder.apply(SetPotion.lootFunction().setPotion(<potion:")
                  .append(potionId).append(">));\n");
            }
        } else if (funcName.contains("exploration_map")) {
            String destination = "buried_treasure";
            if (function.parameters().has("destination")) {
                destination = function.parameters().get("destination").getAsString();
            }
            sb.append("        entryBuilder.apply(ExplorationMap.lootFunction().setDestination(")
              .append(escapeString(destination)).append("));\n");
        } else {
            // Unknown function - add as comment
            sb.append("        // TODO: ").append(funcName).append(" function\n");
        }

        return sb.toString();
    }

    /**
     * Generate CraftTweaker code for a condition.
     */
    private String generateConditionCode(LootCondition condition, String target) {
        StringBuilder sb = new StringBuilder();
        String condType = condition.condition();

        if (condType.contains("random_chance_with_looting")) {
            float chance = 0.1f;
            float multiplier = 0.02f;
            if (condition.parameters().has("chance")) {
                chance = condition.parameters().get("chance").getAsFloat();
            }
            if (condition.parameters().has("looting_multiplier")) {
                multiplier = condition.parameters().get("looting_multiplier").getAsFloat();
            }
            sb.append("    ").append(target).append(".addCondition(RandomChanceWithLooting.lootCondition(")
              .append(chance).append(", ").append(multiplier).append("));\n");
        } else if (condType.contains("random_chance")) {
            float chance = 0.5f;
            if (condition.parameters().has("chance")) {
                chance = condition.parameters().get("chance").getAsFloat();
            }
            sb.append("    ").append(target).append(".addCondition(RandomChance.lootCondition(")
              .append(chance).append("));\n");
        } else if (condType.contains("killed_by_player")) {
            sb.append("    ").append(target).append(".addCondition(KilledByPlayer.lootCondition());\n");
        } else if (condType.contains("survives_explosion")) {
            sb.append("    ").append(target).append(".addCondition(SurvivesExplosion.lootCondition());\n");
        } else {
            // Unknown condition - add as comment
            sb.append("    // Condition: ").append(condType).append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate CraftTweaker code for a function at pool level.
     */
    private String generateFunctionCode(LootFunction function, String target) {
        StringBuilder sb = new StringBuilder();
        String funcName = function.function();

        // Pool-level functions
        if (funcName.contains("enchant_randomly")) {
            sb.append("    ").append(target).append(".apply(EnchantRandomly.lootFunction());\n");
        } else if (funcName.contains("enchant_with_levels")) {
            if (function.parameters().has("levels")) {
                NumberProvider levels = parseNumberProvider(function.parameters().get("levels"));
                if (levels != null) {
                    if (levels instanceof NumberProvider.Constant c) {
                        sb.append("    ").append(target).append(".apply(EnchantWithLevels.lootFunction().setLevels(")
                          .append((int) c.value()).append("));\n");
                    } else if (levels instanceof NumberProvider.Uniform u) {
                        sb.append("    ").append(target).append(".apply(EnchantWithLevels.lootFunction().setLevels(")
                          .append((int) u.min()).append(", ").append((int) u.max()).append("));\n");
                    }
                }
            }
        } else if (funcName.contains("set_count")) {
            // set_count at pool level
            if (function.parameters().has("count")) {
                NumberProvider count = parseNumberProvider(function.parameters().get("count"));
                if (count != null) {
                    if (count instanceof NumberProvider.Constant c) {
                        sb.append("    ").append(target).append(".apply(SetCount.lootFunction().setCount(")
                          .append((int) c.value()).append("));\n");
                    } else if (count instanceof NumberProvider.Uniform u) {
                        sb.append("    ").append(target).append(".apply(SetCount.lootFunction().setCount(")
                          .append((int) u.min()).append(", ").append((int) u.max()).append("));\n");
                    }
                }
            }
        } else {
            // Add comment for unhandled functions
            sb.append("    // Function: ").append(funcName).append("\n");
        }

        return sb.toString();
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
     * Escape a string for use in ZenScript.
     */
    private String escapeString(String str) {
        if (str == null) return "\"\"";
        return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
