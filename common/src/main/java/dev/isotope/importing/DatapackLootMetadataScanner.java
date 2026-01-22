package dev.isotope.importing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import dev.isotope.api.ModLinkRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Scans datapack folders directly for loot_metadata.json files.
 *
 * This provides explicit structure-loot link declarations for datapack authors.
 * The file should be placed at: data/<namespace>/loot_metadata.json
 *
 * File format (same as isotope_links.json for consistency):
 * {
 *   "version": 1,
 *   "links": [
 *     {
 *       "structure": "mymod:my_structure",
 *       "loot_tables": ["mymod:chests/my_structure_loot"]
 *     },
 *     {
 *       "structure": "mymod:another_structure",
 *       "loot_tables": [
 *         "mymod:chests/another_loot",
 *         "mymod:chests/another_treasure"
 *       ]
 *     }
 *   ]
 * }
 *
 * This scanner works independently of the ResourceManager, allowing metadata
 * to be loaded before a world is fully initialized (main menu flow).
 */
public final class DatapackLootMetadataScanner {

    private static final DatapackLootMetadataScanner INSTANCE = new DatapackLootMetadataScanner();
    private static final Gson GSON = new GsonBuilder().create();

    // Statistics
    private int datapacksScanned = 0;
    private int metadataFilesFound = 0;
    private int linksRegistered = 0;
    private boolean scanned = false;

    private DatapackLootMetadataScanner() {}

    public static DatapackLootMetadataScanner getInstance() {
        return INSTANCE;
    }

    /**
     * Scan all available datapacks for loot_metadata.json files.
     * This scans the same locations as DatapackImporter.
     */
    public void scanAll() {
        datapacksScanned = 0;
        metadataFilesFound = 0;
        linksRegistered = 0;

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();

        // Check global datapacks folder
        Path globalDatapacks = gameDir.resolve("datapacks");
        if (Files.exists(globalDatapacks)) {
            scanDatapackFolder(globalDatapacks);
        }

        // Check isotope-export folder
        Path exportDir = gameDir.resolve("isotope-export");
        if (Files.exists(exportDir)) {
            scanDatapackFolder(exportDir);
        }

        // Check all world datapacks
        try {
            Path savesDir = gameDir.resolve("saves");
            if (Files.exists(savesDir)) {
                try (Stream<Path> worlds = Files.list(savesDir)) {
                    worlds.filter(Files::isDirectory)
                        .map(world -> world.resolve("datapacks"))
                        .filter(Files::exists)
                        .forEach(this::scanDatapackFolder);
                }
            }
        } catch (IOException e) {
            Isotope.LOGGER.warn("[DatapackLootMetadataScanner] Failed to scan world datapacks: {}", e.getMessage());
        }

        scanned = true;
        if (linksRegistered > 0) {
            Isotope.LOGGER.info("[DatapackLootMetadataScanner] Loaded {} links from {} metadata files ({} datapacks scanned)",
                linksRegistered, metadataFilesFound, datapacksScanned);
        } else {
            Isotope.LOGGER.debug("[DatapackLootMetadataScanner] No datapack metadata found ({} datapacks scanned)", datapacksScanned);
        }
    }

