package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Links a structure to its associated loot tables.
 */
public record StructureLootLink(
    StructureInfo structure,
    Set<ResourceLocation> linkedTables,
    LinkMethod linkMethod,
    float confidence
) {
    public enum LinkMethod {
        PATH_HEURISTIC,
        EXACT_MATCH,
        MANUAL_MAPPING,
        NONE
    }

    public static StructureLootLink none(StructureInfo structure) {
        return new StructureLootLink(structure, Set.of(), LinkMethod.NONE, 0.0f);
    }

    public static StructureLootLink manual(StructureInfo structure, Set<ResourceLocation> tables) {
        return new StructureLootLink(structure, tables, LinkMethod.MANUAL_MAPPING, 0.95f);
    }

    public static StructureLootLink exact(StructureInfo structure, Set<ResourceLocation> tables) {
        return new StructureLootLink(structure, tables, LinkMethod.EXACT_MATCH, 0.9f);
    }

    public static StructureLootLink heuristic(StructureInfo structure, Set<ResourceLocation> tables, float confidence) {
        return new StructureLootLink(structure, tables, LinkMethod.PATH_HEURISTIC, confidence);
    }

    public boolean hasLinks() {
        return !linkedTables.isEmpty();
    }

    public int linkCount() {
        return linkedTables.size();
    }

    public String confidencePercent() {
        return String.format("%.0f%%", confidence * 100);
    }

    public ResourceLocation structureId() {
        return structure.id();
    }
}
