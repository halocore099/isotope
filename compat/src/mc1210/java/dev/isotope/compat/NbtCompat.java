package dev.isotope.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.Set;

/**
 * MC 1.21.0-1.21.10 NBT compatibility.
 *
 * In MC 1.21.10 and earlier:
 * - CompoundTag.getString() returns String directly (empty string if not present)
 * - CompoundTag.getCompound() returns CompoundTag directly (empty if not present)
 * - CompoundTag.getList() returns ListTag directly (empty if not present)
 * - CompoundTag.getAllKeys() returns the key set
 */
public final class NbtCompat {

    private NbtCompat() {}

    /**
     * Get a string value from a CompoundTag, returning Optional.
     * In MC 1.21.10, getString() returns empty string if not present.
     */
    public static Optional<String> getString(CompoundTag nbt, String key) {
        if (nbt.contains(key, Tag.TAG_STRING)) {
            return Optional.of(nbt.getString(key));
        }
        return Optional.empty();
    }

    /**
     * Get a compound tag, returning Optional.
     * In MC 1.21.10, getCompound() returns empty CompoundTag if not present.
     */
    public static Optional<CompoundTag> getCompound(CompoundTag nbt, String key) {
        if (nbt.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.of(nbt.getCompound(key));
        }
        return Optional.empty();
    }

    /**
     * Get a list tag, returning Optional.
     * In MC 1.21.10, getList() returns empty ListTag if not present.
     */
    public static Optional<ListTag> getList(CompoundTag nbt, String key) {
        if (nbt.contains(key, Tag.TAG_LIST)) {
            return Optional.of(nbt.getList(key, Tag.TAG_COMPOUND));
        }
        return Optional.empty();
    }

    /**
     * Get all keys from a CompoundTag.
     * In MC 1.21.10, the method is getAllKeys().
     */
    public static Set<String> keySet(CompoundTag nbt) {
        return nbt.getAllKeys();
    }
}
