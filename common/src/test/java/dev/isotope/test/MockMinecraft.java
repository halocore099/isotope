package dev.isotope.test;

import dev.isotope.compat.Id;
import dev.isotope.data.loot.*;

import java.util.*;

/**
 * Mock Minecraft environment for integration tests.
 * Provides mock registries and utilities that simulate Minecraft's behavior
 * without requiring the actual game runtime.
 */
public final class MockMinecraft {

    private static final MockMinecraft INSTANCE = new MockMinecraft();

    // Mock registries
    private final Map<Id, String> itemRegistry = new HashMap<>();
    private final Map<Id, String> blockRegistry = new HashMap<>();
    private final Map<Id, String> entityTypeRegistry = new HashMap<>();
    private final Map<Id, String> structureRegistry = new HashMap<>();
    private final Map<Id, LootTableStructure> lootTableRegistry = new HashMap<>();

    private MockMinecraft() {
        initializeVanillaData();
    }

    public static MockMinecraft getInstance() {
        return INSTANCE;
    }

    /**
     * Reset all registries to vanilla defaults.
     */
    public void reset() {
        itemRegistry.clear();
        blockRegistry.clear();
        entityTypeRegistry.clear();
        structureRegistry.clear();
        lootTableRegistry.clear();
        initializeVanillaData();
    }

    private void initializeVanillaData() {
        // Common vanilla items
        registerItem("minecraft:diamond");
        registerItem("minecraft:emerald");
        registerItem("minecraft:gold_ingot");
        registerItem("minecraft:iron_ingot");
        registerItem("minecraft:coal");
        registerItem("minecraft:redstone");
        registerItem("minecraft:lapis_lazuli");
        registerItem("minecraft:bone");
        registerItem("minecraft:string");
        registerItem("minecraft:gunpowder");
        registerItem("minecraft:rotten_flesh");
        registerItem("minecraft:ender_pearl");
        registerItem("minecraft:blaze_rod");
        registerItem("minecraft:golden_apple");
        registerItem("minecraft:enchanted_golden_apple");
        registerItem("minecraft:name_tag");
        registerItem("minecraft:saddle");
        registerItem("minecraft:music_disc_13");
        registerItem("minecraft:music_disc_cat");
        registerItem("minecraft:bread");
        registerItem("minecraft:wheat");
        registerItem("minecraft:apple");
        registerItem("minecraft:arrow");
        registerItem("minecraft:book");
        registerItem("minecraft:paper");
        registerItem("minecraft:experience_bottle");
        registerItem("minecraft:iron_horse_armor");
        registerItem("minecraft:golden_horse_armor");
        registerItem("minecraft:diamond_horse_armor");

        // Common vanilla blocks
        registerBlock("minecraft:chest");
        registerBlock("minecraft:trapped_chest");
        registerBlock("minecraft:barrel");
        registerBlock("minecraft:spawner");
        registerBlock("minecraft:stone");
        registerBlock("minecraft:cobblestone");

        // Common vanilla entity types
        registerEntityType("minecraft:zombie");
        registerEntityType("minecraft:skeleton");
        registerEntityType("minecraft:creeper");
        registerEntityType("minecraft:spider");
        registerEntityType("minecraft:enderman");
        registerEntityType("minecraft:blaze");
        registerEntityType("minecraft:wither_skeleton");
        registerEntityType("minecraft:pig");
        registerEntityType("minecraft:cow");
        registerEntityType("minecraft:sheep");
        registerEntityType("minecraft:chicken");
        registerEntityType("minecraft:ender_dragon");
        registerEntityType("minecraft:wither");

        // Common vanilla structures
        registerStructure("minecraft:village_plains");
        registerStructure("minecraft:village_desert");
        registerStructure("minecraft:village_savanna");
        registerStructure("minecraft:village_snowy");
        registerStructure("minecraft:village_taiga");
        registerStructure("minecraft:pillager_outpost");
        registerStructure("minecraft:desert_pyramid");
        registerStructure("minecraft:jungle_pyramid");
        registerStructure("minecraft:swamp_hut");
        registerStructure("minecraft:igloo");
        registerStructure("minecraft:ocean_ruin_cold");
        registerStructure("minecraft:ocean_ruin_warm");
        registerStructure("minecraft:shipwreck");
        registerStructure("minecraft:shipwreck_beached");
        registerStructure("minecraft:buried_treasure");
        registerStructure("minecraft:mineshaft");
        registerStructure("minecraft:mineshaft_mesa");
        registerStructure("minecraft:woodland_mansion");
        registerStructure("minecraft:stronghold");
        registerStructure("minecraft:fortress");
        registerStructure("minecraft:bastion_remnant");
        registerStructure("minecraft:end_city");
        registerStructure("minecraft:ancient_city");
        registerStructure("minecraft:trail_ruins");
        registerStructure("minecraft:trial_chambers");

        // Register common loot tables
        registerLootTable(createSimpleDungeonLootTable());
        registerLootTable(createZombieLootTable());
        registerLootTable(createVillageLootTable());
    }

