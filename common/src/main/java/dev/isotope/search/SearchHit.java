package dev.isotope.search;

import dev.isotope.compat.Id;

/**
 * Search hit within a loot table.
 */
public record SearchHit(
    Id table,
    int pool,
    int entry,
    String context
) {}
