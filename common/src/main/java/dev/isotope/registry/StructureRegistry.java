package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.StructureInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all discovered structures.
 *
 * Scans Minecraft's structure registry to find all structures
 * (vanilla + modded) that exist in the current game session.
 */
public final class StructureRegistry {

    private static final StructureRegistry INSTANCE = new StructureRegistry();

    private final Map<ResourceLocation, StructureInfo> structures = new LinkedHashMap<>();
    private boolean scanned = false;

    private StructureRegistry() {}

    public static StructureRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Scan the structure registry from the server.
     */
    public void scan(MinecraftServer server) {
        structures.clear();

        try {
            Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);

            registry.keySet().forEach(id -> {
                StructureInfo info = StructureInfo.fromId(id);
                structures.put(id, info);
            });

            scanned = true;
            Isotope.LOGGER.info("StructureRegistry: scanned {} structures", structures.size());

            // Log namespace breakdown
            Map<String, Long> byNamespace = structures.values().stream()
                .collect(Collectors.groupingBy(StructureInfo::namespace, Collectors.counting()));
            byNamespace.forEach((ns, count) ->
                Isotope.LOGGER.debug("  {} structures from {}", count, ns));

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to scan structure registry", e);
        }
    }

    /**
     * Get all discovered structures.
     */
    public Collection<StructureInfo> getAll() {
        return Collections.unmodifiableCollection(structures.values());
    }

    /**
     * Get structure by ID.
     */
    public Optional<StructureInfo> get(ResourceLocation id) {
        return Optional.ofNullable(structures.get(id));
    }

    /**
     * Get structures filtered by namespace.
     */
    public List<StructureInfo> getByNamespace(String namespace) {
        if ("*".equals(namespace)) {
            return new ArrayList<>(structures.values());
        }
        return structures.values().stream()
            .filter(s -> s.namespace().equals(namespace))
            .toList();
    }

    /**
     * Get all unique namespaces.
     */
    public Set<String> getNamespaces() {
        return structures.values().stream()
            .map(StructureInfo::namespace)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Get count of structures per namespace.
     */
    public Map<String, Integer> getNamespaceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StructureInfo info : structures.values()) {
            counts.merge(info.namespace(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Total structure count.
     */
    public int size() {
        return structures.size();
    }

    /**
     * Check if registry has been scanned.
     */
    public boolean isScanned() {
        return scanned;
    }

    /**
     * Reset the registry (for re-scanning).
     */
    public void reset() {
        structures.clear();
        scanned = false;
    }

    /**
     * Add a structure from a loaded save file.
     */
    public void addFromSave(StructureInfo info) {
        structures.put(info.id(), info);
        scanned = true; // Mark as having data
    }
}
