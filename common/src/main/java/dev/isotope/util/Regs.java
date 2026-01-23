package dev.isotope.util;

import dev.isotope.compat.Id;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

/**
 * Registry utility methods that work across MC versions.
 *
 * In MC 1.21.11+, registry methods like get() and getOptional() are ambiguous
 * between Identifier/ResourceLocation and TagKey overloads. This class provides
 * unambiguous methods using ResourceKey-based lookups.
 */
public final class Regs {

    private Regs() {}

    /**
     * Get a value from a registry by Id, returning an Optional.
     * Uses ResourceKey-based lookup to avoid ambiguity in MC 1.21.11+.
     *
     * @param registry The registry to look up in
     * @param registryKey The registry's key (e.g., Registries.ITEM)
     * @param id The identifier to look up
     * @param <T> The registry element type
     * @return Optional containing the value if found
     */
    public static <T> Optional<T> getOptional(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, Id id) {
        ResourceKey<T> key = ResourceKey.create(registryKey, id.mc());
        return registry.getOptional(key);
    }

    /**
     * Get a holder from a registry by Id.
     * Uses ResourceKey-based lookup to avoid ambiguity in MC 1.21.11+.
     *
     * @param registry The registry to look up in
     * @param registryKey The registry's key (e.g., Registries.ITEM)
     * @param id The identifier to look up
     * @param <T> The registry element type
     * @return Optional containing the holder if found
     */
    public static <T> Optional<Holder.Reference<T>> getHolder(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, Id id) {
        ResourceKey<T> key = ResourceKey.create(registryKey, id.mc());
        return registry.get(key);
    }

    /**
     * Get a value from a registry by Id, returning null if not found.
     * Uses ResourceKey-based lookup to avoid ambiguity in MC 1.21.11+.
     *
     * @param registry The registry to look up in
     * @param registryKey The registry's key (e.g., Registries.ITEM)
     * @param id The identifier to look up
     * @param <T> The registry element type
     * @return The value, or null if not found
     */
    public static <T> T getValue(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, Id id) {
        ResourceKey<T> key = ResourceKey.create(registryKey, id.mc());
        return registry.getValue(key);
    }
}
