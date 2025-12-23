package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Links structures to their associated loot tables using path-based heuristics.
 */
public final class StructureLootLinker {
    private static final StructureLootLinker INSTANCE = new StructureLootLinker();

    private final Map<ResourceLocation, StructureLootLink> linkCache = new LinkedHashMap<>();
    private boolean linked = false;

    private static final Map<String, Set<String>> MANUAL_MAPPINGS = createManualMappings();

    private static Map<String, Set<String>> createManualMappings() {
        Map<String, Set<String>> map = new HashMap<>();

        map.put("stronghold", Set.of(
            "stronghold_corridor", "stronghold_crossing", "stronghold_library"
        ));

        map.put("fortress", Set.of("nether_bridge"));

        map.put("monument", Set.of());

        map.put("mineshaft", Set.of("abandoned_mineshaft"));

        map.put("desert_pyramid", Set.of("desert_pyramid"));

        map.put("jungle_pyramid", Set.of("jungle_temple"));

        map.put("igloo", Set.of("igloo_chest"));

        map.put("mansion", Set.of("woodland_mansion"));

        map.put("bastion_remnant", Set.of(
            "bastion_bridge", "bastion_hoglin_stable", "bastion_other", "bastion_treasure"
        ));

        map.put("ruined_portal", Set.of("ruined_portal"));

        map.put("ancient_city", Set.of("ancient_city", "ancient_city_ice_box"));

        map.put("trial_chambers", Set.of(
            "trial_chambers/corridor/cache",
            "trial_chambers/corridor/dispenser",
            "trial_chambers/corridor/entrance",
            "trial_chambers/corridor/supply",
            "trial_chambers/intersection/barrel",
            "trial_chambers/reward/common",
            "trial_chambers/reward/ominous_common",
            "trial_chambers/reward/ominous_rare",
            "trial_chambers/reward/ominous_unique",
            "trial_chambers/reward/rare",
            "trial_chambers/reward/unique",
            "trial_chambers/spawner/contents/trial_chamber"
        ));

        map.put("pillager_outpost", Set.of("pillager_outpost"));

        map.put("village", Set.of(
            "village/village_armorer",
            "village/village_butcher",
            "village/village_cartographer",
            "village/village_desert_house",
            "village/village_fisher",
            "village/village_fletcher",
            "village/village_mason",
            "village/village_plains_house",
            "village/village_savanna_house",
            "village/village_shepherd",
            "village/village_snowy_house",
            "village/village_taiga_house",
            "village/village_tannery",
            "village/village_temple",
            "village/village_toolsmith",
            "village/village_weaponsmith"
        ));

        map.put("shipwreck", Set.of(
            "shipwreck_map", "shipwreck_supply", "shipwreck_treasure"
        ));

        map.put("buried_treasure", Set.of("buried_treasure"));

        map.put("ocean_ruin", Set.of("underwater_ruin_big", "underwater_ruin_small"));

        map.put("end_city", Set.of("end_city_treasure"));

        map.put("trail_ruins", Set.of());

        return Collections.unmodifiableMap(map);
    }

    private StructureLootLinker() {}

    public static StructureLootLinker getInstance() {
        return INSTANCE;
    }

    public void buildLinks() {
        if (linked) return;

        linkCache.clear();
        long start = System.currentTimeMillis();

        Collection<StructureInfo> structures = StructureRegistry.getInstance().getAll();
        Collection<LootTableInfo> chestTables = LootTableRegistry.getInstance()
            .getByCategory(LootTableInfo.LootTableCategory.CHEST);

        Map<String, Set<ResourceLocation>> tablesByKeyword = buildTableIndex(chestTables);

        for (StructureInfo structure : structures) {
            StructureLootLink link = findLinks(structure, tablesByKeyword);
            linkCache.put(structure.id(), link);
        }

        linked = true;
        long elapsed = System.currentTimeMillis() - start;

        int linkedCount = (int) linkCache.values().stream().filter(StructureLootLink::hasLinks).count();
        Isotope.LOGGER.info("Built structure-loot links in {}ms: {}/{} structures linked",
            elapsed, linkedCount, structures.size());
    }

