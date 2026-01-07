package dev.isotope.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootTableSerializer;
import dev.isotope.validation.LootTableValidator;
import dev.isotope.validation.LootTableValidator.ValidationResult;
import dev.isotope.validation.LootTableValidator.ValidationIssue;
import dev.isotope.observation.ObservationSession;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Export edited loot tables as a Minecraft datapack.
     *
     * Creates a valid datapack structure:
     * - pack.mcmeta (format 61 for 1.21.4)
     * - data/<namespace>/loot_table/<path>.json for each edited table
     *
     * @param packName The name of the datapack
     * @param progressCallback Progress callback for status updates
     * @return Export result with success status and location
     */
    public ExportResult exportEditedAsDatapack(String packName, Consumer<String> progressCallback) {
        try {
            LootEditManager editManager = LootEditManager.getInstance();
            Set<ResourceLocation> editedTables = editManager.getEditedTables();

            if (editedTables.isEmpty()) {
                return new ExportResult(false, "No edited loot tables to export", null, List.of());
            }

            progressCallback.accept("Found " + editedTables.size() + " edited loot table(s)");

            // Create datapack directory
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path datapackDir = gameDir.resolve("isotope-datapacks").resolve(packName);
            Files.createDirectories(datapackDir);

            List<String> exportedFiles = new ArrayList<>();

            // Create pack.mcmeta
            progressCallback.accept("Creating pack.mcmeta...");
            createPackMcmeta(datapackDir, packName);
            exportedFiles.add("pack.mcmeta");

            // Export each edited loot table
            for (ResourceLocation tableId : editedTables) {
                progressCallback.accept("Exporting: " + tableId);

                Optional<LootTableStructure> edited = editManager.getEditedStructure(tableId);
                if (edited.isEmpty()) {
                    Isotope.LOGGER.warn("Could not get edited structure for: {}", tableId);
                    continue;
                }

                // Create the directory structure
                // data/<namespace>/loot_table/<path>.json
                Path tablePath = datapackDir
                    .resolve("data")
                    .resolve(tableId.getNamespace())
                    .resolve("loot_table")
                    .resolve(tableId.getPath() + ".json");

                Files.createDirectories(tablePath.getParent());

                // Serialize and write the loot table
                String json = LootTableSerializer.toJson(edited.get());
                Files.writeString(tablePath, json);

                String relativePath = "data/" + tableId.getNamespace() + "/loot_table/" + tableId.getPath() + ".json";
                exportedFiles.add(relativePath);
            }

            progressCallback.accept("Datapack export complete: " + exportedFiles.size() + " files");
            progressCallback.accept("Location: " + datapackDir);

            return new ExportResult(true, null, datapackDir, exportedFiles);

        } catch (Exception e) {
            Isotope.LOGGER.error("Datapack export failed", e);
            return new ExportResult(false, e.getMessage(), null, List.of());
        }
    }

    /**
     * Create the pack.mcmeta file for a datapack.
     */
    private void createPackMcmeta(Path datapackDir, String packName) throws IOException {
        // Format 61 is for Minecraft 1.21.4
        String packMcmeta = """
            {
              "pack": {
                "pack_format": 61,
                "description": "ISOTOPE edited loot tables: %s"
              }
            }
            """.formatted(packName);

        Files.writeString(datapackDir.resolve("pack.mcmeta"), packMcmeta);
    }

    /**
     * Export a validation report for all edited loot tables.
     *
     * @param format Export format (markdown, json, or text)
     * @param progressCallback Progress callback for status updates
     * @return Export result with success status and location
     */
    public ExportResult exportValidationReport(ReportFormat format, Consumer<String> progressCallback) {
        try {
            LootEditManager editManager = LootEditManager.getInstance();
            Set<ResourceLocation> editedTables = editManager.getEditedTables();

            progressCallback.accept("Validating " + editedTables.size() + " edited table(s)...");

            // Collect validation results
            List<ValidationResult> results = new ArrayList<>();
            int totalErrors = 0;
            int totalWarnings = 0;

            for (ResourceLocation tableId : editedTables) {
                Optional<LootTableStructure> structure = editManager.getEditedStructure(tableId);
                if (structure.isPresent()) {
                    ValidationResult result = LootTableValidator.validate(tableId, structure.get());
                    if (result.hasIssues()) {
                        results.add(result);
                        totalErrors += result.errorCount();
                        totalWarnings += result.warningCount();
                    }
                }
            }

            progressCallback.accept("Found " + totalErrors + " error(s), " + totalWarnings + " warning(s)");

            // Generate report content
            String content;
            String extension;
            switch (format) {
                case MARKDOWN -> {
                    content = generateMarkdownReport(results, editedTables.size(), totalErrors, totalWarnings);
                    extension = ".md";
                }
                case JSON -> {
                    content = generateJsonReport(results, editedTables.size(), totalErrors, totalWarnings);
                    extension = ".json";
                }
                default -> {
                    content = generateTextReport(results, editedTables.size(), totalErrors, totalWarnings);
                    extension = ".txt";
                }
            }

            // Write report
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path reportsDir = gameDir.resolve("isotope-reports");
            Files.createDirectories(reportsDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path reportFile = reportsDir.resolve("validation-report_" + timestamp + extension);

            Files.writeString(reportFile, content);

            progressCallback.accept("Report saved: " + reportFile.getFileName());

            return new ExportResult(true, null, reportsDir, List.of(reportFile.getFileName().toString()));

        } catch (Exception e) {
            Isotope.LOGGER.error("Validation report export failed", e);
            return new ExportResult(false, e.getMessage(), null, List.of());
        }
    }

    private String generateMarkdownReport(List<ValidationResult> results, int totalTables,
                                          int totalErrors, int totalWarnings) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ISOTOPE Validation Report\n\n");
        sb.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

        sb.append("## Summary\n\n");
        sb.append("| Metric | Count |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Tables Validated | ").append(totalTables).append(" |\n");
        sb.append("| Tables with Issues | ").append(results.size()).append(" |\n");
        sb.append("| Total Errors | ").append(totalErrors).append(" |\n");
        sb.append("| Total Warnings | ").append(totalWarnings).append(" |\n\n");

        if (results.isEmpty()) {
            sb.append("✅ **No issues found!** All edited loot tables are valid.\n");
            return sb.toString();
        }

        sb.append("## Issues by Table\n\n");

        for (ValidationResult result : results) {
            sb.append("### `").append(result.tableId()).append("`\n\n");
            sb.append("- Errors: ").append(result.errorCount()).append("\n");
            sb.append("- Warnings: ").append(result.warningCount()).append("\n\n");

            if (!result.issues().isEmpty()) {
                sb.append("| Severity | Type | Message | Location |\n");
                sb.append("|----------|------|---------|----------|\n");

                for (ValidationIssue issue : result.issues()) {
                    String severity = switch (issue.severity()) {
                        case ERROR -> "🔴 Error";
                        case WARNING -> "🟡 Warning";
                        case INFO -> "🔵 Info";
                    };
                    String location = issue.poolIndex() >= 0
                        ? "Pool " + (issue.poolIndex() + 1) +
                          (issue.entryIndex() >= 0 ? ", Entry " + (issue.entryIndex() + 1) : "")
                        : "—";
                    sb.append("| ").append(severity)
                      .append(" | ").append(issue.type().name)
                      .append(" | ").append(issue.message())
                      .append(" | ").append(location)
                      .append(" |\n");
                }
                sb.append("\n");
            }
        }

        sb.append("---\n\n");
        sb.append("*Generated by ISOTOPE IDE*\n");

        return sb.toString();
    }

    private String generateJsonReport(List<ValidationResult> results, int totalTables,
                                      int totalErrors, int totalWarnings) {
        Map<String, Object> report = new LinkedHashMap<>();

        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("isotopeVersion", "0.1.0");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tablesValidated", totalTables);
        summary.put("tablesWithIssues", results.size());
        summary.put("totalErrors", totalErrors);
        summary.put("totalWarnings", totalWarnings);
        report.put("summary", summary);

        List<Map<String, Object>> tables = new ArrayList<>();
        for (ValidationResult result : results) {
            Map<String, Object> tableData = new LinkedHashMap<>();
            tableData.put("tableId", result.tableId().toString());
            tableData.put("errorCount", result.errorCount());
            tableData.put("warningCount", result.warningCount());

            List<Map<String, Object>> issues = new ArrayList<>();
            for (ValidationIssue issue : result.issues()) {
                Map<String, Object> issueData = new LinkedHashMap<>();
                issueData.put("severity", issue.severity().name());
                issueData.put("type", issue.type().name());
                issueData.put("message", issue.message());
                issueData.put("poolIndex", issue.poolIndex());
                issueData.put("entryIndex", issue.entryIndex());
                issueData.put("suggestion", issue.suggestion());
                issues.add(issueData);
            }
            tableData.put("issues", issues);
            tables.add(tableData);
        }
        report.put("tables", tables);

        return GSON.toJson(report);
    }

    private String generateTextReport(List<ValidationResult> results, int totalTables,
                                      int totalErrors, int totalWarnings) {
        StringBuilder sb = new StringBuilder();

        sb.append("=".repeat(60)).append("\n");
        sb.append("ISOTOPE VALIDATION REPORT\n");
        sb.append("=".repeat(60)).append("\n\n");

        sb.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

        sb.append("SUMMARY\n");
        sb.append("-".repeat(40)).append("\n");
        sb.append(String.format("  Tables Validated:    %d%n", totalTables));
        sb.append(String.format("  Tables with Issues:  %d%n", results.size()));
        sb.append(String.format("  Total Errors:        %d%n", totalErrors));
        sb.append(String.format("  Total Warnings:      %d%n", totalWarnings));
        sb.append("\n");

        if (results.isEmpty()) {
            sb.append("No issues found! All edited loot tables are valid.\n");
            return sb.toString();
        }

        sb.append("ISSUES BY TABLE\n");
        sb.append("-".repeat(40)).append("\n\n");

        for (ValidationResult result : results) {
            sb.append("[").append(result.tableId()).append("]\n");
            sb.append("  Errors: ").append(result.errorCount())
              .append(", Warnings: ").append(result.warningCount()).append("\n");

            for (ValidationIssue issue : result.issues()) {
                String prefix = switch (issue.severity()) {
                    case ERROR -> "  [ERROR]  ";
                    case WARNING -> "  [WARN]   ";
                    case INFO -> "  [INFO]   ";
                };
                sb.append(prefix).append(issue.type().name).append(": ").append(issue.message()).append("\n");
                if (issue.poolIndex() >= 0) {
                    String location = "    Location: Pool " + (issue.poolIndex() + 1);
                    if (issue.entryIndex() >= 0) {
                        location += ", Entry " + (issue.entryIndex() + 1);
                    }
                    sb.append(location).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("=".repeat(60)).append("\n");
        sb.append("Generated by ISOTOPE IDE\n");

        return sb.toString();
    }

    /**
     * Report format options.
     */
    public enum ReportFormat {
        MARKDOWN,
        JSON,
        TEXT
    }

    private Path getExportDirectory(ExportConfig config) {
        Path baseDir;

        // Use custom path if provided
        if (config.customPath() != null && !config.customPath().isBlank()) {
            baseDir = Paths.get(config.customPath());
        } else {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            baseDir = gameDir.resolve("isotope-export");
        }

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
        boolean timestampedFolder,
        @Nullable String customPath  // Custom export path (null = use default)
    ) {
        public static ExportConfig defaultConfig() {
            return new ExportConfig(true, true, false, false, true, null);
        }

        public static ExportConfig minimal() {
            return new ExportConfig(true, false, false, false, false, null);
        }

        // Constructor for backwards compatibility
        public ExportConfig(boolean exportStructures, boolean exportLootTables,
                           boolean exportLinks, boolean exportSamples, boolean timestampedFolder) {
            this(exportStructures, exportLootTables, exportLinks, exportSamples, timestampedFolder, null);
        }
    }

    public record ExportResult(
        boolean success,
        String error,
        Path exportDirectory,
        List<String> exportedFiles
    ) {}
}
