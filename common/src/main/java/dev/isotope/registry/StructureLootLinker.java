package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.analysis.LootTableContentAnalyzer;
import dev.isotope.api.ModLinkRegistry;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.data.StructureLootLink.Confidence;
import dev.isotope.linking.ConfirmationScorer;
import dev.isotope.linking.LearnedLinksManager;
import dev.isotope.linking.NamespaceValidator;
import dev.isotope.linking.UserCorrectionManager;
import dev.isotope.observation.LootObserver;
import dev.isotope.observation.ObservationCorrelator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

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
    private final Map<Identifier, List<StructureLootLink>> linksByStructure = new LinkedHashMap<>();
    private final Map<Identifier, List<StructureLootLink>> linksByLootTable = new LinkedHashMap<>();

    // Author overrides (persisted)
    private final Set<StructureLootLink> authorAddedLinks = new LinkedHashSet<>();
    private final Set<AuthorRemoval> authorRemovedLinks = new LinkedHashSet<>();

    // Known vanilla mappings (manual, high confidence)
    private static final Map<String, List<String>> VANILLA_MAPPINGS = buildVanillaMappings();

    private StructureLootLinker() {}

    public static StructureLootLinker getInstance() {
        return INSTANCE;
    }

    // Server reference for content analysis (set during link())
    private MinecraftServer currentServer;

    /**
     * Run multi-layer linking between structures and loot tables.
     *
     * Layer order (lowest to highest confidence):
     * 1. Heuristics (path matching, namespace matching)
     * 1.5. Content analysis (signature items hinting at structure)
     * 2. Template parsing (deterministic .nbt analysis)
     * 2.3. Learned links (pre-populated from past sessions)
     * 2.5. Runtime assignment capture (setLootTable() hook with caller context)
     * 3. Runtime observation (ground truth from spatial correlation)
     * 3.5. Confirmation scoring (multi-source corroboration)
     * 4. Namespace validation (cross-namespace checks)
     * 5. Author overrides (final authority)
     */
    public void link(MinecraftServer server) {
        this.currentServer = server;
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
        int spawnerEntityPromotions = 0;
        int contentAnalysisPromotions = 0;
        int poolPromotions = 0;
        int processorPromotions = 0;
        int structureConfigPromotions = 0;
        int modDeclaredPromotions = 0;
        int learnedPromotions = 0;
        int assignmentPromotions = 0;
        int runtimePromotions = 0;
        int confirmationPromotions = 0;

        // Pre-compute all source maps for confirmation scoring
        Map<Identifier, Set<Identifier>> assignmentLinks =
            LootObserver.getInstance().buildAssignmentLinks();
        Map<Identifier, Set<Identifier>> learnedLinks =
            LearnedLinksManager.getInstance().buildLearnedLinksMap();
        Map<Identifier, Set<Identifier>> modDeclaredLinks =
            ModLinkRegistry.getInstance().buildDeclaredLinksMap();
        Map<Identifier, Set<Identifier>> templateLinkMap = new HashMap<>();
        Map<Identifier, Set<Identifier>> observationLinkMap = new HashMap<>();
        Map<Identifier, Set<Identifier>> worldgenLinks =
            WorldgenFeatureParser.getInstance().buildFeatureLootMap();
        Map<Identifier, Set<Identifier>> contentAnalysisLinks = new HashMap<>();

        // Pre-compute content analysis hints (signature items hinting at structures)
        // This analyzes loot tables for unique items that indicate specific structures
        if (server != null) {
            Set<Identifier> allTables = lootTables.getAll().stream()
                .map(t -> t.id())
                .collect(Collectors.toSet());
            Map<Identifier, LootTableContentAnalyzer.StructureHintResult> hints =
                LootTableContentAnalyzer.analyzeAllForStructureHints(server, allTables);

            // Build structure -> loot tables map from hints
            for (Map.Entry<Identifier, LootTableContentAnalyzer.StructureHintResult> entry : hints.entrySet()) {
                Identifier tableId = entry.getKey();
                for (LootTableContentAnalyzer.StructureHint hint : entry.getValue().hints()) {
                    if (hint.confidence() >= 50) { // Only include hints with at least MEDIUM confidence
                        contentAnalysisLinks.computeIfAbsent(hint.structureId(), k -> new HashSet<>()).add(tableId);
                    }
                }
            }
        }

        for (StructureInfo structure : structures.getAll()) {
            // Start with a map for efficient deduplication during building
            Map<Identifier, StructureLootLink> linkMap = new LinkedHashMap<>();

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

            // Layer 1.5: Content analysis (signature items hinting at structure)
            // Uses unique items in loot tables to infer structure origin
            Set<Identifier> contentHintTables = contentAnalysisLinks.get(structure.id());
            if (contentHintTables != null) {
                for (Identifier tableId : contentHintTables) {
                    // Find the hint confidence for this specific structure-table pair
                    LootTableContentAnalyzer.StructureHintResult hintResult =
                        LootTableContentAnalyzer.getCachedHints().get(tableId);
                    if (hintResult != null) {
                        for (LootTableContentAnalyzer.StructureHint hint : hintResult.hints()) {
                            if (hint.structureId().equals(structure.id())) {
                                StructureLootLink contentLink = StructureLootLink.contentAnalysis(
                                    structure.id(), tableId, hint.confidence());
                                if (addOrPromoteLink(linkMap, contentLink)) {
                                    contentAnalysisPromotions++;
                                }
                                break;
                            }
                        }
                    }
                }
            }

            // Layer 2: Template parsing (deterministic)
            StructureTemplateParser templateParser = StructureTemplateParser.getInstance();
            if (templateParser.isParsed()) {
                Set<Identifier> templateLootTables = templateParser.getLootTablesForStructure(structure.id());
                // Store for confirmation scoring
                if (!templateLootTables.isEmpty()) {
                    templateLinkMap.put(structure.id(), new HashSet<>(templateLootTables));
                }
                for (Identifier tableId : templateLootTables) {
                    StructureLootLink templateLink = StructureLootLink.fromTemplate(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, templateLink)) {
                        templatePromotions++;
                    }
                }

                // Layer 2.1: Spawner entity loot tables (from spawner blocks in templates)
                // When a structure contains a spawner, the spawned entity's loot table is linked
                Set<Identifier> spawnerLootTables = templateParser.getSpawnerLootTablesForStructure(structure.id());
                for (Identifier tableId : spawnerLootTables) {
                    StructureLootLink spawnerLink = StructureLootLink.spawnerEntity(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, spawnerLink)) {
                        spawnerEntityPromotions++;
                    }
                }
                // Add spawner loot to template link map for confirmation scoring
                if (!spawnerLootTables.isEmpty()) {
                    templateLinkMap.computeIfAbsent(structure.id(), k -> new HashSet<>()).addAll(spawnerLootTables);
                }
            }

            // Layer 2.2: Template pool parsing (jigsaw structure loot from JSON)
            // Try to find template pools that match this structure's name pattern
            TemplatePoolParser poolParser = TemplatePoolParser.getInstance();
            Set<Identifier> matchingPools = new HashSet<>();
            if (poolParser.isParsed()) {
                Set<Identifier> poolLootTables = findPoolLootForStructure(structure, poolParser, matchingPools);
                // Add pool loot to template link map for confirmation scoring
                if (!poolLootTables.isEmpty()) {
                    templateLinkMap.computeIfAbsent(structure.id(), k -> new HashSet<>()).addAll(poolLootTables);
                }
                for (Identifier tableId : poolLootTables) {
                    // Pool-derived links get TEMPLATE confidence (same as NBT parsing)
                    StructureLootLink poolLink = StructureLootLink.fromTemplate(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, poolLink)) {
                        poolPromotions++;
                    }
                }
            }

            // Layer 2.25: Processor list parsing (loot tables set by processors)
            // Check processors used by the pools that matched this structure
            ProcessorListParser processorParser = ProcessorListParser.getInstance();
            if (processorParser.isParsed() && !matchingPools.isEmpty()) {
                Set<Identifier> processorLootTables = findProcessorLootForPools(matchingPools, poolParser, processorParser);
                // Add processor loot to template link map for confirmation scoring
                if (!processorLootTables.isEmpty()) {
                    templateLinkMap.computeIfAbsent(structure.id(), k -> new HashSet<>()).addAll(processorLootTables);
                }
                for (Identifier tableId : processorLootTables) {
                    // Processor-derived links get TEMPLATE confidence
                    StructureLootLink processorLink = StructureLootLink.fromTemplate(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, processorLink)) {
                        processorPromotions++;
                    }
                }
            }

            // Layer 2.27: Structure config parsing (structure type definitions and start_pool refs)
            // This provides high-confidence links from structure JSON definitions
            StructureConfigParser configParser = StructureConfigParser.getInstance();
            if (configParser.isParsed()) {
                Set<Identifier> configLootTables = configParser.getLootTablesForStructure(structure.id());
                // Add config loot to template link map for confirmation scoring
                if (!configLootTables.isEmpty()) {
                    templateLinkMap.computeIfAbsent(structure.id(), k -> new HashSet<>()).addAll(configLootTables);
                }
                for (Identifier tableId : configLootTables) {
                    // Structure config links get TEMPLATE confidence (deterministic from JSON)
                    StructureLootLink configLink = StructureLootLink.fromTemplate(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, configLink)) {
                        structureConfigPromotions++;
                    }
                }
            }

            // Layer 2.28: Mod-declared links (from ModLinkRegistry API or isotope_links.json)
            // These have very high confidence (95) since mod authors explicitly declared them
            Set<Identifier> modDeclaredTables = modDeclaredLinks.get(structure.id());
            if (modDeclaredTables != null) {
                for (Identifier tableId : modDeclaredTables) {
                    StructureLootLink modLink = StructureLootLink.modDeclared(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, modLink)) {
                        modDeclaredPromotions++;
                    }
                }
            }

            // Layer 2.3: Learned links (pre-populated from past sessions)
            Set<Identifier> learnedTables = learnedLinks.get(structure.id());
            if (learnedTables != null) {
                for (Identifier tableId : learnedTables) {
                    StructureLootLink learnedLink = StructureLootLink.learned(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, learnedLink)) {
                        learnedPromotions++;
                    }
                }
            }

            // Layer 2.5: Runtime assignment capture (setLootTable() hook)
            // This captures direct causation from the call stack
            Set<Identifier> assignedTables = assignmentLinks.get(structure.id());
            if (assignedTables != null) {
                for (Identifier tableId : assignedTables) {
                    StructureLootLink assignedLink = StructureLootLink.runtimeAssigned(structure.id(), tableId);
                    if (addOrPromoteLink(linkMap, assignedLink)) {
                        assignmentPromotions++;
                    }
                }
            }

            // Layer 3: Runtime observation (ground truth from spatial correlation)
            ObservationCorrelator correlator = ObservationCorrelator.getInstance();
            Set<Identifier> observedTables = correlator.getLootTablesFor(structure.id());
            // Store for confirmation scoring
            if (!observedTables.isEmpty()) {
                observationLinkMap.put(structure.id(), new HashSet<>(observedTables));
            }
            for (Identifier tableId : observedTables) {
                StructureLootLink verifiedLink = StructureLootLink.verified(structure.id(), tableId);
                if (addOrPromoteLink(linkMap, verifiedLink)) {
                    runtimePromotions++;
                }
            }

            // Layer 3.5: Confirmation scoring (multi-source corroboration)
            List<StructureLootLink> links = new ArrayList<>(linkMap.values());
            int beforeConfirm = links.size();
            links = ConfirmationScorer.getInstance().applyScoring(
                links, templateLinkMap, worldgenLinks, assignmentLinks, observationLinkMap, learnedLinks
            );
            confirmationPromotions += countConfirmedLinks(links);

            // Layer 4: Namespace validation (cross-namespace checks)
            links = NamespaceValidator.getInstance().filterAndWarn(links);

            // Layer 5: Author overrides (final authority)
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

        // Also check assignment links for structures we don't know about yet
        // This helps discover modded structures that aren't in the structure registry
        for (Map.Entry<Identifier, Set<Identifier>> entry : assignmentLinks.entrySet()) {
            Identifier structureId = entry.getKey();
            if (!linksByStructure.containsKey(structureId)) {
                // This is a new structure discovered via assignment capture
                List<StructureLootLink> newLinks = new ArrayList<>();
                for (Identifier tableId : entry.getValue()) {
                    newLinks.add(StructureLootLink.runtimeAssigned(structureId, tableId));
                }
                if (!newLinks.isEmpty()) {
                    linksByStructure.put(structureId, newLinks);
                    for (StructureLootLink link : newLinks) {
                        linksByLootTable.computeIfAbsent(link.lootTableId(), k -> new ArrayList<>()).add(link);
                    }
                    linkCount += newLinks.size();
                    assignmentPromotions += newLinks.size();
                }
            }
        }

        // Feature linking (features like dungeons that have loot but aren't real structures)
        int featureLinks = 0;
        FeatureRegistry features = FeatureRegistry.getInstance();
        if (features.isInitialized()) {
            for (FeatureRegistry.FeatureDefinition feature : features.getAll()) {
                List<StructureLootLink> featureLinkList = new ArrayList<>();

                for (Identifier tableId : feature.lootTables()) {
                    if (lootTables.get(tableId).isPresent()) {
                        StructureLootLink link = StructureLootLink.feature(feature.id(), tableId);
                        featureLinkList.add(link);
                    }
                }

                if (!featureLinkList.isEmpty()) {
                    linksByStructure.put(feature.id(), featureLinkList);
                    for (StructureLootLink link : featureLinkList) {
                        linksByLootTable.computeIfAbsent(link.lootTableId(), k -> new ArrayList<>()).add(link);
                    }
                    featureLinks += featureLinkList.size();
                }
            }
        }

        Isotope.LOGGER.info("StructureLootLinker: created {} links for {} structures, {} feature links " +
            "(template: {}, spawner: {}, content: {}, pool: {}, processor: {}, config: {}, mod: {}, learned: {}, assignment: {}, runtime: {}, confirmed: {})",
            linkCount, linksByStructure.size(), featureLinks,
            templatePromotions, spawnerEntityPromotions, contentAnalysisPromotions, poolPromotions, processorPromotions, structureConfigPromotions,
            modDeclaredPromotions, learnedPromotions, assignmentPromotions, runtimePromotions, confirmationPromotions);

        // Record verified links to LearnedLinksManager for future sessions
        recordVerifiedLinksToLearned();
    }

    /**
     * Count links with CONFIRMED confidence.
     */
    private int countConfirmedLinks(List<StructureLootLink> links) {
        int count = 0;
        for (StructureLootLink link : links) {
            if (link.isConfirmed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Record all verified links to LearnedLinksManager for future sessions.
     */
    private void recordVerifiedLinksToLearned() {
        List<StructureLootLink> verifiedLinks = new ArrayList<>();

        for (List<StructureLootLink> links : linksByStructure.values()) {
            for (StructureLootLink link : links) {
                if (link.isVerified() || link.isConfirmed()) {
                    verifiedLinks.add(link);
                }
            }
        }

        if (!verifiedLinks.isEmpty()) {
            LearnedLinksManager.getInstance().recordVerifiedLinks(verifiedLinks);
            Isotope.LOGGER.debug("[StructureLootLinker] Recorded {} verified links to learned history",
                verifiedLinks.size());
        }
    }

    /**
     * Add a link or promote existing link if new confidence is higher.
     * Returns true if this was a promotion (new > existing).
     */
    private boolean addOrPromoteLink(Map<Identifier, StructureLootLink> linkMap, StructureLootLink newLink) {
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
                Identifier tableId = Identifier.fromNamespaceAndPath("minecraft", tablePath);
                if (lootTables.get(tableId).isPresent()) {
                    links.add(StructureLootLink.heuristic(structure.id(), tableId, Confidence.HIGH));
                }
            }
        }

        return links;
    }

    /**
     * Find links using path-based heuristics.
     * Checks CHEST, DISPENSER, SPAWNER, POTS, and ARCHAEOLOGY categories.
     */
    private List<StructureLootLink> findPathMatches(StructureInfo structure, LootTableRegistry lootTables) {
        List<StructureLootLink> links = new ArrayList<>();
        String structurePath = structure.path().toLowerCase();

        // Extract structure "base name" (e.g., "village_plains" -> "village")
        String baseName = extractBaseName(structurePath);

        // Categories that can be linked to structures
        Set<LootTableInfo.LootTableCategory> linkableCategories = Set.of(
            LootTableInfo.LootTableCategory.CHEST,
            LootTableInfo.LootTableCategory.DISPENSER,
            LootTableInfo.LootTableCategory.SPAWNER,
            LootTableInfo.LootTableCategory.POTS,
            LootTableInfo.LootTableCategory.ARCHAEOLOGY
        );

        for (LootTableInfo lootTable : lootTables.getAll()) {
            // Only check linkable categories
            if (!linkableCategories.contains(lootTable.category())) {
                continue;
            }

            // Only match same namespace or vanilla-to-vanilla
            if (!structure.namespace().equals(lootTable.namespace())) {
                continue;
            }

            String tablePath = lootTable.path().toLowerCase();
            // Remove category prefix for matching
            String tablePathClean = stripCategoryPrefix(tablePath);

            Confidence confidence = calculatePathConfidence(structurePath, baseName, tablePathClean);
            if (confidence != null) {
                links.add(StructureLootLink.heuristic(structure.id(), lootTable.id(), confidence));
            }
        }

        return links;
    }

    /**
     * Strip common category prefixes from loot table path for matching.
     */
    private String stripCategoryPrefix(String path) {
        String[] prefixes = {
            "chests/", "chest/",
            "dispensers/", "dispenser/",
            "spawners/", "spawner/",
            "pots/", "pot/",
            "archaeology/"
        };
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
        }
        return path;
    }

    /**
     * Calculate confidence based on path matching.
     */
    private Confidence calculatePathConfidence(String structurePath, String baseName, String tablePath) {
        // Remove any remaining subpath (trial_chambers/corridor -> trial_chambers)
        String tableBase = tablePath.contains("/") ? tablePath.substring(0, tablePath.indexOf('/')) : tablePath;

        // Exact match: village_plains -> village_plains
        if (tablePath.equals(structurePath) || tablePath.startsWith(structurePath + "_") ||
            tablePath.startsWith(structurePath + "/")) {
            return Confidence.HIGH;
        }

        // Table base matches structure: trial_chambers -> trial_chambers/corridor
        if (tableBase.equals(structurePath)) {
            return Confidence.HIGH;
        }

        // Base name match: village_plains -> village_*
        if (tablePath.startsWith(baseName + "_") || tablePath.equals(baseName) ||
            tableBase.equals(baseName)) {
            return Confidence.MEDIUM;
        }

        // Contains structure name: ancient_city -> ancient_city_ice_box
        if (tablePath.contains(structurePath) || structurePath.contains(tableBase)) {
            return Confidence.MEDIUM;
        }

        // Fuzzy match: share significant word (jungle_pyramid ↔ jungle_temple)
        // Split both paths into words and look for common significant terms
        Set<String> structureWords = extractSignificantWords(structurePath);
        Set<String> tableWords = extractSignificantWords(tablePath);

        // Find common words
        Set<String> commonWords = new HashSet<>(structureWords);
        commonWords.retainAll(tableWords);

        // If they share a significant word (not just "the", "a", etc.), it's a LOW match
        if (!commonWords.isEmpty()) {
            return Confidence.LOW;
        }

        return null;
    }

    /**
     * Extract significant words from a path for fuzzy matching.
     * Splits on underscores and filters out common suffixes.
     */
    private Set<String> extractSignificantWords(String path) {
        Set<String> words = new HashSet<>();
        // Remove any directory separators first
        String cleanPath = path.replace("/", "_");

        for (String word : cleanPath.split("_")) {
            // Skip common non-distinctive words
            if (word.length() >= 3 &&
                !word.equals("the") && !word.equals("big") && !word.equals("small") &&
                !word.equals("chest") && !word.equals("chests") &&
                !word.equals("common") && !word.equals("rare") &&
                !word.equals("dispenser") && !word.equals("spawner")) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Find loot tables from template pools that match a structure's naming pattern.
     * This works for jigsaw structures like villages, bastions, trial chambers.
     * Also populates the matchingPools set for use by processor linking.
     */
    private Set<Identifier> findPoolLootForStructure(StructureInfo structure, TemplatePoolParser poolParser,
                                                            Set<Identifier> matchingPools) {
        Set<Identifier> result = new HashSet<>();
        String structurePath = structure.path().toLowerCase();
        String namespace = structure.namespace();

        // Get all pools and check for matching names
        Map<Identifier, Set<Identifier>> poolLootMap = poolParser.buildPoolLootMap();

        for (Map.Entry<Identifier, Set<Identifier>> entry : poolLootMap.entrySet()) {
            Identifier poolId = entry.getKey();

            // Only match same namespace
            if (!poolId.getNamespace().equals(namespace)) {
                continue;
            }

            String poolPath = poolId.getPath().toLowerCase();

            // Check if pool path contains structure name or vice versa
            // e.g., structure "trial_chambers" matches pool "trial_chambers/corridor"
            // e.g., structure "village_plains" matches pool "village/plains/houses"
            boolean matches = false;

            // Exact prefix match
            if (poolPath.startsWith(structurePath + "/") || poolPath.equals(structurePath)) {
                matches = true;
            }

            // Handle village variants: village_plains -> village/plains
            if (!matches && structurePath.startsWith("village_")) {
                String villageType = structurePath.substring("village_".length());
                if (poolPath.startsWith("village/" + villageType) || poolPath.startsWith("village/common")) {
                    matches = true;
                }
            }

            // Handle bastion: bastion_remnant -> bastion/*
            if (!matches && structurePath.equals("bastion_remnant") && poolPath.startsWith("bastion")) {
                matches = true;
            }

            // Handle generic containment (structure name is prefix of pool path)
            if (!matches) {
                String baseName = extractBaseName(structurePath);
                if (poolPath.startsWith(baseName + "/") || poolPath.startsWith(baseName + "_")) {
                    matches = true;
                }
            }

            if (matches) {
                matchingPools.add(poolId);
                result.addAll(entry.getValue());
            }
        }

        return result;
    }

    /**
     * Find loot tables from processors used by the given template pools.
     */
    private Set<Identifier> findProcessorLootForPools(Set<Identifier> poolIds,
                                                             TemplatePoolParser poolParser,
                                                             ProcessorListParser processorParser) {
        Set<Identifier> result = new HashSet<>();

        // Collect all processors used by the matching pools
        Set<Identifier> processors = new HashSet<>();
        for (Identifier poolId : poolIds) {
            processors.addAll(poolParser.getProcessorsForPool(poolId));
        }

        // Get loot tables from each processor
        for (Identifier processorId : processors) {
            result.addAll(processorParser.getLootTablesForProcessor(processorId));
        }

        return result;
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
     * Combines in-memory overrides with persistent user corrections.
     */
    private List<StructureLootLink> applyAuthorOverrides(Identifier structureId, List<StructureLootLink> links) {
        List<StructureLootLink> result = new ArrayList<>();
        UserCorrectionManager corrections = UserCorrectionManager.getInstance();

        // Collect all removed links (in-memory + persistent)
        Set<Identifier> removed = authorRemovedLinks.stream()
            .filter(r -> r.structureId.equals(structureId))
            .map(r -> r.lootTableId)
            .collect(Collectors.toSet());

        // Add persistent user-removed links
        for (UserCorrectionManager.CorrectionKey key : corrections.getUserRemovedLinks()) {
            if (key.structure().equals(structureId.toString())) {
                Identifier tableId = Identifier.tryParse(key.lootTable());
                if (tableId != null) {
                    removed.add(tableId);
                }
            }
        }

        // Filter out removed links
        for (StructureLootLink link : links) {
            if (!removed.contains(link.lootTableId())) {
                result.add(link);
            }
        }

        // Add in-memory author-added links
        for (StructureLootLink added : authorAddedLinks) {
            if (added.structureId().equals(structureId)) {
                result.add(added);
            }
        }

        // Add persistent user-added links
        for (StructureLootLink added : corrections.getUserAddedLinks()) {
            if (added.structureId().equals(structureId)) {
                // Only add if not already in result
                boolean alreadyPresent = result.stream()
                    .anyMatch(l -> l.lootTableId().equals(added.lootTableId()));
                if (!alreadyPresent) {
                    result.add(added);
                }
            }
        }

        return result;
    }

    /**
     * Deduplicate links, keeping highest confidence for each loot table.
     */
    private List<StructureLootLink> deduplicateLinks(List<StructureLootLink> links) {
        Map<Identifier, StructureLootLink> best = new LinkedHashMap<>();
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
     * Records the correction for future sessions.
     */
    public void addManualLink(Identifier structureId, Identifier lootTableId) {
        // Get existing link info for correction tracking
        List<StructureLootLink> existingLinks = linksByStructure.get(structureId);
        String originalSource = null;
        String originalConfidence = null;
        if (existingLinks != null) {
            for (StructureLootLink link : existingLinks) {
                if (link.lootTableId().equals(lootTableId)) {
                    originalSource = link.source().name();
                    originalConfidence = link.confidence().name();
                    break;
                }
            }
        }

        // Add in-memory override
        authorAddedLinks.add(StructureLootLink.manual(structureId, lootTableId));

        // Record correction for persistence (learns from user feedback)
        UserCorrectionManager.getInstance().recordAddedLink(
            structureId, lootTableId, originalSource, originalConfidence);

        // Re-run linking to apply
        link(currentServer);
        Isotope.LOGGER.info("Author added link: {} -> {}", structureId, lootTableId);
    }

    /**
     * Remove a link (author override).
     * Records the correction for future sessions.
     */
    public void removeLink(Identifier structureId, Identifier lootTableId) {
        // Get existing link info for correction tracking
        List<StructureLootLink> existingLinks = linksByStructure.get(structureId);
        String originalSource = null;
        String originalConfidence = null;
        if (existingLinks != null) {
            for (StructureLootLink link : existingLinks) {
                if (link.lootTableId().equals(lootTableId)) {
                    originalSource = link.source().name();
                    originalConfidence = link.confidence().name();
                    break;
                }
            }
        }

        // Remove from added if it was manually added
        authorAddedLinks.removeIf(l ->
            l.structureId().equals(structureId) && l.lootTableId().equals(lootTableId));

        // Mark as removed (in-memory)
        authorRemovedLinks.add(new AuthorRemoval(structureId, lootTableId));

        // Record correction for persistence (learns that this was a false positive)
        UserCorrectionManager.getInstance().recordRemovedLink(
            structureId, lootTableId, originalSource, originalConfidence);

        // Re-run linking to apply
        link(currentServer);
        Isotope.LOGGER.info("Author removed link: {} -> {} (was: {})", structureId, lootTableId, originalSource);
    }

    /**
     * Restore a previously removed link.
     * Records the restoration for future sessions.
     */
    public void restoreLink(Identifier structureId, Identifier lootTableId) {
        // Remove from in-memory removals
        authorRemovedLinks.removeIf(r ->
            r.structureId.equals(structureId) && r.lootTableId.equals(lootTableId));

        // Record restoration (user changed their mind)
        UserCorrectionManager.getInstance().recordRestoredLink(structureId, lootTableId);

        link(currentServer);
        Isotope.LOGGER.info("Author restored link: {} -> {}", structureId, lootTableId);
    }

    // --- Query API ---

    /**
     * Get all links for a structure.
     */
    public List<StructureLootLink> getLinksForStructure(Identifier structureId) {
        return linksByStructure.getOrDefault(structureId, Collections.emptyList());
    }

    /**
     * Get all links for a loot table.
     */
    public List<StructureLootLink> getLinksForLootTable(Identifier lootTableId) {
        return linksByLootTable.getOrDefault(lootTableId, Collections.emptyList());
    }

    /**
     * Get structures that have at least one link.
     */
    public Set<Identifier> getLinkedStructures() {
        return Collections.unmodifiableSet(linksByStructure.keySet());
    }

    /**
     * Get loot tables that have at least one link.
     */
    public Set<Identifier> getLinkedLootTables() {
        return Collections.unmodifiableSet(linksByLootTable.keySet());
    }

    /**
     * Check if a structure has any links.
     */
    public boolean hasLinks(Identifier structureId) {
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
        // NOTE: monster_room is a FEATURE, not a structure. It's handled by FeatureRegistry.
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

    private record AuthorRemoval(Identifier structureId, Identifier lootTableId) {}
}
