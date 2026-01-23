package dev.isotope.compat.impl;

import dev.isotope.compat.Id;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/**
 * MC 1.21.11+ implementation of Id using {@link Identifier}.
 */
public record IdImpl(Identifier mc) implements Id {

    public IdImpl {
        Objects.requireNonNull(mc, "Identifier cannot be null");
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
     * @return The underlying Identifier (typed version of toMc())
     */
    public Identifier identifier() {
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
        return new IdImpl(Identifier.fromNamespaceAndPath(namespace, path));
    }

    public static Id parse(String id) {
        return new IdImpl(Identifier.parse(id));
    }

    public static Id fromKey(ResourceKey<?> key) {
        return new IdImpl(key.identifier());
    }

    public static Id wrap(Object mc) {
        if (mc instanceof Identifier identifier) {
            return new IdImpl(identifier);
        }
        throw new IllegalArgumentException("Expected Identifier, got: " +
            (mc == null ? "null" : mc.getClass().getName()));
    }

    public static Id tryParse(String id) {
        try {
            return new IdImpl(Identifier.parse(id));
        } catch (Exception e) {
            return null;
        }
    }
}
