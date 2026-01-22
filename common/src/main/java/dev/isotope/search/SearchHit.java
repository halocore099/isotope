package dev.isotope.search;

import net.minecraft.resources.Identifier;

/**
 * Search hit within a loot table.
 */
public record SearchHit(
    Identifier table,
    int pool,
    int entry,
    String context
) {}
