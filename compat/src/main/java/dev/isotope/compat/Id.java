package dev.isotope.compat;

import net.minecraft.resources.ResourceKey;

/**
 * Version-agnostic wrapper for Minecraft's identifier type.
 *
 * In MC 1.21.11+, this wraps {@code net.minecraft.resources.Identifier}.
 * In MC 1.21.0-1.21.10, this wraps {@code net.minecraft.resources.ResourceLocation}.
 *
 * This abstraction allows common code to work across all versions without
 * needing conditional compilation for the identifier type.
 */
public interface Id extends Comparable<Id> {

    /**
     * @return The namespace portion (e.g., "minecraft" from "minecraft:stone")
     */
    String getNamespace();

    /**
     * @return The path portion (e.g., "stone" from "minecraft:stone")
     */
    String getPath();

    /**
     * @return The underlying Minecraft type (Identifier or ResourceLocation)
     */
    Object toMc();

    /**
     * Get the underlying Minecraft type with a typed cast.
     * Use this when you need to pass the identifier to MC APIs.
     *
     * Example: {@code id.<Identifier>mc()}
     *
     * @param <T> The expected Minecraft type
     * @return The underlying identifier cast to type T
     */
    @SuppressWarnings("unchecked")
    default <T> T mc() {
        return (T) toMc();
    }

    /**
     * @return String representation in "namespace:path" format
     */
    @Override
    String toString();

    @Override
    default int compareTo(Id other) {
        int ns = this.getNamespace().compareTo(other.getNamespace());
        return ns != 0 ? ns : this.getPath().compareTo(other.getPath());
    }

    // Static factory methods - implemented by version-specific IdImpl

    /**
     * Create an Id from namespace and path.
     */
    static Id of(String namespace, String path) {
        return IdFactory.INSTANCE.create(namespace, path);
    }

    /**
     * Parse an Id from a string in "namespace:path" format.
     */
    static Id parse(String id) {
        return IdFactory.INSTANCE.parse(id);
    }

    /**
     * Create an Id from a ResourceKey.
     */
    static Id fromKey(ResourceKey<?> key) {
        return IdFactory.INSTANCE.fromKey(key);
    }

    /**
     * Wrap a native Minecraft identifier (Identifier or ResourceLocation).
     */
    static Id wrap(Object mc) {
        return IdFactory.INSTANCE.wrap(mc);
    }

    /**
     * Try to parse an Id, returning null if invalid.
     */
    static Id tryParse(String id) {
        return IdFactory.INSTANCE.tryParse(id);
    }
}
