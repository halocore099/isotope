package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.LootSource;
import dev.isotope.data.LootSourceType;
import dev.isotope.data.StructureInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified registry providing all loot sources (structures + features + mobs).
 *
 * This is the primary interface for UI components that need to display
 * all sources of loot tables, regardless of whether they are real structures,
 * fire-and-forget features like dungeons, or entity/mob drops.
 *
 * Call compile() after StructureRegistry, FeatureRegistry, and EntityLootRegistry are initialized.
 */
public final class LootSourceRegistry {

    private static final LootSourceRegistry INSTANCE = new LootSourceRegistry();

    private final Map<ResourceLocation, LootSource> sources = new LinkedHashMap<>();
    private boolean compiled = false;

    private LootSourceRegistry() {}

    public static LootSourceRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Compile all loot sources from structures, features, and mobs.
     * Call this after StructureRegistry, FeatureRegistry, and EntityLootRegistry are initialized.
     */
    public void compile() {
        sources.clear();

        int structureCount = 0;
        int featureCount = 0;
        int mobCount = 0;

        // Add real structures first
        for (StructureInfo info : StructureRegistry.getInstance().getAll()) {
            LootSource source = LootSource.fromStructure(info);
            sources.put(source.id(), source);
            structureCount++;
        }

        // Add features (won't override since features have different IDs from structures)
        for (FeatureRegistry.FeatureDefinition feature : FeatureRegistry.getInstance().getAll()) {
            LootSource source = feature.toLootSource();
            // Only add if not already present (features shouldn't conflict with structures)
            if (!sources.containsKey(source.id())) {
                sources.put(source.id(), source);
                featureCount++;
            }
        }

        // Add mobs/entities
        for (EntityLootRegistry.EntityLootInfo entity : EntityLootRegistry.getInstance().getAll()) {
            LootSource source = entity.toLootSource();
            // Only add if not already present
            if (!sources.containsKey(source.id())) {
                sources.put(source.id(), source);
                mobCount++;
            }
        }

        compiled = true;
        Isotope.LOGGER.info("LootSourceRegistry: compiled {} total sources ({} structures, {} features, {} mobs)",
            sources.size(), structureCount, featureCount, mobCount);
    }

    /**
     * Get all loot sources.
     */
    public Collection<LootSource> getAll() {
        return Collections.unmodifiableCollection(sources.values());
    }

    /**
     * Get loot source by ID.
     */
    public Optional<LootSource> get(ResourceLocation id) {
        return Optional.ofNullable(sources.get(id));
    }

    /**
     * Get loot sources filtered by namespace.
     */
    public List<LootSource> getByNamespace(String namespace) {
        if ("*".equals(namespace)) {
            return new ArrayList<>(sources.values());
        }
        return sources.values().stream()
            .filter(s -> s.namespace().equals(namespace))
            .toList();
    }

    /**
     * Get loot sources filtered by type.
     */
    public List<LootSource> getByType(LootSourceType type) {
        return sources.values().stream()
            .filter(s -> s.type() == type)
            .toList();
    }

    /**
     * Get all structures (excluding features).
     */
    public List<LootSource> getStructures() {
        return getByType(LootSourceType.STRUCTURE);
    }

    /**
     * Get all features (excluding structures).
     */
    public List<LootSource> getFeatures() {
        return getByType(LootSourceType.FEATURE);
    }

    /**
     * Get all mobs (entity loot sources).
     */
    public List<LootSource> getMobs() {
        return getByType(LootSourceType.MOB);
    }

    /**
     * Get all unique namespaces.
     */
    public Set<String> getNamespaces() {
        return sources.values().stream()
            .map(LootSource::namespace)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get count of sources per namespace.
     */
    public Map<String, Integer> getNamespaceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LootSource source : sources.values()) {
            counts.merge(source.namespace(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Get count of sources per type.
     */
    public Map<LootSourceType, Integer> getTypeCounts() {
        Map<LootSourceType, Integer> counts = new EnumMap<>(LootSourceType.class);
        for (LootSource source : sources.values()) {
            counts.merge(source.type(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Total source count.
     */
    public int size() {
        return sources.size();
    }

    /**
     * Check if registry has been compiled.
     */
    public boolean isCompiled() {
        return compiled;
    }

    /**
     * Reset the registry.
     */
    public void reset() {
        sources.clear();
        compiled = false;
    }
}
