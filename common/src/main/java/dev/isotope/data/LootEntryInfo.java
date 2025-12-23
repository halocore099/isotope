package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Represents a single entry in a loot pool.
 * Handles different entry types: item, loot_table reference, tag, empty, etc.
 */
public record LootEntryInfo(
    EntryType type,
    Optional<ResourceLocation> itemId,
    Optional<ResourceLocation> tableRef,
    Optional<ResourceLocation> tagId,
    int weight,
    int quality,
    int minCount,
    int maxCount
) {
    public enum EntryType {
        ITEM,
        LOOT_TABLE,
        TAG,
        EMPTY,
        DYNAMIC,
        ALTERNATIVES,
        SEQUENCE,
        GROUP,
        UNKNOWN
    }

    public static LootEntryInfo item(ResourceLocation itemId, int weight, int quality, int minCount, int maxCount) {
        return new LootEntryInfo(
            EntryType.ITEM,
            Optional.of(itemId),
            Optional.empty(),
            Optional.empty(),
            weight,
            quality,
            minCount,
            maxCount
        );
    }

    public static LootEntryInfo tableReference(ResourceLocation tableRef, int weight, int quality) {
        return new LootEntryInfo(
            EntryType.LOOT_TABLE,
            Optional.empty(),
            Optional.of(tableRef),
            Optional.empty(),
            weight,
            quality,
            1,
            1
        );
    }

    public static LootEntryInfo tag(ResourceLocation tagId, int weight, int quality, int minCount, int maxCount) {
        return new LootEntryInfo(
            EntryType.TAG,
            Optional.empty(),
            Optional.empty(),
            Optional.of(tagId),
            weight,
            quality,
            minCount,
            maxCount
        );
    }

    public static LootEntryInfo empty(int weight) {
        return new LootEntryInfo(
            EntryType.EMPTY,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            weight,
            0,
            0,
            0
        );
    }

    public static LootEntryInfo unknown(EntryType type, int weight) {
        return new LootEntryInfo(
            type,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            weight,
            0,
            1,
            1
        );
    }

    public String displayName() {
        return switch (type) {
            case ITEM -> itemId.map(ResourceLocation::toString).orElse("unknown:item");
            case LOOT_TABLE -> "[table:" + tableRef.map(ResourceLocation::toString).orElse("?") + "]";
            case TAG -> "#" + tagId.map(ResourceLocation::toString).orElse("unknown:tag");
            case EMPTY -> "[empty]";
            default -> "[" + type.name().toLowerCase() + "]";
        };
    }

    public String formatForDisplay() {
        String base = displayName();
        if (type == EntryType.ITEM || type == EntryType.TAG) {
            if (minCount == maxCount) {
                base += " x" + minCount;
            } else {
                base += " x" + minCount + "-" + maxCount;
            }
        }
        base += " (w:" + weight + ")";
        return base;
    }
}
