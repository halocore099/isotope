package dev.isotope.api;

import dev.isotope.Isotope;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public API for mods to register structure-loot links.
 *
 * This provides 100% accuracy for mods that adopt it - no heuristics needed.
 * Mods can register links in two ways:
 *
 * 1. Programmatic registration (this class):
 *    ModLinkRegistry.register("mymod:my_structure", "mymod:chests/my_structure");
 *
 * 2. JSON metadata (isotope_links.json in mod jar):
 *    {
 *      "links": [
 *        { "structure": "mymod:my_structure", "loot_tables": ["mymod:chests/my_structure"] }
 *      ]
 *    }
 *
 * Links registered via this API get MOD_DECLARED confidence (95) - higher than
 * any automatic detection method except MANUAL (100) user overrides.
 *
 * Usage for mod developers:
 *
 *   // In your mod initialization
 *   if (Platform.isModLoaded("isotope")) {
 *       ModLinkRegistry.register("mymod:dungeon", "mymod:chests/dungeon_loot");
 *       ModLinkRegistry.register("mymod:dungeon", "mymod:chests/dungeon_treasure");
 *   }
 *
 * Or create an isotope_links.json file in your mod's root:
 *   {
 *     "modId": "mymod",
 *     "links": [
 *       {
 *         "structure": "mymod:dungeon",
 *         "loot_tables": ["mymod:chests/dungeon_loot", "mymod:chests/dungeon_treasure"]
 *       }
 *     ]
 *   }
 */
public final class ModLinkRegistry {

    private static final ModLinkRegistry INSTANCE = new ModLinkRegistry();

    // Mod-declared links: structure ID -> loot table IDs
    private final Map<Identifier, Set<Identifier>> declaredLinks = new ConcurrentHashMap<>();

    // Track which mod declared each link (for debugging/logging)
    private final Map<Identifier, String> linkSources = new ConcurrentHashMap<>();

    // Statistics
    private int programmaticLinks = 0;
    private int jsonLinks = 0;

    private ModLinkRegistry() {}

    public static ModLinkRegistry getInstance() {
        return INSTANCE;
    }

    // --- Public API for mods ---

    /**
     * Register a structure-loot link.
     *
     * @param structureId The structure ID (e.g., "mymod:my_structure")
     * @param lootTableId The loot table ID (e.g., "mymod:chests/my_structure")
     */
    public static void register(String structureId, String lootTableId) {
        register(Identifier.parse(structureId), Identifier.parse(lootTableId));
    }

    /**
     * Register a structure-loot link.
     *
     * @param structureId The structure Identifier
     * @param lootTableId The loot table Identifier
     */
    public static void register(Identifier structureId, Identifier lootTableId) {
        getInstance().registerLink(structureId, lootTableId, "programmatic");
    }

    /**
     * Register multiple loot tables for a structure.
     *
     * @param structureId The structure ID
     * @param lootTableIds List of loot table IDs
     */
    public static void registerAll(String structureId, String... lootTableIds) {
        Identifier structure = Identifier.parse(structureId);
        for (String tableId : lootTableIds) {
            register(structure, Identifier.parse(tableId));
        }
    }

    /**
     * Register multiple loot tables for a structure.
     *
     * @param structureId The structure Identifier
     * @param lootTableIds Collection of loot table Identifiers
     */
    public static void registerAll(Identifier structureId, Collection<Identifier> lootTableIds) {
        for (Identifier tableId : lootTableIds) {
            register(structureId, tableId);
        }
    }

    /**
     * Check if a structure has any mod-declared links.
     */
    public static boolean hasLinks(Identifier structureId) {
        return getInstance().declaredLinks.containsKey(structureId);
    }

    /**
     * Get all mod-declared loot tables for a structure.
     */
    public static Set<Identifier> getLinks(Identifier structureId) {
        return getInstance().declaredLinks.getOrDefault(structureId, Set.of());
    }

    // --- Internal methods ---

    /**
     * Register a link with source tracking.
     */
    void registerLink(Identifier structureId, Identifier lootTableId, String source) {
        declaredLinks.computeIfAbsent(structureId, k -> ConcurrentHashMap.newKeySet()).add(lootTableId);

        // Track source (first one wins for logging purposes)
        String linkKey = structureId + "->" + lootTableId;
        linkSources.putIfAbsent(Identifier.parse(linkKey.replace("->", "_")), source);

        if ("programmatic".equals(source)) {
            programmaticLinks++;
        } else {
            jsonLinks++;
        }

        Isotope.LOGGER.debug("[ModLinkRegistry] Registered link: {} -> {} (source: {})",
            structureId, lootTableId, source);
    }

    /**
     * Register links from JSON metadata (called by ModLinkScanner and DatapackLootMetadataScanner).
     */
    public void registerFromJson(String modId, Identifier structureId, Collection<Identifier> lootTableIds) {
        for (Identifier tableId : lootTableIds) {
            registerLink(structureId, tableId, "json:" + modId);
        }
    }

    /**
     * Build a map of all declared links for use by StructureLootLinker.
     */
    public Map<Identifier, Set<Identifier>> buildDeclaredLinksMap() {
        Map<Identifier, Set<Identifier>> result = new HashMap<>();
        for (Map.Entry<Identifier, Set<Identifier>> entry : declaredLinks.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Get all structures that have mod-declared links.
     */
    public Set<Identifier> getDeclaredStructures() {
        return Collections.unmodifiableSet(declaredLinks.keySet());
    }

    /**
     * Get all mod-declared loot tables for a structure.
     */
    public Set<Identifier> getDeclaredLootTables(Identifier structureId) {
        return declaredLinks.getOrDefault(structureId, Set.of());
    }

    /**
     * Get statistics.
     */
    public Stats getStats() {
        int totalLinks = declaredLinks.values().stream().mapToInt(Set::size).sum();
        return new Stats(declaredLinks.size(), totalLinks, programmaticLinks, jsonLinks);
    }

    public record Stats(int structures, int totalLinks, int programmaticLinks, int jsonLinks) {}

    /**
     * Check if any links have been registered.
     */
    public boolean hasAnyLinks() {
        return !declaredLinks.isEmpty();
    }

    /**
     * Get total link count.
     */
    public int size() {
        return declaredLinks.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Clear all registered links (for testing/reset).
     */
    public void clear() {
        declaredLinks.clear();
        linkSources.clear();
        programmaticLinks = 0;
        jsonLinks = 0;
    }
}
