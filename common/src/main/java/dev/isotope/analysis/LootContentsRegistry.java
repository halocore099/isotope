package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.data.LootTableContents;
import dev.isotope.data.LootTableInfo;
import dev.isotope.registry.LootTableRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches analyzed loot table contents.
 * Lazily analyzes tables on first access.
 */
public final class LootContentsRegistry {
    private static final LootContentsRegistry INSTANCE = new LootContentsRegistry();

    private final Map<ResourceLocation, LootTableContents> cache = new ConcurrentHashMap<>();
    private MinecraftServer server;
    private boolean initialized = false;

    private final Map<ResourceLocation, Set<ResourceLocation>> itemIndex = new ConcurrentHashMap<>();
    private boolean indexBuilt = false;

    private LootContentsRegistry() {}

    public static LootContentsRegistry getInstance() {
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        LootTableAnalyzer.getInstance().initReflection();
        initialized = true;
        Isotope.LOGGER.debug("LootContentsRegistry initialized");
    }

    public void reset() {
        cache.clear();
        itemIndex.clear();
        indexBuilt = false;
        server = null;
        initialized = false;
    }

    public LootTableContents get(ResourceLocation tableId) {
        if (!initialized) {
            return LootTableContents.error(LootTableInfo.fromId(tableId), "Not initialized");
        }

        return cache.computeIfAbsent(tableId, id -> {
            LootTableContents contents = LootTableAnalyzer.getInstance().analyze(server, id);
            if (contents.analyzed()) {
                for (ResourceLocation itemId : contents.allItemIds()) {
                    itemIndex.computeIfAbsent(itemId, k -> ConcurrentHashMap.newKeySet())
                            .add(tableId);
                }
            }
            return contents;
        });
    }

    public void analyzeAll() {
        if (!initialized) {
            Isotope.LOGGER.warn("Cannot analyze - LootContentsRegistry not initialized");
            return;
        }

        long start = System.currentTimeMillis();
        int count = 0;
        int errors = 0;

        for (LootTableInfo info : LootTableRegistry.getInstance().getAll()) {
            LootTableContents contents = get(info.id());
            count++;
            if (!contents.analyzed()) errors++;
        }

        indexBuilt = true;
        long elapsed = System.currentTimeMillis() - start;
        Isotope.LOGGER.info("Analyzed {} loot tables in {}ms ({} errors)", count, elapsed, errors);
    }

    public Set<ResourceLocation> findTablesWithItem(ResourceLocation itemId) {
        if (!indexBuilt && initialized) {
            analyzeAll();
        }
        return itemIndex.getOrDefault(itemId, Set.of());
    }

    public Collection<LootTableContents> getAllCached() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public int cachedCount() {
        return cache.size();
    }

    public int analyzedCount() {
        return (int) cache.values().stream().filter(LootTableContents::analyzed).count();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isIndexBuilt() {
        return indexBuilt;
    }

    public Set<ResourceLocation> getIndexedItems() {
        return Collections.unmodifiableSet(itemIndex.keySet());
    }
}
