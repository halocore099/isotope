package dev.isotope.compat;

import net.minecraft.resources.ResourceKey;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for working with Id instances.
 *
 * This class provides convenience methods for common operations
 * and helps bridge between the Id abstraction and Minecraft APIs.
 */
public final class Ids {

    private Ids() {}

    /**
     * The default namespace for Minecraft resources.
     */
    public static final String MINECRAFT = "minecraft";

    /**
     * Create an Id from namespace and path.
     */
    public static Id of(String namespace, String path) {
        return Id.of(namespace, path);
    }

    /**
     * Create an Id in the minecraft namespace.
     */
    public static Id minecraft(String path) {
        return Id.of(MINECRAFT, path);
    }

    /**
     * Parse an Id from a string.
     */
    public static Id parse(String id) {
        return Id.parse(id);
    }

    /**
     * Try to parse an Id, returning null if invalid.
     */
    public static Id tryParse(String id) {
        return Id.tryParse(id);
    }

    /**
     * Create an Id from a ResourceKey.
     */
    public static Id fromKey(ResourceKey<?> key) {
        return Id.fromKey(key);
    }

    /**
     * Wrap a native Minecraft identifier.
     */
    public static Id wrap(Object mc) {
        return Id.wrap(mc);
    }

    /**
     * Convert a collection of native MC identifiers to Ids.
     */
    public static List<Id> wrapAll(Collection<?> mcIds) {
        return mcIds.stream().map(Id::wrap).collect(Collectors.toList());
    }

    /**
     * Convert Ids to native MC identifiers.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> unwrapAll(Collection<Id> ids) {
        return ids.stream().map(id -> (T) id.toMc()).collect(Collectors.toList());
    }

    /**
     * Create a stream of Ids from ResourceKeys.
     */
    public static Stream<Id> fromKeys(Stream<? extends ResourceKey<?>> keys) {
        return keys.map(Id::fromKey);
    }

    /**
     * Collect Ids from a ResourceKey stream into a Set.
     */
    public static Set<Id> fromKeysToSet(Stream<? extends ResourceKey<?>> keys) {
        return keys.map(Id::fromKey).collect(Collectors.toSet());
    }

    /**
     * Check if a string is a valid identifier.
     */
    public static boolean isValid(String id) {
        return Id.tryParse(id) != null;
    }

    /**
     * Compare two Ids by namespace, then by path.
     */
    public static int compare(Id a, Id b) {
        int ns = a.getNamespace().compareTo(b.getNamespace());
        return ns != 0 ? ns : a.getPath().compareTo(b.getPath());
    }
}
