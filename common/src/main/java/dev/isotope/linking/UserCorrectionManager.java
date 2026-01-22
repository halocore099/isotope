package dev.isotope.linking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.data.StructureLootLink;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists user corrections (manual link additions and removals) across sessions.
 *
 * When users manually add or remove links through the UI, these corrections are
 * stored persistently. This creates a feedback loop where the system learns from
 * user corrections to improve future linking accuracy.
 *
 * Storage: .minecraft/isotope/user_corrections.json
 *
 * Correction Types:
 * - ADDED: User manually added a link that wasn't auto-detected (false negative correction)
 * - REMOVED: User manually removed an auto-detected link (false positive correction)
 * - RESTORED: User restored a previously removed link
 *
 * This data helps identify:
 * - Which automatic detections are wrong (false positives)
 * - Which links the system missed (false negatives)
 * - Patterns in user preferences
 */
public final class UserCorrectionManager {

    private static final UserCorrectionManager INSTANCE = new UserCorrectionManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;

    /**
     * Type of user correction.
     */
    public enum CorrectionType {
        ADDED,      // User added a link (false negative correction)
        REMOVED,    // User removed a link (false positive correction)
        RESTORED    // User restored a previously removed link
    }

    /**
     * A single user correction record.
     */
    public record UserCorrection(
        String structure,
        String lootTable,
        CorrectionType type,
        long timestamp,
        String originalSource,    // What source suggested this link before correction (if any)
        String originalConfidence, // What confidence level it had before correction
        String minecraftVersion,
        String note               // Optional user note
    ) {
        public ResourceLocation structureId() {
            return ResourceLocation.tryParse(structure);
        }

        public ResourceLocation lootTableId() {
            return ResourceLocation.tryParse(lootTable);
        }

        /**
         * Check if this is a positive correction (user says link IS valid).
         */
        public boolean isPositive() {
            return type == CorrectionType.ADDED || type == CorrectionType.RESTORED;
        }

        /**
         * Check if this is a negative correction (user says link is NOT valid).
         */
        public boolean isNegative() {
            return type == CorrectionType.REMOVED;
        }
    }

    /**
     * Key for identifying a structure-loot pair.
     */
    public record CorrectionKey(String structure, String lootTable) {
        public static CorrectionKey from(ResourceLocation structure, ResourceLocation lootTable) {
            return new CorrectionKey(structure.toString(), lootTable.toString());
        }
    }

    /**
     * Persisted data structure.
     */
    private record PersistedData(
        int version,
        long lastUpdated,
        String minecraftVersion,
        List<UserCorrection> corrections,
        CorrectionStatistics statistics
    ) {}

    /**
     * Statistics about user corrections.
     */
    public record CorrectionStatistics(
        int totalCorrections,
        int addedCount,      // False negatives corrected
        int removedCount,    // False positives corrected
        int restoredCount,
        int uniqueStructures,
        int uniqueLootTables
    ) {
        public String summary() {
            return String.format("%d corrections (%d added, %d removed, %d restored)",
                totalCorrections, addedCount, removedCount, restoredCount);
        }
    }

    // In-memory state - keyed by structure+lootTable for fast lookup
    private final Map<CorrectionKey, UserCorrection> corrections = new LinkedHashMap<>();
    private final List<CorrectionListener> listeners = new CopyOnWriteArrayList<>();
    private boolean loaded = false;

    private UserCorrectionManager() {}

    public static UserCorrectionManager getInstance() {
        return INSTANCE;
    }

    // --- Recording Corrections ---

    /**
     * Record that the user manually added a link.
     * This indicates a false negative - the system should have detected this link.
     */
    public void recordAddedLink(ResourceLocation structureId, ResourceLocation lootTableId,
                                 String originalSource, String originalConfidence) {
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);

        UserCorrection correction = new UserCorrection(
            structureId.toString(),
            lootTableId.toString(),
            CorrectionType.ADDED,
            System.currentTimeMillis(),
            originalSource,
            originalConfidence,
            getMinecraftVersion(),
            null
        );

        corrections.put(key, correction);
        save();
        notifyListeners();

