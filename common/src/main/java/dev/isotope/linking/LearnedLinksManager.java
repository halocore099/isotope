package dev.isotope.linking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.data.StructureLootLink;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists learned structure-loot links across game sessions.
 *
 * When links are verified through runtime observation, they are stored
 * persistently. On future sessions, these learned links can pre-populate
 * the linking system with high-confidence data.
 *
 * Storage: .minecraft/isotope/learned_links.json
 *
 * This creates a "memory" effect where the mod gets better at linking
 * over time as more observations are made.
 */
public final class LearnedLinksManager {

    private static final LearnedLinksManager INSTANCE = new LearnedLinksManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;

    // Unique key for a structure-loot link
    public record LinkKey(String structure, String lootTable) {
        public static LinkKey from(Identifier structure, Identifier lootTable) {
            return new LinkKey(structure.toString(), lootTable.toString());
        }

        public Identifier structureId() {
            return Identifier.tryParse(structure);
        }

        public Identifier lootTableId() {
            return Identifier.tryParse(lootTable);
        }
    }

    // Persisted learned link data
    public record LearnedLink(
        String structure,
        String lootTable,
        String confidence,
        String source,
        long firstSeen,
        long lastSeen,
        int observationCount,
        List<String> confirmingSources,
        String learnedVersion  // MC version when this link was learned/last verified
    ) {
        // Backwards-compatible constructor for deserialization of old data
        public LearnedLink {
            if (learnedVersion == null) {
                learnedVersion = "unknown";
            }
        }

        public Identifier structureId() {
            return Identifier.tryParse(structure);
        }

        public Identifier lootTableId() {
            return Identifier.tryParse(lootTable);
        }

        /**
         * Create a StructureLootLink from this learned link.
         * Applies confidence decay based on version age.
         */
        public StructureLootLink toStructureLootLink(String currentVersion) {
            Identifier structId = structureId();
            Identifier tableId = lootTableId();
            if (structId == null || tableId == null) return null;

            // Apply confidence decay based on version age
            int versionAge = calculateVersionAge(learnedVersion, currentVersion);
            return StructureLootLink.learned(structId, tableId, versionAge);
        }

        /**
         * Create a StructureLootLink without decay (for display purposes).
         */
        public StructureLootLink toStructureLootLink() {
            return toStructureLootLink(null);
        }
    }

    /**
     * Calculate version age (number of minor versions behind).
     * Returns 0 for current version, 1 for previous minor, 2+ for older.
     *
     * Version format: 1.21.4, 1.21, 1.20.6, etc.
     * Minor version is the second number (21 in 1.21.4).
     */
    private static int calculateVersionAge(String learnedVersion, String currentVersion) {
        if (learnedVersion == null || currentVersion == null ||
            learnedVersion.equals("unknown") || currentVersion.equals("unknown")) {
            return 0; // Unknown versions don't decay
        }

        int learnedMinor = extractMinorVersion(learnedVersion);
        int currentMinor = extractMinorVersion(currentVersion);

        if (learnedMinor < 0 || currentMinor < 0) {
            return 0; // Failed to parse, no decay
        }

        return Math.max(0, currentMinor - learnedMinor);
    }