    // ===== Registry Methods =====

    public void registerItem(String id) {
        Id itemId = TestFixtures.mockId(id);
        itemRegistry.put(itemId, itemId.getPath());
    }

    public void registerBlock(String id) {
        Id blockId = TestFixtures.mockId(id);
        blockRegistry.put(blockId, blockId.getPath());
    }

    public void registerEntityType(String id) {
        Id entityId = TestFixtures.mockId(id);
        entityTypeRegistry.put(entityId, entityId.getPath());
    }

    public void registerStructure(String id) {
        Id structureId = TestFixtures.mockId(id);
        structureRegistry.put(structureId, structureId.getPath());
    }

    public void registerLootTable(LootTableStructure table) {
        lootTableRegistry.put(table.id(), table);
    }

    // ===== Query Methods =====

    public boolean itemExists(Id id) {
        return itemRegistry.containsKey(id);
    }

    public boolean blockExists(Id id) {
        return blockRegistry.containsKey(id);
    }

    public boolean entityTypeExists(Id id) {
        return entityTypeRegistry.containsKey(id);
    }

    public boolean structureExists(Id id) {
        return structureRegistry.containsKey(id);
    }

    public Optional<LootTableStructure> getLootTable(Id id) {
        return Optional.ofNullable(lootTableRegistry.get(id));
    }

    public Set<Id> getAllItems() {
        return Collections.unmodifiableSet(itemRegistry.keySet());
    }

    public Set<Id> getAllBlocks() {
        return Collections.unmodifiableSet(blockRegistry.keySet());
    }

    public Set<Id> getAllEntityTypes() {
        return Collections.unmodifiableSet(entityTypeRegistry.keySet());
    }

    public Set<Id> getAllStructures() {
        return Collections.unmodifiableSet(structureRegistry.keySet());
    }

    public Set<Id> getAllLootTables() {
        return Collections.unmodifiableSet(lootTableRegistry.keySet());
    }

    public Collection<LootTableStructure> getAllLootTableStructures() {
        return Collections.unmodifiableCollection(lootTableRegistry.values());
    }

    // ===== Namespace Queries =====

    public Set<Id> getItemsByNamespace(String namespace) {
        Set<Id> result = new HashSet<>();
        for (Id id : itemRegistry.keySet()) {
            if (id.getNamespace().equals(namespace)) {
                result.add(id);
            }
        }
        return result;
    }

    public Set<Id> getStructuresByNamespace(String namespace) {
        Set<Id> result = new HashSet<>();
        for (Id id : structureRegistry.keySet()) {
            if (id.getNamespace().equals(namespace)) {
                result.add(id);
            }
        }
        return result;
    }

    public Set<Id> getLootTablesByNamespace(String namespace) {
        Set<Id> result = new HashSet<>();
        for (Id id : lootTableRegistry.keySet()) {
            if (id.getNamespace().equals(namespace)) {
                result.add(id);
            }
        }
        return result;
    }

    public Set<String> getNamespaces() {
        Set<String> namespaces = new HashSet<>();
        lootTableRegistry.keySet().forEach(id -> namespaces.add(id.getNamespace()));
        structureRegistry.keySet().forEach(id -> namespaces.add(id.getNamespace()));
        return namespaces;
    }

    // ===== Sample Loot Tables =====

