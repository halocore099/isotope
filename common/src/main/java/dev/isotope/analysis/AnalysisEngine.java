package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.observation.ObservationSession;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Facade over the observation-based analysis system.
 *
 * In the observation model, ISOTOPE doesn't guess structure-loot relationships.
 * Instead, it forces structure generation and observes actual loot table invocations.
 *
 * This class provides a simplified interface to the observation results.
 */
public final class AnalysisEngine {

    private static final AnalysisEngine INSTANCE = new AnalysisEngine();

    // Analysis state
    private final AtomicBoolean hasData = new AtomicBoolean(false);
    private final AtomicInteger structureCount = new AtomicInteger(0);
    private final AtomicInteger lootTableCount = new AtomicInteger(0);

    private AnalysisEngine() {}

    public static AnalysisEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Called after observation session completes to update state.
     */
    public void onObservationComplete(ObservationSession.SessionResult result) {
        if (result.success()) {
            hasData.set(true);
            structureCount.set(result.structuresPlaced());
            lootTableCount.set(result.uniqueLootTables());
            Isotope.LOGGER.info("Analysis data available: {} structures, {} loot tables",
                result.structuresPlaced(), result.uniqueLootTables());
        } else {
            Isotope.LOGGER.warn("Observation failed: {}", result.error());
        }
    }

    /**
     * Check if analysis data is available.
     */
    public boolean hasData() {
        return hasData.get();
    }

    /**
     * Get the number of structures observed.
     */
    public int getStructureCount() {
        return structureCount.get();
    }

    /**
     * Get the number of unique loot tables observed.
     */
    public int getLootTableCount() {
        return lootTableCount.get();
    }

    /**
     * Get observed structure data.
     */
    public Optional<ObservationSession.ObservedStructureData> getStructureData(ResourceLocation structureId) {
        return ObservationSession.getInstance().getStructureData(structureId);
    }

    /**
     * Get all observed structure data.
     */
    public List<ObservationSession.ObservedStructureData> getAllStructureData() {
        return ObservationSession.getInstance().getAllStructureData();
    }

    /**
     * Get the current observation session state.
     */
    public ObservationSession.SessionState getSessionState() {
        return ObservationSession.getInstance().getState();
    }

    /**
     * Get the last observation result.
     */
    public Optional<ObservationSession.SessionResult> getLastResult() {
        return ObservationSession.getInstance().getLastResult();
    }

    /**
     * Clear all analysis data.
     */
    public void clear() {
        hasData.set(false);
        structureCount.set(0);
        lootTableCount.set(0);
    }

    // --- Config class kept for API compatibility ---

    public record AnalysisConfig(
        int sampleCount,  // Legacy - no longer used
        boolean analyzeAllLootTables,  // Legacy - no longer used
        boolean forceStructurePlacement  // Always true in observation model
    ) {
        public static AnalysisConfig defaultConfig() {
            return new AnalysisConfig(0, true, true);
        }

        public static AnalysisConfig thorough() {
            return new AnalysisConfig(0, true, true);
        }
    }
}
