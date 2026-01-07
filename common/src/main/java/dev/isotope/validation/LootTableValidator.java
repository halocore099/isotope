package dev.isotope.validation;

import dev.isotope.data.loot.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Validates loot table structures and detects potential issues.
 */
public class LootTableValidator {

    /**
     * Severity levels for validation issues.
     */
    public enum Severity {
        ERROR("Error", 0xFFf14c4c),      // Red - will cause problems
        WARNING("Warning", 0xFFf0a020),  // Orange - might cause problems
        INFO("Info", 0xFF4fc3f7);        // Blue - informational

        public final String label;
        public final int color;

        Severity(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    /**
     * Types of validation issues.
     */
    public enum IssueType {
        EMPTY_POOL("Empty pool", "Pool has no entries"),
        ZERO_WEIGHT("Zero weight", "Entry has weight of 0"),
        ZERO_ROLLS("Zero rolls", "Pool always rolls 0 times"),
        MISSING_ITEM("Missing item", "Item does not exist in registry"),
        DUPLICATE_ENTRY("Duplicate entry", "Same item appears multiple times"),
        NEGATIVE_COUNT("Negative count", "Item count can be negative"),
        UNREACHABLE_ENTRY("Unreachable entry", "Entry can never be selected"),
        CONFLICTING_FUNCTIONS("Conflicting functions", "Multiple functions conflict");

        public final String name;
        public final String description;

        IssueType(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    /**
     * A single validation issue.
     */
    public record ValidationIssue(
        IssueType type,
        Severity severity,
        String message,
        int poolIndex,
        int entryIndex,  // -1 if not entry-specific
        String suggestion
    ) {
        public boolean isEntrySpecific() {
            return entryIndex >= 0;
        }
    }

    /**
     * Result of validating a loot table.
     */
    public record ValidationResult(
        ResourceLocation tableId,
        List<ValidationIssue> issues,
        int errorCount,
        int warningCount,
        int infoCount
    ) {
        public boolean hasIssues() {
            return !issues.isEmpty();
        }

        public boolean hasErrors() {
            return errorCount > 0;
        }
    }

    /**
     * Validate a loot table structure.
     */
    public static ValidationResult validate(ResourceLocation tableId, LootTableStructure structure) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (structure == null) {
            issues.add(new ValidationIssue(
                IssueType.EMPTY_POOL,
                Severity.ERROR,
                "Loot table structure is null",
                -1, -1,
                "Check if the loot table exists"
            ));
            return new ValidationResult(tableId, issues, 1, 0, 0);
        }

        // Validate each pool
        for (int poolIdx = 0; poolIdx < structure.pools().size(); poolIdx++) {
            LootPool pool = structure.pools().get(poolIdx);
            validatePool(issues, pool, poolIdx);
        }

        // Count by severity
        int errors = 0, warnings = 0, infos = 0;
        for (ValidationIssue issue : issues) {
            switch (issue.severity) {
                case ERROR -> errors++;
                case WARNING -> warnings++;
                case INFO -> infos++;
            }
        }

        return new ValidationResult(tableId, issues, errors, warnings, infos);
    }

    private static void validatePool(List<ValidationIssue> issues, LootPool pool, int poolIdx) {
        // Check for empty pool
        if (pool.entries().isEmpty()) {
            issues.add(new ValidationIssue(
                IssueType.EMPTY_POOL,
                Severity.WARNING,
                "Pool #" + (poolIdx + 1) + " has no entries",
                poolIdx, -1,
                "Add entries or remove the pool"
            ));
            return;
        }

        // Check for zero rolls
        if (pool.rolls().getMax() <= 0) {
            issues.add(new ValidationIssue(
                IssueType.ZERO_ROLLS,
                Severity.WARNING,
                "Pool #" + (poolIdx + 1) + " has 0 max rolls",
                poolIdx, -1,
                "Set rolls to at least 1"
            ));
        }

        // Track items for duplicate detection
        Map<ResourceLocation, Integer> itemCounts = new HashMap<>();
        int totalWeight = 0;

        // Validate each entry
        for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
            LootEntry entry = pool.entries().get(entryIdx);
            validateEntry(issues, entry, poolIdx, entryIdx, itemCounts);
            totalWeight += entry.weight();
        }

        // Check for duplicates
        for (Map.Entry<ResourceLocation, Integer> itemEntry : itemCounts.entrySet()) {
            if (itemEntry.getValue() > 1) {
                issues.add(new ValidationIssue(
                    IssueType.DUPLICATE_ENTRY,
                    Severity.INFO,
                    itemEntry.getKey().getPath() + " appears " + itemEntry.getValue() + " times in pool #" + (poolIdx + 1),
                    poolIdx, -1,
                    "Consider merging duplicate entries"
                ));
            }
        }

        // Check for unreachable entries (0 total weight means nothing can be selected)
        if (totalWeight == 0 && !pool.entries().isEmpty()) {
            issues.add(new ValidationIssue(
                IssueType.UNREACHABLE_ENTRY,
                Severity.ERROR,
                "All entries in pool #" + (poolIdx + 1) + " have 0 weight",
                poolIdx, -1,
                "Set at least one entry's weight > 0"
            ));
        }
    }

    private static void validateEntry(List<ValidationIssue> issues, LootEntry entry,
                                      int poolIdx, int entryIdx, Map<ResourceLocation, Integer> itemCounts) {
        // Check weight
        if (entry.weight() <= 0) {
            issues.add(new ValidationIssue(
                IssueType.ZERO_WEIGHT,
                Severity.WARNING,
                "Entry #" + (entryIdx + 1) + " in pool #" + (poolIdx + 1) + " has weight " + entry.weight(),
                poolIdx, entryIdx,
                "Set weight to at least 1"
            ));
        }

        // Check item existence for item entries
        String entryType = entry.type();
        if ((entryType.equals("minecraft:item") || entryType.equals("item")) && entry.name().isPresent()) {
            ResourceLocation itemId = entry.name().get();

            // Track for duplicates
            itemCounts.merge(itemId, 1, Integer::sum);

            // Check if item exists (only for minecraft namespace to avoid false positives with mods)
            if (itemId.getNamespace().equals("minecraft")) {
                try {
                    var item = BuiltInRegistries.ITEM.get(itemId);
                    if (item == null || item == BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
                        issues.add(new ValidationIssue(
                            IssueType.MISSING_ITEM,
                            Severity.ERROR,
                            "Item '" + itemId + "' not found in registry",
                            poolIdx, entryIdx,
                            "Check the item ID spelling"
                        ));
                    }
                } catch (Exception e) {
                    // Registry might not be available, skip this check
                }
            }
        }

        // Validate functions
        validateFunctions(issues, entry, poolIdx, entryIdx);
    }

    private static void validateFunctions(List<ValidationIssue> issues, LootEntry entry,
                                          int poolIdx, int entryIdx) {
        boolean hasSetCount = false;
        boolean hasSetDamage = false;

        for (LootFunction func : entry.functions()) {
            String funcName = func.function();

            // Check for duplicate set_count
            if (funcName.contains("set_count")) {
                if (hasSetCount) {
                    issues.add(new ValidationIssue(
                        IssueType.CONFLICTING_FUNCTIONS,
                        Severity.WARNING,
                        "Multiple set_count functions on entry #" + (entryIdx + 1),
                        poolIdx, entryIdx,
                        "Only the last set_count will apply"
                    ));
                }
                hasSetCount = true;

                // Check for negative count
                if (func.parameters().has("count")) {
                    NumberProvider count = parseNumberProvider(func.parameters().get("count"));
                    if (count != null && count.getMin() < 0) {
                        issues.add(new ValidationIssue(
                            IssueType.NEGATIVE_COUNT,
                            Severity.ERROR,
                            "Entry #" + (entryIdx + 1) + " can have negative count",
                            poolIdx, entryIdx,
                            "Set minimum count to 0 or higher"
                        ));
                    }
                }
            }

            // Check for duplicate set_damage
            if (funcName.contains("set_damage")) {
                if (hasSetDamage) {
                    issues.add(new ValidationIssue(
                        IssueType.CONFLICTING_FUNCTIONS,
                        Severity.WARNING,
                        "Multiple set_damage functions on entry #" + (entryIdx + 1),
                        poolIdx, entryIdx,
                        "Only the last set_damage will apply"
                    ));
                }
                hasSetDamage = true;
            }
        }
    }

    private static NumberProvider parseNumberProvider(com.google.gson.JsonElement element) {
        if (element == null) return null;

        if (element.isJsonPrimitive()) {
            return new NumberProvider.Constant(element.getAsFloat());
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                return new NumberProvider.Uniform(obj.get("min").getAsFloat(), obj.get("max").getAsFloat());
            }
        }
        return null;
    }
}
