package dev.isotope.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

/**
 * MC 1.21.11+ registry compatibility.
 *
 * In MC 1.21.11+, uses direct registry methods.
 */
public final class RegistryCompat {

    private RegistryCompat() {}

    /**
     * Get a holder from a registry by ResourceKey.
     */
    public static <T> Optional<Holder.Reference<T>> getHolder(Registry<T> registry, ResourceKey<T> key) {
        return registry.get(key);
    }

    /**
     * Get a value from a registry by ResourceKey.
     */
    public static <T> T getValue(Registry<T> registry, ResourceKey<T> key) {
        return registry.getValue(key);
    }

    /**
     * Look up a registry from RegistryAccess.
     */
    @SuppressWarnings("unchecked")
    public static <T> Registry<T> lookupRegistry(RegistryAccess access, ResourceKey<? extends Registry<T>> registryKey) {
        return (Registry<T>) access.lookupOrThrow(registryKey);
    }
}
