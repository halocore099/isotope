package dev.isotope.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * MC 1.21.0-1.21.10 NBT compatibility.
 *
 * Uses reflection to handle varying API signatures across MC versions:
 * - MC 1.21.1-1.21.4: getString() returns String, contains(String, int)
 * - MC 1.21.7+: getString() returns Optional, contains(String) only
 */
public final class NbtCompat {

    private static final Method CONTAINS_WITH_TYPE;
    private static final Method GET_STRING;
    private static final Method GET_COMPOUND;
    private static final Method GET_LIST;
    private static final Method GET_ALL_KEYS;
    private static final boolean STRING_RETURNS_OPTIONAL;

    static {
        // Detect API shape at class load time
        Method containsWithType = null;
        Method getString = null;
        Method getCompound = null;
        Method getList = null;
        Method getAllKeys = null;
        boolean stringReturnsOptional = false;

        try {
            // Try the two-arg contains method (MC 1.21.1-1.21.4)
            containsWithType = CompoundTag.class.getMethod("contains", String.class, int.class);
        } catch (NoSuchMethodException e) {
            // MC 1.21.7+ only has contains(String)
        }

        try {
            getString = CompoundTag.class.getMethod("getString", String.class);
            // Check if it returns Optional (MC 1.21.7+) or String (MC 1.21.1-1.21.4)
            stringReturnsOptional = getString.getReturnType().equals(Optional.class);
        } catch (NoSuchMethodException e) {
            // Should not happen
        }

        try {
            getCompound = CompoundTag.class.getMethod("getCompound", String.class);
        } catch (NoSuchMethodException e) {
            // Should not happen
        }

        try {
            // Try getList with type parameter (MC 1.21.1-1.21.4)
            getList = CompoundTag.class.getMethod("getList", String.class, int.class);
        } catch (NoSuchMethodException e) {
            try {
                // Try getList without type parameter (MC 1.21.7+)
                getList = CompoundTag.class.getMethod("getList", String.class);
            } catch (NoSuchMethodException e2) {
                // Should not happen
            }
        }

        try {
            getAllKeys = CompoundTag.class.getMethod("getAllKeys");
        } catch (NoSuchMethodException e) {
            try {
                // Try keySet() as fallback
                getAllKeys = CompoundTag.class.getMethod("keySet");
            } catch (NoSuchMethodException e2) {
                // Should not happen
            }
        }

        CONTAINS_WITH_TYPE = containsWithType;
        GET_STRING = getString;
        GET_COMPOUND = getCompound;
        GET_LIST = getList;
        GET_ALL_KEYS = getAllKeys;
        STRING_RETURNS_OPTIONAL = stringReturnsOptional;
    }

    private NbtCompat() {}

    /**
     * Get a string value from a CompoundTag, returning Optional.
     */
    @SuppressWarnings("unchecked")
    public static Optional<String> getString(CompoundTag nbt, String key) {
        try {
            if (!containsKey(nbt, key)) {
                return Optional.empty();
            }
            Object result = GET_STRING.invoke(nbt, key);
            if (STRING_RETURNS_OPTIONAL) {
                return (Optional<String>) result;
            } else {
                String str = (String) result;
                return str.isEmpty() ? Optional.empty() : Optional.of(str);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get a compound tag, returning Optional.
     */
    @SuppressWarnings("unchecked")
    public static Optional<CompoundTag> getCompound(CompoundTag nbt, String key) {
        try {
            if (!containsKey(nbt, key)) {
                return Optional.empty();
            }
            Object result = GET_COMPOUND.invoke(nbt, key);
            if (result instanceof Optional) {
                return (Optional<CompoundTag>) result;
            } else {
                CompoundTag tag = (CompoundTag) result;
                return tag.isEmpty() ? Optional.empty() : Optional.of(tag);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get a list tag, returning Optional.
     */
    @SuppressWarnings("unchecked")
    public static Optional<ListTag> getList(CompoundTag nbt, String key) {
        try {
            if (!containsKey(nbt, key)) {
                return Optional.empty();
            }
            Object result;
            if (GET_LIST.getParameterCount() == 2) {
                // MC 1.21.1-1.21.4: getList(String, int)
                result = GET_LIST.invoke(nbt, key, 10); // 10 = TAG_COMPOUND
            } else {
                // MC 1.21.7+: getList(String)
                result = GET_LIST.invoke(nbt, key);
            }
            if (result instanceof Optional) {
                return (Optional<ListTag>) result;
            } else {
                ListTag tag = (ListTag) result;
                return tag.isEmpty() ? Optional.empty() : Optional.of(tag);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get all keys from a CompoundTag.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> keySet(CompoundTag nbt) {
        try {
            if (GET_ALL_KEYS != null) {
                return (Set<String>) GET_ALL_KEYS.invoke(nbt);
            }
        } catch (Exception e) {
            // Fall through
        }
        return Collections.emptySet();
    }

    /**
     * Check if key exists in the compound tag.
     */
    private static boolean containsKey(CompoundTag nbt, String key) {
        try {
            // Use the simple contains(String) method which exists in all versions
            return nbt.contains(key);
        } catch (Exception e) {
            return false;
        }
    }
}