    private LootTableStructure createSimpleDungeonLootTable() {
        Id tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");

        LootEntry diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 3);
        LootEntry emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 5);
        LootEntry gold = LootEntry.item(TestFixtures.mockId("minecraft", "gold_ingot"), 10);
        LootEntry iron = LootEntry.item(TestFixtures.mockId("minecraft", "iron_ingot"), 15);
        LootEntry coal = LootEntry.item(TestFixtures.mockId("minecraft", "coal"), 20);
        LootEntry bread = LootEntry.item(TestFixtures.mockId("minecraft", "bread"), 25);
        LootEntry saddle = LootEntry.item(TestFixtures.mockId("minecraft", "saddle"), 5);
        LootEntry nameTag = LootEntry.item(TestFixtures.mockId("minecraft", "name_tag"), 5);
        LootEntry horseArmor = LootEntry.item(TestFixtures.mockId("minecraft", "iron_horse_armor"), 3);
        LootEntry goldenApple = LootEntry.item(TestFixtures.mockId("minecraft", "golden_apple"), 2);
        LootEntry enchantedApple = LootEntry.item(TestFixtures.mockId("minecraft", "enchanted_golden_apple"), 1);
        LootEntry musicDisc = LootEntry.item(TestFixtures.mockId("minecraft", "music_disc_13"), 2);

        LootPool pool = LootPool.simple(
            2,  // Average of 1-3 rolls
            List.of(diamond, emerald, gold, iron, coal, bread, saddle, nameTag, horseArmor, goldenApple, enchantedApple, musicDisc)
        );

        return new LootTableStructure(
            tableId,
            "minecraft:chest",
            List.of(pool),
            List.of(),
            Optional.empty()
        );
    }

    private LootTableStructure createZombieLootTable() {
        Id tableId = TestFixtures.mockId("minecraft", "entities/zombie");

        // Use constructor directly: type, name, weight, quality, conditions, functions, children
        LootEntry rottenFlesh = new LootEntry(
            LootEntry.TYPE_ITEM,
            Optional.of(TestFixtures.mockId("minecraft", "rotten_flesh")),
            1,
            0,
            List.of(),
            List.of(LootFunction.setCount(0, 2)),
            List.of()
        );

        LootEntry ironIngot = new LootEntry(
            LootEntry.TYPE_ITEM,
            Optional.of(TestFixtures.mockId("minecraft", "iron_ingot")),
            1,
            0,
            List.of(new LootCondition("minecraft:killed_by_player", null)),
            List.of(),
            List.of()
        );

        LootEntry carrot = new LootEntry(
            LootEntry.TYPE_ITEM,
            Optional.of(TestFixtures.mockId("minecraft", "carrot")),
            1,
            0,
            List.of(new LootCondition("minecraft:killed_by_player", null)),
            List.of(),
            List.of()
        );

        LootEntry potato = new LootEntry(
            LootEntry.TYPE_ITEM,
            Optional.of(TestFixtures.mockId("minecraft", "potato")),
            1,
            0,
            List.of(new LootCondition("minecraft:killed_by_player", null)),
            List.of(),
            List.of()
        );

        LootPool mainPool = LootPool.simple(1, List.of(rottenFlesh));
        LootPool rareDropPool = LootPool.simple(1, List.of(ironIngot, carrot, potato));

        return new LootTableStructure(
            tableId,
            "minecraft:entity",
            List.of(mainPool, rareDropPool),
            List.of(),
            Optional.empty()
        );
    }

    private LootTableStructure createVillageLootTable() {
        Id tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");

        LootEntry diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 3);
        LootEntry ironIngot = LootEntry.item(TestFixtures.mockId("minecraft", "iron_ingot"), 10);
        LootEntry ironSword = LootEntry.item(TestFixtures.mockId("minecraft", "iron_sword"), 5);
        LootEntry ironAxe = LootEntry.item(TestFixtures.mockId("minecraft", "iron_axe"), 5);
        LootEntry ironPickaxe = LootEntry.item(TestFixtures.mockId("minecraft", "iron_pickaxe"), 5);
        LootEntry ironHelmet = LootEntry.item(TestFixtures.mockId("minecraft", "iron_helmet"), 3);
        LootEntry ironChestplate = LootEntry.item(TestFixtures.mockId("minecraft", "iron_chestplate"), 3);
        LootEntry ironLeggings = LootEntry.item(TestFixtures.mockId("minecraft", "iron_leggings"), 3);
        LootEntry ironBoots = LootEntry.item(TestFixtures.mockId("minecraft", "iron_boots"), 3);

        LootPool pool = LootPool.simple(
            5,  // Average of 3-8 rolls
            List.of(diamond, ironIngot, ironSword, ironAxe, ironPickaxe, ironHelmet, ironChestplate, ironLeggings, ironBoots)
        );

        return new LootTableStructure(
            tableId,
            "minecraft:chest",
            List.of(pool),
            List.of(),
            Optional.empty()
        );
    }

    // ===== Modded Content Support =====

    /**
     * Add a modded namespace with items, structures, and loot tables.
     */
    public void addModdedNamespace(String namespace, int itemCount, int structureCount, int lootTableCount) {
        for (int i = 0; i < itemCount; i++) {
            registerItem(namespace + ":item_" + i);
        }
        for (int i = 0; i < structureCount; i++) {
            registerStructure(namespace + ":structure_" + i);
        }
        for (int i = 0; i < lootTableCount; i++) {
            Id tableId = TestFixtures.mockId(namespace, "chests/chest_" + i);
            LootEntry entry = LootEntry.item(TestFixtures.mockId(namespace, "item_0"), 10);
            LootPool pool = LootPool.simple(1, List.of(entry));
            LootTableStructure table = new LootTableStructure(
                tableId,
                "minecraft:chest",
                List.of(pool),
                List.of(),
                Optional.empty()
            );
            registerLootTable(table);
        }
    }

    // ===== JSON Parsing Support =====

    /**
     * Parse a loot table from JSON string and register it.
     */
    public LootTableStructure parseLootTableJson(String tableIdStr, String json) {
        Id tableId = TestFixtures.mockId(tableIdStr);
        // This is a simplified parser for testing - actual parsing would use LootTableParser
        LootTableStructure table = parseSimpleJson(tableId, json);
        registerLootTable(table);
        return table;
    }

    private LootTableStructure parseSimpleJson(Id tableId, String json) {
        // For now, create a basic structure - real implementation would fully parse JSON
        // This is sufficient for testing the registry behavior
        return new LootTableStructure(
            tableId,
            "minecraft:chest",
            List.of(),
            List.of(),
            Optional.empty()
        );
    }
}
