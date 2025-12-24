package dev.isotope.editing;

import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import dev.isotope.data.loot.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.*;
import java.util.function.Consumer;

/**
 * Generates loot from LootTableStructure objects.
 *
 * This is a simplified loot generator that handles the most common cases.
 * For complex conditions or modded functions, it falls back gracefully.
 */
public final class LootGenerator {

    private LootGenerator() {}

    /**
     * Generate loot from a structure and output to consumer.
     *
     * @param structure The loot table structure
     * @param params The loot parameters (context)
     * @param seed The random seed
     * @param consumer Consumer to receive generated items
     */
    public static void generateFromStructure(
            LootTableStructure structure,
            LootParams params,
            long seed,
            Consumer<ItemStack> consumer) {

        Random random = new Random(seed);

        // Process each pool
        for (LootPool pool : structure.pools()) {
            // Check pool conditions (simplified - skip if we can't evaluate)
            if (!evaluateConditions(pool.conditions(), params, random)) {
                continue;
            }

            // Calculate number of rolls
            int rolls = (int) pool.rolls().sample(random);
            int bonusRolls = (int) pool.bonusRolls().sample(random);
            int totalRolls = rolls + bonusRolls;

            // Roll the pool
            for (int i = 0; i < totalRolls; i++) {
                generatePoolRoll(pool, params, random, consumer);
            }
        }
    }

    /**
     * Generate a single roll from a pool.
     */
    private static void generatePoolRoll(
            LootPool pool,
            LootParams params,
            Random random,
            Consumer<ItemStack> consumer) {

        if (pool.entries().isEmpty()) {
            return;
        }

        // Select entry based on weight
        LootEntry selected = selectWeightedEntry(pool.entries(), random);
        if (selected == null) {
            return;
        }

        // Generate items from the selected entry
        List<ItemStack> items = generateFromEntry(selected, params, random);

        // Apply pool functions to all items
        for (ItemStack item : items) {
            applyFunctions(item, pool.functions(), params, random);
            if (!item.isEmpty()) {
                consumer.accept(item);
            }
        }
    }

    /**
     * Select an entry based on weighted random selection.
     */
    private static LootEntry selectWeightedEntry(List<LootEntry> entries, Random random) {
        // Filter entries that pass conditions
        List<LootEntry> valid = new ArrayList<>();
        int totalWeight = 0;

        for (LootEntry entry : entries) {
            // Simplified condition check (always passes for now)
            valid.add(entry);
            totalWeight += Math.max(1, entry.weight());
        }

        if (valid.isEmpty() || totalWeight <= 0) {
            return null;
        }

        // Weighted selection
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (LootEntry entry : valid) {
            cumulative += Math.max(1, entry.weight());
            if (roll < cumulative) {
                return entry;
            }
        }

        return valid.get(valid.size() - 1);
    }

    /**
     * Generate items from a single entry.
     */
    private static List<ItemStack> generateFromEntry(
            LootEntry entry,
            LootParams params,
            Random random) {

        List<ItemStack> items = new ArrayList<>();

        switch (entry.type()) {
            case LootEntry.TYPE_ITEM -> {
                if (entry.name().isPresent()) {
                    ItemStack stack = createItemStack(entry.name().get(), entry, random);
                    if (!stack.isEmpty()) {
                        applyFunctions(stack, entry.functions(), params, random);
                        items.add(stack);
                    }
                }
            }
            case LootEntry.TYPE_EMPTY -> {
                // Empty entry produces nothing
            }
            case LootEntry.TYPE_LOOT_TABLE -> {
                // Nested loot table - would need recursive parsing
                // For now, skip
                Isotope.LOGGER.debug("Skipping nested loot table reference: {}",
                    entry.name().map(ResourceLocation::toString).orElse("unknown"));
            }
            case LootEntry.TYPE_ALTERNATIVES -> {
                // First child that passes conditions
                for (LootEntry child : entry.children()) {
                    List<ItemStack> childItems = generateFromEntry(child, params, random);
                    if (!childItems.isEmpty()) {
                        items.addAll(childItems);
                        break; // Only first matching
                    }
                }
            }
            case LootEntry.TYPE_GROUP -> {
                // All children
                for (LootEntry child : entry.children()) {
                    items.addAll(generateFromEntry(child, params, random));
                }
            }
            case LootEntry.TYPE_SEQUENCE -> {
                // Children in sequence until one fails
                for (LootEntry child : entry.children()) {
                    List<ItemStack> childItems = generateFromEntry(child, params, random);
                    if (childItems.isEmpty()) {
                        break;
                    }
                    items.addAll(childItems);
                }
            }
            case LootEntry.TYPE_TAG -> {
                // Tag entries would need tag lookup
                Isotope.LOGGER.debug("Skipping tag entry: {}",
                    entry.name().map(ResourceLocation::toString).orElse("unknown"));
            }
            default -> {
                Isotope.LOGGER.debug("Unknown entry type: {}", entry.type());
            }
        }

        return items;
    }

