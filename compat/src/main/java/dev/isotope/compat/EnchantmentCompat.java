package dev.isotope.compat;

import net.minecraft.core.RegistryAccess;

import java.util.List;

/**
 * Version-agnostic interface for enchantment registry access.
 * Implementations in mc1210 and mc1211 source sets.
 */
public interface EnchantmentCompat {

    /**
     * Get the singleton instance (version-specific implementation).
     */
    static EnchantmentCompat getInstance() {
        return EnchantmentCompatHolder.INSTANCE;
    }

    /**
     * Get all enchantments that can be applied to the given item.
     *
     * @param itemId The item to check
     * @param includeTreasure Whether to include treasure-only enchantments
     * @param access Registry access for resolving enchantments
     * @return List of applicable enchantment IDs
     */
    List<Id> getApplicableEnchantments(Id itemId, boolean includeTreasure, RegistryAccess access);

    /**
     * Get the maximum level for an enchantment.
     */
    int getMaxLevel(Id enchantmentId, RegistryAccess access);

    /**
     * Get the minimum level for an enchantment (usually 1).
     */
    int getMinLevel(Id enchantmentId, RegistryAccess access);

    /**
     * Get the display name for an enchantment.
     */
    String getDisplayName(Id enchantmentId, RegistryAccess access);

    /**
     * Check if an enchantment is treasure-only (cannot appear at enchanting table).
     */
    boolean isTreasure(Id enchantmentId, RegistryAccess access);

    /**
     * Get the rarity weight of an enchantment (higher = more common).
     */
    int getRarityWeight(Id enchantmentId, RegistryAccess access);

    /**
     * Check if an enchantment can appear on enchanting tables.
     */
    boolean isDiscoverable(Id enchantmentId, RegistryAccess access);
}

/**
 * Holder for lazy initialization of the version-specific implementation.
 */
class EnchantmentCompatHolder {
    static final EnchantmentCompat INSTANCE;

    static {
        EnchantmentCompat impl;
        try {
            // Try to load the version-specific implementation
            Class<?> implClass = Class.forName("dev.isotope.compat.EnchantmentCompatImpl");
            impl = (EnchantmentCompat) implClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // Fallback to a no-op implementation if not found
            impl = new NoOpEnchantmentCompat();
        }
        INSTANCE = impl;
    }
}

/**
 * Fallback no-op implementation.
 */
class NoOpEnchantmentCompat implements EnchantmentCompat {
    @Override
    public List<Id> getApplicableEnchantments(Id itemId, boolean includeTreasure, RegistryAccess access) {
        return List.of();
    }

    @Override
    public int getMaxLevel(Id enchantmentId, RegistryAccess access) {
        return 1;
    }

    @Override
    public int getMinLevel(Id enchantmentId, RegistryAccess access) {
        return 1;
    }

    @Override
    public String getDisplayName(Id enchantmentId, RegistryAccess access) {
        return enchantmentId.getPath();
    }

    @Override
    public boolean isTreasure(Id enchantmentId, RegistryAccess access) {
        return false;
    }

    @Override
    public int getRarityWeight(Id enchantmentId, RegistryAccess access) {
        return 10;
    }

    @Override
    public boolean isDiscoverable(Id enchantmentId, RegistryAccess access) {
        return true;
    }
}
