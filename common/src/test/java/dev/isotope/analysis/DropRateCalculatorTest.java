package dev.isotope.analysis;

import dev.isotope.data.loot.*;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DropRateCalculator.
 */
@UnitTest
class DropRateCalculatorTest {

    // ===== Single Pool Calculations =====

    @Nested
    @DisplayName("calculatePool()")
    class CalculatePool {

        @Test
        @DisplayName("calculates probability based on weights")
        void calculatesWeightProbability() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var iron = LootEntry.item(TestFixtures.mockId("minecraft", "iron_ingot"), 4);
            LootPool pool = LootPool.simple(1, List.of(diamond, iron));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            assertThat(rates).hasSize(2);

            // Diamond has weight 1 out of 5 = 20%
            var diamondRate = rates.stream()
                .filter(r -> r.item().getPath().equals("diamond"))
                .findFirst().orElseThrow();
            assertThat(diamondRate.probability()).isCloseTo(0.2f, within(0.001f));
            assertThat(diamondRate.percentChance()).isCloseTo(20f, within(0.1f));

            // Iron has weight 4 out of 5 = 80%
            var ironRate = rates.stream()
                .filter(r -> r.item().getPath().equals("iron_ingot"))
                .findFirst().orElseThrow();
            assertThat(ironRate.probability()).isCloseTo(0.8f, within(0.001f));
        }

