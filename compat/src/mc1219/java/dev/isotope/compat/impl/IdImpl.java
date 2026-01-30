package dev.isotope.compat.impl;

import dev.isotope.compat.Id;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/**
 * MC 1.21.0-1.21.10 implementation of Id using {@link ResourceLocation}.
 */
public record IdImpl(ResourceLocation mc) implements Id {

    public IdImpl {
        Objects.requireNonNull(mc, "ResourceLocation cannot be null");
    }

    @Override
    public String getNamespace() {
        return mc.getNamespace();
    }

    @Override
    public String getPath() {
        return mc.getPath();
    }

    @Override
    public Object toMc() {
        return mc;
    }

    /**
     * @return The underlying ResourceLocation (typed version of toMc())
     */
    public ResourceLocation resourceLocation() {
        return mc;
    }

    @Override
    public String toString() {
        return mc.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof IdImpl other) {
            return mc.equals(other.mc);
        }
        if (obj instanceof Id other) {
            return getNamespace().equals(other.getNamespace()) &&
                   getPath().equals(other.getPath());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mc.hashCode();
    }

    // Static factory methods

    public static Id of(String namespace, String path) {
        return new IdImpl(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static Id parse(String id) {
        return new IdImpl(ResourceLocation.parse(id));
    }

    public static Id fromKey(ResourceKey<?> key) {
        // MC 1.21.0-1.21.10 uses .location() instead of .identifier()
        return new IdImpl(key.location());
    }

    public static Id wrap(Object mc) {
        if (mc instanceof ResourceLocation rl) {
            return new IdImpl(rl);
        }
        throw new IllegalArgumentException("Expected ResourceLocation, got: " +
            (mc == null ? "null" : mc.getClass().getName()));
    }

    public static Id tryParse(String id) {
        try {
            return new IdImpl(ResourceLocation.parse(id));
        } catch (Exception e) {
            return null;
        }
    }
}
