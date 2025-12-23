package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.StructureInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovers and stores all registered structures from the dynamic registry.
 */
public final class StructureRegistry {
    private static final StructureRegistry INSTANCE = new StructureRegistry();

    private final Map<ResourceLocation, StructureInfo> structures = new LinkedHashMap<>();
    private boolean scanned = false;

    private StructureRegistry() {}

    public static StructureRegistry getInstance() {
        return INSTANCE;
    }

    public void scan(MinecraftServer server) {
        if (scanned) {
            Isotope.LOGGER.warn("Structure registry already scanned - skipping");
            return;
        }

        structures.clear();

        try {
            // Use lookupOrThrow for dynamic registries in 1.21.4
            HolderLookup.RegistryLookup<Structure> lookup = server.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);

            lookup.listElementIds().forEach(key -> {
                ResourceLocation id = key.location();
                structures.put(id, StructureInfo.fromId(id));
            });

            scanned = true;
            logSummary();
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to scan structure registry", e);
        }
    }

    public void reset() {
        structures.clear();
        scanned = false;
    }

    private void logSummary() {
        Map<String, Long> byNamespace = structures.values().stream()
            .collect(Collectors.groupingBy(StructureInfo::namespace, Collectors.counting()));

        Isotope.LOGGER.info("========================================");
        Isotope.LOGGER.info("Structure Registry Scan Complete");
        Isotope.LOGGER.info("Total structures: {}", structures.size());
        byNamespace.forEach((ns, count) ->
            Isotope.LOGGER.info("  {}: {}", ns, count));
        Isotope.LOGGER.info("========================================");
    }

    public Optional<StructureInfo> get(ResourceLocation id) {
        return Optional.ofNullable(structures.get(id));
    }

    public Collection<StructureInfo> getAll() {
        return Collections.unmodifiableCollection(structures.values());
    }

    public Collection<StructureInfo> getByNamespace(String namespace) {
        return structures.values().stream()
            .filter(s -> namespace.equals(s.namespace()))
            .toList();
    }

    public int count() {
        return structures.size();
    }

    public boolean isScanned() {
        return scanned;
    }
}
