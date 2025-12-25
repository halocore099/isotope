package dev.isotope.data.loot;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a complete loot table structure.
 *
 * This is a parsed representation of a Minecraft loot table JSON file,
 * suitable for display and editing in the ISOTOPE UI.
 */
public record LootTableStructure(
    ResourceLocation id,                    // The loot table's resource location
    String type,                            // e.g., "minecraft:chest", "minecraft:entity"
    List<LootPool> pools,                   // The pools in this table
    List<LootFunction> functions,           // Global functions applied to all outputs
    Optional<ResourceLocation> randomSequence  // Optional random sequence for deterministic generation
) {
    // Common loot table type constants
    public static final String TYPE_EMPTY = "minecraft:empty";
    public static final String TYPE_CHEST = "minecraft:chest";
    public static final String TYPE_ENTITY = "minecraft:entity";
    public static final String TYPE_BLOCK = "minecraft:block";
    public static final String TYPE_FISHING = "minecraft:fishing";
    public static final String TYPE_GIFT = "minecraft:gift";
    public static final String TYPE_BARTER = "minecraft:barter";
    public static final String TYPE_ADVANCEMENT_REWARD = "minecraft:advancement_reward";
    public static final String TYPE_ADVANCEMENT_ENTITY = "minecraft:advancement_entity";
    public static final String TYPE_GENERIC = "minecraft:generic";
    public static final String TYPE_COMMAND = "minecraft:command";
    public static final String TYPE_SELECTOR = "minecraft:selector";
    public static final String TYPE_ARCHAEOLOGY = "minecraft:archaeology";

    /**
     * Create an empty loot table.
     */
    public static LootTableStructure empty(ResourceLocation id) {
        return new LootTableStructure(
            id,
            TYPE_EMPTY,
            List.of(),
            List.of(),
            Optional.empty()
        );
    }

    /**
     * Create a chest loot table with given pools.
     */
    public static LootTableStructure chest(ResourceLocation id, List<LootPool> pools) {
        return new LootTableStructure(
            id,
            TYPE_CHEST,
            pools,
            List.of(),
            Optional.empty()
        );
    }

    /**
     * Get the total number of entries across all pools.
     */
    public int getTotalEntryCount() {
        return pools.stream()
            .mapToInt(LootPool::getEntryCount)
            .sum();
    }

    /**
     * Get the number of pools.
     */
    public int getPoolCount() {
        return pools.size();
    }

    /**
     * Check if this is an empty loot table.
     */
    public boolean isEmpty() {
        return TYPE_EMPTY.equals(type) || pools.isEmpty();
    }

    /**
     * Get a display name for the table type.
     */
    public String getTypeDisplayName() {
        String displayType = type;
        if (displayType.startsWith("minecraft:")) {
            displayType = displayType.substring("minecraft:".length());
        }
        // Capitalize first letter
        if (!displayType.isEmpty()) {
            displayType = Character.toUpperCase(displayType.charAt(0)) + displayType.substring(1);
        }
        return displayType;
    }

    /**
     * Get a short ID string (just the path).
     */
    public String getShortId() {
        return id.getPath();
    }

    /**
     * Get the namespace of this table.
     */
    public String getNamespace() {
        return id.getNamespace();
    }

    /**
     * Check if this table has global functions.
     */
    public boolean hasGlobalFunctions() {
        return !functions.isEmpty();
    }

    /**
     * Create a copy with new pools.
     */
    public LootTableStructure withPools(List<LootPool> newPools) {
        return new LootTableStructure(id, type, newPools, functions, randomSequence);
    }

    /**
     * Create a copy with a pool added at the specified index.
     * Use -1 to add at the end.
     */
    public LootTableStructure withPoolAdded(int index, LootPool pool) {
        List<LootPool> newPools = new ArrayList<>(pools);
        if (index < 0) {
            newPools.add(pool); // Add at end
        } else {
            newPools.add(Math.min(index, newPools.size()), pool);
        }
        return withPools(newPools);
    }

    /**
     * Create a copy with a pool removed at the specified index.
     */
    public LootTableStructure withPoolRemoved(int index) {
        if (index < 0 || index >= pools.size()) {
            return this;
        }
        List<LootPool> newPools = new ArrayList<>(pools);
        newPools.remove(index);
        return withPools(newPools);
    }

    /**
     * Create a copy with a pool replaced at the specified index.
     */
    public LootTableStructure withPoolReplaced(int index, LootPool pool) {
        if (index < 0 || index >= pools.size()) {
            return this;
        }
        List<LootPool> newPools = new ArrayList<>(pools);
        newPools.set(index, pool);
        return withPools(newPools);
    }

    /**
     * Create a copy with new functions.
     */
    public LootTableStructure withFunctions(List<LootFunction> newFunctions) {
        return new LootTableStructure(id, type, pools, newFunctions, randomSequence);
    }

    /**
     * Create a copy with a new type.
     */
    public LootTableStructure withType(String newType) {
        return new LootTableStructure(id, newType, pools, functions, randomSequence);
    }

    /**
     * Create a copy with a new ID.
     */
    public LootTableStructure withId(ResourceLocation newId) {
        return new LootTableStructure(newId, type, pools, functions, randomSequence);
    }
}
