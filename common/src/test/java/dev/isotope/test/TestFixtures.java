package dev.isotope.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isotope.data.loot.*;

import java.util.List;
import java.util.Optional;

/**
 * Test fixtures for creating sample loot tables and data structures.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ===== JSON Fixtures =====

    /**
     * Simple chest loot table JSON.
     */
    public static final String SIMPLE_CHEST_JSON = """
        {
            "type": "minecraft:chest",
            "pools": [
                {
                    "rolls": 3,
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:diamond",
                            "weight": 1
                        },
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:iron_ingot",
                            "weight": 5
                        },
                        {
                            "type": "minecraft:empty",
                            "weight": 10
                        }
                    ]
                }
            ]
        }
        """;

    /**
     * Complex loot table with multiple pools and functions.
     */
    public static final String COMPLEX_POOL_JSON = """
        {
            "type": "minecraft:chest",
            "pools": [
                {
                    "rolls": {
                        "type": "minecraft:uniform",
                        "min": 1,
                        "max": 3
                    },
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:gold_ingot",
                            "weight": 5,
                            "functions": [
                                {
                                    "function": "minecraft:set_count",
                                    "count": {
                                        "type": "minecraft:uniform",
                                        "min": 1,
                                        "max": 4
                                    }
                                }
                            ]
                        }
                    ]
                },
                {
                    "rolls": 1,
                    "bonus_rolls": {
                        "type": "minecraft:uniform",
                        "min": 0,
                        "max": 2
                    },
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:emerald",
                            "weight": 2
                        },
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:diamond",
                            "weight": 1
                        }
                    ]
                }
            ]
        }
        """;

    /**
     * Entity loot table with conditions.
     */
    public static final String ENTITY_LOOT_JSON = """
        {
            "type": "minecraft:entity",
            "pools": [
                {
                    "rolls": 1,
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:rotten_flesh",
                            "weight": 1,
                            "functions": [
                                {
                                    "function": "minecraft:set_count",
                                    "count": {
                                        "type": "minecraft:uniform",
                                        "min": 0,
                                        "max": 2
                                    }
                                },
                                {
                                    "function": "minecraft:looting_enchant",
                                    "count": {
                                        "type": "minecraft:uniform",
                                        "min": 0,
                                        "max": 1
                                    }
                                }
                            ]
                        }
                    ]
                },
                {
                    "rolls": 1,
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:iron_ingot",
                            "weight": 1
                        },
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:carrot",
                            "weight": 1
                        },
                        {
                            "type": "minecraft:item",
                            "name": "minecraft:potato",
                            "weight": 1
                        }
                    ],
                    "conditions": [
                        {
                            "condition": "minecraft:killed_by_player"
                        }
                    ]
                }
            ]
        }
        """;

    /**
     * Empty loot table.
     */
    public static final String EMPTY_LOOT_JSON = """
        {
            "type": "minecraft:empty"
        }
        """;

    /**
     * Loot table with alternatives entry.
     */
    public static final String ALTERNATIVES_ENTRY_JSON = """
        {
            "type": "minecraft:chest",
            "pools": [
                {
                    "rolls": 1,
                    "entries": [
                        {
                            "type": "minecraft:alternatives",
                            "children": [
                                {
                                    "type": "minecraft:item",
                                    "name": "minecraft:diamond",
                                    "conditions": [
                                        {
                                            "condition": "minecraft:random_chance",
                                            "chance": 0.1
                                        }
                                    ]
                                },
                                {
                                    "type": "minecraft:item",
                                    "name": "minecraft:iron_ingot"
                                }
                            ]
                        }
                    ]
                }
            ]
        }
        """;

    // ===== Object Builders =====

    /**
     * Create a test Id without Minecraft dependencies.
     */
    public static TestId id(String namespace, String path) {
        return new TestId(namespace, path);
    }

    /**
     * Create a minecraft-namespaced test Id.
     */
    public static TestId mcId(String path) {
        return new TestId("minecraft", path);
    }

    /**
     * Simple test implementation of Id interface for unit testing.
     */
    public record TestId(String namespace, String path) {
        public String getNamespace() {
            return namespace;
        }

        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            return namespace + ":" + path;
        }
    }

    /**
     * Create a simple item entry.
     */
    public static LootEntry itemEntry(String item, int weight) {
        return new LootEntry(
            LootEntry.TYPE_ITEM,
            Optional.of(createMockId("minecraft", item)),
            weight,
            0,
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Create an empty entry.
     */
    public static LootEntry emptyEntry(int weight) {
        return new LootEntry(
            LootEntry.TYPE_EMPTY,
            Optional.empty(),
            weight,
            0,
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Create a simple pool with constant rolls.
     */
    public static LootPool pool(int rolls, LootEntry... entries) {
        return new LootPool(
            "",
            NumberProvider.constant(rolls),
            NumberProvider.constant(0),
            List.of(entries),
            List.of(),
            List.of()
        );
    }

    /**
     * Create a pool with uniform rolls.
     */
    public static LootPool uniformPool(int minRolls, int maxRolls, LootEntry... entries) {
        return new LootPool(
            "",
            NumberProvider.uniform(minRolls, maxRolls),
            NumberProvider.constant(0),
            List.of(entries),
            List.of(),
            List.of()
        );
    }

    /**
     * Create a loot table structure with given pools.
     */
    public static LootTableStructure table(String id, String type, LootPool... pools) {
        return new LootTableStructure(
            createMockId("minecraft", id),
            type,
            List.of(pools),
            List.of(),
            Optional.empty()
        );
    }

    /**
     * Create a chest loot table.
     */
    public static LootTableStructure chestTable(String id, LootPool... pools) {
        return table(id, LootTableStructure.TYPE_CHEST, pools);
    }

    /**
     * Parse JSON to JsonObject.
     */
    public static JsonObject parseJson(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * Create a mock Id - this is a placeholder that works without Minecraft.
     * In actual tests, you may need to use MockMinecraft to properly mock Ids.
     */
    private static dev.isotope.compat.Id createMockId(String namespace, String path) {
        // Return a simple implementation that doesn't require Minecraft
        return new dev.isotope.compat.Id() {
            @Override
            public String getNamespace() {
                return namespace;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public Object toMc() {
                throw new UnsupportedOperationException("Mock Id does not have Minecraft representation");
            }

            @Override
            public String toString() {
                return namespace + ":" + path;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof dev.isotope.compat.Id other) {
                    return namespace.equals(other.getNamespace()) && path.equals(other.getPath());
                }
                return false;
            }

            @Override
            public int hashCode() {
                return 31 * namespace.hashCode() + path.hashCode();
            }
        };
    }

    /**
     * Create a mock Id from a full string (namespace:path).
     */
    public static dev.isotope.compat.Id mockId(String full) {
        int colon = full.indexOf(':');
        if (colon == -1) {
            return createMockId("minecraft", full);
        }
        return createMockId(full.substring(0, colon), full.substring(colon + 1));
    }

    /**
     * Create a mock Id from namespace and path.
     */
    public static dev.isotope.compat.Id mockId(String namespace, String path) {
        return createMockId(namespace, path);
    }
}
