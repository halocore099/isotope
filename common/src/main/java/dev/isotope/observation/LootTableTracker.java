package dev.isotope.observation;

import net.minecraft.resources.ResourceLocation;

/**
 * Thread-local tracker for loot table IDs.
 *
 * Since LootTable objects don't store their own ID, we need to track
 * which table is being resolved/executed via the call stack.
 *
 * The ReloadableRegistriesMixin sets the current table ID when a table
 * is looked up, and the LootTableMixin reads it when the table executes.
 */
public final class LootTableTracker {

    private static final ThreadLocal<ResourceLocation> CURRENT_TABLE = new ThreadLocal<>();

    private LootTableTracker() {}

    /**
     * Set the current table being resolved/executed.
     * Called by ReloadableRegistriesMixin when getLootTable() is called.
     */
    public static void setCurrentTableId(ResourceLocation tableId) {
        CURRENT_TABLE.set(tableId);
    }

    /**
     * Get the current table ID.
     * Called by LootTableMixin when getRandomItems() is called.
     */
    public static ResourceLocation getCurrentTableId() {
        return CURRENT_TABLE.get();
    }

    /**
     * Clear the current table ID.
     * Called after loot generation completes.
     */
    public static void clearCurrentTableId() {
        CURRENT_TABLE.remove();
    }
}
