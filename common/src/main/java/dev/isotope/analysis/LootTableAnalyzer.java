package dev.isotope.analysis;

import dev.isotope.Isotope;
import dev.isotope.data.LootEntryInfo;
import dev.isotope.data.LootPoolInfo;
import dev.isotope.data.LootTableContents;
import dev.isotope.data.LootTableInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Analyzes loot table contents by inspecting LootTable objects.
 * Uses reflection to access private pool/entry fields in 1.21.4.
 */
public final class LootTableAnalyzer {
    private static final LootTableAnalyzer INSTANCE = new LootTableAnalyzer();

    private Field poolsField;
    private Field entriesField;
    private Field rollsField;
    private Field bonusRollsField;
    private Field weightField;
    private Field qualityField;
    private Field itemField;
    private Field tableField;
    private Field tagField;
    private Field childrenField;

    private boolean reflectionInitialized = false;
    private boolean reflectionFailed = false;

    private LootTableAnalyzer() {}

    public static LootTableAnalyzer getInstance() {
        return INSTANCE;
    }

    public void initReflection() {
        if (reflectionInitialized || reflectionFailed) return;

        try {
            // LootTable.pools
            poolsField = findField(LootTable.class, "pools", "f_79109_", "field_988");
            poolsField.setAccessible(true);

            // LootPool.entries
            entriesField = findField(LootPool.class, "entries", "f_79023_", "field_1055");
            entriesField.setAccessible(true);

            // LootPool.rolls
            rollsField = findField(LootPool.class, "rolls", "f_79024_", "field_1050");
            rollsField.setAccessible(true);

            // LootPool.bonusRolls
            bonusRollsField = findField(LootPool.class, "bonusRolls", "f_79025_", "field_1054");
            bonusRollsField.setAccessible(true);

            // LootPoolSingletonContainer.weight
            weightField = findField(LootPoolSingletonContainer.class, "weight", "f_79681_", "field_1009");
            weightField.setAccessible(true);

            // LootPoolSingletonContainer.quality
            qualityField = findField(LootPoolSingletonContainer.class, "quality", "f_79682_", "field_1010");
            qualityField.setAccessible(true);

            // LootItem.item (Holder<Item>)
            itemField = findField(LootItem.class, "item", "f_79566_", "field_1000");
            itemField.setAccessible(true);

            // NestedLootTable.contents
            tableField = findField(NestedLootTable.class, "contents", "f_279575_", "field_27987");
            tableField.setAccessible(true);

            // TagEntry.tag
            tagField = findField(TagEntry.class, "tag", "f_79823_", "field_1005");
            tagField.setAccessible(true);

            // CompositeEntryBase.children
            childrenField = findField(CompositeEntryBase.class, "children", "f_79434_", "field_1016");
            childrenField.setAccessible(true);

            reflectionInitialized = true;
            Isotope.LOGGER.debug("LootTableAnalyzer reflection initialized successfully");
        } catch (Exception e) {
            reflectionFailed = true;
            Isotope.LOGGER.error("Failed to initialize LootTableAnalyzer reflection", e);
        }
    }

    private Field findField(Class<?> clazz, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException("Could not find field with names: " + String.join(", ", names) + " in " + clazz.getName());
    }

