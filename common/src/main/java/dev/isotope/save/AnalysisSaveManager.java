package dev.isotope.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.registry.StructureRegistry;
import dev.isotope.save.AnalysisSave.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages saving and loading analysis data to/from disk.
 * Saves are stored in .minecraft/isotope/analyses/
 *
 * This is the persistence layer for the modpack IDE - all work should
 * be saveable and loadable between sessions.
 */
public final class AnalysisSaveManager {

    private static final AnalysisSaveManager INSTANCE = new AnalysisSaveManager();
    private static final String ISOTOPE_VERSION = "0.1.0";
    private static final SimpleDateFormat FILENAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private Path savesDirectory;

    // Currently loaded save (for tracking edits)
    private AnalysisSave currentSave;
    private String currentSaveId;

    private AnalysisSaveManager() {}

    public static AnalysisSaveManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the saves directory. Must be called after Minecraft is initialized.
     */
    public void init() {
        Minecraft minecraft = Minecraft.getInstance();
        this.savesDirectory = minecraft.gameDirectory.toPath()
            .resolve("isotope")
            .resolve("analyses");

        try {
            Files.createDirectories(savesDirectory);
            Isotope.LOGGER.info("ISOTOPE saves directory: {}", savesDirectory);
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to create saves directory", e);
        }
    }

    /**
     * Get the saves directory path.
     */
    public Path getSavesDirectory() {
        if (savesDirectory == null) {
            init();
        }
        return savesDirectory;
    }

    /**
     * Save the current registry state to a new file.
     * @param name Optional custom name for the save (null = auto-generated timestamp name)
     * @return The created save metadata, or empty if failed
     */
    public Optional<SaveMetadata> saveCurrentAnalysis(String name) {
        try {
            // Build save from current registry state
            AnalysisSave save = buildSaveFromCurrentState(name);

            // Write to file
            String filename = generateFilename(save);
            Path savePath = getSavesDirectory().resolve(filename);

            String json = gson.toJson(save);
            Files.writeString(savePath, json, StandardCharsets.UTF_8);

            currentSave = save;
            currentSaveId = save.id();

            Isotope.LOGGER.info("Saved analysis to: {}", savePath);
            return Optional.of(save.toMetadata());

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to save analysis", e);
            return Optional.empty();
        }
    }

    /**
     * Build an AnalysisSave from current registry state.
     */
    private AnalysisSave buildSaveFromCurrentState(String customName) {
        String name = customName;
        if (name == null || name.isEmpty()) {
            name = "Analysis " + FILENAME_DATE_FORMAT.format(new Date());
        }

        // Get minecraft version
        String mcVersion = SharedConstants.getCurrentVersion().getName();

        // Convert structures
        List<SavedStructure> structures = StructureRegistry.getInstance().getAll().stream()
            .map(SavedStructure::from)
            .toList();

        // Convert loot tables
        List<SavedLootTable> lootTables = LootTableRegistry.getInstance().getAll().stream()
            .map(SavedLootTable::from)
            .toList();

        // Convert links
        List<SavedStructureLootLink> links = StructureLootLinker.getInstance().getAllLinks().stream()
            .map(SavedStructureLootLink::from)
            .toList();

        // Config (default for now)
        SavedAnalysisConfig config = new SavedAnalysisConfig(100, true);

        // No samples yet (future: from AnalysisEngine)
        Map<String, SavedLootSample> samples = new HashMap<>();

        return AnalysisSave.create(
            name,
            mcVersion,
            ISOTOPE_VERSION,
            config,
            structures,
            lootTables,
            links,
            samples
        );
    }

    private String generateFilename(AnalysisSave save) {
        // Sanitize name for filename
        String safeName = save.name()
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .toLowerCase();
        if (safeName.length() > 30) {
            safeName = safeName.substring(0, 30);
        }
        return safeName + "_" + save.id().substring(0, 8) + ".json";
    }

