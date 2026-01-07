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

                // Check for set_count function to add count
                for (LootFunction func : entry.functions()) {
                    String funcName = func.function();
                    if (funcName.equals("minecraft:set_count") || funcName.equals("set_count")) {
                        if (func.parameters().has("count")) {
                            NumberProvider count = parseNumberProvider(func.parameters().get("count"));
                            if (count != null) {
                                sb.append(".count(").append(numberProviderToJS(count)).append(")");
                            }
                        }
                    }
                }

                sb.append(";\n");

                // Add other functions (excluding set_count which we handled)
                for (LootFunction func : entry.functions()) {
                    String funcName = func.function();
                    if (!funcName.equals("minecraft:set_count") && !funcName.equals("set_count")) {
                        // For complex functions, add a comment
                        sb.append("            // TODO: ").append(funcName).append(" function\n");
                    }
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
     * Generate KubeJS code for a condition.
     */
    private String generateConditionCode(LootCondition condition, String target) {
        StringBuilder sb = new StringBuilder();
        String condType = condition.condition();

        // KubeJS condition syntax varies - add as comment for manual adjustment
        sb.append("            // Condition: ").append(condType).append("\n");

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
}
