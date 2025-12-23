package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a single pool within a loot table.
 */
public record LootPoolInfo(
    int poolIndex,
    float minRolls,
    float maxRolls,
    float bonusRollsPerLuck,
    List<LootEntryInfo> entries,
    int conditionCount,
    int functionCount
) {
    public int entryCount() {
        return entries.size();
    }

    public List<LootEntryInfo> itemEntries() {
        return entries.stream()
            .filter(e -> e.type() == LootEntryInfo.EntryType.ITEM)
            .toList();
    }

    public List<LootEntryInfo> tableReferenceEntries() {
        return entries.stream()
            .filter(e -> e.type() == LootEntryInfo.EntryType.LOOT_TABLE)
            .toList();
    }

    public double totalWeight() {
        return entries.stream().mapToInt(LootEntryInfo::weight).sum();
    }

    public Set<ResourceLocation> allItemIds() {
        return entries.stream()
            .filter(e -> e.type() == LootEntryInfo.EntryType.ITEM)
            .map(LootEntryInfo::itemId)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(Collectors.toSet());
    }

    public String rollsDescription() {
        if (minRolls == maxRolls) {
            return String.format("%.0f", minRolls);
        }
        return String.format("%.0f-%.0f", minRolls, maxRolls);
    }
}
