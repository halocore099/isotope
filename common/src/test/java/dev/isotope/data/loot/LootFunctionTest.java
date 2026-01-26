package dev.isotope.data.loot;

import com.google.gson.JsonObject;
import dev.isotope.test.TestCategories.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LootFunction record.
 */
@UnitTest
class LootFunctionTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("setCount() factory methods")
    class SetCountFactory {

        @Test
        @DisplayName("creates set_count with constant value")
        void constantSetCount() {
            LootFunction func = LootFunction.setCount(5);

            assertThat(func.function()).isEqualTo("minecraft:set_count");
            assertThat(func.parameters().has("count")).isTrue();
            assertThat(func.parameters().get("count").getAsInt()).isEqualTo(5);
            assertThat(func.conditions()).isEmpty();
        }

        @Test
        @DisplayName("creates set_count with uniform range")
        void uniformSetCount() {
            LootFunction func = LootFunction.setCount(2, 8);

            assertThat(func.function()).isEqualTo("minecraft:set_count");
            assertThat(func.parameters().has("count")).isTrue();
            JsonObject count = func.parameters().getAsJsonObject("count");
            assertThat(count.get("type").getAsString()).isEqualTo("minecraft:uniform");
            assertThat(count.get("min").getAsInt()).isEqualTo(2);
            assertThat(count.get("max").getAsInt()).isEqualTo(8);
        }
    }

    @Nested
    @DisplayName("enchantRandomly() factory method")
    class EnchantRandomlyFactory {

        @Test
        @DisplayName("creates enchant_randomly function")
        void enchantRandomly() {
            LootFunction func = LootFunction.enchantRandomly();

            assertThat(func.function()).isEqualTo("minecraft:enchant_randomly");
            assertThat(func.conditions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("enchantWithLevels() factory method")
    class EnchantWithLevelsFactory {

        @Test
        @DisplayName("creates enchant_with_levels function")
        void enchantWithLevels() {
            LootFunction func = LootFunction.enchantWithLevels(5, 15, true);

            assertThat(func.function()).isEqualTo("minecraft:enchant_with_levels");
            JsonObject levels = func.parameters().getAsJsonObject("levels");
            assertThat(levels.get("min").getAsInt()).isEqualTo(5);
            assertThat(levels.get("max").getAsInt()).isEqualTo(15);
            assertThat(func.parameters().get("treasure").getAsBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("setDamage() factory method")
    class SetDamageFactory {

        @Test
        @DisplayName("creates set_damage function")
        void setDamage() {
            LootFunction func = LootFunction.setDamage(0.1f, 0.9f);

            assertThat(func.function()).isEqualTo("minecraft:set_damage");
            JsonObject damage = func.parameters().getAsJsonObject("damage");
            assertThat(damage.get("min").getAsFloat()).isCloseTo(0.1f, within(0.001f));
            assertThat(damage.get("max").getAsFloat()).isCloseTo(0.9f, within(0.001f));
        }
    }

    // ===== Display Methods =====

    @Nested
    @DisplayName("getDisplayName()")
    class GetDisplayNameMethod {

        @Test
        @DisplayName("removes minecraft: prefix and formats")
        void formatsDisplayName() {
            LootFunction func = LootFunction.setCount(5);
            assertThat(func.getDisplayName()).isEqualTo("set count");
        }

        @Test
        @DisplayName("handles enchant_with_levels")
        void enchantWithLevelsDisplayName() {
            LootFunction func = LootFunction.enchantWithLevels(5, 15, false);
            assertThat(func.getDisplayName()).isEqualTo("enchant with levels");
        }
    }

    @Nested
    @DisplayName("getParameterSummary()")
    class ParameterSummary {

        @Test
        @DisplayName("returns count for constant set_count")
        void countSummaryConstant() {
            LootFunction func = LootFunction.setCount(5);
            assertThat(func.getParameterSummary()).isEqualTo("5");
        }

        @Test
        @DisplayName("returns range for uniform set_count")
        void countSummaryUniform() {
            LootFunction func = LootFunction.setCount(2, 8);
            assertThat(func.getParameterSummary()).isEqualTo("2-8");
        }

        @Test
        @DisplayName("returns 'any' for enchant_randomly without options")
        void enchantRandomlySummary() {
            LootFunction func = LootFunction.enchantRandomly();
            assertThat(func.getParameterSummary()).isEqualTo("any");
        }

        @Test
        @DisplayName("returns level range for enchant_with_levels")
        void enchantWithLevelsSummary() {
            LootFunction func = LootFunction.enchantWithLevels(5, 15, false);
            assertThat(func.getParameterSummary()).isEqualTo("Lvl 5-15");
        }

        @Test
        @DisplayName("returns percentage range for set_damage")
        void setDamageSummary() {
            LootFunction func = LootFunction.setDamage(0.1f, 0.9f);
            assertThat(func.getParameterSummary()).isEqualTo("10-90%");
        }
    }

    // ===== Type Checking Methods =====

    @Nested
    @DisplayName("Type checking methods")
    class TypeChecking {

        @Test
        @DisplayName("isSetCount() returns true for set_count")
        void isSetCount() {
            LootFunction func = LootFunction.setCount(5);
            assertThat(func.isSetCount()).isTrue();
        }

        @Test
        @DisplayName("isSetCount() returns false for others")
        void isNotSetCount() {
            LootFunction func = LootFunction.enchantRandomly();
            assertThat(func.isSetCount()).isFalse();
        }

        @Test
        @DisplayName("isEnchantment() returns true for enchant functions")
        void isEnchantment() {
            assertThat(LootFunction.enchantRandomly().isEnchantment()).isTrue();
            assertThat(LootFunction.enchantWithLevels(5, 15, false).isEnchantment()).isTrue();
        }

        @Test
        @DisplayName("isDamage() returns true for set_damage")
        void isDamage() {
            LootFunction func = LootFunction.setDamage(0.5f, 1.0f);
            assertThat(func.isDamage()).isTrue();
        }

        @Test
        @DisplayName("isPotion() returns true for set_potion")
        void isPotion() {
            JsonObject params = new JsonObject();
            params.addProperty("id", "minecraft:strength");
            LootFunction func = new LootFunction("minecraft:set_potion", params, List.of());
            assertThat(func.isPotion()).isTrue();
        }

        @Test
        @DisplayName("isAttribute() returns true for set_attributes")
        void isAttribute() {
            LootFunction func = new LootFunction("minecraft:set_attributes", new JsonObject(), List.of());
            assertThat(func.isAttribute()).isTrue();
        }

        @Test
        @DisplayName("isLootingEnchant() returns true for looting_enchant")
        void isLootingEnchant() {
            LootFunction func = new LootFunction("minecraft:looting_enchant", new JsonObject(), List.of());
            assertThat(func.isLootingEnchant()).isTrue();
        }
    }

    // ===== Icon Methods =====

    @Nested
    @DisplayName("getIcon()")
    class Icons {

        @Test
        @DisplayName("returns sparkle for enchantment functions")
        void enchantmentIcon() {
            assertThat(LootFunction.enchantRandomly().getIcon()).isNotEmpty();
        }

        @Test
        @DisplayName("returns empty for set_count")
        void setCountNoIcon() {
            assertThat(LootFunction.setCount(5).getIcon()).isEmpty();
        }
    }

    // ===== NumberProvider Extraction =====

    @Nested
    @DisplayName("getCountAsNumberProvider()")
    class CountAsNumberProvider {

        @Test
        @DisplayName("returns constant for constant count")
        void constantCount() {
            LootFunction func = LootFunction.setCount(5);
            NumberProvider provider = func.getCountAsNumberProvider();

            assertThat(provider).isInstanceOf(NumberProvider.Constant.class);
            assertThat(provider.getMin()).isEqualTo(5);
            assertThat(provider.getMax()).isEqualTo(5);
        }

        @Test
        @DisplayName("returns uniform for uniform count")
        void uniformCount() {
            LootFunction func = LootFunction.setCount(2, 8);
            NumberProvider provider = func.getCountAsNumberProvider();

            assertThat(provider).isInstanceOf(NumberProvider.Uniform.class);
            assertThat(provider.getMin()).isEqualTo(2);
            assertThat(provider.getMax()).isEqualTo(8);
        }

        @Test
        @DisplayName("returns constant(1) for non-set_count function")
        void nonSetCountReturnsDefault() {
            LootFunction func = LootFunction.enchantRandomly();
            NumberProvider provider = func.getCountAsNumberProvider();

            assertThat(provider.getMin()).isEqualTo(1);
            assertThat(provider.getMax()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns constant(1) for set_count without count param")
        void setCountWithoutParam() {
            LootFunction func = new LootFunction("minecraft:set_count", new JsonObject(), List.of());
            NumberProvider provider = func.getCountAsNumberProvider();

            assertThat(provider.getMin()).isEqualTo(1);
        }
    }

    // ===== Record Equality =====

    @Nested
    @DisplayName("Record equality")
    class Equality {

        @Test
        @DisplayName("functions with same values are equal")
        void equalFunctions() {
            LootFunction func1 = LootFunction.setCount(5);
            LootFunction func2 = LootFunction.setCount(5);

            assertThat(func1).isEqualTo(func2);
        }

        @Test
        @DisplayName("functions with different values are not equal")
        void differentFunctions() {
            LootFunction func1 = LootFunction.setCount(5);
            LootFunction func2 = LootFunction.setCount(10);

            assertThat(func1).isNotEqualTo(func2);
        }
    }
}
