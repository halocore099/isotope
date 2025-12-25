package dev.isotope.importing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootTableParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Imports loot tables from existing datapacks.
 *
 * Scans datapack folders for loot table JSON files, parses them,
 * and caches them for comparison/editing.
 */
public final class DatapackImporter {

    private static final DatapackImporter INSTANCE = new DatapackImporter();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DatapackImporter() {}

    public static DatapackImporter getInstance() {
        return INSTANCE;
    }

    /**
     * Result of an import operation.
     */
    public record ImportResult(
        boolean success,
        int tablesFound,
        int tablesImported,
        int tablesSkipped,
        List<String> errors,
        List<ImportedTable> importedTables
    ) {
        public static ImportResult success(int found, int imported, int skipped, List<ImportedTable> tables) {
            return new ImportResult(true, found, imported, skipped, List.of(), tables);
        }

        public static ImportResult failure(String error) {
            return new ImportResult(false, 0, 0, 0, List.of(error), List.of());
        }

        public static ImportResult partial(int found, int imported, int skipped,
                                           List<String> errors, List<ImportedTable> tables) {
            return new ImportResult(true, found, imported, skipped, errors, tables);
        }
    }

    /**
     * Info about an imported loot table.
     */
    public record ImportedTable(
        ResourceLocation tableId,
        Path sourcePath,
        LootTableStructure structure
    ) {}

    /**
     * Datapack info for display.
     */
    public record DatapackInfo(
        String name,
        Path path,
        int lootTableCount,
        String description
    ) {}

    /**
     * Find available datapacks in standard locations.
     */
    public List<DatapackInfo> findAvailableDatapacks() {
        List<DatapackInfo> datapacks = new ArrayList<>();

        // Check global datapacks folder
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path globalDatapacks = gameDir.resolve("datapacks");
        if (Files.exists(globalDatapacks)) {
            scanDatapackFolder(globalDatapacks, datapacks);
        }

        // Check isotope-export folder (our own exports)
        Path exportDir = gameDir.resolve("isotope-export");
        if (Files.exists(exportDir)) {
            scanDatapackFolder(exportDir, datapacks);
        }

        // Check current world's datapacks if in a world
        try {
            Path savesDir = gameDir.resolve("saves");
            if (Files.exists(savesDir)) {
                try (Stream<Path> worlds = Files.list(savesDir)) {
                    worlds.filter(Files::isDirectory)
                        .map(world -> world.resolve("datapacks"))
                        .filter(Files::exists)
                        .forEach(dp -> scanDatapackFolder(dp, datapacks));
                }
            }
        } catch (IOException e) {
            Isotope.LOGGER.warn("Failed to scan world datapacks: {}", e.getMessage());
        }

        return datapacks;
    }

    private void scanDatapackFolder(Path folder, List<DatapackInfo> results) {
        try (Stream<Path> entries = Files.list(folder)) {
            entries.filter(this::isDatapack)
                .forEach(pack -> {
                    DatapackInfo info = analyzeDatapack(pack);
                    if (info != null && info.lootTableCount() > 0) {
                        results.add(info);
                    }
                });
        } catch (IOException e) {
            Isotope.LOGGER.warn("Failed to scan folder {}: {}", folder, e.getMessage());
        }
    }

    private boolean isDatapack(Path path) {
        if (Files.isDirectory(path)) {
            // Directory datapack - must have pack.mcmeta
            return Files.exists(path.resolve("pack.mcmeta"));
        }
        // Could also be a zip file, but we'll skip those for simplicity
        return false;
    }

    private DatapackInfo analyzeDatapack(Path packPath) {
        try {
            String name = packPath.getFileName().toString();
            String description = "";

            // Read pack.mcmeta for description
            Path mcmeta = packPath.resolve("pack.mcmeta");
            if (Files.exists(mcmeta)) {
                String content = Files.readString(mcmeta);
                JsonObject json = GSON.fromJson(content, JsonObject.class);
                if (json.has("pack") && json.getAsJsonObject("pack").has("description")) {
                    var descElem = json.getAsJsonObject("pack").get("description");
                    if (descElem.isJsonPrimitive()) {
                        description = descElem.getAsString();
                    }
                }
            }

            // Count loot tables
            int lootTableCount = countLootTables(packPath);

            return new DatapackInfo(name, packPath, lootTableCount, description);
        } catch (Exception e) {
            Isotope.LOGGER.warn("Failed to analyze datapack {}: {}", packPath, e.getMessage());
            return null;
        }
    }