    /**
     * Scan a specific datapack for loot_metadata.json.
     */
    public void scanDatapack(Path packPath) {
        if (!Files.isDirectory(packPath)) {
            return;
        }

        // Must have pack.mcmeta to be a valid datapack
        if (!Files.exists(packPath.resolve("pack.mcmeta"))) {
            return;
        }

        datapacksScanned++;

        Path dataDir = packPath.resolve("data");
        if (!Files.exists(dataDir)) {
            return;
        }

        // Scan each namespace for loot_metadata.json
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            namespaces.filter(Files::isDirectory)
                .forEach(namespace -> {
                    Path metadataFile = namespace.resolve("loot_metadata.json");
                    if (Files.exists(metadataFile)) {
                        parseMetadataFile(metadataFile, namespace.getFileName().toString());
                    }
                });
        } catch (IOException e) {
            Isotope.LOGGER.warn("[DatapackLootMetadataScanner] Failed to scan datapack {}: {}",
                packPath.getFileName(), e.getMessage());
        }
    }

    private void scanDatapackFolder(Path folder) {
        try (Stream<Path> entries = Files.list(folder)) {
            entries.filter(Files::isDirectory)
                .filter(p -> Files.exists(p.resolve("pack.mcmeta")))
                .forEach(this::scanDatapack);
        } catch (IOException e) {
            Isotope.LOGGER.warn("[DatapackLootMetadataScanner] Failed to scan folder {}: {}", folder, e.getMessage());
        }
    }

    /**
     * Parse a loot_metadata.json file and register its links.
     */
    private void parseMetadataFile(Path file, String namespace) {
        try {
            String content = Files.readString(file);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json == null) {
                return;
            }

            metadataFilesFound++;

            // Check version for forward compatibility
            int version = json.has("version") ? json.get("version").getAsInt() : 1;
            if (version > 1) {
                Isotope.LOGGER.warn("[DatapackLootMetadataScanner] {} uses format version {}, some features may not work",
                    file, version);
            }

            // Parse links array
            if (!json.has("links")) {
                Isotope.LOGGER.debug("[DatapackLootMetadataScanner] {} has no 'links' array", file);
                return;
            }

            JsonArray links = json.getAsJsonArray("links");
            int linksInFile = 0;

            for (JsonElement linkElem : links) {
                if (!linkElem.isJsonObject()) continue;
                JsonObject linkObj = linkElem.getAsJsonObject();

                // Get structure ID
                if (!linkObj.has("structure")) {
                    Isotope.LOGGER.warn("[DatapackLootMetadataScanner] {} has link without 'structure' field", file);
                    continue;
                }
                String structureStr = linkObj.get("structure").getAsString();
                Identifier structureId;
                try {
                    structureId = Identifier.parse(structureStr);
                } catch (Exception e) {
                    Isotope.LOGGER.warn("[DatapackLootMetadataScanner] {} has invalid structure ID: {}", file, structureStr);
                    continue;
                }

                // Get loot tables
                List<Identifier> lootTables = new ArrayList<>();
                if (linkObj.has("loot_tables")) {
                    JsonElement tablesElem = linkObj.get("loot_tables");
                    if (tablesElem.isJsonArray()) {
                        for (JsonElement tableElem : tablesElem.getAsJsonArray()) {
                            if (tableElem.isJsonPrimitive()) {
                                try {
                                    lootTables.add(Identifier.parse(tableElem.getAsString()));
                                } catch (Exception e) {
                                    Isotope.LOGGER.warn("[DatapackLootMetadataScanner] {} has invalid loot table ID: {}",
                                        file, tableElem.getAsString());
                                }
                            }
                        }
                    } else if (tablesElem.isJsonPrimitive()) {
                        // Single loot table as string
                        try {
                            lootTables.add(Identifier.parse(tablesElem.getAsString()));
                        } catch (Exception e) {
                            Isotope.LOGGER.warn("[DatapackLootMetadataScanner] {} has invalid loot table ID: {}",
                                file, tablesElem.getAsString());
                        }
                    }
                }
                // Also support singular "loot_table" key
                if (linkObj.has("loot_table")) {
                    try {
                        lootTables.add(Identifier.parse(linkObj.get("loot_table").getAsString()));
                    } catch (Exception e) {
                        // Invalid
                    }
                }

                // Register the links via ModLinkRegistry (reuse existing infrastructure)
                if (!lootTables.isEmpty()) {
                    ModLinkRegistry.getInstance().registerFromJson("datapack:" + namespace, structureId, lootTables);
                    linksInFile += lootTables.size();
                }
            }

            if (linksInFile > 0) {
                linksRegistered += linksInFile;
                Isotope.LOGGER.debug("[DatapackLootMetadataScanner] Loaded {} links from {} (namespace: {})",
                    linksInFile, file.getFileName(), namespace);
            }

        } catch (Exception e) {
            Isotope.LOGGER.warn("[DatapackLootMetadataScanner] Failed to parse {}: {}", file, e.getMessage());
        }
    }

    /**
     * Get scan statistics.
     */
    public Stats getStats() {
        return new Stats(datapacksScanned, metadataFilesFound, linksRegistered);
    }

    public record Stats(int datapacksScanned, int metadataFilesFound, int linksRegistered) {}

    public boolean isScanned() {
        return scanned;
    }

    /**
     * Reset scanner state for re-scanning.
     */
    public void reset() {
        datapacksScanned = 0;
        metadataFilesFound = 0;
        linksRegistered = 0;
        scanned = false;
    }
}
