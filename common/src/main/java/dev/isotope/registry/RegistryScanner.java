package dev.isotope.registry;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.isotope.Isotope;
import dev.isotope.analysis.HeadlessAnalysisWorld;
import dev.isotope.analysis.OrphanDetector;
import dev.isotope.editing.LootEditManager;
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
        Isotope.LOGGER.info("Scanning registries for structures and loot tables...");

        // Layer 1: Registry scan (authoritative)
        StructureRegistry.getInstance().scan(server);
        LootTableRegistry.getInstance().scan(server);

        // Layer 2: Template parsing (deterministic)
        Isotope.LOGGER.info("Parsing structure templates for loot table references...");
        StructureTemplateParser.getInstance().parse(server);

        // Layer 3: Multi-layer linking (heuristics + templates + observations)
        StructureLootLinker.getInstance().link();

        // Layer 4: Orphan detection (surface gaps)
        OrphanDetector.OrphanReport orphanReport = OrphanDetector.getInstance().detect();

        // Pre-parse loot tables for the editor (while server is available)
        LootEditManager.getInstance().preParseLootTables(server);

        // Summary logging
        var templateStats = StructureTemplateParser.getInstance().getStats();
        Isotope.LOGGER.info("Registry scan complete: {} structures, {} loot tables, {} links",
            StructureRegistry.getInstance().size(),
            LootTableRegistry.getInstance().size(),
            StructureLootLinker.getInstance().getLinkCount());
        Isotope.LOGGER.info("Template parsing: {} templates scanned, {} loot references found",
            templateStats.templatesScanned(), templateStats.lootReferencesFound());
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
            StructureTemplateParser.getInstance().parse(currentServer);
            StructureLootLinker.getInstance().link();
            OrphanDetector.getInstance().detect();
        }
    }
}
