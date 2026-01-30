package dev.isotope.util;

import dev.isotope.compat.Id;
import dev.isotope.compat.RegistryCompat;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

/**
 * Registry utility methods that work across MC versions.
 *
 * Uses RegistryCompat to handle varying registry API signatures between versions.
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
     * Uses RegistryCompat to handle version differences.
     *
     * @param registry The registry to look up in
     * @param registryKey The registry's key (e.g., Registries.ITEM)
     * @param id The identifier to look up
     * @param <T> The registry element type
     * @return Optional containing the holder if found
     */
    public static <T> Optional<Holder.Reference<T>> getHolder(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, Id id) {
        ResourceKey<T> key = ResourceKey.create(registryKey, id.mc());
        return RegistryCompat.getHolder(registry, key);
    }

    /**
     * Get a value from a registry by Id, returning null if not found.
     * Uses RegistryCompat to handle version differences.
     *
     * @param registry The registry to look up in
     * @param registryKey The registry's key (e.g., Registries.ITEM)
     * @param id The identifier to look up
     * @param <T> The registry element type
     * @return The value, or null if not found
     */
    public static <T> T getValue(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, Id id) {
        ResourceKey<T> key = ResourceKey.create(registryKey, id.mc());
        return RegistryCompat.getValue(registry, key);
    }
}
