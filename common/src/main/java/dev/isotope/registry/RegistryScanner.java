package dev.isotope.registry;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.isotope.Isotope;
import dev.isotope.analysis.HeadlessAnalysisWorld;
import dev.isotope.analysis.OrphanDetector;
import dev.isotope.api.ModLinkRegistry;
import dev.isotope.api.ModLinkScanner;
import dev.isotope.editing.LootEditManager;
import dev.isotope.importing.DatapackLootMetadataScanner;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hooks into server lifecycle to scan registries.
 *
 * <h2>Overview</h2>
 * This is the foundation of ISOTOPE's discovery system. We scan all structures
 * and loot tables immediately when a world loads, then run heuristic linking
 * to establish relationships.
 *
 * <h2>Version Compatibility Strategy</h2>
 * ISOTOPE supports Minecraft 1.20.1 through 1.21.4+ using a multi-layer approach:
 *
 * <ul>
 *   <li><b>Compile-time:</b> Stonecutter handles API differences via conditionals</li>
 *   <li><b>Runtime:</b> Each registry uses reflection to detect and use the appropriate API:
 *     <ul>
 *       <li>1.21+: {@code server.reloadableRegistries().getLootTable(ResourceKey)}</li>
 *       <li>1.20.x: {@code server.getLootData().getLootTable(ResourceLocation)}</li>
 *     </ul>
 *   </li>
 *   <li><b>Mixins:</b> Version-specific mixins are loaded conditionally via {@code IsotopeMixinPlugin}</li>
 * </ul>
 *
 * <h2>Scan Layers</h2>
 * The scan runs in ordered layers, each building on the previous:
 * <ol>
 *   <li><b>Layer 1 - Registry Scan:</b> Authoritative data from MC registries (structures, loot tables, entities)</li>
 *   <li><b>Layer 1.5 - Feature Discovery:</b> Fire-and-forget worldgen features (dungeons, bonus chests)</li>
 *   <li><b>Layer 2 - Template Parsing:</b> NBT structure templates for loot table references</li>
 *   <li><b>Layer 2.2-2.5 - JSON Parsing:</b> Template pools, processor lists, structure configs, mod links</li>
 *   <li><b>Layer 3 - Linking:</b> Multi-source heuristic linking with confidence scoring</li>
 *   <li><b>Layer 4 - Orphan Detection:</b> Surface unlinked loot tables and structures</li>
 *   <li><b>Layer 5 - Compilation:</b> Build unified source registry for UI</li>
 * </ol>
 *
 * <h2>Error Handling</h2>
 * Each layer is wrapped in its own try-catch to allow partial failures without
 * stopping the entire scan. Failed steps are logged and counted in the summary.
 *
 * @see StructureRegistry
 * @see LootTableRegistry
 * @see StructureLootLinker
 */
public final class RegistryScanner {
    private static boolean initialized = false;
    private static MinecraftServer currentServer = null;

    /** Tracks failed scan steps for summary logging */
    private static final List<String> failedSteps = new ArrayList<>();

    private RegistryScanner() {}

