package dev.isotope.search;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Search hit within a loot table.
 */
public record SearchHit(
    ResourceLocation table,
    int pool,
    int entry,
    String context
) {}
