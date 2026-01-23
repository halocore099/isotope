package dev.isotope.compat;

import net.minecraft.resources.ResourceKey;

import java.util.ServiceLoader;

/**
 * Factory interface for creating Id instances.
 *
 * The implementation is loaded via ServiceLoader, allowing version-specific
 * implementations to be provided without modifying this interface.
 */
public interface IdFactory {

    /**
     * Singleton instance loaded via ServiceLoader.
     */
    IdFactory INSTANCE = loadFactory();

    /**
     * Create an Id from namespace and path.
     */
    Id create(String namespace, String path);

    /**
     * Parse an Id from a string in "namespace:path" format.
     */
    Id parse(String id);

    /**
     * Create an Id from a ResourceKey.
     */
    Id fromKey(ResourceKey<?> key);

    /**
     * Wrap a native Minecraft identifier.
     */
    Id wrap(Object mc);

    /**
     * Try to parse an Id, returning null if invalid.
     */
    Id tryParse(String id);

    private static IdFactory loadFactory() {
        ServiceLoader<IdFactory> loader = ServiceLoader.load(IdFactory.class);
        return loader.findFirst().orElseThrow(() ->
            new IllegalStateException("No IdFactory implementation found. " +
                "Ensure the correct version-specific compat module is included.")
        );
    }
}
