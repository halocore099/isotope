package dev.isotope.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.observation.ObservationSession;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Manages export of observation data to JSON files.
 *
 * In the observation model, we export actual observed structure-loot relationships,
 * not heuristic guesses.
 */
public final class ExportManager {

    private static final ExportManager INSTANCE = new ExportManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportManager() {}

    public static ExportManager getInstance() {
        return INSTANCE;
    }

    /**
     * Export all observation data to JSON files.
     */
    public ExportResult exportAll(ExportConfig config, Consumer<String> progressCallback) {
        try {
            Path exportDir = getExportDirectory(config);
            Files.createDirectories(exportDir);

            progressCallback.accept("Exporting to: " + exportDir);

            List<String> exportedFiles = new ArrayList<>();

            // Export observed structures with their loot tables
            if (config.exportStructures()) {
                progressCallback.accept("Exporting observed structures...");
                Path structuresFile = exportDir.resolve("observed_structures.json");
                exportObservedStructures(structuresFile);
                exportedFiles.add("observed_structures.json");
            }

            // Export loot table invocations
            if (config.exportLootTables()) {
                progressCallback.accept("Exporting loot table observations...");
                Path lootFile = exportDir.resolve("observed_loot_tables.json");
                exportObservedLootTables(lootFile);
                exportedFiles.add("observed_loot_tables.json");
            }

            // Export summary
            progressCallback.accept("Exporting summary...");
            Path summaryFile = exportDir.resolve("summary.json");
            exportSummary(summaryFile, exportedFiles);
            exportedFiles.add("summary.json");

            progressCallback.accept("Export complete: " + exportedFiles.size() + " files");

            return new ExportResult(true, null, exportDir, exportedFiles);

        } catch (Exception e) {
            Isotope.LOGGER.error("Export failed", e);
            return new ExportResult(false, e.getMessage(), null, List.of());
        }
    }

    private Path getExportDirectory(ExportConfig config) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path baseDir = gameDir.resolve("isotope-export");

        if (config.timestampedFolder()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            return baseDir.resolve(timestamp);
        }
        return baseDir;
    }

    private void exportObservedStructures(Path file) throws IOException {
        List<Map<String, Object>> structures = new ArrayList<>();

        for (var data : ObservationSession.getInstance().getAllStructureData()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("structureId", data.structureId().toString());
            entry.put("namespace", data.structureId().getNamespace());
            entry.put("path", data.structureId().getPath());
            entry.put("hasLoot", data.hasLoot());
            entry.put("lootTableCount", data.lootTableCount());

            // Loot tables with invocation counts
            List<Map<String, Object>> lootTables = new ArrayList<>();
            for (ResourceLocation tableId : data.lootTables()) {
                Map<String, Object> tableEntry = new LinkedHashMap<>();
                tableEntry.put("tableId", tableId.toString());
                tableEntry.put("invocationCount", data.invocationCounts().getOrDefault(tableId, 0));
                lootTables.add(tableEntry);
            }
            entry.put("lootTables", lootTables);

            // Observed items
            List<String> items = data.observedItems().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
            entry.put("observedItems", items);

            // Placement info
            if (data.placement() != null) {
                Map<String, Object> placement = new LinkedHashMap<>();
                placement.put("source", data.placement().source().name());
                placement.put("origin", Map.of(
                    "x", data.placement().origin().getX(),
                    "y", data.placement().origin().getY(),
                    "z", data.placement().origin().getZ()
                ));
                if (data.placement().boundingBox() != null) {
                    var bounds = data.placement().boundingBox();
                    placement.put("bounds", Map.of(
                        "minX", bounds.minX(),
                        "minY", bounds.minY(),
                        "minZ", bounds.minZ(),
                        "maxX", bounds.maxX(),
                        "maxY", bounds.maxY(),
                        "maxZ", bounds.maxZ()
                    ));
                }
                entry.put("placement", placement);
            }

            structures.add(entry);
        }

        // Sort by ID
        structures.sort(Comparator.comparing(m -> (String) m.get("structureId")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportType", "observedStructures");
        root.put("dataSource", "OBSERVATION (ground truth)");
        root.put("count", structures.size());
        root.put("structures", structures);

        Files.writeString(file, GSON.toJson(root));
    }

    private void exportObservedLootTables(Path file) throws IOException {
        // Collect all unique loot tables with their source structures
        Map<ResourceLocation, Set<ResourceLocation>> tableToStructures = new LinkedHashMap<>();

        for (var data : ObservationSession.getInstance().getAllStructureData()) {
            for (ResourceLocation tableId : data.lootTables()) {
                tableToStructures.computeIfAbsent(tableId, k -> new LinkedHashSet<>())
                    .add(data.structureId());
            }
        }

        List<Map<String, Object>> tables = new ArrayList<>();
        for (var entry : tableToStructures.entrySet()) {
            Map<String, Object> tableEntry = new LinkedHashMap<>();
            tableEntry.put("tableId", entry.getKey().toString());
            tableEntry.put("namespace", entry.getKey().getNamespace());
            tableEntry.put("path", entry.getKey().getPath());
            tableEntry.put("usedByStructures", entry.getValue().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList());
            tableEntry.put("structureCount", entry.getValue().size());
            tables.add(tableEntry);
        }

        tables.sort(Comparator.comparing(m -> (String) m.get("tableId")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportType", "observedLootTables");
        root.put("dataSource", "OBSERVATION (ground truth)");
        root.put("count", tables.size());
        root.put("lootTables", tables);

        Files.writeString(file, GSON.toJson(root));
    }

    private void exportSummary(Path file, List<String> exportedFiles) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("exportTime", LocalDateTime.now().toString());
        summary.put("isotopeVersion", "0.1.0");
        summary.put("dataSource", "OBSERVATION (ground truth)");

        // Get session result if available
        var sessionResult = ObservationSession.getInstance().getLastResult();
        if (sessionResult.isPresent()) {
            var result = sessionResult.get();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("structuresObserved", result.structuresPlaced());
            stats.put("structuresFailed", result.structuresFailed());
            stats.put("structuresWithLoot", result.structuresWithLoot());
            stats.put("uniqueLootTables", result.uniqueLootTables());
            stats.put("lootInvocations", result.lootInvocations());
            summary.put("observationStats", stats);
        }

        summary.put("exportedFiles", exportedFiles);

        Files.writeString(file, GSON.toJson(summary));
    }

    // --- Inner classes ---

    public record ExportConfig(
        boolean exportStructures,
        boolean exportLootTables,
        boolean exportLinks,    // Legacy - not used
        boolean exportSamples,  // Legacy - not used
        boolean timestampedFolder
    ) {
        public static ExportConfig defaultConfig() {
            return new ExportConfig(true, true, false, false, true);
        }

        public static ExportConfig minimal() {
            return new ExportConfig(true, false, false, false, false);
        }
    }

    public record ExportResult(
        boolean success,
        String error,
        Path exportDirectory,
        List<String> exportedFiles
    ) {}
}
