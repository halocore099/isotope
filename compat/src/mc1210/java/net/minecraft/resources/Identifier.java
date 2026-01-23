package net.minecraft.resources;

/**
 * Shim class for MC 1.21.10 and earlier.
 * Provides static factory methods that delegate to ResourceLocation.
 *
 * Note: This does NOT make Identifier interchangeable with ResourceLocation.
 * Code should use dev.isotope.compat.Id for version-agnostic identifier handling.
 * This shim only provides static factory method compatibility.
 */
public final class Identifier {

    private Identifier() {}

    public static ResourceLocation withDefaultNamespace(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation parse(String id) {
        return ResourceLocation.parse(id);
    }
}
