package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.data.StructureLootLink.Confidence;
import dev.isotope.observation.ObservationCorrelator;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Links structures to loot tables using multiple evidence sources.
 *
 * This is the core of ISOTOPE's "No Silent Failure" model.
 * Links are tagged with confidence levels so authors know what to trust.
 *
 * Evidence layers (in order of confidence):
 * 1. MANUAL - Author explicitly defined (highest)
 * 2. VERIFIED - Runtime observation confirmed
 * 3. TEMPLATE - Found in structure template .nbt file
 * 4. HIGH - Strong heuristic (vanilla mapping, exact path match)
 * 5. MEDIUM - Moderate heuristic (partial path match)
 * 6. LOW - Weak heuristic (namespace only)
 *
 * Key principle: Confidence only goes UP, never down.
 */
public final class StructureLootLinker {

    private static final StructureLootLinker INSTANCE = new StructureLootLinker();

    // Linked results
    private final Map<ResourceLocation, List<StructureLootLink>> linksByStructure = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<StructureLootLink>> linksByLootTable = new LinkedHashMap<>();

    // Author overrides (persisted)
    private final Set<StructureLootLink> authorAddedLinks = new LinkedHashSet<>();
    private final Set<AuthorRemoval> authorRemovedLinks = new LinkedHashSet<>();

    // Known vanilla mappings (manual, high confidence)
    private static final Map<String, List<String>> VANILLA_MAPPINGS = buildVanillaMappings();

    private StructureLootLinker() {}

    public static StructureLootLinker getInstance() {
        return INSTANCE;
    }

    /**
     * Run multi-layer linking between structures and loot tables.
     *
     * Layer order (lowest to highest confidence):
     * 1. Heuristics (path matching, namespace matching)
     * 2. Template parsing (deterministic .nbt analysis)
     * 3. Runtime observation (ground truth from gameplay)
     * 4. Author overrides (final authority)
     */
    public void link() {
        linksByStructure.clear();
        linksByLootTable.clear();

        StructureRegistry structures = StructureRegistry.getInstance();
        LootTableRegistry lootTables = LootTableRegistry.getInstance();

        if (!structures.isScanned() || !lootTables.isScanned()) {
            Isotope.LOGGER.warn("Cannot link: registries not scanned");
            return;
        }

        int linkCount = 0;
        int templatePromotions = 0;
        int runtimePromotions = 0;

        for (StructureInfo structure : structures.getAll()) {
            // Start with a map for efficient deduplication during building
            Map<ResourceLocation, StructureLootLink> linkMap = new LinkedHashMap<>();

            // Layer 1: Heuristics (lowest confidence)
            // 1a. Namespace heuristics (only for modded, weakest)
            if (!structure.isVanilla()) {
                for (StructureLootLink link : findNamespaceMatches(structure, lootTables)) {
                    addOrPromoteLink(linkMap, link);
                }
            }

            // 1b. Path-based heuristics
            for (StructureLootLink link : findPathMatches(structure, lootTables)) {
                addOrPromoteLink(linkMap, link);
            }

            // 1c. Vanilla mappings (high confidence heuristic)
            for (StructureLootLink link : findVanillaMappings(structure, lootTables)) {
                addOrPromoteLink(linkMap, link);
            }

            // Layer 2: Template parsing (deterministic)
            StructureTemplateParser templateParser = StructureTemplateParser.getInstance();
            if (templateParser.isParsed()) {
                Set<ResourceLocation> templateLootTables = templateParser.getLootTablesForStructure(structure.id());
                for (ResourceLocation tableId : templateLootTables) {
                    StructureLootLink templateLink = StructureLootLink.fromTemplate(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, templateLink)) {
                        templatePromotions++;
                    }
                }
            }

            // Layer 3: Runtime observation (ground truth)
            ObservationCorrelator correlator = ObservationCorrelator.getInstance();
            Set<ResourceLocation> observedTables = correlator.getLootTablesFor(structure.id());
            for (ResourceLocation tableId : observedTables) {
                StructureLootLink verifiedLink = StructureLootLink.verified(structure.id(), tableId);
                if (addOrPromoteLink(linkMap, verifiedLink)) {
                    runtimePromotions++;
                }
            }

            // Layer 4: Author overrides (final authority)
            List<StructureLootLink> links = new ArrayList<>(linkMap.values());
            links = applyAuthorOverrides(structure.id(), links);

            // Final deduplication (should be minimal at this point)
            links = deduplicateLinks(links);

            if (!links.isEmpty()) {
                linksByStructure.put(structure.id(), links);
                for (StructureLootLink link : links) {
                    linksByLootTable.computeIfAbsent(link.lootTableId(), k -> new ArrayList<>()).add(link);
                }
                linkCount += links.size();
            }
        }

