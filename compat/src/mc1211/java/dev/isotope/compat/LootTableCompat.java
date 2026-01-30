package dev.isotope.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Optional;

/**
 * MC 1.21.11+ loot table compatibility.
 *
 * In MC 1.21.11+, Entity.getLootTable() returns Optional directly.
 */
public final class LootTableCompat {

    private LootTableCompat() {}

    /**
     * Get the loot table key for an entity.
     */
    public static Optional<ResourceKey<LootTable>> getLootTable(Entity entity) {
        return entity.getLootTable();
    }
}
