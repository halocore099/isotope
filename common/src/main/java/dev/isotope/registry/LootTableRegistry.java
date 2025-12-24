package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.LootTableInfo.LootTableCategory;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all discovered loot tables.
 *
 * Scans Minecraft's loot table registry to find all loot tables
 * (vanilla + modded) that exist in the current game session.
 */
public final class LootTableRegistry {

    private static final LootTableRegistry INSTANCE = new LootTableRegistry();

    private final Map<ResourceLocation, LootTableInfo> lootTables = new LinkedHashMap<>();
    private boolean scanned = false;

    private LootTableRegistry() {}

    public static LootTableRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Scan the loot table registry from the server.
     * In 1.21.4, loot tables are in the reloadable registries.
     */
    public void scan(MinecraftServer server) {
        lootTables.clear();

        try {
            // In 1.21.4, reloadableRegistries() returns a Holder with a lookup() method
            var holder = server.reloadableRegistries();

            // Log the holder type for debugging
            Isotope.LOGGER.info("Holder type: {}", holder.getClass().getName());

            // Try lookup() with no args to see what it returns
            var lookup = holder.lookup();
            Isotope.LOGGER.info("Lookup type: {}", lookup.getClass().getName());

            // The lookup should implement HolderLookup.Provider
            if (lookup instanceof net.minecraft.core.HolderLookup.Provider provider) {
                // Now we can look up the loot table registry
                var lootLookup = provider.lookupOrThrow(Registries.LOOT_TABLE);

                lootLookup.listElementIds().forEach(key -> {
                    ResourceLocation id = key.location();
                    if (id.getPath().equals("empty")) {
                        return;
                    }
                    LootTableInfo info = LootTableInfo.fromId(id);
                    lootTables.put(id, info);
                });
            } else {
                Isotope.LOGGER.warn("Lookup is not a HolderLookup.Provider: {}", lookup.getClass());
            }

            scanned = true;
            Isotope.LOGGER.info("LootTableRegistry: scanned {} loot tables", lootTables.size());

            // Log category breakdown
            Map<LootTableCategory, Long> byCategory = lootTables.values().stream()
                .collect(Collectors.groupingBy(LootTableInfo::category, Collectors.counting()));
            byCategory.forEach((cat, count) ->
                Isotope.LOGGER.debug("  {} {} loot tables", count, cat));

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to scan loot table registry", e);
        }
    }

    /**
     * Get all discovered loot tables.
     */
    public Collection<LootTableInfo> getAll() {
        return Collections.unmodifiableCollection(lootTables.values());
    }

    /**
     * Get loot table by ID.
     */
    public Optional<LootTableInfo> get(ResourceLocation id) {
        return Optional.ofNullable(lootTables.get(id));
    }

    /**
     * Get loot tables filtered by category.
     */
    public List<LootTableInfo> getByCategory(LootTableCategory category) {
        return lootTables.values().stream()
            .filter(lt -> lt.category() == category)
            .toList();
    }

    /**
     * Get loot tables filtered by namespace.
     */
    public List<LootTableInfo> getByNamespace(String namespace) {
        if ("*".equals(namespace)) {
            return new ArrayList<>(lootTables.values());
        }
        return lootTables.values().stream()
            .filter(lt -> lt.namespace().equals(namespace))
            .toList();
    }

    /**
     * Get CHEST category loot tables (most relevant for structures).
     */
    public List<LootTableInfo> getChestLootTables() {
        return getByCategory(LootTableCategory.CHEST);
    }

    /**
     * Get all unique namespaces.
     */
    public Set<String> getNamespaces() {
        return lootTables.values().stream()
            .map(LootTableInfo::namespace)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get count of loot tables per category.
     */
    public Map<LootTableCategory, Integer> getCategoryCounts() {
        Map<LootTableCategory, Integer> counts = new EnumMap<>(LootTableCategory.class);
        for (LootTableInfo info : lootTables.values()) {
            counts.merge(info.category(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Get count of loot tables per namespace.
     */
    public Map<String, Integer> getNamespaceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LootTableInfo info : lootTables.values()) {
            counts.merge(info.namespace(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Total loot table count.
     */
    public int size() {
        return lootTables.size();
    }

    /**
     * Check if registry has been scanned.
     */
    public boolean isScanned() {
        return scanned;
    }

    /**
     * Reset the registry (for re-scanning).
     */
    public void reset() {
        lootTables.clear();
        scanned = false;
    }

    /**
     * Add a loot table from a loaded save file.
     */
    public void addFromSave(LootTableInfo info) {
        lootTables.put(info.id(), info);
        scanned = true; // Mark as having data
    }
}
