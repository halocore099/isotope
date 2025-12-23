package dev.isotope.registry;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovers and stores all registered loot tables from the reloadable registry.
 */
public final class LootTableRegistry {
    private static final LootTableRegistry INSTANCE = new LootTableRegistry();

    private final Map<ResourceLocation, LootTableInfo> lootTables = new LinkedHashMap<>();
    private boolean scanned = false;

    private LootTableRegistry() {}

    public static LootTableRegistry getInstance() {
        return INSTANCE;
    }

    public void scan(MinecraftServer server) {
        if (scanned) {
            Isotope.LOGGER.warn("Loot table registry already scanned - skipping");
            return;
        }

        lootTables.clear();

        try {
            // In 1.21.4, loot tables are in reloadable registries
            // Use getKeys() to enumerate all loot table IDs
            Collection<ResourceLocation> keys = server.reloadableRegistries()
                .getKeys(Registries.LOOT_TABLE);

            keys.forEach(id -> {
                lootTables.put(id, LootTableInfo.fromId(id));
            });

            scanned = true;
            logSummary();
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to scan loot table registry", e);
        }
    }

    public void reset() {
        lootTables.clear();
        scanned = false;
    }

    private void logSummary() {
        Map<String, Long> byNamespace = lootTables.values().stream()
            .collect(Collectors.groupingBy(LootTableInfo::namespace, Collectors.counting()));

        Map<LootTableInfo.LootTableCategory, Long> byCategory = lootTables.values().stream()
            .collect(Collectors.groupingBy(LootTableInfo::category, Collectors.counting()));

        Isotope.LOGGER.info("========================================");
        Isotope.LOGGER.info("Loot Table Registry Scan Complete");
        Isotope.LOGGER.info("Total loot tables: {}", lootTables.size());
        Isotope.LOGGER.info("By namespace:");
        byNamespace.forEach((ns, count) ->
            Isotope.LOGGER.info("  {}: {}", ns, count));
        Isotope.LOGGER.info("By category:");
        byCategory.forEach((cat, count) ->
            Isotope.LOGGER.info("  {}: {}", cat, count));
        Isotope.LOGGER.info("========================================");
    }

    public Optional<LootTableInfo> get(ResourceLocation id) {
        return Optional.ofNullable(lootTables.get(id));
    }

    public Collection<LootTableInfo> getAll() {
        return Collections.unmodifiableCollection(lootTables.values());
    }

    public Collection<LootTableInfo> getByNamespace(String namespace) {
        return lootTables.values().stream()
            .filter(lt -> namespace.equals(lt.namespace()))
            .toList();
    }

    public Collection<LootTableInfo> getByCategory(LootTableInfo.LootTableCategory category) {
        return lootTables.values().stream()
            .filter(lt -> category == lt.category())
            .toList();
    }

    public int count() {
        return lootTables.size();
    }

    public boolean isScanned() {
        return scanned;
    }
}
