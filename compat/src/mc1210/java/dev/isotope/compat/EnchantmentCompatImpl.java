package dev.isotope.compat;

import dev.isotope.compat.impl.IdImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MC 1.21.0-1.21.10 implementation of EnchantmentCompat.
 */
public class EnchantmentCompatImpl implements EnchantmentCompat {

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
                    // Check treasure filter
                    if (!includeTreasure && ench.isTreasureOnly()) {
                        continue;
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
                return holder.get().value().getFullname(1).getString();
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
                return holder.get().value().isTreasureOnly();
            }
        } catch (Exception e) {
            // Fallback
        }
        return false;
    }

    @Override
    public int getRarityWeight(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                // In 1.21.x, rarity is accessed via Enchantment#rarity()
                // Weight is derived from rarity enum
                var rarity = holder.get().value().getRarity();
                return switch (rarity) {
                    case COMMON -> 10;
                    case UNCOMMON -> 5;
                    case RARE -> 2;
                    case VERY_RARE -> 1;
                };
            }
        } catch (Exception e) {
            // Fallback
        }
        return 10;
    }

    @Override
    public boolean isDiscoverable(Id enchantmentId, RegistryAccess access) {
        try {
            Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
            ResourceLocation loc = (ResourceLocation) enchantmentId.toMc();
            Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
            if (holder.isPresent()) {
                return holder.get().value().isDiscoverable();
            }
        } catch (Exception e) {
            // Fallback
        }
        return true;
    }
}
