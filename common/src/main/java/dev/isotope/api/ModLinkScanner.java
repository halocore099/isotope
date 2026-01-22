package dev.isotope.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Scans for isotope_links.json and loot_metadata.json files in mod/datapack data folders.
 *
 * Mods and datapacks can declare structure-loot links by including a JSON file at:
 *   data/<namespace>/isotope_links.json   (for mods)
 *   data/<namespace>/loot_metadata.json   (for datapacks, also works for mods)
 *
 * File format (same for both files):
 * {
 *   "modId": "mymod",
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
 * This provides a way for mods and datapacks to declare links without any code dependency
 * on Isotope - just include a JSON file in your mod/datapack.
 */
public final class ModLinkScanner {

    private static final ModLinkScanner INSTANCE = new ModLinkScanner();
    private static final Gson GSON = new GsonBuilder().create();

    // Statistics
    private int filesScanned = 0;
    private int modsWithLinks = 0;
    private int linksFound = 0;
    private boolean scanned = false;

    private ModLinkScanner() {}

    public static ModLinkScanner getInstance() {
        return INSTANCE;
    }

    /**
     * Scan for isotope_links.json and loot_metadata.json files in all loaded mod/datapack data folders.
     */
    public void scan(MinecraftServer server) {
        filesScanned = 0;
        modsWithLinks = 0;
        linksFound = 0;

        try {
            ResourceManager resourceManager = server.getResourceManager();

            // Look for isotope_links.json in all namespaces
            // The path would be: data/<namespace>/isotope_links.json
            Map<ResourceLocation, Resource> isotopeLinks = resourceManager.listResources(
                "",
                path -> path.getPath().equals("isotope_links.json")
            );

            Isotope.LOGGER.debug("[ModLinkScanner] Found {} isotope_links.json files", isotopeLinks.size());

            for (Map.Entry<ResourceLocation, Resource> entry : isotopeLinks.entrySet()) {
                parseLinkFile(entry.getKey(), entry.getValue(), "mod");
                filesScanned++;
            }

            // Also look for loot_metadata.json (same format, typically used by datapacks)
            // The path would be: data/<namespace>/loot_metadata.json
            Map<ResourceLocation, Resource> lootMetadata = resourceManager.listResources(
                "",
                path -> path.getPath().equals("loot_metadata.json")
            );

            Isotope.LOGGER.debug("[ModLinkScanner] Found {} loot_metadata.json files", lootMetadata.size());

            for (Map.Entry<ResourceLocation, Resource> entry : lootMetadata.entrySet()) {
                parseLinkFile(entry.getKey(), entry.getValue(), "datapack");
                filesScanned++;
            }

            scanned = true;
            if (linksFound > 0) {
                Isotope.LOGGER.info("[ModLinkScanner] Loaded {} links from {} sources ({} files scanned)",
                    linksFound, modsWithLinks, filesScanned);
            } else {
                Isotope.LOGGER.debug("[ModLinkScanner] No declared links found ({} files scanned)", filesScanned);
            }

        } catch (Exception e) {
            Isotope.LOGGER.error("[ModLinkScanner] Failed to scan for mod/datapack links", e);
        }
    }

    /**
     * Parse a single isotope_links.json or loot_metadata.json file.
     */
    private void parseLinkFile(ResourceLocation path, Resource resource, String sourceType) {
        String namespace = path.getNamespace();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            // Get mod ID from file or use namespace
            String modId = json.has("modId") ? json.get("modId").getAsString() : namespace;

            // Parse version (for future compatibility)
            int version = json.has("version") ? json.get("version").getAsInt() : 1;
            if (version > 1) {
                Isotope.LOGGER.warn("[ModLinkScanner] {} uses format version {}, some features may not work",
                    path, version);
            }

            // Parse links array
            if (!json.has("links")) {
                Isotope.LOGGER.debug("[ModLinkScanner] {} has no 'links' array", path);
                return;
            }

            JsonArray links = json.getAsJsonArray("links");
            int linksInFile = 0;

            for (JsonElement linkElem : links) {
                if (!linkElem.isJsonObject()) continue;
                JsonObject linkObj = linkElem.getAsJsonObject();

                // Get structure ID
                if (!linkObj.has("structure")) {
                    Isotope.LOGGER.warn("[ModLinkScanner] {} has link without 'structure' field", path);
                    continue;
                }
                String structureStr = linkObj.get("structure").getAsString();
                ResourceLocation structureId;
                try {
                    structureId = ResourceLocation.parse(structureStr);
                } catch (Exception e) {
                    Isotope.LOGGER.warn("[ModLinkScanner] {} has invalid structure ID: {}", path, structureStr);
                    continue;
                }

                // Get loot tables
                List<ResourceLocation> lootTables = new ArrayList<>();
                if (linkObj.has("loot_tables")) {
                    JsonElement tablesElem = linkObj.get("loot_tables");
                    if (tablesElem.isJsonArray()) {
                        for (JsonElement tableElem : tablesElem.getAsJsonArray()) {
                            if (tableElem.isJsonPrimitive()) {
                                try {
                                    lootTables.add(ResourceLocation.parse(tableElem.getAsString()));
                                } catch (Exception e) {
                                    Isotope.LOGGER.warn("[ModLinkScanner] {} has invalid loot table ID: {}",
                                        path, tableElem.getAsString());
                                }
                            }
                        }
                    } else if (tablesElem.isJsonPrimitive()) {
                        // Single loot table as string
                        try {
                            lootTables.add(ResourceLocation.parse(tablesElem.getAsString()));
                        } catch (Exception e) {
                            Isotope.LOGGER.warn("[ModLinkScanner] {} has invalid loot table ID: {}",
                                path, tablesElem.getAsString());
                        }
                    }
                }
                // Also support singular "loot_table" key
                if (linkObj.has("loot_table")) {
                    try {
                        lootTables.add(ResourceLocation.parse(linkObj.get("loot_table").getAsString()));
                    } catch (Exception e) {
                        // Invalid
                    }
                }

                // Register the links
                if (!lootTables.isEmpty()) {
                    ModLinkRegistry.getInstance().registerFromJson(sourceType + ":" + modId, structureId, lootTables);
                    linksInFile += lootTables.size();
                }
            }

            if (linksInFile > 0) {
                modsWithLinks++;
                linksFound += linksInFile;
                Isotope.LOGGER.debug("[ModLinkScanner] Loaded {} links from {} (source: {}:{})",
                    linksInFile, path, sourceType, modId);
            }

        } catch (Exception e) {
            Isotope.LOGGER.warn("[ModLinkScanner] Failed to parse {}: {}", path, e.getMessage());
        }
    }

    /**
     * Get scan statistics.
     */
    public Stats getStats() {
        return new Stats(filesScanned, modsWithLinks, linksFound);
    }

    public record Stats(int filesScanned, int modsWithLinks, int linksFound) {}

    public boolean isScanned() {
        return scanned;
    }
}
