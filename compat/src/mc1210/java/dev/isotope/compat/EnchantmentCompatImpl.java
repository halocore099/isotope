package dev.isotope.compat;

import dev.isotope.compat.impl.IdImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MC 1.21.0-1.21.8 implementation of EnchantmentCompat.
 * Uses ResourceLocation for identifiers.
 *
 * Uses actual registry lookups for enchantment data rather than hardcoded values.
 * Fallbacks are provided only for edge cases where registry access fails.
 */
public class EnchantmentCompatImpl implements EnchantmentCompat {

    // Fallback treasure enchantments (used only if tag lookup fails)
    private static final Set<String> FALLBACK_TREASURE_ENCHANTS = Set.of(
        "mending", "frost_walker", "binding_curse", "vanishing_curse", "soul_speed", "swift_sneak"
    );

    // Fallback rarity weights (used only if weight lookup fails)
    private static final Set<String> VERY_RARE_ENCHANTS = Set.of(
        "infinity", "mending", "silk_touch", "fortune", "looting", "luck_of_the_sea"
    );

    private static final Set<String> RARE_ENCHANTS = Set.of(
        "channeling", "multishot", "riptide", "thorns", "flame", "punch"
    );

    private static final Set<String> UNCOMMON_ENCHANTS = Set.of(
        "fire_aspect", "knockback", "fire_protection", "feather_falling", "blast_protection",
        "projectile_protection", "aqua_affinity", "depth_strider", "respiration"
    );

    @Override
    public List<Id> getApplicableEnchantments(Id itemId, boolean includeTreasure, RegistryAccess access) {
        List<Id> result = new ArrayList<>();

        try {
            // Get item from registry
            ResourceLocation itemLoc = (ResourceLocation) itemId.toMc();
            Registry<Item> itemRegistry = access.lookupOrThrow(Registries.ITEM);
            Optional<Holder.Reference<Item>> itemHolder = itemRegistry.get(itemLoc);
            if (itemHolder.isEmpty()) {
                return result;
            }

            ItemStack stack = new ItemStack(itemHolder.get().value());

            // Get enchantment registry
            Registry<Enchantment> enchRegistry = access.lookupOrThrow(Registries.ENCHANTMENT);

            // Check each enchantment
            for (var entry : enchRegistry.entrySet()) {
                Enchantment ench = entry.getValue();
                ResourceLocation enchId = entry.getKey().location();

                // Check if enchantment can be applied to this item
                if (ench.canEnchant(stack)) {
                    // Check treasure filter using tags
                    if (!includeTreasure) {
                        boolean isTreasure = false;
                        try {
                            // Get holder to check tags
                            Optional<Holder.Reference<Enchantment>> holderOpt = enchRegistry.get(enchId);
                            if (holderOpt.isPresent()) {
                                Holder.Reference<Enchantment> holder = holderOpt.get();
                                // Check TREASURE tag or not IN_ENCHANTING_TABLE
                                isTreasure = holder.is(EnchantmentTags.TREASURE) ||
                                            !holder.is(EnchantmentTags.IN_ENCHANTING_TABLE);
                            } else {
                                // Fallback to hardcoded check
                                isTreasure = FALLBACK_TREASURE_ENCHANTS.contains(enchId.getPath());
                            }
                        } catch (Exception e) {
                            // Fallback to hardcoded check
                            isTreasure = FALLBACK_TREASURE_ENCHANTS.contains(enchId.getPath());
                        }
                        if (isTreasure) {
                            continue;
                        }
                    }
                    result.add(IdImpl.wrap(enchId));
                }
            }
        } catch (Exception e) {
            // Log error but return empty list rather than crash
        }

        return result;
    }

    @Override
    public int getMaxLevel(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                return holder.get().value().getMaxLevel();
            }
        } catch (Exception e) {
            // Fallback
        }
        return 1;
    }

    @Override
    public int getMinLevel(Id enchantmentId, RegistryAccess access) {
        // Minecraft enchantments always start at level 1
        return 1;
    }

    @Override
    public String getDisplayName(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                // getFullname takes a Holder and level parameter
                return Enchantment.getFullname(holder.get(), 1).getString();
            }
        } catch (Exception e) {
            // Fallback
        }
        // Fallback: convert path to readable name
        String path = enchantmentId.getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace("_", " ");
    }

    @Override
    public boolean isTreasure(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                // Check if enchantment has the TREASURE tag
                if (holder.get().is(EnchantmentTags.TREASURE)) {
                    return true;
                }
                // Also check: if NOT in enchanting table, treat as treasure-like
                if (!holder.get().is(EnchantmentTags.IN_ENCHANTING_TABLE)) {
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            // Fallback to hardcoded list
        }
        return FALLBACK_TREASURE_ENCHANTS.contains(enchantmentId.getPath());
    }

    @Override
    public int getRarityWeight(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                // In MC 1.21.x, enchantments have a weight() method
                Enchantment ench = holder.get().value();
                int weight = ench.getWeight();
                if (weight > 0) {
                    return weight;
                }
            }
        } catch (Exception e) {
            // Fallback to hardcoded estimation
        }
        // Fallback: use hardcoded rarity estimation
        String path = enchantmentId.getPath();
        if (VERY_RARE_ENCHANTS.contains(path)) {
            return 1;
        } else if (RARE_ENCHANTS.contains(path)) {
            return 2;
        } else if (UNCOMMON_ENCHANTS.contains(path)) {
            return 5;
        }
        return 10; // Common
    }

    @Override
    public boolean isDiscoverable(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                // Discoverable = in enchanting table OR tradeable OR available from loot
                if (holder.get().is(EnchantmentTags.IN_ENCHANTING_TABLE) ||
                    holder.get().is(EnchantmentTags.TRADEABLE) ||
                    holder.get().is(EnchantmentTags.ON_RANDOM_LOOT)) {
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            // Fallback
        }
        // Fallback: non-treasure enchantments are typically discoverable
        return !FALLBACK_TREASURE_ENCHANTS.contains(enchantmentId.getPath());
    }
}
