package dev.isotope.data.loot;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a loot entry within a pool.
 *
 * Entry types include:
 * - minecraft:item - A specific item
 * - minecraft:loot_table - Reference to another loot table
 * - minecraft:empty - Empty entry (gap in drops)
 * - minecraft:tag - All items from a tag
 * - minecraft:alternatives - First matching child entry
 * - minecraft:group - All child entries
 * - minecraft:sequence - Sequential child entries
 * - minecraft:dynamic - Dynamic content (like shulker box contents)
 */
public record LootEntry(
    String type,                           // Entry type
    Optional<ResourceLocation> name,       // Item ID for item entries, table ID for loot_table entries
    int weight,                            // Weight for selection (default 1)
    int quality,                           // Quality modifier based on luck
    List<LootCondition> conditions,        // Conditions for this entry
    List<LootFunction> functions,          // Functions to apply to output
    List<LootEntry> children               // For composite entries (alternatives, group, etc.)
) {
    // Common entry type constants
    public static final String TYPE_ITEM = "minecraft:item";
    public static final String TYPE_LOOT_TABLE = "minecraft:loot_table";
    public static final String TYPE_EMPTY = "minecraft:empty";
    public static final String TYPE_TAG = "minecraft:tag";
    public static final String TYPE_ALTERNATIVES = "minecraft:alternatives";
    public static final String TYPE_GROUP = "minecraft:group";
    public static final String TYPE_SEQUENCE = "minecraft:sequence";
    public static final String TYPE_DYNAMIC = "minecraft:dynamic";

    /**
     * Create a simple item entry with default weight.
     */
    public static LootEntry item(ResourceLocation itemId) {
        return item(itemId, 1);
    }

    /**
     * Create an item entry with specified weight.
     */
    public static LootEntry item(ResourceLocation itemId, int weight) {
        return new LootEntry(
            TYPE_ITEM,
            Optional.of(itemId),
            weight,
            0,
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Create an item entry with weight and count.
     */
    public static LootEntry item(ResourceLocation itemId, int weight, int minCount, int maxCount) {
        List<LootFunction> functions = new ArrayList<>();
        if (minCount != 1 || maxCount != 1) {
            functions.add(LootFunction.setCount(minCount, maxCount));
        }
        return new LootEntry(
            TYPE_ITEM,
            Optional.of(itemId),
            weight,
            0,
            List.of(),
            functions,
            List.of()
        );
    }

    /**
     * Create an empty entry.
     */
    public static LootEntry empty(int weight) {
        return new LootEntry(
            TYPE_EMPTY,
            Optional.empty(),
            weight,
            0,
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Create a loot table reference entry.
     */
    public static LootEntry lootTable(ResourceLocation tableId, int weight) {
        return new LootEntry(
            TYPE_LOOT_TABLE,
            Optional.of(tableId),
            weight,
            0,
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Check if this is an item entry.
     */
    public boolean isItem() {
        return TYPE_ITEM.equals(type);
    }

    /**
     * Check if this is an empty entry.
     */
    public boolean isEmpty() {
        return TYPE_EMPTY.equals(type);
    }

    /**
     * Check if this is a composite entry (has children).
     */
    public boolean isComposite() {
        return TYPE_ALTERNATIVES.equals(type) ||
               TYPE_GROUP.equals(type) ||
               TYPE_SEQUENCE.equals(type);
    }

    /**
     * Get the item ID if this is an item entry.
     */
    public Optional<ResourceLocation> getItemId() {
        if (isItem()) {
            return name;
        }
        return Optional.empty();
    }

    /**
     * Get a display name for this entry.
     */
    public String getDisplayName() {
        if (isItem() && name.isPresent()) {
            String path = name.get().getPath();
            // Convert snake_case to Title Case
            return path.replace("_", " ");
        }
        if (TYPE_LOOT_TABLE.equals(type) && name.isPresent()) {
            return "Table: " + name.get().getPath();
        }
        if (TYPE_EMPTY.equals(type)) {
            return "(empty)";
        }
        if (TYPE_TAG.equals(type) && name.isPresent()) {
            return "Tag: " + name.get().getPath();
        }
        if (isComposite()) {
            return type.substring("minecraft:".length()) + " (" + children.size() + ")";
        }
        return type;
    }

    /**
     * Get the set_count function if present, or null.
     */
    public LootFunction getSetCountFunction() {
        return functions.stream()
            .filter(LootFunction::isSetCount)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the count range as a string (e.g., "1-3" or "1").
     */
    public String getCountString() {
        LootFunction setCount = getSetCountFunction();
        if (setCount != null) {
            NumberProvider count = setCount.getCountAsNumberProvider();
            return count.toString();
        }
        return "1";
    }

    /**
     * Create a copy with modified weight.
     */
    public LootEntry withWeight(int newWeight) {
        return new LootEntry(type, name, newWeight, quality, conditions, functions, children);
    }

    /**
     * Create a copy with modified functions.
     */
    public LootEntry withFunctions(List<LootFunction> newFunctions) {
        return new LootEntry(type, name, weight, quality, conditions, newFunctions, children);
    }

    /**
     * Create a copy with modified conditions.
     */
    public LootEntry withConditions(List<LootCondition> newConditions) {
        return new LootEntry(type, name, weight, quality, newConditions, functions, children);
    }

    /**
     * Create a copy with a different item.
     */
    public LootEntry withItem(ResourceLocation newItem) {
        return new LootEntry(TYPE_ITEM, Optional.of(newItem), weight, quality, conditions, functions, children);
    }
}