        Isotope.LOGGER.info("StructureLootLinker: created {} links for {} structures " +
            "(template promotions: {}, runtime promotions: {})",
            linkCount, linksByStructure.size(), templatePromotions, runtimePromotions);
    }

    /**
     * Add a link or promote existing link if new confidence is higher.
     * Returns true if this was a promotion (new > existing).
     */
    private boolean addOrPromoteLink(Map<ResourceLocation, StructureLootLink> linkMap, StructureLootLink newLink) {
        StructureLootLink existing = linkMap.get(newLink.lootTableId());
        if (existing == null) {
            linkMap.put(newLink.lootTableId(), newLink);
            return false;
        }

        // Only promote, never downgrade
        if (newLink.confidence().getScore() > existing.confidence().getScore()) {
            linkMap.put(newLink.lootTableId(), newLink);
            return true;
        }

        return false;
    }

    /**
     * Find links using known vanilla structure-loot mappings.
     */
    private List<StructureLootLink> findVanillaMappings(StructureInfo structure, LootTableRegistry lootTables) {
        List<StructureLootLink> links = new ArrayList<>();

        List<String> mappedTables = VANILLA_MAPPINGS.get(structure.path());
        if (mappedTables != null) {
            for (String tablePath : mappedTables) {
                ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath("minecraft", tablePath);
                if (lootTables.get(tableId).isPresent()) {
                    links.add(StructureLootLink.heuristic(structure.id(), tableId, Confidence.HIGH));
                }
            }
        }

        return links;
    }

    /**
     * Find links using path-based heuristics.
     */
    private List<StructureLootLink> findPathMatches(StructureInfo structure, LootTableRegistry lootTables) {
        List<StructureLootLink> links = new ArrayList<>();
        String structurePath = structure.path().toLowerCase();

        // Extract structure "base name" (e.g., "village_plains" -> "village")
        String baseName = extractBaseName(structurePath);

        for (LootTableInfo lootTable : lootTables.getChestLootTables()) {
            // Only match same namespace or vanilla-to-vanilla
            if (!structure.namespace().equals(lootTable.namespace())) {
                continue;
            }

            String tablePath = lootTable.path().toLowerCase();
            // Remove "chests/" or "chest/" prefix for matching (vanilla vs modded conventions)
            String tablePathClean;
            if (tablePath.startsWith("chests/")) {
                tablePathClean = tablePath.substring(7);
            } else if (tablePath.startsWith("chest/")) {
                tablePathClean = tablePath.substring(6);
            } else {
                tablePathClean = tablePath;
            }

            Confidence confidence = calculatePathConfidence(structurePath, baseName, tablePathClean);
            if (confidence != null) {
                links.add(StructureLootLink.heuristic(structure.id(), lootTable.id(), confidence));
            }
        }

        return links;
    }

    /**
     * Calculate confidence based on path matching.
     */
    private Confidence calculatePathConfidence(String structurePath, String baseName, String tablePath) {
        // Exact match: village_plains -> village_plains
        if (tablePath.equals(structurePath) || tablePath.startsWith(structurePath + "_")) {
            return Confidence.HIGH;
        }

        // Base name match: village_plains -> village_*
        if (tablePath.startsWith(baseName + "_") || tablePath.equals(baseName)) {
            return Confidence.MEDIUM;
        }

        // Contains structure name: ancient_city -> ancient_city_ice_box
        if (tablePath.contains(structurePath) || structurePath.contains(tablePath.replace("_", ""))) {
            return Confidence.MEDIUM;
        }

        return null;
    }

    /**
     * Find links based on namespace matching (weak heuristic for modded content).
     */
    private List<StructureLootLink> findNamespaceMatches(StructureInfo structure, LootTableRegistry lootTables) {
        List<StructureLootLink> links = new ArrayList<>();

        // For modded structures, link to chest loot tables from the same mod
        for (LootTableInfo lootTable : lootTables.getByNamespace(structure.namespace())) {
            if (lootTable.category() == LootTableInfo.LootTableCategory.CHEST) {
                links.add(StructureLootLink.heuristic(structure.id(), lootTable.id(), Confidence.LOW));
            }
        }

        return links;
    }

    /**
     * Apply author overrides (additions and removals).
     */
    private List<StructureLootLink> applyAuthorOverrides(ResourceLocation structureId, List<StructureLootLink> links) {
        List<StructureLootLink> result = new ArrayList<>();

        // Filter out author-removed links
        Set<ResourceLocation> removed = authorRemovedLinks.stream()
            .filter(r -> r.structureId.equals(structureId))
            .map(r -> r.lootTableId)
            .collect(Collectors.toSet());

        for (StructureLootLink link : links) {
            if (!removed.contains(link.lootTableId())) {
                result.add(link);
            }
        }

        // Add author-added links
        for (StructureLootLink added : authorAddedLinks) {
            if (added.structureId().equals(structureId)) {
                result.add(added);
            }
        }

        return result;
    }

    /**
     * Deduplicate links, keeping highest confidence for each loot table.
     */
    private List<StructureLootLink> deduplicateLinks(List<StructureLootLink> links) {
        Map<ResourceLocation, StructureLootLink> best = new LinkedHashMap<>();
        for (StructureLootLink link : links) {
            StructureLootLink existing = best.get(link.lootTableId());
            if (existing == null || link.confidence().getScore() > existing.confidence().getScore()) {
                best.put(link.lootTableId(), link);
            }
        }
        return new ArrayList<>(best.values());
    }

    /**
     * Extract base name from structure path.
     * "village_plains" -> "village"
     * "ancient_city" -> "ancient_city"
     */
    private String extractBaseName(String path) {
        // Common suffixes to strip
        String[] suffixes = {"_plains", "_desert", "_savanna", "_snowy", "_taiga",
            "_small", "_large", "_big", "_medium"};
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length());
            }
        }
        return path;
    }

    // --- Author Override API ---

    /**
     * Add a manual link (author override).
     */
    public void addManualLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        authorAddedLinks.add(StructureLootLink.manual(structureId, lootTableId));
        // Re-run linking to apply
        link();
        Isotope.LOGGER.info("Author added link: {} -> {}", structureId, lootTableId);
    }

    /**
     * Remove a link (author override).
     */
    public void removeLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        // Remove from added if it was manually added
        authorAddedLinks.removeIf(l ->
            l.structureId().equals(structureId) && l.lootTableId().equals(lootTableId));
        // Mark as removed
        authorRemovedLinks.add(new AuthorRemoval(structureId, lootTableId));
        // Re-run linking to apply
        link();
        Isotope.LOGGER.info("Author removed link: {} -> {}", structureId, lootTableId);
    }

    /**
     * Restore a previously removed link.
     */
    public void restoreLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        authorRemovedLinks.removeIf(r ->
            r.structureId.equals(structureId) && r.lootTableId.equals(lootTableId));
        link();
        Isotope.LOGGER.info("Author restored link: {} -> {}", structureId, lootTableId);
    }

    // --- Query API ---

    /**
     * Get all links for a structure.
     */
    public List<StructureLootLink> getLinksForStructure(ResourceLocation structureId) {
        return linksByStructure.getOrDefault(structureId, Collections.emptyList());
    }

    /**
     * Get all links for a loot table.
     */
    public List<StructureLootLink> getLinksForLootTable(ResourceLocation lootTableId) {
        return linksByLootTable.getOrDefault(lootTableId, Collections.emptyList());
    }

    /**
     * Get structures that have at least one link.
     */
    public Set<ResourceLocation> getLinkedStructures() {
        return Collections.unmodifiableSet(linksByStructure.keySet());
    }

    /**
     * Get loot tables that have at least one link.
     */
    public Set<ResourceLocation> getLinkedLootTables() {
        return Collections.unmodifiableSet(linksByLootTable.keySet());
    }

    /**
     * Check if a structure has any links.
     */
    public boolean hasLinks(ResourceLocation structureId) {
        return linksByStructure.containsKey(structureId);
    }

    /**
     * Total link count.
     */
    public int getLinkCount() {
        return linksByStructure.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Reset all data.
     */
    public void reset() {
        linksByStructure.clear();
        linksByLootTable.clear();
        authorAddedLinks.clear();
        authorRemovedLinks.clear();
    }

    /**
     * Get all links (for saving).
     */
    public List<StructureLootLink> getAllLinks() {
        return linksByStructure.values().stream()
            .flatMap(List::stream)
            .toList();
    }

    /**
     * Add a link directly (for loading from save).
     * Does not trigger re-linking.
     */
    public void addLink(StructureLootLink link) {
        linksByStructure.computeIfAbsent(link.structureId(), k -> new ArrayList<>()).add(link);
        linksByLootTable.computeIfAbsent(link.lootTableId(), k -> new ArrayList<>()).add(link);
    }

    // --- Vanilla Mappings ---

    private static Map<String, List<String>> buildVanillaMappings() {
        Map<String, List<String>> m = new HashMap<>();

        // Villages
        m.put("village_plains", List.of(
            "chests/village/village_weaponsmith", "chests/village/village_toolsmith",
            "chests/village/village_armorer", "chests/village/village_cartographer",
            "chests/village/village_mason", "chests/village/village_shepherd",
            "chests/village/village_butcher", "chests/village/village_tannery",
            "chests/village/village_temple", "chests/village/village_plains_house",
            "chests/village/village_fisher"
        ));
        m.put("village_desert", m.get("village_plains"));
        m.put("village_savanna", m.get("village_plains"));
        m.put("village_snowy", m.get("village_plains"));
        m.put("village_taiga", m.get("village_plains"));

        // Stronghold
        m.put("stronghold", List.of(
            "chests/stronghold_corridor", "chests/stronghold_crossing",
            "chests/stronghold_library"
        ));

        // Mineshaft
        m.put("mineshaft", List.of("chests/abandoned_mineshaft"));
        m.put("mineshaft_mesa", List.of("chests/abandoned_mineshaft"));

        // Ocean structures
        m.put("shipwreck", List.of(
            "chests/shipwreck_map", "chests/shipwreck_supply", "chests/shipwreck_treasure"
        ));
        m.put("shipwreck_beached", m.get("shipwreck"));
        m.put("buried_treasure", List.of("chests/buried_treasure"));
        m.put("ocean_ruin_cold", List.of("chests/underwater_ruin_small", "chests/underwater_ruin_big"));
        m.put("ocean_ruin_warm", m.get("ocean_ruin_cold"));

        // Nether
        m.put("bastion_remnant", List.of(
            "chests/bastion_treasure", "chests/bastion_other",
            "chests/bastion_hoglin_stable", "chests/bastion_bridge"
        ));
        m.put("fortress", List.of("chests/nether_bridge"));
        m.put("ruined_portal", List.of("chests/ruined_portal"));
        m.put("ruined_portal_desert", m.get("ruined_portal"));
        m.put("ruined_portal_jungle", m.get("ruined_portal"));
        m.put("ruined_portal_mountain", m.get("ruined_portal"));
        m.put("ruined_portal_nether", m.get("ruined_portal"));
        m.put("ruined_portal_ocean", m.get("ruined_portal"));
        m.put("ruined_portal_swamp", m.get("ruined_portal"));

        // End
        m.put("end_city", List.of("chests/end_city_treasure"));

        // Overworld dungeons
        m.put("monster_room", List.of("chests/simple_dungeon")); // Spawner dungeon
        m.put("desert_pyramid", List.of("chests/desert_pyramid"));
        m.put("jungle_pyramid", List.of("chests/jungle_temple"));
        m.put("igloo", List.of("chests/igloo_chest"));
        m.put("pillager_outpost", List.of("chests/pillager_outpost"));
        m.put("woodland_mansion", List.of("chests/woodland_mansion"));
        m.put("ancient_city", List.of("chests/ancient_city", "chests/ancient_city_ice_box"));

        // Trial chambers
        m.put("trial_chambers", List.of(
            "chests/trial_chambers/reward", "chests/trial_chambers/reward_common",
            "chests/trial_chambers/reward_rare", "chests/trial_chambers/reward_unique",
            "chests/trial_chambers/supply", "chests/trial_chambers/corridor",
            "chests/trial_chambers/entrance", "chests/trial_chambers/intersection",
            "chests/trial_chambers/intersection_barrel"
        ));

        return m;
    }

    // --- Inner classes ---

    private record AuthorRemoval(ResourceLocation structureId, ResourceLocation lootTableId) {}
}
