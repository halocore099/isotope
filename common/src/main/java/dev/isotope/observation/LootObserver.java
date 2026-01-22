package dev.isotope.observation;

import dev.isotope.Isotope;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;

import dev.isotope.registry.StructureClassRegistry;

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
 *
 * Also tracks loot table assignments via setLootTable() hooks for
 * higher-confidence linking (captures direct causation, not just correlation).
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
    private final Map<Identifier, List<LootInvocation>> invocationsByTable = new ConcurrentHashMap<>();

    // Loot table assignments captured via setLootTable() hook
    private final Queue<LootAssignment> assignments = new ConcurrentLinkedQueue<>();

    // Quick lookup: caller origin -> assignments from that caller
    private final Map<String, List<LootAssignment>> assignmentsByOrigin = new ConcurrentHashMap<>();

    // Quick lookup: table -> all assignments of that table
    private final Map<Identifier, List<LootAssignment>> assignmentsByTable = new ConcurrentHashMap<>();

    /**
     * Record of a loot table being assigned to a container.
     * Captures the exact moment and calling context.
     */
    public record LootAssignment(
        Identifier tableId,
        String callerOrigin,      // e.g., "net.minecraft.world.level.levelgen.structure.StructureTemplate.placeInWorld"
        String callerNamespace,   // Extracted namespace: "minecraft" or mod ID
        BlockPos position,
        long timestamp
    ) {
        /**
         * Extract a structure/feature ID from the caller origin.
         * First tries exact lookup via StructureClassRegistry, then falls
         * back to heuristics to map class names to structure IDs.
         */
        public Optional<Identifier> inferStructureId() {
            if (callerOrigin == null || callerOrigin.equals("unknown")) {
                return Optional.empty();
            }

            // Layer 1: Try exact class registry lookup first
            // This provides 100% accurate structure identification when the class is known
            String className = callerOrigin.contains(".") ?
                callerOrigin.substring(0, callerOrigin.lastIndexOf('.')) : callerOrigin;

            StructureClassRegistry classRegistry = StructureClassRegistry.getInstance();
            if (classRegistry.isScanned()) {
                Optional<Identifier> exact = classRegistry.getStructureForClass(className);
                if (exact.isPresent()) {
                    return exact;
                }
            }

            // Layer 2: Heuristic fallback - extract from class name patterns
            String lowerOrigin = callerOrigin.toLowerCase();

            // Look for known structure patterns
            if (lowerOrigin.contains("village")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "village"));
            }
            if (lowerOrigin.contains("stronghold")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "stronghold"));
            }
            if (lowerOrigin.contains("mineshaft")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "mineshaft"));
            }
            if (lowerOrigin.contains("dungeon") || lowerOrigin.contains("monster_room")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "monster_room"));
            }
            if (lowerOrigin.contains("shipwreck")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "shipwreck"));
            }
            if (lowerOrigin.contains("ocean_ruin")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "ocean_ruin"));
            }
            if (lowerOrigin.contains("buried_treasure")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "buried_treasure"));
            }
            if (lowerOrigin.contains("end_city")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "end_city"));
            }
            if (lowerOrigin.contains("bastion")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "bastion_remnant"));
            }
            if (lowerOrigin.contains("fortress") || lowerOrigin.contains("nether_bridge")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "fortress"));
            }
            if (lowerOrigin.contains("ruined_portal")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "ruined_portal"));
            }
            if (lowerOrigin.contains("desert_pyramid")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "desert_pyramid"));
            }
            if (lowerOrigin.contains("jungle") && (lowerOrigin.contains("temple") || lowerOrigin.contains("pyramid"))) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "jungle_pyramid"));
            }
            if (lowerOrigin.contains("igloo")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "igloo"));
            }
            if (lowerOrigin.contains("pillager") || lowerOrigin.contains("outpost")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "pillager_outpost"));
            }
            if (lowerOrigin.contains("woodland") || lowerOrigin.contains("mansion")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "woodland_mansion"));
            }
            if (lowerOrigin.contains("ancient_city")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "ancient_city"));
            }
            if (lowerOrigin.contains("trial_chamber")) {
                return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, "trial_chambers"));
            }

            // For modded structures, try to extract from class name
            // e.g., "com.example.mymod.structures.CoolDungeonFeature" -> "mymod:cool_dungeon"
            if (!callerNamespace.equals("minecraft")) {
                String structureName = extractStructureNameFromClass(callerOrigin);
                if (structureName != null) {
                    return Optional.of(Identifier.fromNamespaceAndPath(callerNamespace, structureName));
                }
            }

            return Optional.empty();
        }

        private String extractStructureNameFromClass(String className) {
            // Try to extract a structure name from the class name
            // e.g., "VolcanoFeature" -> "volcano"
            // e.g., "CoolDungeonPiece" -> "cool_dungeon"

            int lastDot = className.lastIndexOf('.');
            String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;

            // Remove common suffixes
            simpleName = simpleName
                .replace("Feature", "")
                .replace("Structure", "")
                .replace("Piece", "")
                .replace("Template", "")
                .replace("Generator", "");

            // Convert CamelCase to snake_case
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < simpleName.length(); i++) {
                char c = simpleName.charAt(i);
                if (Character.isUpperCase(c) && i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            }

            String name = result.toString();
            return name.isEmpty() ? null : name;
        }
    }

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
        assignments.clear();
        assignmentsByOrigin.clear();
        assignmentsByTable.clear();
    }

    /**
     * Called by LootTableMixin when a loot table generates items.
     * This is the core observation hook.
     */
    public void onLootTableInvoked(
            Identifier tableId,
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
        List<Identifier> itemIds = new ArrayList<>();
        for (ItemStack stack : generatedItems) {
            if (!stack.isEmpty()) {
                stack.getItemHolder().unwrapKey().ifPresent(key ->
                    itemIds.add(key.id())
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
    public List<LootInvocation> getInvocationsOf(Identifier tableId) {
        return invocationsByTable.getOrDefault(tableId, List.of());
    }

    /**
     * Get all unique loot tables that were invoked.
     */
    public Set<Identifier> getObservedTables() {
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

    // --- Loot Assignment Tracking (from setLootTable() hook) ---

    /**
     * Called by RandomizableContainerMixin when a loot table is assigned to a container.
     * This captures the exact moment of assignment with caller context.
     */
    public void onLootAssigned(Identifier tableId, String callerOrigin, BlockPos pos) {
        if (!recording.get()) return;

        // Extract namespace from caller origin
        String namespace = extractNamespaceFromOrigin(callerOrigin);

        // Create assignment record
        LootAssignment assignment = new LootAssignment(
            tableId,
            callerOrigin,
            namespace,
            pos,
            System.currentTimeMillis()
        );

        // Store
        assignments.add(assignment);

        // Index by origin
        assignmentsByOrigin.computeIfAbsent(callerOrigin, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(assignment);

        // Index by table
        assignmentsByTable.computeIfAbsent(tableId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(assignment);

        Isotope.LOGGER.debug("[LootObserver] Captured assignment: {} from {} at {}",
            tableId, callerOrigin, pos);
    }

    /**
     * Extract a mod namespace from the caller origin (class name).
     * e.g., "com.example.mymod.structures.CoolFeature" -> "mymod"
     * e.g., "net.minecraft.world.level.levelgen..." -> "minecraft"
     */
    private String extractNamespaceFromOrigin(String callerOrigin) {
        if (callerOrigin == null || callerOrigin.equals("unknown")) {
            return "minecraft";
        }

        // Minecraft classes
        if (callerOrigin.startsWith("net.minecraft.") || callerOrigin.startsWith("com.mojang.")) {
            return "minecraft";
        }

        // Try to extract mod ID from package
        // Common patterns: com.author.modid, net.author.modid, modid.something
        String[] parts = callerOrigin.split("\\.");
        if (parts.length >= 3) {
            // Usually mod ID is the third component: com.author.modid.something
            String potentialModId = parts[2].toLowerCase();
            // Validate it looks like a mod ID (lowercase, no special chars except underscore)
            if (potentialModId.matches("[a-z0-9_]+") && potentialModId.length() >= 2) {
                return potentialModId;
            }
        }

        // Fallback: try first component if it looks like a mod ID
        if (parts.length > 0) {
            String first = parts[0].toLowerCase();
            if (!first.equals("com") && !first.equals("net") && !first.equals("org") &&
                first.matches("[a-z0-9_]+")) {
                return first;
            }
        }

        return "minecraft";
    }

    /**
     * Get all loot table assignments captured during this session.
     */
    public Collection<LootAssignment> getAllAssignments() {
        return Collections.unmodifiableCollection(assignments);
    }

    /**
     * Get assignments for a specific loot table.
     */
    public List<LootAssignment> getAssignmentsFor(Identifier tableId) {
        return assignmentsByTable.getOrDefault(tableId, List.of());
    }

    /**
     * Get all unique caller origins that assigned loot tables.
     */
    public Set<String> getAssignmentOrigins() {
        return Collections.unmodifiableSet(assignmentsByOrigin.keySet());
    }

    /**
     * Get all loot tables that were assigned from a specific origin.
     */
    public Set<Identifier> getTablesAssignedFrom(String origin) {
        List<LootAssignment> originAssignments = assignmentsByOrigin.get(origin);
        if (originAssignments == null) return Set.of();

        Set<Identifier> tables = new HashSet<>();
        for (LootAssignment assignment : originAssignments) {
            tables.add(assignment.tableId());
        }
        return tables;
    }

    /**
     * Get total assignment count.
     */
    public int getTotalAssignments() {
        return assignments.size();
    }

    /**
     * Build a map of inferred structure/feature IDs to their assigned loot tables.
     * This is used by the linker to create RUNTIME_ASSIGNED confidence links.
     */
    public Map<Identifier, Set<Identifier>> buildAssignmentLinks() {
        Map<Identifier, Set<Identifier>> links = new HashMap<>();

        for (LootAssignment assignment : assignments) {
            assignment.inferStructureId().ifPresent(structureId -> {
                links.computeIfAbsent(structureId, k -> new HashSet<>()).add(assignment.tableId());
            });
        }

        return links;
    }
}
