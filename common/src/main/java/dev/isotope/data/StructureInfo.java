package dev.isotope.data;

import dev.isotope.compat.Id;

/**
 * Immutable data class holding discovered structure information.
 */
public record StructureInfo(
    Id id,
    String namespace,
    String path
) {
    public static StructureInfo fromId(Id id) {
        return new StructureInfo(id, id.getNamespace(), id.getPath());
    }

    public String fullId() {
        return id.toString();
    }

    public boolean isVanilla() {
        return "minecraft".equals(namespace);
    }
}
