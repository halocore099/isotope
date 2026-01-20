package dev.isotope.observation;

import dev.isotope.Isotope;
import dev.isotope.compat.RegistryHelper;
import dev.isotope.compat.VersionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
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

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
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
        Registry<Structure> structureRegistry = RegistryHelper.getStructureRegistry(server.registryAccess());

        List<ResourceLocation> structureIds = new ArrayList<>();
        structureRegistry.keySet().forEach(structureIds::add);

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
            // Get the structure from registry using version-compatible method
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
            Registry<Structure> structureRegistry = RegistryHelper.getStructureRegistry(server.registryAccess());

            Holder<Structure> holder = getStructureHolder(structureRegistry, key);

            if (holder == null) {
                return PlacementResult.failed(structureId, "Structure not found in registry");
            }

            Structure structure = holder.value();

            // Generate the structure using version-compatible method
            var chunkPos = level.getChunk(targetPos).getPos();

            StructureStart start = generateStructureVersionCompatible(
                structure, holder, level, chunkPos, 0
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

    /**
     * Get a structure holder using version-compatible reflection.
     *
     * 1.21+: Registry.get(key) returns Optional<Holder.Reference<T>>
     * 1.20.x: Registry.get(key) returns T directly (nullable)
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private Holder<Structure> getStructureHolder(Registry<Structure> registry, ResourceKey<Structure> key) {
        try {
            Object result = registry.get(key);

            if (result == null) {
                return null;
            }

            // Check if it's an Optional (1.21+)
            if (result instanceof Optional) {
                Optional<?> optional = (Optional<?>) result;
                if (optional.isEmpty()) {
                    return null;
                }
                return (Holder<Structure>) optional.get();
            }

            // 1.20.x: Direct structure returned, wrap in a Holder
            if (result instanceof Structure) {
                Structure structure = (Structure) result;
                return Holder.direct(structure);
            }

            // If it's already a Holder
            if (result instanceof Holder) {
                return (Holder<Structure>) result;
            }

            return null;
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to get structure holder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate a structure using version-compatible reflection.
     *
     * 1.21+: Structure.generate(Holder, ResourceKey, RegistryAccess, ...)
     * 1.20.x: Structure.generate(RegistryAccess, ...)
     */
    @Nullable
    private StructureStart generateStructureVersionCompatible(Structure structure,
                                                              Holder<Structure> structureHolder,
                                                              ServerLevel level,
                                                              net.minecraft.world.level.ChunkPos chunkPos,
                                                              long seedOffset) {
        try {
            // Get common parameters
            var registryAccess = level.registryAccess();
            var generator = level.getChunkSource().getGenerator();
            var biomeSource = generator.getBiomeSource();
            var structureManager = level.getStructureManager();
            long seed = level.getSeed() + seedOffset;

            // Try to get randomState if it exists (1.20.4+)
            Object randomState = null;
            try {
                Method randomStateMethod = level.getChunkSource().getClass().getMethod("randomState");
                randomState = randomStateMethod.invoke(level.getChunkSource());
            } catch (NoSuchMethodException e) {
                // randomState doesn't exist in this version
            }

            // Find the generate method
            for (Method method : Structure.class.getMethods()) {
                if (!method.getName().equals("generate")) continue;

                Class<?>[] params = method.getParameterTypes();

                // Try 1.21+ signature (with Holder and ResourceKey first)
                if (params.length >= 11 && params[0].getName().contains("Holder")) {
                    try {
                        return (StructureStart) method.invoke(structure,
                            structureHolder,
                            level.dimension(),
                            registryAccess,
                            generator,
                            biomeSource,
                            randomState,
                            structureManager,
                            seed,
                            chunkPos,
                            0,
                            level,
                            (java.util.function.Predicate<?>) biome -> true
                        );
                    } catch (Exception e) {
                        Isotope.LOGGER.debug("1.21+ generate failed: {}", e.getMessage());
                    }
                }

                // Try 1.20.x signature (without Holder and ResourceKey)
                if (params.length >= 10 && params[0].getName().contains("RegistryAccess")) {
                    try {
                        return (StructureStart) method.invoke(structure,
                            registryAccess,
                            generator,
                            biomeSource,
                            randomState,
                            structureManager,
                            seed,
                            chunkPos,
                            0,
                            level,
                            (java.util.function.Predicate<?>) biome -> true
                        );
                    } catch (Exception e) {
                        Isotope.LOGGER.debug("1.20.x generate failed: {}", e.getMessage());
                    }
                }
            }

            Isotope.LOGGER.warn("Could not find compatible Structure.generate() method");
            return null;
        } catch (Exception e) {
            Isotope.LOGGER.error("Structure generation failed: {}", e.getMessage());
            return null;
        }
    }
}