    /**
     * Extract minor version number from MC version string.
     * Examples: "1.21.4" -> 21, "1.20" -> 20, "1.19.2" -> 19
     */
    private static int extractMinorVersion(String version) {
        if (version == null || version.isEmpty()) return -1;

        // Handle versions like "1.21.4", "1.21", "24w01a" (snapshots)
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    // Persisted data structure
    private record PersistedData(
        int version,
        long lastUpdated,
        String minecraftVersion,
        String isotopeVersion,
        List<LearnedLink> links,
        Statistics statistics
    ) {}

    public record Statistics(
        int totalSessions,
        int totalObservations,
        int structuresLinked,
        int lootTablesLinked
    ) {}

    // In-memory state
    private final Map<LinkKey, LearnedLink> learnedLinks = new LinkedHashMap<>();
    private final List<LearnedLinksListener> listeners = new CopyOnWriteArrayList<>();
    private Statistics statistics = new Statistics(0, 0, 0, 0);
    private boolean loaded = false;

    private LearnedLinksManager() {}

    public static LearnedLinksManager getInstance() {
        return INSTANCE;
    }

    /**
     * Record a verified link from runtime observation.
     * If already known, increments the observation count and refreshes the version.
     */
    public void recordVerifiedLink(StructureLootLink link) {
        if (link == null) return;

        LinkKey key = LinkKey.from(link.structureId(), link.lootTableId());
        long now = System.currentTimeMillis();
        String currentVersion = getMinecraftVersion();

        LearnedLink existing = learnedLinks.get(key);
        if (existing != null) {
            // Increment observation count and refresh version (resets decay)
            LearnedLink updated = new LearnedLink(
                existing.structure(),
                existing.lootTable(),
                link.confidence().name(),
                link.source().name(),
                existing.firstSeen(),
                now,
                existing.observationCount() + 1,
                mergeConfirmingSources(existing.confirmingSources(), link.source().name()),
                currentVersion  // Refresh version on re-verification
            );
            learnedLinks.put(key, updated);
        } else {
            // New link
            LearnedLink newLink = new LearnedLink(
                link.structureId().toString(),
                link.lootTableId().toString(),
                link.confidence().name(),
                link.source().name(),
                now,
                now,
                1,
                List.of(link.source().name()),
                currentVersion
            );
            learnedLinks.put(key, newLink);
        }

        // Update statistics
        updateStatistics();
        save();
        notifyListeners();
    }

    /**
     * Record multiple verified links at once (batch operation).
     */
    public void recordVerifiedLinks(Collection<StructureLootLink> links) {
        String currentVersion = getMinecraftVersion();
        long now = System.currentTimeMillis();

        for (StructureLootLink link : links) {
            if (link != null && link.isVerified()) {
                LinkKey key = LinkKey.from(link.structureId(), link.lootTableId());

                LearnedLink existing = learnedLinks.get(key);
                if (existing != null) {
                    LearnedLink updated = new LearnedLink(
                        existing.structure(),
                        existing.lootTable(),
                        link.confidence().name(),
                        link.source().name(),
                        existing.firstSeen(),
                        now,
                        existing.observationCount() + 1,
                        mergeConfirmingSources(existing.confirmingSources(), link.source().name()),
                        currentVersion  // Refresh version on re-verification
                    );
                    learnedLinks.put(key, updated);
                } else {
                    LearnedLink newLink = new LearnedLink(
                        link.structureId().toString(),
                        link.lootTableId().toString(),
                        link.confidence().name(),
                        link.source().name(),
                        now,
                        now,
                        1,
                        List.of(link.source().name()),
                        currentVersion
                    );
                    learnedLinks.put(key, newLink);
                }
            }
        }

        updateStatistics();
        save();
        notifyListeners();
    }

    private List<String> mergeConfirmingSources(List<String> existing, String newSource) {
        Set<String> sources = new LinkedHashSet<>(existing);
        sources.add(newSource);
        return new ArrayList<>(sources);
    }

    /**
     * Get all learned loot tables for a specific structure.
     */
    public Set<Identifier> getLearnedLootTables(Identifier structureId) {
        ensureLoaded();
        String structStr = structureId.toString();
        Set<Identifier> result = new HashSet<>();

        for (LearnedLink link : learnedLinks.values()) {
            if (link.structure().equals(structStr)) {
                Identifier tableId = link.lootTableId();
                if (tableId != null) {
                    result.add(tableId);
                }
            }
        }

        return result;
    }

    /**
     * Get all learned structures for a specific loot table.
     */
    public Set<Identifier> getLearnedStructures(Identifier lootTableId) {
        ensureLoaded();
        String tableStr = lootTableId.toString();
        Set<Identifier> result = new HashSet<>();

        for (LearnedLink link : learnedLinks.values()) {
            if (link.lootTable().equals(tableStr)) {
                Identifier structId = link.structureId();
                if (structId != null) {
                    result.add(structId);
                }
            }
        }

        return result;
    }

    /**
     * Build a map of structure -> loot tables for linking integration.
     */
    public Map<Identifier, Set<Identifier>> buildLearnedLinksMap() {
        ensureLoaded();
        Map<Identifier, Set<Identifier>> result = new LinkedHashMap<>();

        for (LearnedLink link : learnedLinks.values()) {
            Identifier structId = link.structureId();
            Identifier tableId = link.lootTableId();

            if (structId != null && tableId != null) {
                result.computeIfAbsent(structId, k -> new HashSet<>()).add(tableId);
            }
        }

        return result;
    }

    /**
     * Get all learned links as StructureLootLink objects.
     * Applies confidence decay based on version age.
     */
    public List<StructureLootLink> getAllAsLinks() {
        ensureLoaded();
        String currentVersion = getMinecraftVersion();
        List<StructureLootLink> result = new ArrayList<>();

        for (LearnedLink link : learnedLinks.values()) {
            StructureLootLink structLink = link.toStructureLootLink(currentVersion);
            if (structLink != null) {
                result.add(structLink);
            }
        }

        return result;
    }

    /**
     * Get learned links with decay info for display.
     * Returns entries with their version age.
     */
    public List<LearnedLinkWithDecay> getAllWithDecayInfo() {
        ensureLoaded();
        String currentVersion = getMinecraftVersion();
        List<LearnedLinkWithDecay> result = new ArrayList<>();

        for (LearnedLink link : learnedLinks.values()) {
            int versionAge = calculateVersionAge(link.learnedVersion(), currentVersion);
            result.add(new LearnedLinkWithDecay(link, versionAge, getDecayStatus(versionAge)));
        }

        return result;
    }

    /**
     * Get a human-readable decay status.
     */
    private static String getDecayStatus(int versionAge) {
        if (versionAge == 0) return "Current";
        if (versionAge == 1) return "Recent";
        if (versionAge == 2) return "Decayed";
        return "Stale (" + versionAge + " versions old)";
    }

    /**
     * Container for learned link with decay information.
     */
    public record LearnedLinkWithDecay(
        LearnedLink link,
        int versionAge,
        String decayStatus
    ) {
        public boolean isDecayed() {
            return versionAge >= 2;
        }

        public boolean isStale() {
            return versionAge >= 3;
        }
    }

    /**
     * Check if a specific link has been learned.
     */
    public boolean isLearned(Identifier structureId, Identifier lootTableId) {
        ensureLoaded();
        LinkKey key = LinkKey.from(structureId, lootTableId);
        return learnedLinks.containsKey(key);
    }

    /**
     * Get observation count for a specific link.
     */
    public int getObservationCount(Identifier structureId, Identifier lootTableId) {
        ensureLoaded();
        LinkKey key = LinkKey.from(structureId, lootTableId);
        LearnedLink link = learnedLinks.get(key);
        return link != null ? link.observationCount() : 0;
    }

    /**
     * Get total number of learned links.
     */
    public int size() {
        ensureLoaded();
        return learnedLinks.size();
    }

    /**
     * Get current statistics.
     */
    public Statistics getStatistics() {
        ensureLoaded();
        return statistics;
    }

    /**
     * Clear all learned data.
     */
    public void clear() {
        learnedLinks.clear();
        statistics = new Statistics(0, 0, 0, 0);
        save();
        notifyListeners();
    }

    /**
     * Purge stale links that are older than the specified version age.
     * This helps keep the learned links file clean and accurate.
     *
     * @param maxVersionAge Maximum allowed version age (e.g., 3 to keep 3 versions)
     * @return Number of links purged
     */
    public int purgeStaleLinks(int maxVersionAge) {
        ensureLoaded();
        String currentVersion = getMinecraftVersion();
        int purged = 0;

        Iterator<Map.Entry<LinkKey, LearnedLink>> it = learnedLinks.entrySet().iterator();
        while (it.hasNext()) {
            LearnedLink link = it.next().getValue();
            int versionAge = calculateVersionAge(link.learnedVersion(), currentVersion);
            if (versionAge > maxVersionAge) {
                it.remove();
                purged++;
            }
        }

        if (purged > 0) {
            updateStatistics();
            save();
            notifyListeners();
            Isotope.LOGGER.info("[LearnedLinksManager] Purged {} stale links (older than {} versions)",
                purged, maxVersionAge);
        }

        return purged;
    }

    /**
     * Get decay statistics for the current learned links.
     */
    public DecayStatistics getDecayStatistics() {
        ensureLoaded();
        String currentVersion = getMinecraftVersion();

        int current = 0, recent = 0, decayed = 0, stale = 0;

        for (LearnedLink link : learnedLinks.values()) {
            int versionAge = calculateVersionAge(link.learnedVersion(), currentVersion);
            if (versionAge == 0) current++;
            else if (versionAge == 1) recent++;
            else if (versionAge == 2) decayed++;
            else stale++;
        }

        return new DecayStatistics(current, recent, decayed, stale, learnedLinks.size());
    }

    /**
     * Decay statistics record.
     */
    public record DecayStatistics(
        int current,    // Version age 0
        int recent,     // Version age 1
        int decayed,    // Version age 2
        int stale,      // Version age 3+
        int total
    ) {
        public String summary() {
            return String.format("%d current, %d recent, %d decayed, %d stale (total: %d)",
                current, recent, decayed, stale, total);
        }
    }

    // Listeners

    public void addListener(LearnedLinksListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LearnedLinksListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (LearnedLinksListener listener : listeners) {
            listener.onLearnedLinksChanged();
        }
    }

    // Persistence

    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("isotope")
            .resolve("learned_links.json");
    }