    public static void init() {
        if (initialized) {
            Isotope.LOGGER.warn("RegistryScanner already initialized");
            return;
        }

        LifecycleEvent.SERVER_STARTED.register(RegistryScanner::onServerStarted);
        LifecycleEvent.SERVER_STOPPING.register(RegistryScanner::onServerStopping);

        initialized = true;
        Isotope.LOGGER.debug("RegistryScanner lifecycle hooks registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        currentServer = server;
        failedSteps.clear();

        long totalStartTime = System.currentTimeMillis();
        Isotope.LOGGER.info("=== ISOTOPE Registry Scan Starting ===");

        // Build the scan steps - each step is isolated for error handling
        List<ScanStep> steps = buildScanSteps(server);

        // Execute each step with timing and error handling
        int completedSteps = 0;
        for (ScanStep step : steps) {
            if (runStep(step, server)) {
                completedSteps++;
            }
        }

        // Summary logging
        long totalTime = System.currentTimeMillis() - totalStartTime;
        logSummary(totalTime, completedSteps, steps.size());

        // Check if this is the temporary registry loading world (main menu flow)
        if (RegistryLoader.getInstance().isTempWorld(server)) {
            Isotope.LOGGER.info("Temp world detected - notifying RegistryLoader");
            RegistryLoader.getInstance().onTempWorldReady(server);
            return;
        }

        // Check if this is the headless analysis/observation world
        if (HeadlessAnalysisWorld.getInstance().isAnalysisWorld(server)) {
            Isotope.LOGGER.info("Analysis world detected - delegating to HeadlessAnalysisWorld");
            HeadlessAnalysisWorld.getInstance().onServerReady(server);
        }
    }

    /**
     * Build the ordered list of scan steps.
     * Each step is a named operation that can fail independently.
     */
    private static List<ScanStep> buildScanSteps(MinecraftServer server) {
        List<ScanStep> steps = new ArrayList<>();

        // Layer 1: Registry scan (authoritative)
        steps.add(new ScanStep("Layer 1: Structure Registry",
            s -> StructureRegistry.getInstance().scan(s)));
        steps.add(new ScanStep("Layer 1: Loot Table Registry",
            s -> LootTableRegistry.getInstance().scan(s)));
        steps.add(new ScanStep("Layer 1: Structure Class Registry",
            s -> StructureClassRegistry.getInstance().scan(s)));

        // Layer 1.5: Feature discovery
        steps.add(new ScanStep("Layer 1.5: Feature Registry",
            s -> {
                FeatureRegistry.getInstance().initialize();
                WorldgenFeatureParser.getInstance().parse(s);
                FeatureRegistry.getInstance().addFromWorldgen(WorldgenFeatureParser.getInstance());
            }));
        steps.add(new ScanStep("Layer 1.5: Entity Loot Registry",
            s -> EntityLootRegistry.getInstance().initialize()));

        // Layer 2: Template parsing (deterministic)
        steps.add(new ScanStep("Layer 2: Structure Template Parser",
            s -> StructureTemplateParser.getInstance().parse(s)));

        // Layer 2.2: Template pool parsing (jigsaw structure loot from JSON)
        steps.add(new ScanStep("Layer 2.2: Template Pool Parser",
            s -> TemplatePoolParser.getInstance().parse(s)));

        // Layer 2.25: Processor list parsing
        steps.add(new ScanStep("Layer 2.25: Processor List Parser",
            s -> ProcessorListParser.getInstance().parse(s)));

        // Layer 2.3: Structure config parsing
        steps.add(new ScanStep("Layer 2.3: Structure Config Parser",
            s -> StructureConfigParser.getInstance().parse(s)));

        // Layer 2.4: Mod-declared links
        steps.add(new ScanStep("Layer 2.4: Mod Link Scanner",
            s -> ModLinkScanner.getInstance().scan(s)));

        // Layer 2.5: Datapack metadata
        steps.add(new ScanStep("Layer 2.5: Datapack Metadata Scanner",
            s -> DatapackLootMetadataScanner.getInstance().scanAll()));

        // Layer 3: Multi-layer linking
        steps.add(new ScanStep("Layer 3: Structure-Loot Linker",
            s -> StructureLootLinker.getInstance().link(s)));

        // Layer 4: Orphan detection
        steps.add(new ScanStep("Layer 4: Orphan Detection",
            s -> OrphanDetector.getInstance().detect()));

        // Layer 5: Compile unified source registry
        steps.add(new ScanStep("Layer 5: Loot Source Registry",
            s -> {
                LootSourceRegistry.getInstance().compile();
                LootSourceRegistry.getInstance().dumpDebugInfo();
            }));

        // Pre-parse loot tables for the editor
        steps.add(new ScanStep("Pre-parse Loot Tables",
            s -> LootEditManager.getInstance().preParseLootTables(s)));

        return steps;
    }

    /**
     * Execute a single scan step with timing and error handling.
     * @return true if step completed successfully, false if it failed
     */
    private static boolean runStep(ScanStep step, MinecraftServer server) {
        long startTime = System.currentTimeMillis();
        try {
            step.action.accept(server);
            long elapsed = System.currentTimeMillis() - startTime;
            Isotope.LOGGER.debug("{} completed in {}ms", step.name, elapsed);
            return true;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            failedSteps.add(step.name);
            Isotope.LOGGER.error("{} FAILED after {}ms: {}", step.name, elapsed, e.getMessage());
            Isotope.LOGGER.debug("Stack trace for {} failure:", step.name, e);
            return false;
        }
    }

    /**
     * Log the scan summary with statistics from each registry.
     */
    private static void logSummary(long totalTime, int completedSteps, int totalSteps) {
        Isotope.LOGGER.info("=== ISOTOPE Registry Scan Complete ===");
        Isotope.LOGGER.info("Total time: {}ms ({}/{} steps succeeded)",
            totalTime, completedSteps, totalSteps);

        if (!failedSteps.isEmpty()) {
            Isotope.LOGGER.warn("Failed steps: {}", String.join(", ", failedSteps));
        }

        // Core registry stats
        Isotope.LOGGER.info("Registries: {} structures, {} loot tables, {} entities, {} links",
            StructureRegistry.getInstance().size(),
            LootTableRegistry.getInstance().size(),
            EntityLootRegistry.getInstance().size(),
            StructureLootLinker.getInstance().getLinkCount());

        // Parsing stats
        var templateStats = StructureTemplateParser.getInstance().getStats();
        Isotope.LOGGER.info("Templates: {} scanned, {} loot refs",
            templateStats.templatesScanned(), templateStats.lootReferencesFound());

        var poolStats = TemplatePoolParser.getInstance().getStats();
        Isotope.LOGGER.info("Pools: {} parsed, {} with loot, {} refs",
            poolStats.filesParsed(), poolStats.poolsWithLoot(), poolStats.lootReferences());

        var processorStats = ProcessorListParser.getInstance().getStats();
        Isotope.LOGGER.info("Processors: {} parsed, {} with loot, {} refs",
            processorStats.filesParsed(), processorStats.processorsWithLoot(), processorStats.lootReferences());

        var structureConfigStats = StructureConfigParser.getInstance().getStats();
        Isotope.LOGGER.info("Structure configs: {} parsed, {} jigsaw, {} with loot ({} refs)",
            structureConfigStats.filesParsed(), structureConfigStats.jigsawStructures(),
            structureConfigStats.structuresWithLoot(), structureConfigStats.lootReferences());

        // Optional stats (only log if non-zero)
        var modLinkStats = ModLinkRegistry.getInstance().getStats();
        if (modLinkStats.totalLinks() > 0) {
            Isotope.LOGGER.info("Mod links: {} structures, {} links ({} programmatic, {} JSON)",
                modLinkStats.structures(), modLinkStats.totalLinks(),
                modLinkStats.programmaticLinks(), modLinkStats.jsonLinks());
        }

        var datapackMetaStats = DatapackLootMetadataScanner.getInstance().getStats();
        if (datapackMetaStats.linksRegistered() > 0) {
            Isotope.LOGGER.info("Datapack metadata: {} datapacks, {} files, {} links",
                datapackMetaStats.datapacksScanned(), datapackMetaStats.metadataFilesFound(),
                datapackMetaStats.linksRegistered());
        }

        var orphanReport = OrphanDetector.getInstance().getLastReport();
        if (orphanReport != null && orphanReport.hasOrphans()) {
            Isotope.LOGGER.info("Orphans: {}", orphanReport.summary());
        }
    }

    /**
     * A named scan step with its action.
     */
    private record ScanStep(String name, Consumer<MinecraftServer> action) {}

    private static void onServerStopping(MinecraftServer server) {
        // Check if this is the analysis world
        if (HeadlessAnalysisWorld.getInstance().isAnalysisWorld(server)) {
            Isotope.LOGGER.info("Analysis world stopping - preserving registry data");
        } else {
            Isotope.LOGGER.debug("Server stopping: {}", server.getWorldData().getLevelName());
        }
        currentServer = null;
    }

    /**
     * Get the current server (if available).
     */
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    /**
     * Check if registries have been scanned.
     */
    public static boolean isScanned() {
        return StructureRegistry.getInstance().isScanned() &&
               LootTableRegistry.getInstance().isScanned();
    }

    /**
     * Force a re-scan of registries.
     */
    public static void rescan() {
        if (currentServer != null) {
            StructureRegistry.getInstance().scan(currentServer);
            LootTableRegistry.getInstance().scan(currentServer);
            StructureClassRegistry.getInstance().scan(currentServer);
            FeatureRegistry.getInstance().initialize();
            WorldgenFeatureParser.getInstance().parse(currentServer);
            FeatureRegistry.getInstance().addFromWorldgen(WorldgenFeatureParser.getInstance());
            EntityLootRegistry.getInstance().initialize();
            StructureTemplateParser.getInstance().parse(currentServer);
            TemplatePoolParser.getInstance().parse(currentServer);
            ProcessorListParser.getInstance().parse(currentServer);
            StructureConfigParser.getInstance().parse(currentServer);
            ModLinkScanner.getInstance().scan(currentServer);
            DatapackLootMetadataScanner.getInstance().scanAll();
            StructureLootLinker.getInstance().link(currentServer);
            OrphanDetector.getInstance().detect();
            LootSourceRegistry.getInstance().compile();
        }
    }
}