    public LootTableContents analyze(MinecraftServer server, ResourceLocation tableId) {
        LootTableInfo info = LootTableInfo.fromId(tableId);

        if (!reflectionInitialized) {
            if (reflectionFailed) {
                return LootTableContents.error(info, "Reflection initialization failed");
            }
            return LootTableContents.error(info, "Analyzer not initialized");
        }

        try {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tableId);
            LootTable table = server.reloadableRegistries().getLootTable(key);

            if (table == LootTable.EMPTY) {
                return LootTableContents.error(info, "Empty or missing loot table");
            }

            List<LootPoolInfo> poolInfos = analyzePools(table);
            return LootTableContents.success(info, poolInfos);

        } catch (Exception e) {
            Isotope.LOGGER.debug("Error analyzing loot table {}: {}", tableId, e.getMessage());
            return LootTableContents.error(info, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<LootPoolInfo> analyzePools(LootTable table) throws Exception {
        List<LootPool> pools = (List<LootPool>) poolsField.get(table);
        List<LootPoolInfo> result = new ArrayList<>();

        int index = 0;
        for (LootPool pool : pools) {
            result.add(analyzePool(pool, index++));
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private LootPoolInfo analyzePool(LootPool pool, int index) throws Exception {
        NumberProvider rolls = (NumberProvider) rollsField.get(pool);
        NumberProvider bonusRolls = (NumberProvider) bonusRollsField.get(pool);

        float[] rollRange = extractNumberRange(rolls);
        float bonus = extractConstant(bonusRolls);

        List<LootPoolEntryContainer> entries = (List<LootPoolEntryContainer>) entriesField.get(pool);
        List<LootEntryInfo> entryInfos = new ArrayList<>();

        for (LootPoolEntryContainer entry : entries) {
            entryInfos.addAll(analyzeEntry(entry));
        }

        int conditionCount = 0;
        int functionCount = 0;

        return new LootPoolInfo(index, rollRange[0], rollRange[1], bonus, entryInfos, conditionCount, functionCount);
    }

    private List<LootEntryInfo> analyzeEntry(LootPoolEntryContainer entry) {
        List<LootEntryInfo> results = new ArrayList<>();

        try {
            if (entry instanceof LootItem itemEntry) {
                ResourceLocation itemId = extractItemId(itemEntry);
                int weight = getWeight(entry);
                int quality = getQuality(entry);
                results.add(LootEntryInfo.item(itemId, weight, quality, 1, 1));

            } else if (entry instanceof NestedLootTable nestedEntry) {
                ResourceLocation tableRef = extractTableRef(nestedEntry);
                int weight = getWeight(entry);
                int quality = getQuality(entry);
                results.add(LootEntryInfo.tableReference(tableRef, weight, quality));

            } else if (entry instanceof TagEntry tagEntry) {
                ResourceLocation tagId = extractTagId(tagEntry);
                int weight = getWeight(entry);
                int quality = getQuality(entry);
                results.add(LootEntryInfo.tag(tagId, weight, quality, 1, 1));

            } else if (entry instanceof EmptyLootItem) {
                int weight = getWeight(entry);
                results.add(LootEntryInfo.empty(weight));

            } else if (entry instanceof AlternativesEntry ||
                       entry instanceof SequentialEntry ||
                       entry instanceof EntryGroup) {
                List<LootPoolEntryContainer> children = getChildren(entry);
                for (LootPoolEntryContainer child : children) {
                    results.addAll(analyzeEntry(child));
                }

            } else {
                int weight = (entry instanceof LootPoolSingletonContainer) ? getWeight(entry) : 1;
                results.add(LootEntryInfo.unknown(LootEntryInfo.EntryType.UNKNOWN, weight));
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("Error analyzing entry: {}", e.getMessage());
            results.add(LootEntryInfo.unknown(LootEntryInfo.EntryType.UNKNOWN, 1));
        }

        return results;
    }

    private int getWeight(LootPoolEntryContainer entry) {
        if (!(entry instanceof LootPoolSingletonContainer)) return 1;
        try {
            return ((Number) weightField.get(entry)).intValue();
        } catch (Exception e) {
            return 1;
        }
    }

    private int getQuality(LootPoolEntryContainer entry) {
        if (!(entry instanceof LootPoolSingletonContainer)) return 0;
        try {
            return ((Number) qualityField.get(entry)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private float[] extractNumberRange(NumberProvider provider) {
        if (provider instanceof ConstantValue constant) {
            float val = constant.value();
            return new float[]{val, val};
        } else if (provider instanceof UniformGenerator) {
            try {
                Field minField = UniformGenerator.class.getDeclaredField("min");
                Field maxField = UniformGenerator.class.getDeclaredField("max");
                minField.setAccessible(true);
                maxField.setAccessible(true);
                NumberProvider minProvider = (NumberProvider) minField.get(provider);
                NumberProvider maxProvider = (NumberProvider) maxField.get(provider);
                float min = extractConstant(minProvider);
                float max = extractConstant(maxProvider);
                return new float[]{min, max};
            } catch (Exception e) {
                return new float[]{1, 1};
            }
        }
        return new float[]{1, 1};
    }

    private float extractConstant(NumberProvider provider) {
        if (provider instanceof ConstantValue constant) {
            return constant.value();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private ResourceLocation extractItemId(LootItem entry) {
        try {
            Object holder = itemField.get(entry);
            if (holder instanceof Holder<?> h) {
                Optional<ResourceKey<Item>> keyOpt = ((Holder<Item>) h).unwrapKey();
                if (keyOpt.isPresent()) {
                    return keyOpt.get().location();
                }
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to extract item ID: {}", e.getMessage());
        }
        return ResourceLocation.withDefaultNamespace("unknown");
    }

    @SuppressWarnings("unchecked")
    private ResourceLocation extractTableRef(NestedLootTable entry) {
        try {
            Object contents = tableField.get(entry);
            if (contents instanceof ResourceKey<?> key) {
                return key.location();
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to extract table reference: {}", e.getMessage());
        }
        return ResourceLocation.withDefaultNamespace("unknown");
    }

    private ResourceLocation extractTagId(TagEntry entry) {
        try {
            Object tag = tagField.get(entry);
            if (tag != null) {
                return ResourceLocation.parse(tag.toString().replace("#", ""));
            }
        } catch (Exception e) {
            Isotope.LOGGER.debug("Failed to extract tag ID: {}", e.getMessage());
        }
        return ResourceLocation.withDefaultNamespace("unknown");
    }

    @SuppressWarnings("unchecked")
    private List<LootPoolEntryContainer> getChildren(LootPoolEntryContainer entry) {
        try {
            return (List<LootPoolEntryContainer>) childrenField.get(entry);
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean isInitialized() {
        return reflectionInitialized;
    }
}
