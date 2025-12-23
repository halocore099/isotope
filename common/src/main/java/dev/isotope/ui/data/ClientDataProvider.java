package dev.isotope.ui.data;

import dev.isotope.analysis.StructureLootLinker;
import dev.isotope.data.StructureInfo;
import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Bridge to access server registry data from client UI.
 * Works in singleplayer/LAN where integrated server is available.
 */
@Environment(EnvType.CLIENT)
public final class ClientDataProvider {

    private static final ClientDataProvider INSTANCE = new ClientDataProvider();

    private ClientDataProvider() {}

    public static ClientDataProvider getInstance() {
        return INSTANCE;
    }

    public boolean isDataAvailable() {
        return Minecraft.getInstance().getSingleplayerServer() != null
            && StructureRegistry.getInstance().isScanned();
    }

    public Collection<StructureInfo> getAllStructures() {
        return StructureRegistry.getInstance().getAll();
    }

    public Collection<StructureInfo> getStructuresByNamespace(String namespace) {
        return StructureRegistry.getInstance().getByNamespace(namespace);
    }

    public List<String> getAllNamespaces() {
        return getAllStructures().stream()
            .map(StructureInfo::namespace)
            .distinct()
            .sorted((a, b) -> {
                // Sort "minecraft" first, then alphabetically
                if ("minecraft".equals(a)) return -1;
                if ("minecraft".equals(b)) return 1;
                return a.compareTo(b);
            })
            .collect(Collectors.toList());
    }

    public Optional<StructureLootLink> getStructureLink(ResourceLocation structureId) {
        return StructureLootLinker.getInstance().getLink(structureId);
    }

    public int getTotalStructureCount() {
        return StructureRegistry.getInstance().count();
    }

    public int getTotalLootTableCount() {
        return LootTableRegistry.getInstance().count();
    }

    public int getLinkedStructureCount() {
        return StructureLootLinker.getInstance().linkedStructureCount();
    }

    public Map<String, Integer> getStructureCountByNamespace() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String ns : getAllNamespaces()) {
            counts.put(ns, (int) getStructuresByNamespace(ns).size());
        }
        return counts;
    }
}
