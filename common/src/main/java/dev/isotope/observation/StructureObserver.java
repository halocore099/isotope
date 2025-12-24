package dev.isotope.observation;

import dev.isotope.Isotope;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records structure placements during analysis.
 * Works with StructureStartMixin to observe when structures are generated.
 */
public final class StructureObserver {

    private static final StructureObserver INSTANCE = new StructureObserver();

    private final AtomicBoolean recording = new AtomicBoolean(false);

    // All placements observed during this session
    private final Queue<StructurePlacement> placements = new ConcurrentLinkedQueue<>();

    // Quick lookup by structure type
    private final Map<ResourceLocation, List<StructurePlacement>> placementsByStructure = new ConcurrentHashMap<>();

    // Quick lookup by chunk for spatial queries
    private final Map<Long, List<StructurePlacement>> placementsByChunk = new ConcurrentHashMap<>();

    private StructureObserver() {}

    public static StructureObserver getInstance() {
        return INSTANCE;
    }

    public void startRecording() {
        clear();
        recording.set(true);
        Isotope.LOGGER.info("[StructureObserver] Started recording structure placements");
    }

    public void stopRecording() {
        recording.set(false);
        Isotope.LOGGER.info("[StructureObserver] Stopped recording. Total placements: {}", placements.size());
    }

    public void clear() {
        placements.clear();
        placementsByStructure.clear();
        placementsByChunk.clear();
    }

    /**
     * Record a structure placement.
     * Called by StructureStartMixin or StructurePlacementEngine.
     */
    public void onStructurePlaced(StructurePlacement placement) {
        if (!recording.get()) return;

        placements.add(placement);

        // Index by structure type
        placementsByStructure
            .computeIfAbsent(placement.structureId(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(placement);

        // Index by chunk (using center of bounding box)
        BlockPos center = placement.center();
        long chunkKey = ((long) (center.getX() >> 4) << 32) | ((long) (center.getZ() >> 4) & 0xFFFFFFFFL);
        placementsByChunk
            .computeIfAbsent(chunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(placement);

        Isotope.LOGGER.debug("[StructureObserver] Recorded: {} at {} ({})",
            placement.structureId(),
            placement.origin(),
            placement.source());
    }

    /**
     * Find the structure that contains or is nearest to a position.
     * Used by ObservationCorrelator to link loot invocations to structures.
     */
    public Optional<StructurePlacement> findStructureAt(BlockPos pos) {
        // First check for exact containment
        for (StructurePlacement placement : placements) {
            if (placement.contains(pos)) {
                return Optional.of(placement);
            }
        }

        // Then check for nearby (within 32 blocks of bounding box)
        for (StructurePlacement placement : placements) {
            if (placement.isNear(pos, 32)) {
                return Optional.of(placement);
            }
        }

        return Optional.empty();
    }

    /**
     * Find all structures near a position.
     */
    public List<StructurePlacement> findStructuresNear(BlockPos pos, int radius) {
        List<StructurePlacement> result = new ArrayList<>();
        for (StructurePlacement placement : placements) {
            if (placement.isNear(pos, radius)) {
                result.add(placement);
            }
        }
        return result;
    }

    /**
     * Get all placements of a specific structure type.
     */
    public List<StructurePlacement> getPlacementsOf(ResourceLocation structureId) {
        return placementsByStructure.getOrDefault(structureId, List.of());
    }

    /**
     * Get all unique structure types that were placed.
     */
    public Set<ResourceLocation> getObservedStructures() {
        return Collections.unmodifiableSet(placementsByStructure.keySet());
    }

    public int getTotalPlacements() {
        return placements.size();
    }

    public Collection<StructurePlacement> getAllPlacements() {
        return Collections.unmodifiableCollection(placements);
    }

    public boolean isRecording() {
        return recording.get();
    }
}