    public void save() {
        try {
            Path path = getFilePath();
            Files.createDirectories(path.getParent());

            List<LearnedLink> linksList = new ArrayList<>(learnedLinks.values());

            PersistedData data = new PersistedData(
                VERSION,
                System.currentTimeMillis(),
                getMinecraftVersion(),
                getIsotopeVersion(),
                linksList,
                statistics
            );

            String json = GSON.toJson(data);
            Files.writeString(path, json);

            Isotope.LOGGER.debug("[LearnedLinksManager] Saved {} learned links", linksList.size());
        } catch (IOException e) {
            Isotope.LOGGER.error("[LearnedLinksManager] Failed to save learned links", e);
        }
    }

    public void load() {
        Path path = getFilePath();
        if (!Files.exists(path)) {
            Isotope.LOGGER.debug("[LearnedLinksManager] No learned links file found");
            return;
        }

        try {
            String json = Files.readString(path);
            PersistedData data = GSON.fromJson(json, PersistedData.class);

            learnedLinks.clear();
            if (data != null && data.links() != null) {
                for (LearnedLink link : data.links()) {
                    LinkKey key = new LinkKey(link.structure(), link.lootTable());
                    learnedLinks.put(key, link);
                }

                if (data.statistics() != null) {
                    statistics = data.statistics();
                }
            }

            Isotope.LOGGER.info("[LearnedLinksManager] Loaded {} learned links", learnedLinks.size());
        } catch (Exception e) {
            Isotope.LOGGER.error("[LearnedLinksManager] Failed to load learned links", e);
        }
    }

    private void updateStatistics() {
        Set<String> structures = new HashSet<>();
        Set<String> lootTables = new HashSet<>();
        int totalObs = 0;

        for (LearnedLink link : learnedLinks.values()) {
            structures.add(link.structure());
            lootTables.add(link.lootTable());
            totalObs += link.observationCount();
        }

        statistics = new Statistics(
            statistics.totalSessions() + 1,
            totalObs,
            structures.size(),
            lootTables.size()
        );
    }

    private String getMinecraftVersion() {
        try {
            // MC 1.21.6+ uses id() instead of getName()
            return net.minecraft.SharedConstants.getCurrentVersion().id();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getIsotopeVersion() {
        try {
            return dev.architectury.platform.Platform.getMod(Isotope.MOD_ID).getVersion();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @FunctionalInterface
    public interface LearnedLinksListener {
        void onLearnedLinksChanged();
    }
}
