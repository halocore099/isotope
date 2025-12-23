package dev.isotope.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.analysis.AnalysisEngine;
import dev.isotope.analysis.LootContentsRegistry;
import dev.isotope.analysis.StructureLootLinker;
import dev.isotope.data.*;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureRegistry;
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
 * Manages export of analysis data to JSON files.
 */
public final class ExportManager {

    private static final ExportManager INSTANCE = new ExportManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportManager() {}

    public static ExportManager getInstance() {
        return INSTANCE;
    }

    /**
     * Export all analysis data to JSON files.
     */
    public ExportResult exportAll(ExportConfig config, Consumer<String> progressCallback) {
        try {
            Path exportDir = getExportDirectory(config);
            Files.createDirectories(exportDir);

            progressCallback.accept("Exporting to: " + exportDir);

            List<String> exportedFiles = new ArrayList<>();

            // Export structures
            if (config.exportStructures()) {
                progressCallback.accept("Exporting structures...");
                Path structuresFile = exportDir.resolve("structures.json");
                exportStructures(structuresFile);
                exportedFiles.add("structures.json");
            }

            // Export loot tables
            if (config.exportLootTables()) {
                progressCallback.accept("Exporting loot tables...");
                Path lootTablesFile = exportDir.resolve("loot_tables.json");
                exportLootTables(lootTablesFile);
                exportedFiles.add("loot_tables.json");
            }

            // Export structure-loot links
            if (config.exportLinks()) {
                progressCallback.accept("Exporting structure-loot links...");
                Path linksFile = exportDir.resolve("structure_loot_links.json");
                exportLinks(linksFile);
                exportedFiles.add("structure_loot_links.json");
            }

            // Export sample data
            if (config.exportSamples()) {
                progressCallback.accept("Exporting sample data...");
                Path samplesFile = exportDir.resolve("loot_samples.json");
                exportSamples(samplesFile);
                exportedFiles.add("loot_samples.json");
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

    private void exportStructures(Path file) throws IOException {
        List<Map<String, Object>> structures = new ArrayList<>();

        for (StructureInfo info : StructureRegistry.getInstance().getAll()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", info.fullId());
            entry.put("namespace", info.namespace());
            entry.put("path", info.path());
            entry.put("isVanilla", info.isVanilla());

            // Add link info
            StructureLootLinker.getInstance().getLink(info.id()).ifPresent(link -> {
                entry.put("hasLoot", link.hasLinks());
                entry.put("lootTableCount", link.linkCount());
                entry.put("linkMethod", link.linkMethod().name());
                entry.put("confidence", link.confidence());
                entry.put("linkedTables", link.linkedTables().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .toList());
            });

            structures.add(entry);
        }

        // Sort by ID
        structures.sort(Comparator.comparing(m -> (String) m.get("id")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportType", "structures");
        root.put("count", structures.size());
        root.put("structures", structures);

        Files.writeString(file, GSON.toJson(root));
    }

    private void exportLootTables(Path file) throws IOException {
        List<Map<String, Object>> tables = new ArrayList<>();

        for (LootTableInfo info : LootTableRegistry.getInstance().getAll()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", info.fullId());
            entry.put("namespace", info.namespace());
            entry.put("path", info.path());
            entry.put("category", info.category().name());

            // Add analyzed content if available
            LootTableContents contents = LootContentsRegistry.getInstance().get(info.id());
            if (contents.analyzed()) {
                entry.put("analyzed", true);
                entry.put("poolCount", contents.poolCount());
                entry.put("totalEntries", contents.totalEntryCount());

                List<Map<String, Object>> pools = new ArrayList<>();
                for (LootPoolInfo pool : contents.pools()) {
                    Map<String, Object> poolData = new LinkedHashMap<>();
                    poolData.put("index", pool.poolIndex());
                    poolData.put("rolls", pool.rollsDescription());
                    poolData.put("minRolls", pool.minRolls());
                    poolData.put("maxRolls", pool.maxRolls());

                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (LootEntryInfo entryInfo : pool.entries()) {
                        Map<String, Object> entryData = new LinkedHashMap<>();
                        entryData.put("type", entryInfo.type().name());
                        entryInfo.itemId().ifPresent(id -> entryData.put("itemId", id.toString()));
                        entryInfo.tableRef().ifPresent(id -> entryData.put("tableRef", id.toString()));
                        entryInfo.tagId().ifPresent(id -> entryData.put("tagId", id.toString()));
                        entryData.put("weight", entryInfo.weight());
                        entryData.put("minCount", entryInfo.minCount());
                        entryData.put("maxCount", entryInfo.maxCount());
                        entries.add(entryData);
                    }
                    poolData.put("entries", entries);
                    pools.add(poolData);
                }
                entry.put("pools", pools);
            } else {
                entry.put("analyzed", false);
                if (contents.errorMessage() != null) {
                    entry.put("error", contents.errorMessage());
                }
            }

            tables.add(entry);
        }

        // Sort by ID
        tables.sort(Comparator.comparing(m -> (String) m.get("id")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportType", "lootTables");
        root.put("count", tables.size());
        root.put("lootTables", tables);

        Files.writeString(file, GSON.toJson(root));
    }

    private void exportLinks(Path file) throws IOException {
        List<Map<String, Object>> links = new ArrayList<>();

        for (StructureLootLink link : StructureLootLinker.getInstance().getAllLinks()) {
            if (!link.hasLinks()) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("structureId", link.structureId().toString());
            entry.put("linkMethod", link.linkMethod().name());
            entry.put("confidence", link.confidence());
            entry.put("linkedTables", link.linkedTables().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList());
            links.add(entry);
        }

        links.sort(Comparator.comparing(m -> (String) m.get("structureId")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportType", "structureLootLinks");
        root.put("linkedCount", links.size());
        root.put("links", links);

        Files.writeString(file, GSON.toJson(root));
    }

    private void exportSamples(Path file) throws IOException {
        List<Map<String, Object>> samples = new ArrayList<>();

        for (var entry : AnalysisEngine.getInstance().getAllSamples().entrySet()) {
            var sample = entry.getValue();
            if (sample.hasError()) continue;

            Map<String, Object> sampleData = new LinkedHashMap<>();
            sampleData.put("tableId", sample.tableId().toString());
            sampleData.put("sampleCount", sample.sampleCount());
            sampleData.put("totalItemsDropped", sample.totalItemsDropped());
            sampleData.put("avgItemsPerRoll", sample.averageItemsPerRoll());

            List<Map<String, Object>> items = new ArrayList<>();
            for (var itemData : sample.itemDistribution()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemId", itemData.getItemId().toString());
                item.put("occurrences", itemData.getOccurrences());
                item.put("dropRate", itemData.getDropRate(sample.sampleCount()));
                item.put("avgCount", itemData.getAverageCount());
                item.put("minCount", itemData.getMinCount());
                item.put("maxCount", itemData.getMaxCount());
                items.add(item);
            }

            // Sort by drop rate descending
            items.sort((a, b) -> Double.compare(
                (Double) b.get("dropRate"),
                (Double) a.get("dropRate")
            ));

            sampleData.put("items", items);
            samples.add(sampleData);
        }

        samples.sort(Comparator.comparing(m -> (String) m.get("tableId")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportType", "lootSamples");
        root.put("sampledCount", samples.size());
        root.put("samples", samples);

        Files.writeString(file, GSON.toJson(root));
    }

    private void exportSummary(Path file, List<String> exportedFiles) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("exportTime", LocalDateTime.now().toString());
        summary.put("isotopeVersion", "0.1.0");
        summary.put("minecraftVersion", "1.21.4");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalStructures", StructureRegistry.getInstance().count());
        stats.put("totalLootTables", LootTableRegistry.getInstance().count());
        stats.put("linkedStructures", StructureLootLinker.getInstance().linkedStructureCount());
        stats.put("sampledLootTables", AnalysisEngine.getInstance().getAllSamples().size());
        stats.put("analyzedLootTables", LootContentsRegistry.getInstance().analyzedCount());
        summary.put("statistics", stats);

        summary.put("exportedFiles", exportedFiles);

        Files.writeString(file, GSON.toJson(summary));
    }

    // --- Inner classes ---

    public record ExportConfig(
        boolean exportStructures,
        boolean exportLootTables,
        boolean exportLinks,
        boolean exportSamples,
        boolean timestampedFolder
    ) {
        public static ExportConfig defaultConfig() {
            return new ExportConfig(true, true, true, true, true);
        }

        public static ExportConfig minimal() {
            return new ExportConfig(true, false, true, false, false);
        }
    }

    public record ExportResult(
        boolean success,
        String error,
        Path exportDirectory,
        List<String> exportedFiles
    ) {}
}