    /**
     * List all available saves with metadata.
     */
    public List<SaveMetadata> listSaves() {
        List<SaveMetadata> result = new ArrayList<>();

        try (Stream<Path> files = Files.list(getSavesDirectory())) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        // Read and parse just enough for metadata
                        String json = Files.readString(path, StandardCharsets.UTF_8);
                        AnalysisSave save = gson.fromJson(json, AnalysisSave.class);
                        if (save != null) {
                            result.add(save.toMetadata());
                        }
                    } catch (Exception e) {
                        Isotope.LOGGER.warn("Failed to read save metadata: {}", path, e);
                    }
                });
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to list saves", e);
        }

        // Sort by timestamp, newest first
        result.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return result;
    }

    /**
     * Load a full save by ID.
     * @param saveId The ID of the save to load
     * @return The loaded save, or empty if not found
     */
    public Optional<AnalysisSave> loadSave(String saveId) {
        try (Stream<Path> files = Files.list(getSavesDirectory())) {
            Optional<Path> savePath = files
                .filter(p -> p.toString().endsWith(".json"))
                .filter(path -> {
                    try {
                        String json = Files.readString(path, StandardCharsets.UTF_8);
                        AnalysisSave save = gson.fromJson(json, AnalysisSave.class);
                        return save != null && saveId.equals(save.id());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst();

            if (savePath.isPresent()) {
                String json = Files.readString(savePath.get(), StandardCharsets.UTF_8);
                AnalysisSave save = gson.fromJson(json, AnalysisSave.class);

                // Restore to registries
                restoreToRegistries(save);

                currentSave = save;
                currentSaveId = save.id();

                Isotope.LOGGER.info("Loaded save: {} ({} structures, {} tables, {} links)",
                    save.name(), save.structures().size(), save.lootTables().size(), save.links().size());

                return Optional.of(save);
            }
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to load save: {}", saveId, e);
        }

        return Optional.empty();
    }

    /**
     * Restore a save's data to the registries.
     */
    private void restoreToRegistries(AnalysisSave save) {
        // Clear existing data
        StructureRegistry.getInstance().reset();
        LootTableRegistry.getInstance().reset();
        StructureLootLinker.getInstance().reset();

        // Restore structures
        for (SavedStructure ss : save.structures()) {
            StructureRegistry.getInstance().addFromSave(ss.toStructureInfo());
        }

        // Restore loot tables
        for (SavedLootTable slt : save.lootTables()) {
            LootTableRegistry.getInstance().addFromSave(slt.toLootTableInfo());
        }

        // Restore links
        for (SavedStructureLootLink link : save.links()) {
            StructureLootLinker.getInstance().addLink(link.toLink());
        }

        Isotope.LOGGER.info("Restored registries from save");
    }

    /**
     * Delete a save by ID.
     */
    public boolean deleteSave(String saveId) {
        try (Stream<Path> files = Files.list(getSavesDirectory())) {
            Optional<Path> savePath = files
                .filter(p -> p.toString().endsWith(".json"))
                .filter(path -> {
                    try {
                        String json = Files.readString(path, StandardCharsets.UTF_8);
                        AnalysisSave save = gson.fromJson(json, AnalysisSave.class);
                        return save != null && saveId.equals(save.id());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst();

            if (savePath.isPresent()) {
                Files.delete(savePath.get());
                Isotope.LOGGER.info("Deleted save: {}", saveId);

                if (saveId.equals(currentSaveId)) {
                    currentSave = null;
                    currentSaveId = null;
                }
                return true;
            }
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to delete save: {}", saveId, e);
        }
        return false;
    }

    /**
     * Check if any saves exist.
     */
    public boolean hasSaves() {
        try (Stream<Path> files = Files.list(getSavesDirectory())) {
            return files.anyMatch(p -> p.toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the currently loaded save (if any).
     */
    public Optional<AnalysisSave> getCurrentSave() {
        return Optional.ofNullable(currentSave);
    }

    /**
     * Check if there's a current save loaded.
     */
    public boolean hasCurrentSave() {
        return currentSave != null;
    }

    /**
     * Clear the current save reference (for new analysis).
     */
    public void clearCurrentSave() {
        currentSave = null;
        currentSaveId = null;
    }
}
