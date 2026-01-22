package dev.isotope.data;

import net.minecraft.resources.Identifier;

/**
 * Immutable data class holding discovered loot table information.
 */
public record LootTableInfo(
    Identifier id,
    String namespace,
    String path,
    LootTableCategory category
) {
    public enum LootTableCategory {
        CHEST,
        ENTITY,
        BLOCK,
        GAMEPLAY,
        ARCHAEOLOGY,
        EQUIPMENT,
        SHEARING,
        DISPENSER,   // Dispenser loot (jungle temple, trial chambers)
        SPAWNER,     // Spawner drops (trial chambers)
        POTS,        // Decorated pot loot
        OTHER
    }

    public static LootTableInfo fromId(Identifier id) {
        return new LootTableInfo(
            id,
            id.getNamespace(),
            id.getPath(),
            inferCategory(id.getPath())
        );
    }

    /**
     * Create LootTableInfo with an explicit category (from content analysis).
     */
    public static LootTableInfo fromIdWithCategory(Identifier id, LootTableCategory category) {
        return new LootTableInfo(
            id,
            id.getNamespace(),
            id.getPath(),
            category
        );
    }

    /**
     * Infer category from path (fallback method).
     */
    public static LootTableCategory inferCategoryFromPath(String path) {
        return inferCategory(path);
    }

    private static LootTableCategory inferCategory(String path) {
        // Handle both singular and plural prefixes (vanilla uses "chests/", some mods use "chest/")
        if (path.startsWith("chests/") || path.startsWith("chest/")) return LootTableCategory.CHEST;
        if (path.startsWith("entities/") || path.startsWith("entity/")) return LootTableCategory.ENTITY;
        if (path.startsWith("blocks/") || path.startsWith("block/")) return LootTableCategory.BLOCK;
        if (path.startsWith("gameplay/")) return LootTableCategory.GAMEPLAY;
        if (path.startsWith("archaeology/")) return LootTableCategory.ARCHAEOLOGY;
        if (path.startsWith("equipment/")) return LootTableCategory.EQUIPMENT;
        if (path.startsWith("shearing/")) return LootTableCategory.SHEARING;
        if (path.startsWith("dispensers/") || path.startsWith("dispenser/")) return LootTableCategory.DISPENSER;
        if (path.startsWith("spawners/") || path.startsWith("spawner/")) return LootTableCategory.SPAWNER;
        if (path.startsWith("pots/") || path.startsWith("pot/")) return LootTableCategory.POTS;
        return LootTableCategory.OTHER;
    }

    public String fullId() {
        return id.toString();
    }

    public boolean isVanilla() {
        return "minecraft".equals(namespace);
    }
}
