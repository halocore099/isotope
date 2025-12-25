package dev.isotope.data;

import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.data.loot.NumberProvider;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A template for quickly creating common loot entries.
 *
 * Templates define pre-configured entry settings like weight, count,
 * and functions that can be applied to create new entries.
 */
public record EntryTemplate(
    String id,
    String name,
    String description,
    String category,
    Optional<ResourceLocation> defaultItem,
    int defaultWeight,
    NumberProvider defaultCount,
    List<LootFunction> functions
) {
    /**
     * Create a LootEntry from this template.
     *
     * @param itemId The item to use (overrides defaultItem if present)
     */
    public LootEntry createEntry(ResourceLocation itemId) {
        return new LootEntry(
            "minecraft:item",
            Optional.of(itemId),
            defaultWeight,
            0,
            List.of(),
            new ArrayList<>(functions),
            List.of()
        );
    }

    /**
     * Create a LootEntry from this template using the default item.
     * Throws if no default item is set.
     */
    public LootEntry createEntry() {
        if (defaultItem.isEmpty()) {
            throw new IllegalStateException("Template has no default item");
        }
        return createEntry(defaultItem.get());
    }

    // ===== Built-in Templates =====

    public static final EntryTemplate COMMON_ITEM = new EntryTemplate(
        "common_item",
        "Common Item",
        "A standard item with weight 10, count 1-3",
        "Basic",
        Optional.empty(),
        10,
        new NumberProvider.Uniform(1, 3),
        List.of(createSetCountFunction(1, 3))
    );

    public static final EntryTemplate UNCOMMON_ITEM = new EntryTemplate(
        "uncommon_item",
        "Uncommon Item",
        "A less common item with weight 5, count 1-2",
        "Basic",
        Optional.empty(),
        5,
        new NumberProvider.Uniform(1, 2),
        List.of(createSetCountFunction(1, 2))
    );

    public static final EntryTemplate RARE_ITEM = new EntryTemplate(
        "rare_item",
        "Rare Item",
        "A rare drop with weight 1, count 1",
        "Basic",
        Optional.empty(),
        1,
        new NumberProvider.Constant(1),
        List.of(createSetCountFunction(1, 1))
    );

    public static final EntryTemplate FOOD_STACK = new EntryTemplate(
        "food_stack",
        "Food Stack",
        "Food items with weight 10, count 2-5",
        "Food",
        Optional.empty(),
        10,
        new NumberProvider.Uniform(2, 5),
        List.of(createSetCountFunction(2, 5))
    );

    public static final EntryTemplate ENCHANTED_GEAR = new EntryTemplate(
        "enchanted_gear",
        "Enchanted Gear",
        "Equipment with random enchantments",
        "Equipment",
        Optional.empty(),
        3,
        new NumberProvider.Constant(1),
        List.of(createEnchantRandomlyFunction())
    );

    public static final EntryTemplate TREASURE = new EntryTemplate(
        "treasure",
        "Treasure",
        "Rare treasure with weight 1, single item",
        "Valuables",
        Optional.of(ResourceLocation.parse("minecraft:diamond")),
        1,
        new NumberProvider.Constant(1),
        List.of(createSetCountFunction(1, 1))
    );

    public static final EntryTemplate EMERALD_STACK = new EntryTemplate(
        "emerald_stack",
        "Emerald Stack",
        "Emeralds for trading, count 1-4",
        "Valuables",
        Optional.of(ResourceLocation.parse("minecraft:emerald")),
        8,
        new NumberProvider.Uniform(1, 4),
        List.of(createSetCountFunction(1, 4))
    );

    public static final EntryTemplate ARROW_STACK = new EntryTemplate(
        "arrow_stack",
        "Arrow Stack",
        "Arrows for combat, count 4-12",
        "Combat",
        Optional.of(ResourceLocation.parse("minecraft:arrow")),
        10,
        new NumberProvider.Uniform(4, 12),
        List.of(createSetCountFunction(4, 12))
    );

    public static final EntryTemplate IRON_INGOT_STACK = new EntryTemplate(
        "iron_ingot_stack",
        "Iron Ingots",
        "Iron ingots, count 1-4",
        "Resources",
        Optional.of(ResourceLocation.parse("minecraft:iron_ingot")),
        10,
        new NumberProvider.Uniform(1, 4),
        List.of(createSetCountFunction(1, 4))
    );

    public static final EntryTemplate GOLD_INGOT_STACK = new EntryTemplate(
        "gold_ingot_stack",
        "Gold Ingots",
        "Gold ingots, count 1-3",
        "Resources",
        Optional.of(ResourceLocation.parse("minecraft:gold_ingot")),
        5,
        new NumberProvider.Uniform(1, 3),
        List.of(createSetCountFunction(1, 3))
    );

    /**
     * All built-in templates.
     */
    public static final List<EntryTemplate> BUILTIN_TEMPLATES = List.of(
        COMMON_ITEM,
        UNCOMMON_ITEM,
        RARE_ITEM,
        FOOD_STACK,
        ENCHANTED_GEAR,
        TREASURE,
        EMERALD_STACK,
        ARROW_STACK,
        IRON_INGOT_STACK,
        GOLD_INGOT_STACK
    );

    // ===== Helper Methods =====

    private static LootFunction createSetCountFunction(int min, int max) {
        JsonObject params = new JsonObject();
        if (min == max) {
            params.addProperty("count", min);
        } else {
            JsonObject count = new JsonObject();
            count.addProperty("type", "minecraft:uniform");
            count.addProperty("min", min);
            count.addProperty("max", max);
            params.add("count", count);
        }
        return new LootFunction("minecraft:set_count", params, List.of());
    }

    private static LootFunction createEnchantRandomlyFunction() {
        return new LootFunction("minecraft:enchant_randomly", new JsonObject(), List.of());
    }
}
