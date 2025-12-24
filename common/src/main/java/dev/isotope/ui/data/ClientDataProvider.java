package dev.isotope.ui.data;

import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.RegistryScanner;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.registry.StructureRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Bridge to access registry and link data from client UI.
 *
 * This is the primary data source for ISOTOPE's UI.
 * Shows ALL structures and loot tables from registries,
 * with heuristic links and author overrides.
 */
@Environment(EnvType.CLIENT)
public final class ClientDataProvider {

    private static final ClientDataProvider INSTANCE = new ClientDataProvider();

    private ClientDataProvider() {}

    public static ClientDataProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Check if registry data is available.
     */
    public boolean isDataAvailable() {
        return RegistryScanner.isScanned();
    }

    // --- Structure API ---

    /**
     * Get all discovered structures.
     */
    public List<StructureInfo> getAllStructures() {
        return new ArrayList<>(StructureRegistry.getInstance().getAll());
    }

    /**
     * Get structures filtered by namespace.
     */
    public List<StructureInfo> getStructuresByNamespace(String namespace) {
        return StructureRegistry.getInstance().getByNamespace(namespace);
    }

    /**
     * Get structure by ID.
     */
    public Optional<StructureInfo> getStructure(ResourceLocation structureId) {
        return StructureRegistry.getInstance().get(structureId);
    }

    /**
     * Get all unique namespaces from structures.
     */
    public List<String> getAllStructureNamespaces() {
        List<String> namespaces = new ArrayList<>(StructureRegistry.getInstance().getNamespaces());
        // Sort: minecraft first, then alphabetically
        namespaces.sort((a, b) -> {
            if ("minecraft".equals(a)) return -1;
            if ("minecraft".equals(b)) return 1;
            return a.compareTo(b);
        });
        return namespaces;
    }

    /**
     * Get structure counts by namespace.
     */
    public Map<String, Integer> getStructureCountByNamespace() {
        return StructureRegistry.getInstance().getNamespaceCounts();
    }

    /**
     * Total structure count.
     */
    public int getTotalStructureCount() {
        return StructureRegistry.getInstance().size();
    }

    // --- Loot Table API ---

    /**
     * Get all discovered loot tables.
     */
    public List<LootTableInfo> getAllLootTables() {
        return new ArrayList<>(LootTableRegistry.getInstance().getAll());
    }

    /**
     * Get loot tables filtered by category.
     */
    public List<LootTableInfo> getLootTablesByCategory(LootTableInfo.LootTableCategory category) {
        return LootTableRegistry.getInstance().getByCategory(category);
    }

    /**
     * Get loot tables filtered by namespace.
     */
    public List<LootTableInfo> getLootTablesByNamespace(String namespace) {
        return LootTableRegistry.getInstance().getByNamespace(namespace);
    }

    /**
     * Get loot table by ID.
     */
    public Optional<LootTableInfo> getLootTable(ResourceLocation lootTableId) {
        return LootTableRegistry.getInstance().get(lootTableId);
    }

    /**
     * Get loot table counts by category.
     */
    public Map<LootTableInfo.LootTableCategory, Integer> getLootTableCountByCategory() {
        return LootTableRegistry.getInstance().getCategoryCounts();
    }

    /**
     * Total loot table count.
     */
    public int getTotalLootTableCount() {
        return LootTableRegistry.getInstance().size();
    }

    // --- Link API ---

    /**
     * Get links for a structure (sorted by confidence).
     */
    public List<StructureLootLink> getLinksForStructure(ResourceLocation structureId) {
        List<StructureLootLink> links = new ArrayList<>(
            StructureLootLinker.getInstance().getLinksForStructure(structureId));
        // Sort by confidence (highest first)
        links.sort((a, b) -> Integer.compare(b.confidence().getScore(), a.confidence().getScore()));
        return links;
    }

    /**
     * Get links for a loot table.
     */
    public List<StructureLootLink> getLinksForLootTable(ResourceLocation lootTableId) {
        return StructureLootLinker.getInstance().getLinksForLootTable(lootTableId);
    }

    /**
     * Check if a structure has any loot links.
     */
    public boolean structureHasLoot(ResourceLocation structureId) {
        return StructureLootLinker.getInstance().hasLinks(structureId);
    }

    /**
     * Get count of structures with at least one loot link.
     */
    public int getStructuresWithLootCount() {
        return StructureLootLinker.getInstance().getLinkedStructures().size();
    }

    /**
     * Get total link count.
     */
    public int getTotalLinkCount() {
        return StructureLootLinker.getInstance().getLinkCount();
    }

    // --- Author Override API ---

    /**
     * Add a manual link (author override).
     */
    public void addLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        StructureLootLinker.getInstance().addManualLink(structureId, lootTableId);
    }

    /**
     * Remove a link (author override).
     */
    public void removeLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        StructureLootLinker.getInstance().removeLink(structureId, lootTableId);
    }

    /**
     * Restore a previously removed link.
     */
    public void restoreLink(ResourceLocation structureId, ResourceLocation lootTableId) {
        StructureLootLinker.getInstance().restoreLink(structureId, lootTableId);
    }
}
