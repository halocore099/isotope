package dev.isotope.search;

import net.minecraft.resources.ResourceLocation;

/**
 * Search hit within a loot table.
 */
public record SearchHit(
    ResourceLocation table,
    int pool,
    int entry,
    String context
) {}
