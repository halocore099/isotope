package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.compat.RegistryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages test arena for structure loot testing.
 *
 * Creates a test environment with multiple copies of a structure
 * spawned in a grid pattern for easy loot comparison testing.
 *
 * Note: Full void world creation requires more complex APIs.
 * This implementation spawns structures in the current world instead.
 */
public final class TestArenaManager {

    private static final TestArenaManager INSTANCE = new TestArenaManager();

    // Default settings
    private static final int DEFAULT_STRUCTURE_COUNT = 16; // 4x4 grid
    private static final int STRUCTURE_SPACING = 80; // Blocks between structures
    private static final int PLATFORM_Y = 100; // Y level for spawning

    @Nullable
    private ResourceLocation currentStructure;
    private int structureCount = DEFAULT_STRUCTURE_COUNT;
    private boolean isArenaActive = false;

    private TestArenaManager() {}

    public static TestArenaManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set the number of structures to spawn in the arena.
     */
    public void setStructureCount(int count) {
        this.structureCount = Math.max(1, Math.min(64, count)); // Clamp 1-64
    }

    /**
     * Get the current structure count setting.
     */
    public int getStructureCount() {
        return structureCount;
    }

    /**
     * Check if arena is currently active.
     */
    public boolean isArenaActive() {
        return isArenaActive;
    }

    /**
     * Get the structure being tested.
     */
    @Nullable
    public ResourceLocation getCurrentStructure() {
        return currentStructure;
    }

    /**
     * Create a test arena by spawning multiple structures in the current world.
     * Player will be teleported to an area with N structures spawned in a grid.
     *
     * @param structureId The structure to spawn
     * @param progressCallback Callback for progress updates
     */
    public void createArena(ResourceLocation structureId, Consumer<String> progressCallback) {
        this.currentStructure = structureId;

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();

        if (server == null) {
            progressCallback.accept("Error: Not in singleplayer world");
            return;
        }

        progressCallback.accept("Creating test arena...");

        // Run on server thread
        server.execute(() -> {
            spawnStructuresInArena(server, structureId, progressCallback);
        });

        isArenaActive = true;
    }

    /**
     * Spawn multiple copies of a structure in a grid pattern.
     */
    private void spawnStructuresInArena(MinecraftServer server, ResourceLocation structureId,
                                        Consumer<String> progressCallback) {
        ServerLevel level = server.overworld();
        if (level == null) {
            notifyClient(progressCallback, "Error: No overworld");
            return;
        }

        // Get structure from registry
        Registry<Structure> structureRegistry = RegistryHelper.getStructureRegistry(level.registryAccess());

        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
        Optional<Holder.Reference<Structure>> structureHolder = structureRegistry.get(structureKey);

        if (structureHolder.isEmpty()) {
            notifyClient(progressCallback, "Error: Unknown structure " + structureId);
            return;
        }

        Structure structure = structureHolder.get().value();

        // Calculate grid dimensions
        int gridSize = (int) Math.ceil(Math.sqrt(structureCount));

        // Find a good starting position (away from spawn)
        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
            ? null : server.getPlayerList().getPlayers().get(0);

        BlockPos basePos;
        if (player != null) {
            // Start 500 blocks away from player in +X +Z direction
            basePos = player.blockPosition().offset(500, 0, 500);
        } else {
            basePos = new BlockPos(1000, PLATFORM_Y, 1000);
        }

        int startX = basePos.getX() - (gridSize * STRUCTURE_SPACING) / 2;
        int startZ = basePos.getZ() - (gridSize * STRUCTURE_SPACING) / 2;

        notifyClient(progressCallback, "Spawning " + structureCount + " structures...");

        // Spawn structures in grid
        RandomSource random = level.getRandom();
        int spawned = 0;
        int failed = 0;

        for (int gz = 0; gz < gridSize && spawned < structureCount; gz++) {
            for (int gx = 0; gx < gridSize && spawned < structureCount; gx++) {
                int x = startX + gx * STRUCTURE_SPACING;
                int z = startZ + gz * STRUCTURE_SPACING;

                BlockPos pos = new BlockPos(x, PLATFORM_Y + 1, z);

                try {
                    boolean success = spawnStructure(level, structure, structureHolder.get(), pos, random);
                    if (success) {
                        spawned++;
                        final int count = spawned;
                        notifyClient(progressCallback, "Spawned " + count + "/" + structureCount);
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    Isotope.LOGGER.warn("Failed to spawn structure at {}: {}", pos, e.getMessage());
                    failed++;
                }
            }
        }

        // Teleport player to center of arena
        if (player != null) {
            int centerX = startX + (gridSize * STRUCTURE_SPACING) / 2;
            int centerZ = startZ + (gridSize * STRUCTURE_SPACING) / 2;

            player.teleportTo(level, centerX, PLATFORM_Y + 10, centerZ,
                Set.of(), 0, 0, true);
            player.setGameMode(GameType.CREATIVE);
        }

        final int finalSpawned = spawned;
        final int finalFailed = failed;
        notifyClient(progressCallback, "Arena ready! " + finalSpawned + " structures" +
            (finalFailed > 0 ? " (" + finalFailed + " failed)" : ""));
        Isotope.LOGGER.info("Test arena created with {} copies of {} ({} failed)",
            finalSpawned, structureId, finalFailed);
    }

    /**
     * Spawn a single structure at a position.
     */
    private boolean spawnStructure(ServerLevel level, Structure structure,
                                   Holder<Structure> structureHolder,
                                   BlockPos pos, RandomSource random) {
        try {
            ChunkPos chunkPos = new ChunkPos(pos);

            // Create structure start
            StructureStart start = structure.generate(
                structureHolder,
                level.dimension(),
                level.registryAccess(),
                level.getChunkSource().getGenerator(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed() + pos.hashCode(), // Unique seed per position
                chunkPos,
                0, // References
                level,
                biome -> true // Accept all biomes
            );

            if (start == null || !start.isValid()) {
                Isotope.LOGGER.debug("Structure generation returned invalid start at {}", pos);
                return false;
            }

            // Force load chunks around the structure
            BoundingBox boundingBox = start.getBoundingBox();
            int minCX = boundingBox.minX() >> 4;
            int maxCX = boundingBox.maxX() >> 4;
            int minCZ = boundingBox.minZ() >> 4;
            int maxCZ = boundingBox.maxZ() >> 4;

            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    level.getChunk(cx, cz);
                }
            }

            // Place structure pieces
            start.placeInChunk(
                level,
                level.structureManager(),
                level.getChunkSource().getGenerator(),
                random,
                boundingBox,
                chunkPos
            );

            return true;

        } catch (Exception e) {
            Isotope.LOGGER.debug("Structure spawn failed at {}: {}", pos, e.getMessage());
            return false;
        }
    }

    /**
     * Create a glass platform for the arena.
     */
    private void createGlassPlatform(ServerLevel level, int startX, int startZ, int width, int depth) {
        for (int x = startX; x < startX + width; x++) {
            for (int z = startZ; z < startZ + depth; z++) {
                level.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.GLASS.defaultBlockState(), 2);
            }
        }
    }

    /**
     * Notify the client on the main thread.
     */
    private void notifyClient(Consumer<String> callback, String message) {
        Minecraft.getInstance().execute(() -> callback.accept(message));
    }

    /**
     * Exit the arena.
     */
    public void exitArena() {
        isArenaActive = false;
        currentStructure = null;
    }
}
