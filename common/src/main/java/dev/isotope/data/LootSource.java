package dev.isotope.data;

import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Represents any source of loot tables in Minecraft.
 *
 * Can be a real structure (from registry) or a virtual "feature" (dungeon, fossil, etc.).
 * This provides a unified abstraction for the UI to display both structures and features.
 */
public record LootSource(
    Identifier id,
    String namespace,
    String path,
    LootSourceType type,
    String displayName,
    String description
) {
    /**
     * Create from a real StructureInfo.
     */
    public static LootSource fromStructure(StructureInfo info) {
        return new LootSource(
            info.id(),
            info.namespace(),
            info.path(),
            LootSourceType.STRUCTURE,
            formatDisplayName(info.path()),
            "Structure with persistent tracking"
        );
    }

    /**
     * Create a virtual feature source.
     */
    public static LootSource feature(Identifier id, String displayName, String description) {
        return new LootSource(
            id,
            id.getNamespace(),
            id.getPath(),
            LootSourceType.FEATURE,
            displayName,
            description
        );
    }

    /**
     * Create a mob/entity loot source.
     */
    public static LootSource mob(Identifier entityId, String displayName, String description) {
        return new LootSource(
            entityId,
            entityId.getNamespace(),
            entityId.getPath(),
            LootSourceType.MOB,
            displayName,
            description
        );
    }

    /**
     * Create from ID with auto-detected type.
     */
    public static LootSource fromId(Identifier id, LootSourceType type) {
        return new LootSource(
            id,
            id.getNamespace(),
            id.getPath(),
            type,
            formatDisplayName(id.getPath()),
            type.getLabel()
        );
    }

    public String fullId() {
        return id.toString();
    }

    public boolean isVanilla() {
        return "minecraft".equals(namespace);
    }

    public boolean isStructure() {
        return type == LootSourceType.STRUCTURE;
    }

    public boolean isFeature() {
        return type == LootSourceType.FEATURE;
    }

    public boolean isMob() {
        return type == LootSourceType.MOB;
    }

    /**
     * Convert snake_case to Title Case for display.
     */
    public static String formatDisplayName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return Arrays.stream(path.split("_"))
            .map(word -> word.isEmpty() ? "" :
                Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }
}
