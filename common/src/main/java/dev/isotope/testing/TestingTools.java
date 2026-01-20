package dev.isotope.testing;

import dev.isotope.Isotope;
import dev.isotope.compat.RegistryHelper;
import dev.isotope.compat.VersionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Utility class for in-world testing operations.
 *
 * Provides tools for:
 * - Locating structures in the world
 * - Teleporting players to structures
 * - Creating test arenas with multiple structure copies
 */
public final class TestingTools {

    private TestingTools() {}

    /**
     * Result of a structure locate operation.
     */
    public record LocateResult(
        boolean found,
        @Nullable BlockPos position,
        @Nullable String structureName,
        int distance,
        @Nullable String error
    ) {
        public static LocateResult success(BlockPos pos, String name, int distance) {
            return new LocateResult(true, pos, name, distance, null);
        }

        public static LocateResult notFound(String structureName) {
            return new LocateResult(false, null, structureName, -1, "Structure not found within search radius");
        }

        public static LocateResult error(String message) {
            return new LocateResult(false, null, null, -1, message);
        }
    }

    /**
     * Locate the nearest instance of a structure.
     *
     * @param server The Minecraft server
     * @param structureId The structure's resource location
     * @param searchRadius Search radius in chunks (default: 100)
     * @return LocateResult with position if found
     */
    public static LocateResult locateStructure(MinecraftServer server, ResourceLocation structureId, int searchRadius) {
        try {
            // Get the overworld (most structures are there)
            ServerLevel level = server.overworld();
            if (level == null) {
                return LocateResult.error("No overworld available");
            }

            // Get player position as search origin
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
                ? null
                : server.getPlayerList().getPlayers().get(0);

            if (player == null) {
                return LocateResult.error("No player found");
            }

            BlockPos playerPos = player.blockPosition();

            // Get structure registry
            Registry<Structure> structureRegistry = RegistryHelper.getStructureRegistry(level.registryAccess());

            // Find the structure holder using version-compatible method
            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
            Holder<Structure> structureHolder = getStructureHolder(structureRegistry, structureKey);
            if (structureHolder == null) {
                return LocateResult.error("Unknown structure: " + structureId);
            }

            // Create a HolderSet with just this structure
            HolderSet<Structure> holderSet = HolderSet.direct(structureHolder);

            // Locate the structure
            var result = level.getChunkSource().getGenerator().findNearestMapStructure(
                level,
                holderSet,
                playerPos,
                searchRadius,
                false // Don't skip existing chunks
            );

            if (result == null) {
                return LocateResult.notFound(structureId.toString());
            }

            BlockPos structurePos = result.getFirst();
            int distance = (int) Math.sqrt(playerPos.distSqr(structurePos));

            Isotope.LOGGER.info("Located {} at {} (distance: {} blocks)",
                structureId, structurePos, distance);

            return LocateResult.success(structurePos, structureId.toString(), distance);

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to locate structure {}: {}", structureId, e.getMessage());
            return LocateResult.error("Locate failed: " + e.getMessage());
        }
    }

    /**
     * Teleport a player to a position.
     *
     * @param server The Minecraft server
     * @param position Target position
     * @return true if teleport succeeded
     */
    public static boolean teleportPlayer(MinecraftServer server, BlockPos position) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
                ? null
                : server.getPlayerList().getPlayers().get(0);

            if (player == null) {
                Isotope.LOGGER.error("No player to teleport");
                return false;
            }

            // Teleport to slightly above the position to avoid suffocation
            double x = position.getX() + 0.5;
            double y = position.getY() + 1;
            double z = position.getZ() + 0.5;

            VersionHelper.teleportPlayer(player, player.serverLevel(), x, y, z,
                Set.of(), player.getYRot(), player.getXRot());

            Isotope.LOGGER.info("Teleported player to {}, {}, {}", x, y, z);
            return true;

        } catch (Exception e) {
            Isotope.LOGGER.error("Teleport failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Locate and teleport to a structure in one operation.
     *
     * @param server The Minecraft server
     * @param structureId The structure to find and teleport to
     * @return LocateResult with details
     */
    public static LocateResult locateAndTeleport(MinecraftServer server, ResourceLocation structureId) {
        LocateResult result = locateStructure(server, structureId, 100);

        if (result.found() && result.position() != null) {
            boolean teleported = teleportPlayer(server, result.position());
            if (!teleported) {
                return LocateResult.error("Found structure but teleport failed");
            }
        }

        return result;
    }

    /**
     * Check if we're in a singleplayer world (required for testing tools).
     */
    public static boolean isInSingleplayerWorld(MinecraftServer server) {
        return server != null && server.isSingleplayer();
    }

    /**
     * Get the current player's position.
     */
    @Nullable
    public static BlockPos getPlayerPosition(MinecraftServer server) {
        if (server == null) return null;

        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
            ? null
            : server.getPlayerList().getPlayers().get(0);

        return player != null ? player.blockPosition() : null;
    }

    /**
     * Get a structure holder using version-compatible reflection.
     *
     * 1.21+: Registry.get(key) returns Optional<Holder.Reference<T>>
     * 1.20.x: Registry.get(key) returns T directly (nullable)
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Holder<Structure> getStructureHolder(Registry<Structure> registry, ResourceKey<Structure> key) {
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
}
