package dev.isotope.observation;

import dev.isotope.Isotope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Forces structure generation for observation.
 *
 * This is the core of ISOTOPE's analysis - we place each structure type
 * and then observe what loot tables it invokes.
 */
public final class StructurePlacementEngine {

    private static final StructurePlacementEngine INSTANCE = new StructurePlacementEngine();

    // Spacing between forced structures to avoid overlap
    private static final int STRUCTURE_SPACING = 512;

    // Maximum search radius for structure placement
    private static final int SEARCH_RADIUS = 100;

    private StructurePlacementEngine() {}

    public static StructurePlacementEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Place all registered structures for observation.
     *
     * @param server The Minecraft server
     * @param level The level to place structures in
     * @param onProgress Callback for progress updates
     * @return Map of structure ID -> placement result
     */
    public Map<ResourceLocation, PlacementResult> placeAllStructures(
            MinecraftServer server,
            ServerLevel level,
            Consumer<String> onProgress) {

        Map<ResourceLocation, PlacementResult> results = new LinkedHashMap<>();

        // Get all registered structures
        HolderLookup.RegistryLookup<Structure> lookup = server.registryAccess()
            .lookupOrThrow(Registries.STRUCTURE);

        List<ResourceLocation> structureIds = new ArrayList<>();
        lookup.listElementIds().forEach(key -> structureIds.add(key.location()));

        onProgress.accept("Found " + structureIds.size() + " structures to place");

        // Calculate grid positions for placing structures
        AtomicInteger placedCount = new AtomicInteger(0);
        int gridSize = (int) Math.ceil(Math.sqrt(structureIds.size()));

        for (int i = 0; i < structureIds.size(); i++) {
            ResourceLocation structureId = structureIds.get(i);

            // Calculate position on grid
            int gridX = i % gridSize;
            int gridZ = i / gridSize;
            BlockPos targetPos = new BlockPos(
                gridX * STRUCTURE_SPACING,
                64, // Y level doesn't matter for /place
                gridZ * STRUCTURE_SPACING
            );

            onProgress.accept(String.format("Placing %s (%d/%d)...",
                structureId.getPath(), i + 1, structureIds.size()));

            PlacementResult result = placeStructure(server, level, structureId, targetPos);
            results.put(structureId, result);

            if (result.success()) {
                placedCount.incrementAndGet();
            }

            // Give the server time to process
            if (i % 10 == 0) {
                // Tick the server to process pending tasks
                server.tickServer(() -> true);
            }
        }

        onProgress.accept(String.format("Placed %d/%d structures successfully",
            placedCount.get(), structureIds.size()));

        return results;
    }

    /**
     * Place a single structure at or near the target position.
     */
    public PlacementResult placeStructure(
            MinecraftServer server,
            ServerLevel level,
            ResourceLocation structureId,
            BlockPos targetPos) {

        try {
            // Get the structure from registry
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
            var lookup = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);

            Holder.Reference<Structure> holder = lookup.get(key).orElse(null);

            if (holder == null) {
                return PlacementResult.failed(structureId, "Structure not found in registry");
            }

            Structure structure = holder.value();

            // Generate the structure using Structure.generate()
            // This creates a StructureStart directly
            var chunkPos = level.getChunk(targetPos).getPos();

            StructureStart start = structure.generate(
                holder,
                level.dimension(),
                level.registryAccess(),
                level.getChunkSource().getGenerator(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                chunkPos,
                0, // references
                level,
                biome -> true // Accept any biome for forced placement
            );

            if (start == null || start == StructureStart.INVALID_START) {
                return PlacementResult.failed(structureId, "Structure generation returned invalid start");
            }

            // Actually place the structure
            BoundingBox bounds = start.getBoundingBox();

            // Generate the chunk with the structure
            start.placeInChunk(
                level,
                level.structureManager(),
                level.getChunkSource().getGenerator(),
                level.getRandom(),
                bounds,
                chunkPos
            );

            // Record the placement
            BlockPos origin = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
            StructurePlacement placement = StructurePlacement.forced(structureId, origin, bounds);
            StructureObserver.getInstance().onStructurePlaced(placement);

            // Now trigger loot table generation for any containers
            triggerContainerLoot(level, bounds);

            return PlacementResult.success(structureId, origin, bounds);

        } catch (Exception e) {
            Isotope.LOGGER.warn("Failed to place structure {}: {}", structureId, e.getMessage());
            return PlacementResult.failed(structureId, e.getMessage());
        }
    }

    /**
     * Find and trigger loot generation for all containers within a bounding box.
     * This simulates a player opening each chest.
     */
    private void triggerContainerLoot(ServerLevel level, BoundingBox bounds) {
        // Iterate through all blocks in the bounding box
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                        // This triggers loot table generation
                        container.unpackLootTable(null); // null player = no luck bonus
                    }
                }
            }
        }
    }

    /**
     * Result of a structure placement attempt.
     */
    public record PlacementResult(
        ResourceLocation structureId,
        boolean success,
        BlockPos origin,
        BoundingBox bounds,
        String error
    ) {
        public static PlacementResult success(ResourceLocation id, BlockPos origin, BoundingBox bounds) {
            return new PlacementResult(id, true, origin, bounds, null);
        }

        public static PlacementResult failed(ResourceLocation id, String error) {
            return new PlacementResult(id, false, BlockPos.ZERO, null, error);
        }
    }
}