        Isotope.LOGGER.info("[UserCorrectionManager] Recorded ADDED correction: {} -> {}",
            structureId, lootTableId);
    }

    /**
     * Record that the user manually removed a link.
     * This indicates a false positive - the system incorrectly suggested this link.
     */
    public void recordRemovedLink(ResourceLocation structureId, ResourceLocation lootTableId,
                                   String originalSource, String originalConfidence) {
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);

        UserCorrection correction = new UserCorrection(
            structureId.toString(),
            lootTableId.toString(),
            CorrectionType.REMOVED,
            System.currentTimeMillis(),
            originalSource,
            originalConfidence,
            getMinecraftVersion(),
            null
        );

        corrections.put(key, correction);
        save();
        notifyListeners();

        Isotope.LOGGER.info("[UserCorrectionManager] Recorded REMOVED correction: {} -> {} (was: {})",
            structureId, lootTableId, originalSource);
    }

    /**
     * Record that the user restored a previously removed link.
     */
    public void recordRestoredLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);

        // Check if there was a previous removal
        UserCorrection previous = corrections.get(key);
        String originalSource = previous != null ? previous.originalSource() : null;
        String originalConfidence = previous != null ? previous.originalConfidence() : null;

        UserCorrection correction = new UserCorrection(
            structureId.toString(),
            lootTableId.toString(),
            CorrectionType.RESTORED,
            System.currentTimeMillis(),
            originalSource,
            originalConfidence,
            getMinecraftVersion(),
            null
        );

        corrections.put(key, correction);
        save();
        notifyListeners();

        Isotope.LOGGER.info("[UserCorrectionManager] Recorded RESTORED correction: {} -> {}",
            structureId, lootTableId);
    }

    // --- Query API ---

    /**
     * Check if user has added this link manually.
     */
    public boolean isUserAdded(ResourceLocation structureId, ResourceLocation lootTableId) {
        ensureLoaded();
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);
        UserCorrection correction = corrections.get(key);
        return correction != null && correction.type() == CorrectionType.ADDED;
    }

    /**
     * Check if user has removed this link.
     */
    public boolean isUserRemoved(ResourceLocation structureId, ResourceLocation lootTableId) {
        ensureLoaded();
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);
        UserCorrection correction = corrections.get(key);
        return correction != null && correction.type() == CorrectionType.REMOVED;
    }

    /**
     * Check if user has any correction for this link.
     */
    public Optional<UserCorrection> getCorrection(ResourceLocation structureId, ResourceLocation lootTableId) {
        ensureLoaded();
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);
        return Optional.ofNullable(corrections.get(key));
    }

    /**
     * Get all user-added links (for use in linking).
     */
    public Set<StructureLootLink> getUserAddedLinks() {
        ensureLoaded();
        Set<StructureLootLink> result = new LinkedHashSet<>();

        for (UserCorrection correction : corrections.values()) {
            if (correction.isPositive()) {
                ResourceLocation structId = correction.structureId();
                ResourceLocation tableId = correction.lootTableId();
                if (structId != null && tableId != null) {
                    result.add(StructureLootLink.manual(structId, tableId));
                }
            }
        }

        return result;
    }

    /**
     * Get all user-removed link pairs (for filtering during linking).
     */
    public Set<CorrectionKey> getUserRemovedLinks() {
        ensureLoaded();
        Set<CorrectionKey> result = new LinkedHashSet<>();

        for (Map.Entry<CorrectionKey, UserCorrection> entry : corrections.entrySet()) {
            if (entry.getValue().type() == CorrectionType.REMOVED) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    /**
     * Get all corrections.
     */
    public List<UserCorrection> getAllCorrections() {
        ensureLoaded();
        return new ArrayList<>(corrections.values());
    }

    /**
     * Get corrections for a specific structure.
     */
    public List<UserCorrection> getCorrectionsForStructure(ResourceLocation structureId) {
        ensureLoaded();
        String structStr = structureId.toString();
        List<UserCorrection> result = new ArrayList<>();

        for (UserCorrection correction : corrections.values()) {
            if (correction.structure().equals(structStr)) {
                result.add(correction);
            }
        }

        return result;
    }

    /**
     * Get number of corrections.
     */
    public int size() {
        ensureLoaded();
        return corrections.size();
    }

    /**
     * Get correction statistics.
     */
    public CorrectionStatistics getStatistics() {
        ensureLoaded();

        int added = 0, removed = 0, restored = 0;
        Set<String> structures = new HashSet<>();
        Set<String> lootTables = new HashSet<>();

        for (UserCorrection correction : corrections.values()) {
            switch (correction.type()) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case RESTORED -> restored++;
            }
            structures.add(correction.structure());
            lootTables.add(correction.lootTable());
        }

        return new CorrectionStatistics(
            corrections.size(),
            added,
            removed,
            restored,
            structures.size(),
            lootTables.size()
        );
    }

    /**
     * Clear a specific correction.
     */
    public void clearCorrection(ResourceLocation structureId, ResourceLocation lootTableId) {
        CorrectionKey key = CorrectionKey.from(structureId, lootTableId);
        if (corrections.remove(key) != null) {
            save();
            notifyListeners();
        }
    }

    /**
     * Clear all corrections.
     */
    public void clear() {
        corrections.clear();
        save();
        notifyListeners();
    }

    // --- Listeners ---

    public void addListener(CorrectionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CorrectionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (CorrectionListener listener : listeners) {
            listener.onCorrectionsChanged();
        }
    }

    // --- Persistence ---

    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("isotope")
            .resolve("user_corrections.json");
    }

    public void save() {
        try {
            Path path = getFilePath();
            Files.createDirectories(path.getParent());

            List<UserCorrection> correctionsList = new ArrayList<>(corrections.values());

            PersistedData data = new PersistedData(
                VERSION,
                System.currentTimeMillis(),
                getMinecraftVersion(),
                correctionsList,
                getStatistics()
            );

            String json = GSON.toJson(data);
            Files.writeString(path, json);

            Isotope.LOGGER.debug("[UserCorrectionManager] Saved {} corrections", correctionsList.size());
        } catch (IOException e) {
            Isotope.LOGGER.error("[UserCorrectionManager] Failed to save corrections", e);
        }
    }

    public void load() {
        Path path = getFilePath();
        if (!Files.exists(path)) {
            Isotope.LOGGER.debug("[UserCorrectionManager] No corrections file found");
            return;
        }

        try {
            String json = Files.readString(path);
            PersistedData data = GSON.fromJson(json, PersistedData.class);

            corrections.clear();
            if (data != null && data.corrections() != null) {
                for (UserCorrection correction : data.corrections()) {
                    CorrectionKey key = new CorrectionKey(correction.structure(), correction.lootTable());
                    corrections.put(key, correction);
                }
            }

            Isotope.LOGGER.info("[UserCorrectionManager] Loaded {} corrections", corrections.size());
        } catch (Exception e) {
            Isotope.LOGGER.error("[UserCorrectionManager] Failed to load corrections", e);
        }
    }

    private String getMinecraftVersion() {
        try {
            // MC 1.21.6+ uses id() instead of getName()
            return net.minecraft.SharedConstants.getCurrentVersion().id();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @FunctionalInterface
    public interface CorrectionListener {
        void onCorrectionsChanged();
    }
}