    private int countLootTables(Path packPath) {
        Path dataDir = packPath.resolve("data");
        if (!Files.exists(dataDir)) {
            return 0;
        }

        int count = 0;
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                Path lootTableDir = namespace.resolve("loot_table");
                if (Files.exists(lootTableDir)) {
                    try (Stream<Path> files = Files.walk(lootTableDir)) {
                        count += (int) files.filter(p -> p.toString().endsWith(".json")).count();
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return count;
    }

    /**
     * Import loot tables from a datapack.
     *
     * @param packPath Path to the datapack folder
     * @param progressCallback Callback for progress updates
     * @return Import result
     */
    public ImportResult importFromDatapack(Path packPath, Consumer<String> progressCallback) {
        if (!Files.exists(packPath) || !Files.isDirectory(packPath)) {
            return ImportResult.failure("Datapack not found: " + packPath);
        }

        Path dataDir = packPath.resolve("data");
        if (!Files.exists(dataDir)) {
            return ImportResult.failure("No data folder found in datapack");
        }

        List<ImportedTable> importedTables = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int found = 0;
        int imported = 0;
        int skipped = 0;

        try (Stream<Path> namespaces = Files.list(dataDir)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                String namespaceName = namespace.getFileName().toString();
                Path lootTableDir = namespace.resolve("loot_table");

                if (!Files.exists(lootTableDir)) {
                    continue;
                }

                progressCallback.accept("Scanning " + namespaceName + "...");

                try (Stream<Path> files = Files.walk(lootTableDir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                        found++;

                        // Build table ID from path
                        Path relativePath = lootTableDir.relativize(file);
                        String pathStr = relativePath.toString()
                            .replace(".json", "")
                            .replace("\\", "/");
                        ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath(namespaceName, pathStr);

                        try {
                            String json = Files.readString(file);
                            Optional<LootTableStructure> structure = LootTableParser.parseFromString(tableId, json);

                            if (structure.isPresent()) {
                                importedTables.add(new ImportedTable(tableId, file, structure.get()));
                                imported++;
                                progressCallback.accept("Imported: " + tableId);
                            } else {
                                skipped++;
                                errors.add("Failed to parse: " + tableId);
                            }
                        } catch (Exception e) {
                            skipped++;
                            errors.add("Error reading " + tableId + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            return ImportResult.failure("Failed to scan datapack: " + e.getMessage());
        }

        progressCallback.accept("Import complete: " + imported + " tables");

        if (errors.isEmpty()) {
            return ImportResult.success(found, imported, skipped, importedTables);
        } else {
            return ImportResult.partial(found, imported, skipped, errors, importedTables);
        }
    }

    /**
     * Import from a specific folder path (custom path input).
     */
    public ImportResult importFromPath(String pathString, Consumer<String> progressCallback) {
        Path path = Path.of(pathString);

        // Check if it's a direct datapack folder
        if (Files.exists(path.resolve("pack.mcmeta"))) {
            return importFromDatapack(path, progressCallback);
        }

        // Check if it's a loot_table folder directly
        if (path.getFileName().toString().equals("loot_table") ||
            path.toString().contains("loot_table")) {
            return importFromLootTableFolder(path, progressCallback);
        }

        // Check if it has a data folder
        Path dataDir = path.resolve("data");
        if (Files.exists(dataDir)) {
            return importFromDatapack(path, progressCallback);
        }

        return ImportResult.failure("Could not find loot tables at: " + pathString);
    }

    /**
     * Import directly from a loot_table folder.
     */
    private ImportResult importFromLootTableFolder(Path lootTableDir, Consumer<String> progressCallback) {
        if (!Files.exists(lootTableDir)) {
            return ImportResult.failure("Folder not found: " + lootTableDir);
        }

        // Try to determine namespace from path
        String namespace = "imported";
        Path parent = lootTableDir.getParent();
        if (parent != null) {
            Path grandparent = parent.getParent();
            if (grandparent != null && grandparent.getFileName().toString().equals("data")) {
                namespace = parent.getFileName().toString();
            }
        }

        List<ImportedTable> importedTables = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int found = 0;
        int imported = 0;
        int skipped = 0;

        final String finalNamespace = namespace;

        try (Stream<Path> files = Files.walk(lootTableDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                found++;

                Path relativePath = lootTableDir.relativize(file);
                String pathStr = relativePath.toString()
                    .replace(".json", "")
                    .replace("\\", "/");
                ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath(finalNamespace, pathStr);

                try {
                    String json = Files.readString(file);
                    Optional<LootTableStructure> structure = LootTableParser.parseFromString(tableId, json);

                    if (structure.isPresent()) {
                        importedTables.add(new ImportedTable(tableId, file, structure.get()));
                        imported++;
                        progressCallback.accept("Imported: " + tableId);
                    } else {
                        skipped++;
                        errors.add("Failed to parse: " + tableId);
                    }
                } catch (Exception e) {
                    skipped++;
                    errors.add("Error reading " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            return ImportResult.failure("Failed to scan folder: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            return ImportResult.success(found, imported, skipped, importedTables);
        } else {
            return ImportResult.partial(found, imported, skipped, errors, importedTables);
        }
    }

    /**
     * Apply imported tables to the edit manager's original cache.
     * This allows them to be viewed and compared.
     */
    public void applyImportedTables(List<ImportedTable> tables) {
        LootEditManager manager = LootEditManager.getInstance();
        for (ImportedTable table : tables) {
            // Cache as "original" for viewing
            manager.cacheOriginalStructure(table.structure());
        }
        Isotope.LOGGER.info("Applied {} imported tables to cache", tables.size());
    }
}
