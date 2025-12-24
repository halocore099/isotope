package dev.isotope.observation;

import dev.isotope.Isotope;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central observer that records all loot table invocations.
 * Mixins call into this to report when loot tables are executed.
 *
 * This is the core of ISOTOPE's observation model - we don't guess
 * what loot tables structures use, we watch them actually happen.
 */
public final class LootObserver {

    private static final LootObserver INSTANCE = new LootObserver();

    // Recording state
    private final AtomicBoolean recording = new AtomicBoolean(false);

    // All invocations observed during this session
    private final Queue<LootInvocation> invocations = new ConcurrentLinkedQueue<>();

    // Quick lookup: position -> invocations at that position
    private final Map<Long, List<LootInvocation>> invocationsByChunk = new ConcurrentHashMap<>();

    // Quick lookup: table -> all invocations of that table
    private final Map<ResourceLocation, List<LootInvocation>> invocationsByTable = new ConcurrentHashMap<>();

    private LootObserver() {}

    public static LootObserver getInstance() {
        return INSTANCE;
    }

    /**
     * Start recording loot table invocations.
     * Called when analysis begins.
     */
    public void startRecording() {
        clear();
        recording.set(true);
        Isotope.LOGGER.info("[LootObserver] Started recording loot table invocations");
    }

    /**
     * Stop recording and finalize observations.
     */
    public void stopRecording() {
        recording.set(false);
        Isotope.LOGGER.info("[LootObserver] Stopped recording. Total invocations: {}", invocations.size());
    }

    /**
     * Clear all recorded data.
     */
    public void clear() {
        invocations.clear();
        invocationsByChunk.clear();
        invocationsByTable.clear();
    }

    /**
     * Called by LootTableMixin when a loot table generates items.
     * This is the core observation hook.
     */
    public void onLootTableInvoked(
            ResourceLocation tableId,
            LootParams params,
            List<ItemStack> generatedItems) {

        if (!recording.get()) return;

        // Extract position from the level if available
        // In observation model, position is tracked via StructureObserver correlation
        BlockPos position = BlockPos.ZERO;
        String contextType = "OBSERVED";

        // Try to get the level and use its spawn position as reference
        // Actual position correlation happens in ObservationCorrelator
        try {
            if (params.getLevel() != null) {
                contextType = "LEVEL_CONTEXT";
            }
        } catch (Exception e) {
            // Level not available
        }

        // Convert items to resource locations
        List<ResourceLocation> itemIds = new ArrayList<>();
        for (ItemStack stack : generatedItems) {
            if (!stack.isEmpty()) {
                stack.getItemHolder().unwrapKey().ifPresent(key ->
                    itemIds.add(key.location())
                );
            }
        }

        // Create and store the invocation record
        LootInvocation invocation = new LootInvocation(
            tableId,
            position,
            System.currentTimeMillis(),
            contextType,
            itemIds
        );

        invocations.add(invocation);

        // Index by chunk
        long chunkKey = invocation.chunkKey();
        invocationsByChunk.computeIfAbsent(chunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(invocation);

        // Index by table
        invocationsByTable.computeIfAbsent(tableId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(invocation);

        Isotope.LOGGER.debug("[LootObserver] Recorded: {} at {} ({} items)",
            tableId, position, itemIds.size());
    }

    /**
     * Get all invocations near a position.
     * Used by ObservationCorrelator to link structure placements to loot.
     */
    public List<LootInvocation> getInvocationsNear(BlockPos center, int radius) {
        List<LootInvocation> result = new ArrayList<>();

        // Check nearby chunks
        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long chunkKey = ((long) (centerChunkX + dx) << 32) | ((long) (centerChunkZ + dz) & 0xFFFFFFFFL);
                List<LootInvocation> chunkInvocations = invocationsByChunk.get(chunkKey);

                if (chunkInvocations != null) {
                    for (LootInvocation inv : chunkInvocations) {
                        if (inv.isNear(center, radius)) {
                            result.add(inv);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Get all invocations of a specific loot table.
     */
    public List<LootInvocation> getInvocationsOf(ResourceLocation tableId) {
        return invocationsByTable.getOrDefault(tableId, List.of());
    }

    /**
     * Get all unique loot tables that were invoked.
     */
    public Set<ResourceLocation> getObservedTables() {
        return Collections.unmodifiableSet(invocationsByTable.keySet());
    }

    /**
     * Get total invocation count.
     */
    public int getTotalInvocations() {
        return invocations.size();
    }

    /**
     * Get all invocations.
     */
    public Collection<LootInvocation> getAllInvocations() {
        return Collections.unmodifiableCollection(invocations);
    }

    public boolean isRecording() {
        return recording.get();
    }
}
