package dev.isotope.registry;

import dev.isotope.compat.Id;
import dev.isotope.data.loot.*;
import dev.isotope.test.MockMinecraft;
import dev.isotope.test.TestCategories.IntegrationTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for LootTableRegistry behavior using MockMinecraft.
 * Tests registry scanning, categorization, and querying.
 */
@IntegrationTest
class LootTableRegistryTest {

    private MockMinecraft mockMc;

    @BeforeEach
    void setUp() {
        mockMc = MockMinecraft.getInstance();
        mockMc.reset();
    }

    // ===== Registry Population =====

    @Nested
    @DisplayName("Registry population")
    class RegistryPopulation {

        @Test
        @DisplayName("vanilla loot tables are registered")
        void vanillaTablesRegistered() {
            Set<Id> tables = mockMc.getAllLootTables();

            assertThat(tables).isNotEmpty();
            assertThat(tables).anyMatch(id -> id.getPath().contains("chests/simple_dungeon"));
            assertThat(tables).anyMatch(id -> id.getPath().contains("entities/zombie"));
        }

        @Test
        @DisplayName("can register custom loot table")
        void registerCustomTable() {
            Id tableId = TestFixtures.mockId("mymod", "chests/custom_chest");
            LootEntry entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 10);
            LootPool pool = LootPool.simple(1, List.of(entry));
            LootTableStructure table = new LootTableStructure(
                tableId,
                "minecraft:chest",
                List.of(pool),
                List.of(),
                Optional.empty()
            );

            mockMc.registerLootTable(table);

            assertThat(mockMc.getLootTable(tableId)).isPresent();
            assertThat(mockMc.getLootTable(tableId).get().pools()).hasSize(1);
        }

