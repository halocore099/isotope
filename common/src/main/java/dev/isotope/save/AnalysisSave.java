package dev.isotope.save;

import dev.isotope.analysis.AnalysisEngine.AnalysisConfig;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Complete analysis save data that can be serialized to JSON.
 */
public record AnalysisSave(
    String id,
    String name,
    long timestamp,
    String minecraftVersion,
    String isotopeVersion,
    SavedAnalysisConfig config,
    List<SavedStructure> structures,
    List<SavedLootTable> lootTables,
    List<SavedStructureLootLink> links,
    Map<String, SavedLootSample> samples
) {
    /**
     * Create a new save with generated ID and current timestamp.
     */
    public static AnalysisSave create(
            String name,
            String minecraftVersion,
            String isotopeVersion,
            SavedAnalysisConfig config,
            List<SavedStructure> structures,
            List<SavedLootTable> lootTables,
            List<SavedStructureLootLink> links,
            Map<String, SavedLootSample> samples
    ) {
        return new AnalysisSave(
            UUID.randomUUID().toString(),
            name,
            System.currentTimeMillis(),
            minecraftVersion,
            isotopeVersion,
            config,
            structures,
            lootTables,
            links,
            samples
        );
    }

    /**
     * Get metadata for list display without loading full data.
     */
    public SaveMetadata toMetadata() {
        // Count unique structures that have at least one link
        long linkedStructureCount = links.stream()
            .map(SavedStructureLootLink::structureId)
            .distinct()
            .count();

        return new SaveMetadata(
            id,
            name,
            timestamp,
            structures.size(),
            lootTables.size(),
            (int) linkedStructureCount
        );
    }

    // --- Inner record classes for serialization ---

    public record SavedAnalysisConfig(
        int sampleCount,
        boolean analyzeAllLootTables
    ) {
        public static SavedAnalysisConfig from(AnalysisConfig config) {
            return new SavedAnalysisConfig(config.sampleCount(), config.analyzeAllLootTables());
        }

        public AnalysisConfig toConfig() {
            return new AnalysisConfig(sampleCount, analyzeAllLootTables, false);
        }
    }

    public record SavedStructure(
        String id,
        String namespace,
        String path
    ) {
        public static SavedStructure from(StructureInfo info) {
            return new SavedStructure(
                info.id().toString(),
                info.namespace(),
                info.path()
            );
        }

        public StructureInfo toStructureInfo() {
            return new StructureInfo(
                ResourceLocation.parse(id),
                namespace,
                path
            );
        }
    }

    public record SavedLootTable(
        String id,
        String namespace,
        String path,
        String category
    ) {
        public static SavedLootTable from(LootTableInfo info) {
            return new SavedLootTable(
                info.id().toString(),
                info.namespace(),
                info.path(),
                info.category().name()
            );
        }

        public LootTableInfo toLootTableInfo() {
            return new LootTableInfo(
                ResourceLocation.parse(id),
                namespace,
                path,
                LootTableInfo.LootTableCategory.valueOf(category)
            );
        }
    }

    public record SavedStructureLootLink(
        String structureId,
        String lootTableId,
        String confidence,  // Confidence enum name
        String source       // LinkSource enum name
    ) {
        public static SavedStructureLootLink from(dev.isotope.data.StructureLootLink link) {
            return new SavedStructureLootLink(
                link.structureId().toString(),
                link.lootTableId().toString(),
                link.confidence().name(),
                link.source().name()
            );
        }

        public dev.isotope.data.StructureLootLink toLink() {
            return new dev.isotope.data.StructureLootLink(
                ResourceLocation.parse(structureId),
                ResourceLocation.parse(lootTableId),
                dev.isotope.data.StructureLootLink.Confidence.valueOf(confidence),
                dev.isotope.data.StructureLootLink.LinkSource.valueOf(source)
            );
        }
    }

    public record SavedLootSample(
        String tableId,
        int sampleCount,
        int totalItemsDropped,
        List<SavedItemSample> items,
        String error
    ) {}

    public record SavedItemSample(
        String itemId,
        int occurrences,
        int totalCount,
        int minCount,
        int maxCount
    ) {}
}
