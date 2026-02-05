package dev.isotope.compat;

import dev.isotope.compat.impl.IdImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
 * Uses actual registry lookups via RegistryCompat for enchantment data rather
 * than hardcoded values. RegistryCompat handles the varying registry API across
 * MC 1.21.0-1.21.8 versions via reflection.
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
            // Get item from registry via RegistryCompat (handles API differences)
            ResourceLocation itemLoc = (ResourceLocation) itemId.toMc();
            Registry<Item> itemRegistry = RegistryCompat.lookupRegistry(access, Registries.ITEM);
            if (itemRegistry == null) {
                return result;
            }

            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemLoc);
            Item item = RegistryCompat.getValue(itemRegistry, itemKey);
            if (item == null) {
                return result;
            }

            ItemStack stack = new ItemStack(item);

            // Get enchantment registry
            Registry<Enchantment> enchRegistry = RegistryCompat.lookupRegistry(access, Registries.ENCHANTMENT);
            if (enchRegistry == null) {
                return result;
            }

            // Check each enchantment
            for (var entry : enchRegistry.entrySet()) {
                Enchantment ench = entry.getValue();
                ResourceKey<Enchantment> enchKey = entry.getKey();
                ResourceLocation enchId = enchKey.location();

                // Check if enchantment can be applied to this item
                if (ench.canEnchant(stack)) {
                    // Check treasure filter using tags
                    if (!includeTreasure) {
                        boolean isTreasure = false;
                        try {
                            Optional<Holder.Reference<Enchantment>> holderOpt =
                                RegistryCompat.getHolder(enchRegistry, enchKey);
                            if (holderOpt.isPresent()) {
                                Holder.Reference<Enchantment> holder = holderOpt.get();
                                isTreasure = holder.is(EnchantmentTags.TREASURE) ||
                                            !holder.is(EnchantmentTags.IN_ENCHANTING_TABLE);
                            } else {
                                isTreasure = FALLBACK_TREASURE_ENCHANTS.contains(enchId.getPath());
                            }
                        } catch (Exception e) {
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
            // Return empty list rather than crash
        }

        return result;
    }

    @Override
    public int getMaxLevel(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = RegistryCompat.lookupRegistry(access, Registries.ENCHANTMENT);
            if (registry != null) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT,
                    (ResourceLocation) enchantmentId.toMc());
                Enchantment ench = RegistryCompat.getValue(registry, key);
                if (ench != null) {
                    return ench.getMaxLevel();
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return 1;
    }

    @Override
    public int getMinLevel(Id enchantmentId, RegistryAccess access) {
        return 1;
    }

    @Override
    public String getDisplayName(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = RegistryCompat.lookupRegistry(access, Registries.ENCHANTMENT);
            if (registry != null) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT,
                    (ResourceLocation) enchantmentId.toMc());
                Optional<Holder.Reference<Enchantment>> holder = RegistryCompat.getHolder(registry, key);
                if (holder.isPresent()) {
                    return Enchantment.getFullname(holder.get(), 1).getString();
                }
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
            Registry<Enchantment> registry = RegistryCompat.lookupRegistry(access, Registries.ENCHANTMENT);
            if (registry != null) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT,
                    (ResourceLocation) enchantmentId.toMc());
                Optional<Holder.Reference<Enchantment>> holder = RegistryCompat.getHolder(registry, key);
                if (holder.isPresent()) {
                    if (holder.get().is(EnchantmentTags.TREASURE)) {
                        return true;
                    }
                    if (!holder.get().is(EnchantmentTags.IN_ENCHANTING_TABLE)) {
                        return true;
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return FALLBACK_TREASURE_ENCHANTS.contains(enchantmentId.getPath());
    }

    @Override
    public int getRarityWeight(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = RegistryCompat.lookupRegistry(access, Registries.ENCHANTMENT);
            if (registry != null) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT,
                    (ResourceLocation) enchantmentId.toMc());
                Enchantment ench = RegistryCompat.getValue(registry, key);
                if (ench != null) {
                    int weight = ench.getWeight();
                    if (weight > 0) {
                        return weight;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        String path = enchantmentId.getPath();
        if (VERY_RARE_ENCHANTS.contains(path)) {
            return 1;
        } else if (RARE_ENCHANTS.contains(path)) {
            return 2;
        } else if (UNCOMMON_ENCHANTS.contains(path)) {
            return 5;
        }
        return 10;
    }

    @Override
    public boolean isDiscoverable(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = RegistryCompat.lookupRegistry(access, Registries.ENCHANTMENT);
            if (registry != null) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT,
                    (ResourceLocation) enchantmentId.toMc());
                Optional<Holder.Reference<Enchantment>> holder = RegistryCompat.getHolder(registry, key);
                if (holder.isPresent()) {
                    if (holder.get().is(EnchantmentTags.IN_ENCHANTING_TABLE) ||
                        holder.get().is(EnchantmentTags.TRADEABLE) ||
                        holder.get().is(EnchantmentTags.ON_RANDOM_LOOT)) {
                        return true;
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return !FALLBACK_TREASURE_ENCHANTS.contains(enchantmentId.getPath());
    }
}
