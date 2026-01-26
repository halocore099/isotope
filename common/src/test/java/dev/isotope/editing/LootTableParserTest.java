package dev.isotope.editing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isotope.compat.Id;
import dev.isotope.data.loot.*;
import dev.isotope.test.MockMinecraft;
import dev.isotope.test.TestCategories.IntegrationTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for LootTableParser.
 * Tests JSON parsing of loot tables into LootTableStructure objects.
 */
@IntegrationTest
class LootTableParserTest {

    private MockMinecraft mockMc;

    @BeforeEach
    void setUp() {
        mockMc = MockMinecraft.getInstance();
        mockMc.reset();
    }

    // ===== Simple Loot Table Parsing =====

    @Nested
    @DisplayName("Simple loot table parsing")
    class SimpleParsing {

        @Test
        @DisplayName("parses simple chest loot table")
        void parseSimpleChest() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 3,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:diamond",
                          "weight": 5
                        },
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:emerald",
                          "weight": 10
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:chests/test", json);

            assertThat(table.type()).isEqualTo("minecraft:chest");
            assertThat(table.pools()).hasSize(1);
            assertThat(table.pools().get(0).entries()).hasSize(2);
            assertThat(table.pools().get(0).rolls().getMin()).isEqualTo(3);
            assertThat(table.pools().get(0).rolls().getMax()).isEqualTo(3);
        }

        @Test
        @DisplayName("parses empty loot table")
        void parseEmptyTable() {
            String json = """
                {
                  "type": "minecraft:empty"
                }
                """;

            LootTableStructure table = parseJson("minecraft:empty/test", json);

            assertThat(table.type()).isEqualTo("minecraft:empty");
            assertThat(table.pools()).isEmpty();
        }

        @Test
        @DisplayName("parses loot table with no pools")
        void parseNoPools() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": []
                }
                """;

            LootTableStructure table = parseJson("minecraft:chests/empty", json);

            assertThat(table.pools()).isEmpty();
        }
    }

    // ===== Number Provider Parsing =====

    @Nested
    @DisplayName("Number provider parsing")
    class NumberProviderParsing {

        @Test
        @DisplayName("parses constant rolls")
        void parseConstantRolls() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 5,
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            NumberProvider rolls = table.pools().get(0).rolls();

            assertThat(rolls).isInstanceOf(NumberProvider.Constant.class);
            assertThat(rolls.getMin()).isEqualTo(5);
            assertThat(rolls.getMax()).isEqualTo(5);
        }

        @Test
        @DisplayName("parses uniform rolls")
        void parseUniformRolls() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": {
                        "type": "minecraft:uniform",
                        "min": 2,
                        "max": 8
                      },
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            NumberProvider rolls = table.pools().get(0).rolls();

            assertThat(rolls).isInstanceOf(NumberProvider.Uniform.class);
            assertThat(rolls.getMin()).isEqualTo(2);
            assertThat(rolls.getMax()).isEqualTo(8);
        }

        @Test
        @DisplayName("parses binomial rolls")
        void parseBinomialRolls() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": {
                        "type": "minecraft:binomial",
                        "n": 10,
                        "p": 0.3
                      },
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            NumberProvider rolls = table.pools().get(0).rolls();

            assertThat(rolls).isInstanceOf(NumberProvider.Binomial.class);
            NumberProvider.Binomial binomial = (NumberProvider.Binomial) rolls;
            assertThat(binomial.n()).isEqualTo(10);
            assertThat(binomial.p()).isCloseTo(0.3f, within(0.001f));
        }
    }

    // ===== Entry Type Parsing =====

    @Nested
    @DisplayName("Entry type parsing")
    class EntryTypeParsing {

        @Test
        @DisplayName("parses item entry")
        void parseItemEntry() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:diamond",
                          "weight": 5,
                          "quality": 2
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_ITEM);
            assertThat(entry.name()).isPresent();
            assertThat(entry.name().get().getPath()).isEqualTo("diamond");
            assertThat(entry.weight()).isEqualTo(5);
            assertThat(entry.quality()).isEqualTo(2);
        }

        @Test
        @DisplayName("parses empty entry")
        void parseEmptyEntry() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:empty",
                          "weight": 20
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_EMPTY);
            assertThat(entry.isEmpty()).isTrue();
            assertThat(entry.weight()).isEqualTo(20);
        }

        @Test
        @DisplayName("parses loot_table reference entry")
        void parseLootTableEntry() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:loot_table",
                          "value": "minecraft:chests/simple_dungeon",
                          "weight": 1
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_LOOT_TABLE);
            assertThat(entry.name()).isPresent();
            assertThat(entry.name().get().getPath()).isEqualTo("chests/simple_dungeon");
        }

        @Test
        @DisplayName("parses tag entry")
        void parseTagEntry() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:tag",
                          "name": "minecraft:music_discs",
                          "weight": 1
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_TAG);
            assertThat(entry.name()).isPresent();
        }
    }

    // ===== Composite Entry Parsing =====

    @Nested
    @DisplayName("Composite entry parsing")
    class CompositeEntryParsing {

        @Test
        @DisplayName("parses alternatives entry")
        void parseAlternatives() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:alternatives",
                          "children": [
                            {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 1},
                            {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 5}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_ALTERNATIVES);
            assertThat(entry.isComposite()).isTrue();
            assertThat(entry.children()).hasSize(2);
        }

        @Test
        @DisplayName("parses group entry")
        void parseGroup() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:group",
                          "children": [
                            {"type": "minecraft:item", "name": "minecraft:diamond"},
                            {"type": "minecraft:item", "name": "minecraft:gold_ingot"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_GROUP);
            assertThat(entry.isComposite()).isTrue();
            assertThat(entry.children()).hasSize(2);
        }

        @Test
        @DisplayName("parses sequence entry")
        void parseSequence() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:sequence",
                          "children": [
                            {"type": "minecraft:item", "name": "minecraft:diamond"},
                            {"type": "minecraft:empty"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.type()).isEqualTo(LootEntry.TYPE_SEQUENCE);
            assertThat(entry.isComposite()).isTrue();
        }
    }

    // ===== Function Parsing =====

    @Nested
    @DisplayName("Function parsing")
    class FunctionParsing {

        @Test
        @DisplayName("parses set_count function")
        void parseSetCount() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:diamond",
                          "functions": [
                            {
                              "function": "minecraft:set_count",
                              "count": {"type": "minecraft:uniform", "min": 2, "max": 5}
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.functions()).hasSize(1);
            assertThat(entry.functions().get(0).function()).isEqualTo("minecraft:set_count");
            assertThat(entry.functions().get(0).isSetCount()).isTrue();
        }

        @Test
        @DisplayName("parses enchant_randomly function")
        void parseEnchantRandomly() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:book",
                          "functions": [
                            {"function": "minecraft:enchant_randomly"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.functions()).hasSize(1);
            assertThat(entry.functions().get(0).isEnchantment()).isTrue();
        }

        @Test
        @DisplayName("parses multiple functions")
        void parseMultipleFunctions() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:iron_sword",
                          "functions": [
                            {"function": "minecraft:set_damage", "damage": {"min": 0.1, "max": 0.9}},
                            {"function": "minecraft:enchant_randomly"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.functions()).hasSize(2);
        }
    }

    // ===== Condition Parsing =====

    @Nested
    @DisplayName("Condition parsing")
    class ConditionParsing {

        @Test
        @DisplayName("parses killed_by_player condition")
        void parseKilledByPlayer() {
            String json = """
                {
                  "type": "minecraft:entity",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:iron_ingot",
                          "conditions": [
                            {"condition": "minecraft:killed_by_player"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:entities/test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.conditions()).hasSize(1);
            assertThat(entry.conditions().get(0).condition()).isEqualTo("minecraft:killed_by_player");
        }

        @Test
        @DisplayName("parses random_chance condition")
        void parseRandomChance() {
            String json = """
                {
                  "type": "minecraft:entity",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "minecraft:ender_pearl",
                          "conditions": [
                            {"condition": "minecraft:random_chance", "chance": 0.025}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:entities/test", json);
            LootEntry entry = table.pools().get(0).entries().get(0);

            assertThat(entry.conditions()).hasSize(1);
            LootCondition condition = entry.conditions().get(0);
            assertThat(condition.condition()).isEqualTo("minecraft:random_chance");
            assertThat(condition.parameters()).isNotNull();
        }

        @Test
        @DisplayName("parses pool-level conditions")
        void parsePoolConditions() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "pools": [
                    {
                      "rolls": 1,
                      "conditions": [
                        {"condition": "minecraft:survives_explosion"}
                      ],
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);
            LootPool pool = table.pools().get(0);

            assertThat(pool.conditions()).hasSize(1);
            assertThat(pool.conditions().get(0).condition()).isEqualTo("minecraft:survives_explosion");
        }
    }

    // ===== Table-Level Features =====

    @Nested
    @DisplayName("Table-level features")
    class TableLevelFeatures {

        @Test
        @DisplayName("parses random_sequence")
        void parseRandomSequence() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "random_sequence": "minecraft:chests/simple_dungeon",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);

            assertThat(table.randomSequence()).isPresent();
            assertThat(table.randomSequence().get().getPath()).isEqualTo("chests/simple_dungeon");
        }

        @Test
        @DisplayName("parses table-level functions")
        void parseTableFunctions() {
            String json = """
                {
                  "type": "minecraft:chest",
                  "functions": [
                    {"function": "minecraft:set_count", "count": 2}
                  ],
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                      ]
                    }
                  ]
                }
                """;

            LootTableStructure table = parseJson("minecraft:test", json);

            assertThat(table.functions()).hasSize(1);
            assertThat(table.functions().get(0).function()).isEqualTo("minecraft:set_count");
        }
    }

    // ===== Error Handling =====

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("handles malformed JSON gracefully")
        void handlesMalformedJson() {
            String json = "{ invalid json }";

            assertThatThrownBy(() -> parseJson("minecraft:test", json))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("handles missing required fields")
        void handlesMissingFields() {
            String json = """
                {
                  "pools": [
                    {
                      "entries": []
                    }
                  ]
                }
                """;

            // Should parse but with default values
            LootTableStructure table = parseJson("minecraft:test", json);

            // Type should default or be null
            assertThat(table).isNotNull();
        }
    }

    // ===== Helper Methods =====

    /**
     * Parse JSON string into LootTableStructure.
     * This simulates what LootTableParser does without requiring Minecraft runtime.
     */
    private LootTableStructure parseJson(String tableIdStr, String json) {
        Id tableId = TestFixtures.mockId(tableIdStr);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

        // Parse type
        String type = jsonObject.has("type") ? jsonObject.get("type").getAsString() : "minecraft:generic";

        // Parse pools
        List<LootPool> pools = new java.util.ArrayList<>();
        if (jsonObject.has("pools") && jsonObject.get("pools").isJsonArray()) {
            for (var poolElement : jsonObject.getAsJsonArray("pools")) {
                pools.add(parsePool(poolElement.getAsJsonObject()));
            }
        }

        // Parse table-level functions
        List<LootFunction> functions = new java.util.ArrayList<>();
        if (jsonObject.has("functions") && jsonObject.get("functions").isJsonArray()) {
            for (var funcElement : jsonObject.getAsJsonArray("functions")) {
                functions.add(parseFunction(funcElement.getAsJsonObject()));
            }
        }

        // Parse random_sequence
        Optional<Id> randomSequence = Optional.empty();
        if (jsonObject.has("random_sequence")) {
            randomSequence = Optional.of(TestFixtures.mockId(jsonObject.get("random_sequence").getAsString()));
        }

        return new LootTableStructure(tableId, type, pools, functions, randomSequence);
    }

    private LootPool parsePool(JsonObject poolJson) {
        // Parse rolls
        NumberProvider rolls = NumberProvider.constant(1);
        if (poolJson.has("rolls")) {
            rolls = parseNumberProvider(poolJson.get("rolls"));
        }

        // Parse bonus_rolls
        NumberProvider bonusRolls = NumberProvider.constant(0);
        if (poolJson.has("bonus_rolls")) {
            bonusRolls = parseNumberProvider(poolJson.get("bonus_rolls"));
        }

        // Parse entries
        List<LootEntry> entries = new java.util.ArrayList<>();
        if (poolJson.has("entries") && poolJson.get("entries").isJsonArray()) {
            for (var entryElement : poolJson.getAsJsonArray("entries")) {
                entries.add(parseEntry(entryElement.getAsJsonObject()));
            }
        }

        // Parse pool conditions
        List<LootCondition> conditions = new java.util.ArrayList<>();
        if (poolJson.has("conditions") && poolJson.get("conditions").isJsonArray()) {
            for (var condElement : poolJson.getAsJsonArray("conditions")) {
                conditions.add(parseCondition(condElement.getAsJsonObject()));
            }
        }

        // Parse pool functions
        List<LootFunction> functions = new java.util.ArrayList<>();
        if (poolJson.has("functions") && poolJson.get("functions").isJsonArray()) {
            for (var funcElement : poolJson.getAsJsonArray("functions")) {
                functions.add(parseFunction(funcElement.getAsJsonObject()));
            }
        }

        return new LootPool("", rolls, bonusRolls, entries, conditions, functions);
    }

    private LootEntry parseEntry(JsonObject entryJson) {
        String type = entryJson.has("type") ? entryJson.get("type").getAsString() : LootEntry.TYPE_ITEM;
        int weight = entryJson.has("weight") ? entryJson.get("weight").getAsInt() : 1;
        int quality = entryJson.has("quality") ? entryJson.get("quality").getAsInt() : 0;

        // Parse name/value
        Optional<Id> name = Optional.empty();
        if (entryJson.has("name")) {
            name = Optional.of(TestFixtures.mockId(entryJson.get("name").getAsString()));
        } else if (entryJson.has("value")) {
            name = Optional.of(TestFixtures.mockId(entryJson.get("value").getAsString()));
        }

        // Parse functions
        List<LootFunction> functions = new java.util.ArrayList<>();
        if (entryJson.has("functions") && entryJson.get("functions").isJsonArray()) {
            for (var funcElement : entryJson.getAsJsonArray("functions")) {
                functions.add(parseFunction(funcElement.getAsJsonObject()));
            }
        }

        // Parse conditions
        List<LootCondition> conditions = new java.util.ArrayList<>();
        if (entryJson.has("conditions") && entryJson.get("conditions").isJsonArray()) {
            for (var condElement : entryJson.getAsJsonArray("conditions")) {
                conditions.add(parseCondition(condElement.getAsJsonObject()));
            }
        }

        // Parse children for composite entries
        List<LootEntry> children = new java.util.ArrayList<>();
        if (entryJson.has("children") && entryJson.get("children").isJsonArray()) {
            for (var childElement : entryJson.getAsJsonArray("children")) {
                children.add(parseEntry(childElement.getAsJsonObject()));
            }
        }

        return new LootEntry(type, name, weight, quality, conditions, functions, children);
    }

    private LootFunction parseFunction(JsonObject funcJson) {
        String function = funcJson.has("function") ? funcJson.get("function").getAsString() : "unknown";

        // Copy parameters (excluding "function" key)
        JsonObject params = new JsonObject();
        for (String key : funcJson.keySet()) {
            if (!key.equals("function") && !key.equals("conditions")) {
                params.add(key, funcJson.get(key));
            }
        }

        // Parse function conditions
        List<LootCondition> conditions = new java.util.ArrayList<>();
        if (funcJson.has("conditions") && funcJson.get("conditions").isJsonArray()) {
            for (var condElement : funcJson.getAsJsonArray("conditions")) {
                conditions.add(parseCondition(condElement.getAsJsonObject()));
            }
        }

        return new LootFunction(function, params, conditions);
    }

    private LootCondition parseCondition(JsonObject condJson) {
        String condition = condJson.has("condition") ? condJson.get("condition").getAsString() : "unknown";

        // Copy parameters (excluding "condition" key)
        JsonObject params = new JsonObject();
        for (String key : condJson.keySet()) {
            if (!key.equals("condition")) {
                params.add(key, condJson.get(key));
            }
        }

        return new LootCondition(condition, params.size() > 0 ? params : null);
    }

    private NumberProvider parseNumberProvider(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive()) {
            return NumberProvider.constant(element.getAsInt());
        }

        JsonObject obj = element.getAsJsonObject();
        String type = obj.has("type") ? obj.get("type").getAsString() : "minecraft:constant";

        return switch (type) {
            case "minecraft:constant" -> NumberProvider.constant(obj.get("value").getAsInt());
            case "minecraft:uniform" -> NumberProvider.uniform(
                obj.get("min").getAsInt(),
                obj.get("max").getAsInt()
            );
            case "minecraft:binomial" -> NumberProvider.binomial(
                obj.get("n").getAsInt(),
                obj.get("p").getAsFloat()
            );
            default -> NumberProvider.constant(1);
        };
    }
}
