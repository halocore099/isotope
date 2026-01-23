package dev.isotope.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Optional;
import java.util.Set;

/**
 * MC 1.21.11+ NBT compatibility.
 *
 * In MC 1.21.11+:
 * - CompoundTag.getString() returns Optional<String>
 * - CompoundTag.getCompound() returns Optional<CompoundTag>
 * - CompoundTag.getList() returns Optional<ListTag>
 * - CompoundTag.keySet() returns the key set
 */
public final class NbtCompat {

    private NbtCompat() {}

    /**
     * Get a string value from a CompoundTag, returning Optional.
     * In MC 1.21.11+, getString() already returns Optional.
     */
    public static Optional<String> getString(CompoundTag nbt, String key) {
        return nbt.getString(key);
    }

    /**
     * Get a compound tag, returning Optional.
     * In MC 1.21.11+, getCompound() already returns Optional.
     */
    public static Optional<CompoundTag> getCompound(CompoundTag nbt, String key) {
        return nbt.getCompound(key);
    }

    /**
     * Get a list tag, returning Optional.
     * In MC 1.21.11+, getList() already returns Optional.
     */
    public static Optional<ListTag> getList(CompoundTag nbt, String key) {
        return nbt.getList(key);
    }

    /**
     * Get all keys from a CompoundTag.
     * In MC 1.21.11+, the method is keySet().
     */
    public static Set<String> keySet(CompoundTag nbt) {
        return nbt.keySet();
    }
}
