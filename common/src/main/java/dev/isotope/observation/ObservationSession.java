package dev.isotope.observation;

import dev.isotope.Isotope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.function.Consumer;

/**
 * Orchestrates a complete observation session.
 *
 * An observation session:
 * 1. Starts recording loot/structure events
 * 2. Forces structure placement
 * 3. Correlates observations
 * 4. Produces results
 *
 * This replaces the old heuristic-based analysis.
 */
public final class ObservationSession {

    private static final ObservationSession INSTANCE = new ObservationSession();

    private SessionState state = SessionState.IDLE;
    private SessionResult lastResult = null;
    private Consumer<String> progressCallback;

    private ObservationSession() {}

    public static ObservationSession getInstance() {
        return INSTANCE;
    }

    public enum SessionState {
        IDLE,
        PREPARING,
        PLACING_STRUCTURES,
        TRIGGERING_LOOT,
        CORRELATING,
        COMPLETE,
        FAILED
    }

    /**
     * Run a complete observation session.
     */
    public SessionResult runSession(MinecraftServer server, Consumer<String> onProgress) {
        this.progressCallback = onProgress;

        try {
            state = SessionState.PREPARING;
            progress("Preparing observation session...");

            // Clear previous data
            LootObserver.getInstance().clear();
            StructureObserver.getInstance().clear();
            ObservationCorrelator.getInstance().clear();

            // Start recording
            LootObserver.getInstance().startRecording();
            StructureObserver.getInstance().startRecording();

            state = SessionState.PLACING_STRUCTURES;
            progress("Starting structure placement phase...");

            // Get the overworld for structure placement
            ServerLevel level = server.overworld();

            // Place all structures
            Map<ResourceLocation, StructurePlacementEngine.PlacementResult> placementResults =
                StructurePlacementEngine.getInstance().placeAllStructures(server, level, this::progress);

            // Count successes/failures
            int successCount = 0;
            int failCount = 0;
            List<String> failedStructures = new ArrayList<>();

            for (var entry : placementResults.entrySet()) {
                if (entry.getValue().success()) {
                    successCount++;
                } else {
                    failCount++;
                    failedStructures.add(entry.getKey().toString() + ": " + entry.getValue().error());
                }
            }

            progress(String.format("Placed %d structures (%d failed)", successCount, failCount));

            // Stop recording
            LootObserver.getInstance().stopRecording();
            StructureObserver.getInstance().stopRecording();

            state = SessionState.CORRELATING;
            progress("Correlating observations...");

            // Correlate structures with loot invocations
            ObservationCorrelator.CorrelationResult correlation =
                ObservationCorrelator.getInstance().correlate();

            progress(String.format("Correlated %d loot invocations to %d structures",
                correlation.correlatedInvocations(), correlation.structuresWithLoot()));

            state = SessionState.COMPLETE;
            progress("Observation session complete!");

            // Build result
            lastResult = new SessionResult(
                true,
                null,
                successCount,
                failCount,
                correlation.totalInvocations(),
                correlation.structuresWithLoot(),
                LootObserver.getInstance().getObservedTables().size(),
                failedStructures
            );

            return lastResult;

        } catch (Exception e) {
            Isotope.LOGGER.error("Observation session failed", e);
            state = SessionState.FAILED;
            progress("ERROR: " + e.getMessage());

            lastResult = new SessionResult(
                false,
                e.getMessage(),
                0, 0, 0, 0, 0,
                List.of()
            );

            return lastResult;
        } finally {
            // Ensure recording is stopped
            if (LootObserver.getInstance().isRecording()) {
                LootObserver.getInstance().stopRecording();
            }
            if (StructureObserver.getInstance().isRecording()) {
                StructureObserver.getInstance().stopRecording();
            }
        }
    }

    private void progress(String message) {
        Isotope.LOGGER.info("[ObservationSession] {}", message);
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }

    public SessionState getState() {
        return state;
    }

    public Optional<SessionResult> getLastResult() {
        return Optional.ofNullable(lastResult);
    }

    /**
     * Get the observed data for a structure.
     */
    public Optional<ObservedStructureData> getStructureData(ResourceLocation structureId) {
        var observation = ObservationCorrelator.getInstance().getObservation(structureId);
        if (observation.isEmpty()) {
            return Optional.empty();
        }

        var obs = observation.get();
        return Optional.of(new ObservedStructureData(
            structureId,
            obs.lootTables(),
            obs.getObservedItems(),
            obs.getInvocationCounts(),
            obs.placement()
        ));
    }

    /**
     * Get all observed structure data.
     */
    public List<ObservedStructureData> getAllStructureData() {
        List<ObservedStructureData> result = new ArrayList<>();
        for (var obs : ObservationCorrelator.getInstance().getAllObservations()) {
            result.add(new ObservedStructureData(
                obs.placement().structureId(),
                obs.lootTables(),
                obs.getObservedItems(),
                obs.getInvocationCounts(),
                obs.placement()
            ));
        }
        return result;
    }

    /**
     * Result of an observation session.
     */
    public record SessionResult(
        boolean success,
        String error,
        int structuresPlaced,
        int structuresFailed,
        int lootInvocations,
        int structuresWithLoot,
        int uniqueLootTables,
        List<String> failedStructures
    ) {}

    /**
     * Observed data for a single structure.
     * This is the authoritative truth about what loot tables a structure uses.
     */
    public record ObservedStructureData(
        ResourceLocation structureId,
        Set<ResourceLocation> lootTables,
        Set<ResourceLocation> observedItems,
        Map<ResourceLocation, Integer> invocationCounts,
        StructurePlacement placement
    ) {
        public boolean hasLoot() {
            return !lootTables.isEmpty();
        }

        public int lootTableCount() {
            return lootTables.size();
        }
    }
}
