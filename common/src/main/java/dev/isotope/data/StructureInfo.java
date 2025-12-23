package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable data class holding discovered structure information.
 */
public record StructureInfo(
    ResourceLocation id,
    String namespace,
    String path
) {
    public static StructureInfo fromId(ResourceLocation id) {
        return new StructureInfo(id, id.getNamespace(), id.getPath());
    }

    public String fullId() {
        return id.toString();
    }

    public boolean isVanilla() {
        return "minecraft".equals(namespace);
    }
}