        @Test
        @DisplayName("handles equal weights correctly")
        void equalWeights() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            LootPool pool = LootPool.simple(1, List.of(diamond, emerald));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            assertThat(rates).allMatch(r -> r.probability() == 0.5f);
        }

        @Test
        @DisplayName("returns empty list for pool with zero total weight")
        void zeroTotalWeight() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 0);
            LootPool pool = LootPool.simple(1, List.of(diamond));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            assertThat(rates).isEmpty();
        }

        @Test
        @DisplayName("skips entries without names")
        void skipsEntriesWithoutNames() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var empty = LootEntry.empty(4);
            LootPool pool = LootPool.simple(1, List.of(diamond, empty));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            // Should only have diamond, not empty entry
            assertThat(rates).hasSize(1);
            assertThat(rates.get(0).item().getPath()).isEqualTo("diamond");
            // But probability includes empty weight: 1 out of 5 = 20%
            assertThat(rates.get(0).probability()).isCloseTo(0.2f, within(0.001f));
        }

        @Test
        @DisplayName("stores pool and entry indices")
        void storesIndices() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            LootPool pool = LootPool.simple(1, List.of(diamond));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 5);

            assertThat(rates.get(0).poolIndex()).isEqualTo(5);
            assertThat(rates.get(0).entryIndex()).isEqualTo(0);
        }
    }

    // ===== Full Table Calculations =====

    @Nested
    @DisplayName("calculate()")
    class Calculate {

        @Test
        @DisplayName("combines rates from multiple pools")
        void combinesPoolRates() {
            var diamond = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var emerald = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            var pool1 = LootPool.simple(1, List.of(diamond));
            var pool2 = LootPool.simple(1, List.of(emerald));
            LootTableStructure table = TestFixtures.chestTable("test", pool1, pool2);

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculate(table);

            assertThat(rates).hasSize(2);
        }

        @Test
        @DisplayName("sorts results by probability descending")
        void sortsByProbability() {
            var rare = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var common = LootEntry.item(TestFixtures.mockId("minecraft", "iron_ingot"), 10);
            var pool = LootPool.simple(1, List.of(rare, common));
            LootTableStructure table = TestFixtures.chestTable("test", pool);

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculate(table);

            assertThat(rates.get(0).probability()).isGreaterThan(rates.get(1).probability());
            assertThat(rates.get(0).item().getPath()).isEqualTo("iron_ingot");
        }

        @Test
        @DisplayName("returns empty list for empty table")
        void emptyTable() {
            LootTableStructure table = LootTableStructure.empty(TestFixtures.mockId("minecraft", "test"));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculate(table);

            assertThat(rates).isEmpty();
        }
    }

    // ===== Pool Statistics =====

    @Nested
    @DisplayName("calculatePoolStats()")
    class PoolStats {

        @Test
        @DisplayName("calculates average rolls for constant")
        void averageRollsConstant() {
            LootPool pool = LootPool.simple(5, List.of());

            DropRateCalculator.PoolStats stats = DropRateCalculator.calculatePoolStats(pool, 0);

            assertThat(stats.avgRolls()).isEqualTo(5f);
        }

        @Test
        @DisplayName("calculates average rolls for uniform")
        void averageRollsUniform() {
            LootPool pool = LootPool.withRolls(2, 8, List.of());

            DropRateCalculator.PoolStats stats = DropRateCalculator.calculatePoolStats(pool, 0);

            assertThat(stats.avgRolls()).isCloseTo(5f, within(0.001f));
        }

        @Test
        @DisplayName("calculates total weight")
        void totalWeight() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 3);
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 7);
            LootPool pool = LootPool.simple(1, List.of(entry1, entry2));

            DropRateCalculator.PoolStats stats = DropRateCalculator.calculatePoolStats(pool, 0);

            assertThat(stats.totalWeight()).isEqualTo(10);
        }

        @Test
        @DisplayName("calculates expected items per roll")
        void expectedItems() {
            var entry1 = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            var entry2 = LootEntry.item(TestFixtures.mockId("minecraft", "emerald"), 1);
            // With 3 rolls and avg count 1, expected = 3 * (0.5 * 1 + 0.5 * 1) = 3
            LootPool pool = LootPool.simple(3, List.of(entry1, entry2));

            DropRateCalculator.PoolStats stats = DropRateCalculator.calculatePoolStats(pool, 0);

            assertThat(stats.expectedItemsPerRoll()).isCloseTo(3f, within(0.1f));
        }
    }

    // ===== Average Count Calculation =====

    @Nested
    @DisplayName("Average count from set_count function")
    class AverageCount {

        @Test
        @DisplayName("default count is 1 when no function")
        void defaultCount() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1);
            LootPool pool = LootPool.simple(1, List.of(entry));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            assertThat(rates.get(0).avgCount()).isEqualTo(1f);
        }

        @Test
        @DisplayName("extracts constant count")
        void constantCount() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1, 5, 5);
            LootPool pool = LootPool.simple(1, List.of(entry));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            // The count is 5, so avgCount should be 5
            assertThat(rates.get(0).avgCount()).isCloseTo(5f, within(0.1f));
        }

        @Test
        @DisplayName("calculates average for uniform count")
        void uniformCount() {
            var entry = LootEntry.item(TestFixtures.mockId("minecraft", "diamond"), 1, 2, 8);
            LootPool pool = LootPool.simple(1, List.of(entry));

            List<DropRateCalculator.DropRate> rates = DropRateCalculator.calculatePool(pool, 0);

            // Average of 2-8 is 5
            assertThat(rates.get(0).avgCount()).isCloseTo(5f, within(0.1f));
        }
    }

    // ===== Rarity Colors =====

    @Nested
    @DisplayName("getRarityColor()")
    class RarityColors {

        @Test
        @DisplayName("returns common color for >= 50%")
        void commonColor() {
            int color = DropRateCalculator.getRarityColor(0.6f);
            assertThat(color).isEqualTo(0xFF888888);
        }

        @Test
        @DisplayName("returns uncommon color for 20-50%")
        void uncommonColor() {
            int color = DropRateCalculator.getRarityColor(0.3f);
            assertThat(color).isEqualTo(0xFF55FF55);
        }

        @Test
        @DisplayName("returns rare color for 5-20%")
        void rareColor() {
            int color = DropRateCalculator.getRarityColor(0.1f);
            assertThat(color).isEqualTo(0xFF5555FF);
        }

        @Test
        @DisplayName("returns epic color for 1-5%")
        void epicColor() {
            int color = DropRateCalculator.getRarityColor(0.03f);
            assertThat(color).isEqualTo(0xFFAA00AA);
        }

        @Test
        @DisplayName("returns legendary color for < 1%")
        void legendaryColor() {
            int color = DropRateCalculator.getRarityColor(0.005f);
            assertThat(color).isEqualTo(0xFFFFAA00);
        }
    }

    // ===== DropRate Record =====

    @Nested
    @DisplayName("DropRate record")
    class DropRateRecord {

        @Test
        @DisplayName("percentChance() converts probability to percentage")
        void percentChance() {
            var rate = new DropRateCalculator.DropRate(
                TestFixtures.mockId("minecraft", "diamond"),
                0.25f, 1f, 1, 0, 0
            );

            assertThat(rate.percentChance()).isCloseTo(25f, within(0.01f));
        }
    }
}
