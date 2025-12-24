package dev.isotope.data.loot;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a loot pool within a loot table.
 *
 * A pool is rolled a number of times (determined by rolls + bonusRolls * luck),
 * and each roll selects one entry based on weights.
 */
public record LootPool(
    String name,                       // Optional pool name (for identification)
    NumberProvider rolls,              // Number of times to roll this pool
    NumberProvider bonusRolls,         // Additional rolls based on luck attribute
    List<LootEntry> entries,           // Entries to select from
    List<LootCondition> conditions,    // Conditions for the entire pool
    List<LootFunction> functions       // Functions applied to all pool outputs
) {
    /**
     * Create a simple pool with constant rolls and no bonus.
     */
    public static LootPool simple(int rolls, List<LootEntry> entries) {
        return new LootPool(
            "",
            NumberProvider.constant(rolls),
            NumberProvider.constant(0),
            entries,
            List.of(),
            List.of()
        );
    }

    /**
     * Create a pool with uniform roll range.
     */
    public static LootPool withRolls(float minRolls, float maxRolls, List<LootEntry> entries) {
        return new LootPool(
            "",
            NumberProvider.uniform(minRolls, maxRolls),
            NumberProvider.constant(0),
            entries,
            List.of(),
            List.of()
        );
    }

    /**
     * Get the total weight of all entries in this pool.
     */
    public int getTotalWeight() {
        return entries.stream()
            .mapToInt(LootEntry::weight)
            .sum();
    }

    /**
     * Get a display string for the rolls (e.g., "1", "1-3").
     */
    public String getRollsString() {
        return rolls.toString();
    }

    /**
     * Get entry count.
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Check if this pool has any conditions.
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /**
     * Check if this pool has any functions.
     */
    public boolean hasFunctions() {
        return !functions.isEmpty();
    }

    /**
     * Create a copy with a new name.
     */
    public LootPool withName(String newName) {
        return new LootPool(newName, rolls, bonusRolls, entries, conditions, functions);
    }

    /**
     * Create a copy with new rolls.
     */
    public LootPool withRolls(NumberProvider newRolls) {
        return new LootPool(name, newRolls, bonusRolls, entries, conditions, functions);
    }

    /**
     * Create a copy with new entries.
     */
    public LootPool withEntries(List<LootEntry> newEntries) {
        return new LootPool(name, rolls, bonusRolls, newEntries, conditions, functions);
    }

    /**
     * Create a copy with an entry added at the specified index.
     */
    public LootPool withEntryAdded(int index, LootEntry entry) {
        List<LootEntry> newEntries = new ArrayList<>(entries);
        newEntries.add(Math.min(index, newEntries.size()), entry);
        return withEntries(newEntries);
    }

    /**
     * Create a copy with an entry removed at the specified index.
     */
    public LootPool withEntryRemoved(int index) {
        if (index < 0 || index >= entries.size()) {
            return this;
        }
        List<LootEntry> newEntries = new ArrayList<>(entries);
        newEntries.remove(index);
        return withEntries(newEntries);
    }

    /**
     * Create a copy with an entry replaced at the specified index.
     */
    public LootPool withEntryReplaced(int index, LootEntry entry) {
        if (index < 0 || index >= entries.size()) {
            return this;
        }
        List<LootEntry> newEntries = new ArrayList<>(entries);
        newEntries.set(index, entry);
        return withEntries(newEntries);
    }

    /**
     * Create a copy with new conditions.
     */
    public LootPool withConditions(List<LootCondition> newConditions) {
        return new LootPool(name, rolls, bonusRolls, entries, newConditions, functions);
    }

    /**
     * Create a copy with new functions.
     */
    public LootPool withFunctions(List<LootFunction> newFunctions) {
        return new LootPool(name, rolls, bonusRolls, entries, conditions, newFunctions);
    }
}
