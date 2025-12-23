package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Complete analyzed contents of a loot table.
 * Extends LootTableInfo with deep content analysis.
 */
public record LootTableContents(
    LootTableInfo info,
    List<LootPoolInfo> pools,
    boolean analyzed,
    String errorMessage
) {
    public static LootTableContents empty(LootTableInfo info) {
        return new LootTableContents(info, List.of(), false, "Not analyzed");
    }

    public static LootTableContents error(LootTableInfo info, String error) {
        return new LootTableContents(info, List.of(), false, error);
    }

    public static LootTableContents success(LootTableInfo info, List<LootPoolInfo> pools) {
        return new LootTableContents(info, pools, true, null);
    }

    public int poolCount() {
        return pools.size();
    }

    public int totalEntryCount() {
        return pools.stream().mapToInt(LootPoolInfo::entryCount).sum();
    }

    public Set<ResourceLocation> allItemIds() {
        return pools.stream()
            .flatMap(p -> p.entries().stream())
            .filter(e -> e.type() == LootEntryInfo.EntryType.ITEM)
            .map(LootEntryInfo::itemId)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(Collectors.toSet());
    }

    public Set<ResourceLocation> allTableReferences() {
        return pools.stream()
            .flatMap(p -> p.entries().stream())
            .filter(e -> e.type() == LootEntryInfo.EntryType.LOOT_TABLE)
            .map(LootEntryInfo::tableRef)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(Collectors.toSet());
    }

    public Set<ResourceLocation> allTagIds() {
        return pools.stream()
            .flatMap(p -> p.entries().stream())
            .filter(e -> e.type() == LootEntryInfo.EntryType.TAG)
            .map(LootEntryInfo::tagId)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(Collectors.toSet());
    }

    public boolean containsItem(ResourceLocation itemId) {
        return allItemIds().contains(itemId);
    }

    public boolean containsTableReference(ResourceLocation tableId) {
        return allTableReferences().contains(tableId);
    }

    public int uniqueItemCount() {
        return allItemIds().size();
    }

    public ResourceLocation id() {
        return info.id();
    }
}
