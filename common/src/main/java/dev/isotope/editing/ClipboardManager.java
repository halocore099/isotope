package dev.isotope.editing;

import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Clipboard manager for copy/paste operations in ISOTOPE.
 *
 * Supports copying entries and pools between loot tables.
 */
public final class ClipboardManager {

    private static final ClipboardManager INSTANCE = new ClipboardManager();

    @Nullable
    private LootEntry copiedEntry;

    @Nullable
    private LootPool copiedPool;

    @Nullable
    private ResourceLocation sourceTable;

    private ClipboardManager() {}

    public static ClipboardManager getInstance() {
        return INSTANCE;
    }

    // ===== Entry Operations =====

    /**
     * Copy an entry to the clipboard.
     */
    public void copyEntry(LootEntry entry, ResourceLocation fromTable) {
        // Create a deep copy
        this.copiedEntry = new LootEntry(
            entry.type(),
            entry.name(),
            entry.weight(),
            entry.quality(),
            new ArrayList<>(entry.conditions()),
            new ArrayList<>(entry.functions()),
            new ArrayList<>(entry.children())
        );
        this.copiedPool = null;
        this.sourceTable = fromTable;
    }

    /**
     * Get the copied entry (if any).
     */
    public Optional<LootEntry> getEntry() {
        return Optional.ofNullable(copiedEntry);
    }

    /**
     * Check if an entry is in the clipboard.
     */
    public boolean hasEntry() {
        return copiedEntry != null;
    }

    // ===== Pool Operations =====

    /**
     * Copy a pool to the clipboard.
     */
    public void copyPool(LootPool pool, ResourceLocation fromTable) {
        // Create a deep copy
        this.copiedPool = new LootPool(
            pool.name(),
            pool.rolls(),
            pool.bonusRolls(),
            new ArrayList<>(pool.entries()),
            new ArrayList<>(pool.conditions()),
            new ArrayList<>(pool.functions())
        );
        this.copiedEntry = null;
        this.sourceTable = fromTable;
    }

    /**
     * Get the copied pool (if any).
     */
    public Optional<LootPool> getPool() {
        return Optional.ofNullable(copiedPool);
    }

    /**
     * Check if a pool is in the clipboard.
     */
    public boolean hasPool() {
        return copiedPool != null;
    }

    // ===== General =====

    /**
     * Get the source table where the item was copied from.
     */
    public Optional<ResourceLocation> getSourceTable() {
        return Optional.ofNullable(sourceTable);
    }

    /**
     * Check if anything is in the clipboard.
     */
    public boolean hasContent() {
        return copiedEntry != null || copiedPool != null;
    }

    /**
     * Get a description of what's in the clipboard.
     */
    public String getContentDescription() {
        if (copiedEntry != null) {
            return "Entry: " + copiedEntry.name().map(ResourceLocation::getPath).orElse(copiedEntry.type());
        }
        if (copiedPool != null) {
            return "Pool with " + copiedPool.entries().size() + " entries";
        }
        return "Empty";
    }

    /**
     * Clear the clipboard.
     */
    public void clear() {
        copiedEntry = null;
        copiedPool = null;
        sourceTable = null;
    }
}