    /**
     * Create an ItemStack from an item ID and entry.
     */
    private static ItemStack createItemStack(ResourceLocation itemId, LootEntry entry, Random random) {
        try {
            // In 1.21.4, registry get returns Optional<Reference<Item>>
            var itemOpt = BuiltInRegistries.ITEM.get(itemId);
            if (itemOpt.isEmpty()) {
                Isotope.LOGGER.warn("Unknown item: {}", itemId);
                return ItemStack.EMPTY;
            }

            var item = itemOpt.get().value();
            if (item == Items.AIR) {
                Isotope.LOGGER.warn("Item is AIR: {}", itemId);
                return ItemStack.EMPTY;
            }

            // Get count from set_count function
            int count = 1;
            LootFunction setCount = entry.getSetCountFunction();
            if (setCount != null) {
                NumberProvider countProvider = setCount.getCountAsNumberProvider();
                count = Math.max(1, (int) countProvider.sample(random));
            }

            return new ItemStack(item, count);
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to create item {}: {}", itemId, e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * Apply functions to an item stack.
     */
    private static void applyFunctions(
            ItemStack stack,
            List<LootFunction> functions,
            LootParams params,
            Random random) {

        for (LootFunction func : functions) {
            applyFunction(stack, func, params, random);
        }
    }

    /**
     * Apply a single function to an item stack.
     */
    private static void applyFunction(
            ItemStack stack,
            LootFunction func,
            LootParams params,
            Random random) {

        switch (func.function()) {
            case "minecraft:set_count" -> {
                // Already handled in createItemStack, but update if called separately
                NumberProvider count = func.getCountAsNumberProvider();
                stack.setCount(Math.max(1, (int) count.sample(random)));
            }
            case "minecraft:set_damage" -> {
                if (stack.isDamageableItem()) {
                    JsonObject params_ = func.parameters();
                    float minDamage = params_.has("damage") ? getDamageMin(params_) : 0;
                    float maxDamage = params_.has("damage") ? getDamageMax(params_) : 1;
                    float damage = minDamage + random.nextFloat() * (maxDamage - minDamage);
                    int maxDurability = stack.getMaxDamage();
                    stack.setDamageValue((int) (maxDurability * (1 - damage)));
                }
            }
            case "minecraft:enchant_randomly", "minecraft:enchant_with_levels" -> {
                // Simplified enchantment - just mark that it would be enchanted
                // Full implementation would need access to enchantment registry
                Isotope.LOGGER.debug("Skipping enchantment function: {}", func.function());
            }
            case "minecraft:set_nbt", "minecraft:set_components" -> {
                // NBT/component functions are complex, skip for now
                Isotope.LOGGER.debug("Skipping NBT/component function: {}", func.function());
            }
            case "minecraft:exploration_map", "minecraft:set_banner_pattern",
                 "minecraft:set_book_cover", "minecraft:set_custom_data" -> {
                // Complex functions, skip
                Isotope.LOGGER.debug("Skipping complex function: {}", func.function());
            }
            default -> {
                // Unknown function, log and skip
                Isotope.LOGGER.debug("Unknown loot function: {}", func.function());
            }
        }
    }

    /**
     * Evaluate conditions (simplified - returns true for common cases).
     */
    private static boolean evaluateConditions(
            List<LootCondition> conditions,
            LootParams params,
            Random random) {

        for (LootCondition condition : conditions) {
            if (!evaluateCondition(condition, params, random)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluate a single condition.
     */
    private static boolean evaluateCondition(
            LootCondition condition,
            LootParams params,
            Random random) {

        switch (condition.condition()) {
            case "minecraft:random_chance" -> {
                JsonObject params_ = condition.parameters();
                float chance = params_.has("chance") ? params_.get("chance").getAsFloat() : 1.0f;
                return random.nextFloat() < chance;
            }
            case "minecraft:survives_explosion" -> {
                // In test mode, always survives
                return true;
            }
            case "minecraft:killed_by_player" -> {
                // Can't evaluate without entity context, default to true
                return true;
            }
            case "minecraft:inverted" -> {
                // Would need to evaluate child condition
                return true;
            }
            default -> {
                // Unknown conditions default to true in test mode
                return true;
            }
        }
    }

    // Helper to extract damage min from parameters
    private static float getDamageMin(JsonObject params) {
        var damage = params.get("damage");
        if (damage.isJsonPrimitive()) {
            return damage.getAsFloat();
        } else if (damage.isJsonObject()) {
            JsonObject obj = damage.getAsJsonObject();
            return obj.has("min") ? obj.get("min").getAsFloat() : 0;
        }
        return 0;
    }

    // Helper to extract damage max from parameters
    private static float getDamageMax(JsonObject params) {
        var damage = params.get("damage");
        if (damage.isJsonPrimitive()) {
            return damage.getAsFloat();
        } else if (damage.isJsonObject()) {
            JsonObject obj = damage.getAsJsonObject();
            return obj.has("max") ? obj.get("max").getAsFloat() : 1;
        }
        return 1;
    }
}