    private Map<String, Set<ResourceLocation>> buildTableIndex(Collection<LootTableInfo> tables) {
        Map<String, Set<ResourceLocation>> index = new HashMap<>();

        for (LootTableInfo table : tables) {
            String path = table.path().replace("chests/", "");
            String[] parts = path.split("[/_]");

            for (String part : parts) {
                if (part.length() > 2) {
                    index.computeIfAbsent(part.toLowerCase(), k -> new HashSet<>())
                         .add(table.id());
                }
            }

            index.computeIfAbsent(path.toLowerCase(), k -> new HashSet<>())
                 .add(table.id());
        }

        return index;
    }

    private StructureLootLink findLinks(StructureInfo structure,
                                        Map<String, Set<ResourceLocation>> tableIndex) {
        Set<ResourceLocation> matches = new HashSet<>();
        StructureLootLink.LinkMethod method = StructureLootLink.LinkMethod.NONE;
        float confidence = 0.0f;

        String structurePath = structure.path().toLowerCase();

        for (Map.Entry<String, Set<String>> mapping : MANUAL_MAPPINGS.entrySet()) {
            if (structurePath.contains(mapping.getKey())) {
                for (String tableSuffix : mapping.getValue()) {
                    ResourceLocation tableId = ResourceLocation.withDefaultNamespace("chests/" + tableSuffix);
                    if (LootTableRegistry.getInstance().get(tableId).isPresent()) {
                        matches.add(tableId);
                    }
                }
                if (!matches.isEmpty()) {
                    return StructureLootLink.manual(structure, matches);
                }
            }
        }

        Set<ResourceLocation> exactMatches = tableIndex.get(structurePath);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            return StructureLootLink.exact(structure, new HashSet<>(exactMatches));
        }

        for (String part : structurePath.split("_")) {
            if (part.length() > 3) {
                Set<ResourceLocation> partialMatches = tableIndex.get(part);
                if (partialMatches != null) {
                    matches.addAll(partialMatches);
                }
            }
        }

        if (!matches.isEmpty()) {
            confidence = Math.min(0.7f, 0.3f + (0.1f * matches.size()));
            return StructureLootLink.heuristic(structure, matches, confidence);
        }

        addArchaeologyTables(structure, matches);

        if (!matches.isEmpty()) {
            return StructureLootLink.heuristic(structure, matches, 0.5f);
        }

        return StructureLootLink.none(structure);
    }

    private void addArchaeologyTables(StructureInfo structure, Set<ResourceLocation> matches) {
        String path = structure.path().toLowerCase();

        for (LootTableInfo table : LootTableRegistry.getInstance()
                .getByCategory(LootTableInfo.LootTableCategory.ARCHAEOLOGY)) {
            String tablePath = table.path().toLowerCase();

            if (path.contains("trail_ruins") && tablePath.contains("trail_ruins")) {
                matches.add(table.id());
            } else if (path.contains("desert_pyramid") && tablePath.contains("desert_pyramid")) {
                matches.add(table.id());
            } else if (path.contains("ocean_ruin") && tablePath.contains("ocean_ruin")) {
                matches.add(table.id());
            } else if (path.contains("desert_well") && tablePath.contains("desert_well")) {
                matches.add(table.id());
            }
        }
    }

    public Optional<StructureLootLink> getLink(ResourceLocation structureId) {
        if (!linked) buildLinks();
        return Optional.ofNullable(linkCache.get(structureId));
    }

    public Collection<StructureLootLink> getAllLinks() {
        if (!linked) buildLinks();
        return Collections.unmodifiableCollection(linkCache.values());
    }

    public Collection<StructureLootLink> getLinkedOnly() {
        if (!linked) buildLinks();
        return linkCache.values().stream()
            .filter(StructureLootLink::hasLinks)
            .toList();
    }

    public List<StructureInfo> findStructuresWithTable(ResourceLocation tableId) {
        if (!linked) buildLinks();
        return linkCache.values().stream()
            .filter(link -> link.linkedTables().contains(tableId))
            .map(StructureLootLink::structure)
            .toList();
    }

    public void reset() {
        linkCache.clear();
        linked = false;
    }

    public boolean isLinked() {
        return linked;
    }

    public int linkedStructureCount() {
        if (!linked) return 0;
        return (int) linkCache.values().stream().filter(StructureLootLink::hasLinks).count();
    }
}