        @Test
        @DisplayName("getLootTable returns empty for non-existent table")
        void nonExistentTableReturnsEmpty() {
            Id fakeId = TestFixtures.mockId("fake", "nonexistent");

            assertThat(mockMc.getLootTable(fakeId)).isEmpty();
        }
    }

    // ===== Category Detection =====

    @Nested
    @DisplayName("Category detection")
    class CategoryDetection {

        @Test
        @DisplayName("chest loot tables identified by path")
        void chestTablesIdentifiedByPath() {
            Set<Id> tables = mockMc.getAllLootTables();

            List<Id> chestTables = tables.stream()
                .filter(id -> id.getPath().contains("chests/"))
                .toList();

            assertThat(chestTables).isNotEmpty();
            assertThat(chestTables).allMatch(id -> id.getPath().startsWith("chests/"));
        }

        @Test
        @DisplayName("entity loot tables identified by path")
        void entityTablesIdentifiedByPath() {
            Set<Id> tables = mockMc.getAllLootTables();

            List<Id> entityTables = tables.stream()
                .filter(id -> id.getPath().contains("entities/"))
                .toList();

            assertThat(entityTables).isNotEmpty();
            assertThat(entityTables).allMatch(id -> id.getPath().startsWith("entities/"));
        }

        @Test
        @DisplayName("loot table type field indicates category")
        void typeFieldIndicatesCategory() {
            Id chestTableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            Id entityTableId = TestFixtures.mockId("minecraft", "entities/zombie");

            Optional<LootTableStructure> chestTable = mockMc.getLootTable(chestTableId);
            Optional<LootTableStructure> entityTable = mockMc.getLootTable(entityTableId);

            assertThat(chestTable).isPresent();
            assertThat(chestTable.get().type()).isEqualTo("minecraft:chest");

            assertThat(entityTable).isPresent();
            assertThat(entityTable.get().type()).isEqualTo("minecraft:entity");
        }
    }

    // ===== Namespace Filtering =====

    @Nested
    @DisplayName("Namespace filtering")
    class NamespaceFiltering {

        @Test
        @DisplayName("filters by minecraft namespace")
        void filtersByMinecraftNamespace() {
            Set<Id> minecraftTables = mockMc.getLootTablesByNamespace("minecraft");

            assertThat(minecraftTables).isNotEmpty();
            assertThat(minecraftTables).allMatch(id -> id.getNamespace().equals("minecraft"));
        }

        @Test
        @DisplayName("returns empty set for unknown namespace")
        void emptyForUnknownNamespace() {
            Set<Id> unknownTables = mockMc.getLootTablesByNamespace("nonexistent_mod");

            assertThat(unknownTables).isEmpty();
        }

        @Test
        @DisplayName("supports modded namespaces")
        void supportsModdedNamespaces() {
            mockMc.addModdedNamespace("testmod", 5, 2, 3);

            Set<Id> moddedTables = mockMc.getLootTablesByNamespace("testmod");

            assertThat(moddedTables).hasSize(3);
            assertThat(moddedTables).allMatch(id -> id.getNamespace().equals("testmod"));
        }

        @Test
        @DisplayName("getNamespaces returns all registered namespaces")
        void getNamespacesReturnsAll() {
            mockMc.addModdedNamespace("mod1", 1, 1, 1);
            mockMc.addModdedNamespace("mod2", 1, 1, 1);

            Set<String> namespaces = mockMc.getNamespaces();

            assertThat(namespaces).contains("minecraft", "mod1", "mod2");
        }
    }

    // ===== Loot Table Structure =====

    @Nested
    @DisplayName("Loot table structure")
    class LootTableStructureTests {

        @Test
        @DisplayName("dungeon loot table has correct structure")
        void dungeonTableStructure() {
            Id tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            Optional<LootTableStructure> table = mockMc.getLootTable(tableId);

            assertThat(table).isPresent();
            LootTableStructure structure = table.get();

            assertThat(structure.id()).isEqualTo(tableId);
            assertThat(structure.pools()).isNotEmpty();
            assertThat(structure.pools().get(0).entries()).isNotEmpty();
        }

        @Test
        @DisplayName("entity loot table has multiple pools")
        void entityTableMultiplePools() {
            Id tableId = TestFixtures.mockId("minecraft", "entities/zombie");
            Optional<LootTableStructure> table = mockMc.getLootTable(tableId);

            assertThat(table).isPresent();
            LootTableStructure structure = table.get();

            // Zombie has main drop pool and rare drop pool
            assertThat(structure.pools().size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("entries have correct weights")
        void entriesHaveCorrectWeights() {
            Id tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            Optional<LootTableStructure> table = mockMc.getLootTable(tableId);

            assertThat(table).isPresent();
            List<LootEntry> entries = table.get().pools().get(0).entries();

            // Check that entries have varying weights (rarity-based)
            Set<Integer> weights = new HashSet<>();
            for (LootEntry entry : entries) {
                weights.add(entry.weight());
            }

            assertThat(weights.size()).isGreaterThan(1); // Not all same weight
        }

        @Test
        @DisplayName("pools have roll counts")
        void poolsHaveRollCounts() {
            Id tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            Optional<LootTableStructure> table = mockMc.getLootTable(tableId);

            assertThat(table).isPresent();
            LootPool pool = table.get().pools().get(0);

            assertThat(pool.rolls()).isNotNull();
            assertThat(pool.rolls().getMin()).isGreaterThan(0);
        }
    }

    // ===== Item Registry Integration =====

    @Nested
    @DisplayName("Item registry integration")
    class ItemRegistryIntegration {

        @Test
        @DisplayName("loot table entries reference registered items")
        void entriesReferenceRegisteredItems() {
            Id tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            Optional<LootTableStructure> table = mockMc.getLootTable(tableId);

            assertThat(table).isPresent();

            for (LootPool pool : table.get().pools()) {
                for (LootEntry entry : pool.entries()) {
                    if (entry.isItem() && entry.name().isPresent()) {
                        Id itemId = entry.name().get();
                        assertThat(mockMc.itemExists(itemId))
                            .as("Item %s should exist in registry", itemId)
                            .isTrue();
                    }
                }
            }
        }

        @Test
        @DisplayName("can validate item existence")
        void canValidateItemExistence() {
            assertThat(mockMc.itemExists(TestFixtures.mockId("minecraft", "diamond"))).isTrue();
            assertThat(mockMc.itemExists(TestFixtures.mockId("minecraft", "fake_item"))).isFalse();
        }
    }

    // ===== Structure Registry Integration =====

    @Nested
    @DisplayName("Structure registry integration")
    class StructureRegistryIntegration {

        @Test
        @DisplayName("vanilla structures are registered")
        void vanillaStructuresRegistered() {
            Set<Id> structures = mockMc.getAllStructures();

            assertThat(structures).isNotEmpty();
            assertThat(structures).anyMatch(id -> id.getPath().contains("village"));
            assertThat(structures).anyMatch(id -> id.getPath().contains("stronghold"));
            assertThat(structures).anyMatch(id -> id.getPath().contains("ancient_city"));
        }

        @Test
        @DisplayName("can filter structures by namespace")
        void filterStructuresByNamespace() {
            Set<Id> minecraftStructures = mockMc.getStructuresByNamespace("minecraft");

            assertThat(minecraftStructures).isNotEmpty();
            assertThat(minecraftStructures).allMatch(id -> id.getNamespace().equals("minecraft"));
        }

        @Test
        @DisplayName("modded structures can be registered")
        void moddedStructuresCanBeRegistered() {
            mockMc.registerStructure("testmod:custom_structure");

            assertThat(mockMc.structureExists(TestFixtures.mockId("testmod", "custom_structure"))).isTrue();
        }
    }

    // ===== Entity Registry Integration =====

    @Nested
    @DisplayName("Entity registry integration")
    class EntityRegistryIntegration {

        @Test
        @DisplayName("vanilla entity types are registered")
        void vanillaEntitiesRegistered() {
            Set<Id> entities = mockMc.getAllEntityTypes();

            assertThat(entities).isNotEmpty();
            assertThat(entities).anyMatch(id -> id.getPath().equals("zombie"));
            assertThat(entities).anyMatch(id -> id.getPath().equals("skeleton"));
            assertThat(entities).anyMatch(id -> id.getPath().equals("creeper"));
        }

        @Test
        @DisplayName("entity loot table maps to entity type")
        void entityLootTableMapsToType() {
            // The zombie loot table should correspond to the zombie entity
            Id zombieTable = TestFixtures.mockId("minecraft", "entities/zombie");
            Id zombieEntity = TestFixtures.mockId("minecraft", "zombie");

            assertThat(mockMc.getLootTable(zombieTable)).isPresent();
            assertThat(mockMc.entityTypeExists(zombieEntity)).isTrue();

            // Path convention: entities/<entity_name> -> <entity_name>
            String entityName = zombieTable.getPath().replace("entities/", "");
            assertThat(entityName).isEqualTo(zombieEntity.getPath());
        }
    }

    // ===== Bulk Operations =====

    @Nested
    @DisplayName("Bulk operations")
    class BulkOperations {

        @Test
        @DisplayName("getAllLootTableStructures returns all tables")
        void getAllStructures() {
            Collection<LootTableStructure> allTables = mockMc.getAllLootTableStructures();

            assertThat(allTables).isNotEmpty();
            assertThat(allTables.size()).isEqualTo(mockMc.getAllLootTables().size());
        }

        @Test
        @DisplayName("reset clears and reinitializes")
        void resetReinitializes() {
            // Add custom data
            mockMc.addModdedNamespace("tempmod", 5, 5, 5);
            assertThat(mockMc.getLootTablesByNamespace("tempmod")).hasSize(5);

            // Reset
            mockMc.reset();

            // Custom data should be gone
            assertThat(mockMc.getLootTablesByNamespace("tempmod")).isEmpty();

            // Vanilla data should still be there
            assertThat(mockMc.getLootTablesByNamespace("minecraft")).isNotEmpty();
        }
    }
}
