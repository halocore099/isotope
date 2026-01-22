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

/**
 * Hooks into server lifecycle to scan registries.
 *
 * This is the foundation of ISOTOPE's discovery system.
 * We scan all structures and loot tables immediately when a world loads,
 * then run heuristic linking to establish relationships.
 */
public final class RegistryScanner {
    private static boolean initialized = false;
    private static MinecraftServer currentServer = null;

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

        // Always scan registries - this is the core of ISOTOPE
        Isotope.LOGGER.info("Scanning registries for structures, loot tables, and entities...");

        // Layer 1: Registry scan (authoritative)
        StructureRegistry.getInstance().scan(server);
        LootTableRegistry.getInstance().scan(server);
        StructureClassRegistry.getInstance().scan(server);

        // Layer 1.5: Feature discovery
        FeatureRegistry.getInstance().initialize();
        WorldgenFeatureParser.getInstance().parse(server);
        FeatureRegistry.getInstance().addFromWorldgen(WorldgenFeatureParser.getInstance());
        EntityLootRegistry.getInstance().initialize();

        // Layer 2: Template parsing (deterministic)
        Isotope.LOGGER.info("Parsing structure templates for loot table references...");
        StructureTemplateParser.getInstance().parse(server);

        // Layer 2.2: Template pool parsing (jigsaw structure loot from JSON)
        TemplatePoolParser.getInstance().parse(server);

        // Layer 2.25: Processor list parsing (loot tables set by structure processors)
        ProcessorListParser.getInstance().parse(server);

        // Layer 2.3: Structure config parsing (structure definitions with start_pool and type-based loot)
        StructureConfigParser.getInstance().parse(server);

        // Layer 2.4: Mod-declared links (from isotope_links.json and loot_metadata.json files)
        ModLinkScanner.getInstance().scan(server);

        // Layer 2.5: Datapack metadata (direct folder scan for unloaded datapacks)
        DatapackLootMetadataScanner.getInstance().scanAll();

        // Layer 3: Multi-layer linking (heuristics + templates + observations + content analysis)
        StructureLootLinker.getInstance().link(server);

        // Layer 4: Orphan detection (surface gaps)
        OrphanDetector.OrphanReport orphanReport = OrphanDetector.getInstance().detect();

        // Layer 5: Compile unified source registry (structures + features)
        LootSourceRegistry.getInstance().compile();

        // Debug dump: output all loot table → source mappings to log
        LootSourceRegistry.getInstance().dumpDebugInfo();

        // Pre-parse loot tables for the editor (while server is available)
        LootEditManager.getInstance().preParseLootTables(server);

        // Summary logging
        var templateStats = StructureTemplateParser.getInstance().getStats();
        var poolStats = TemplatePoolParser.getInstance().getStats();
        var processorStats = ProcessorListParser.getInstance().getStats();
        Isotope.LOGGER.info("Registry scan complete: {} structures, {} loot tables, {} entities, {} links",
            StructureRegistry.getInstance().size(),
            LootTableRegistry.getInstance().size(),
            EntityLootRegistry.getInstance().size(),
            StructureLootLinker.getInstance().getLinkCount());
        Isotope.LOGGER.info("Template parsing: {} templates scanned, {} loot references found",
            templateStats.templatesScanned(), templateStats.lootReferencesFound());
        Isotope.LOGGER.info("Pool parsing: {} pools scanned, {} with loot, {} loot references",
            poolStats.filesParsed(), poolStats.poolsWithLoot(), poolStats.lootReferences());
        Isotope.LOGGER.info("Processor parsing: {} processors scanned, {} with loot, {} loot references",
            processorStats.filesParsed(), processorStats.processorsWithLoot(), processorStats.lootReferences());
        var structureConfigStats = StructureConfigParser.getInstance().getStats();
        Isotope.LOGGER.info("Structure config parsing: {} structures, {} jigsaw, {} with loot ({} refs)",
            structureConfigStats.filesParsed(), structureConfigStats.jigsawStructures(),
            structureConfigStats.structuresWithLoot(), structureConfigStats.lootReferences());
        var modLinkStats = ModLinkRegistry.getInstance().getStats();
        if (modLinkStats.totalLinks() > 0) {
            Isotope.LOGGER.info("Mod-declared links: {} structures, {} links ({} programmatic, {} from JSON)",
                modLinkStats.structures(), modLinkStats.totalLinks(),
                modLinkStats.programmaticLinks(), modLinkStats.jsonLinks());
        }
        var datapackMetaStats = DatapackLootMetadataScanner.getInstance().getStats();
        if (datapackMetaStats.linksRegistered() > 0) {
            Isotope.LOGGER.info("Datapack metadata: {} datapacks scanned, {} metadata files, {} links",
                datapackMetaStats.datapacksScanned(), datapackMetaStats.metadataFilesFound(),
                datapackMetaStats.linksRegistered());
        }
        if (orphanReport.hasOrphans()) {
            Isotope.LOGGER.info("Orphan detection: {}", orphanReport.summary());
        }

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
