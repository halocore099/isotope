package dev.isotope.data.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NumberProvider implementations.
 */
class NumberProviderTest {

    private final Random random = new Random(42); // Fixed seed for reproducibility

    // ===== Constant Tests =====

    @Test
    @DisplayName("Constant provider always returns same value")
    void constantAlwaysReturnsSameValue() {
        NumberProvider provider = NumberProvider.constant(5);

        for (int i = 0; i < 100; i++) {
            assertEquals(5, provider.sample(random));
        }
    }

    @Test
    @DisplayName("Constant min and max are equal to value")
    void constantMinMaxEqualValue() {
        NumberProvider provider = NumberProvider.constant(10);

        assertEquals(10, provider.getMin());
        assertEquals(10, provider.getMax());
    }

    @Test
    @DisplayName("Constant toString returns integer format")
    void constantToString() {
        assertEquals("5", NumberProvider.constant(5).toString());
        assertEquals("0", NumberProvider.constant(0).toString());
        assertEquals("100", NumberProvider.constant(100).toString());
    }

    // ===== Uniform Tests =====

    @Test
    @DisplayName("Uniform provider returns values within range")
    void uniformWithinRange() {
        NumberProvider provider = NumberProvider.uniform(1, 10);

        for (int i = 0; i < 1000; i++) {
            float value = provider.sample(random);
            assertTrue(value >= 1, "Value " + value + " should be >= 1");
            assertTrue(value <= 10, "Value " + value + " should be <= 10");
        }
    }

    @Test
    @DisplayName("Uniform min and max return correct bounds")
    void uniformMinMax() {
        NumberProvider provider = NumberProvider.uniform(5, 15);

        assertEquals(5, provider.getMin());
        assertEquals(15, provider.getMax());
    }

    @Test
    @DisplayName("Uniform toString shows range format")
    void uniformToString() {
        assertEquals("1-10", NumberProvider.uniform(1, 10).toString());
        assertEquals("0-5", NumberProvider.uniform(0, 5).toString());
    }

    @Test
    @DisplayName("Uniform with same min/max acts like constant")
    void uniformSameMinMax() {
        NumberProvider provider = NumberProvider.uniform(7, 7);

        for (int i = 0; i < 100; i++) {
            assertEquals(7, provider.sample(random));
        }
    }

    // ===== Binomial Tests =====

    @Test
    @DisplayName("Binomial provider returns values within 0 to n")
    void binomialWithinRange() {
        NumberProvider provider = NumberProvider.binomial(10, 0.5f);

        for (int i = 0; i < 1000; i++) {
            float value = provider.sample(random);
            assertTrue(value >= 0, "Value should be >= 0");
            assertTrue(value <= 10, "Value should be <= 10");
        }
    }

    @Test
    @DisplayName("Binomial min is 0 and max is n")
    void binomialMinMax() {
        NumberProvider provider = NumberProvider.binomial(8, 0.3f);

        assertEquals(0, provider.getMin());
        assertEquals(8, provider.getMax());
    }

    @Test
    @DisplayName("Binomial toString shows formula format")
    void binomialToString() {
        assertEquals("binomial(10, 0.5)", NumberProvider.binomial(10, 0.5f).toString());
    }

    @Test
    @DisplayName("Binomial with p=0 always returns 0")
    void binomialProbabilityZero() {
        NumberProvider provider = NumberProvider.binomial(10, 0f);

        for (int i = 0; i < 100; i++) {
            assertEquals(0, provider.sample(random));
        }
    }

    @Test
    @DisplayName("Binomial with p=1 always returns n")
    void binomialProbabilityOne() {
        NumberProvider provider = NumberProvider.binomial(10, 1f);

        for (int i = 0; i < 100; i++) {
            assertEquals(10, provider.sample(random));
        }
    }

    // ===== Record Equality Tests =====

    @Test
    @DisplayName("Constant providers with same value are equal")
    void constantEquality() {
        assertEquals(NumberProvider.constant(5), NumberProvider.constant(5));
        assertNotEquals(NumberProvider.constant(5), NumberProvider.constant(6));
    }

    @Test
    @DisplayName("Uniform providers with same bounds are equal")
    void uniformEquality() {
        assertEquals(NumberProvider.uniform(1, 5), NumberProvider.uniform(1, 5));
        assertNotEquals(NumberProvider.uniform(1, 5), NumberProvider.uniform(1, 6));
    }

    @Test
    @DisplayName("Binomial providers with same parameters are equal")
    void binomialEquality() {
        assertEquals(NumberProvider.binomial(10, 0.5f), NumberProvider.binomial(10, 0.5f));
        assertNotEquals(NumberProvider.binomial(10, 0.5f), NumberProvider.binomial(10, 0.6f));
    }

    // ===== Type Safety Tests =====

    @Test
    @DisplayName("Different provider types are not equal")
    void differentTypesNotEqual() {
        NumberProvider constant = NumberProvider.constant(5);
        NumberProvider uniform = NumberProvider.uniform(5, 5);

        assertNotEquals(constant, uniform);
    }

    @Test
    @DisplayName("Factory methods return correct types")
    void factoryMethodTypes() {
        assertTrue(NumberProvider.constant(5) instanceof NumberProvider.Constant);
        assertTrue(NumberProvider.uniform(1, 5) instanceof NumberProvider.Uniform);
        assertTrue(NumberProvider.binomial(10, 0.5f) instanceof NumberProvider.Binomial);
    }
}
