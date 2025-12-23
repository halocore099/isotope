package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable data class holding discovered loot table information.
 */
public record LootTableInfo(
    ResourceLocation id,
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
        OTHER
    }

    public static LootTableInfo fromId(ResourceLocation id) {
        return new LootTableInfo(
            id,
            id.getNamespace(),
            id.getPath(),
            inferCategory(id.getPath())
        );
    }

    private static LootTableCategory inferCategory(String path) {
        if (path.startsWith("chests/")) return LootTableCategory.CHEST;
        if (path.startsWith("entities/")) return LootTableCategory.ENTITY;
        if (path.startsWith("blocks/")) return LootTableCategory.BLOCK;
        if (path.startsWith("gameplay/")) return LootTableCategory.GAMEPLAY;
        if (path.startsWith("archaeology/")) return LootTableCategory.ARCHAEOLOGY;
        if (path.startsWith("equipment/")) return LootTableCategory.EQUIPMENT;
        if (path.startsWith("shearing/")) return LootTableCategory.SHEARING;
        return LootTableCategory.OTHER;
    }

    public String fullId() {
        return id.toString();
    }

    public boolean isVanilla() {
        return "minecraft".equals(namespace);
    }
}
