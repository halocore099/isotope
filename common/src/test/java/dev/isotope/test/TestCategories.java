package dev.isotope.test;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test category annotations for organizing and filtering tests.
 */
public final class TestCategories {

    private TestCategories() {}

    /**
     * Unit tests - fast, isolated, no external dependencies.
     * These tests should run in milliseconds and test single units of code.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("unit")
    public @interface UnitTest {}

    /**
     * Integration tests - may involve multiple components or mocked Minecraft APIs.
     * These tests verify that components work together correctly.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("integration")
    public @interface IntegrationTest {}

    /**
     * Slow tests - tests that take significant time to run.
     * Used for statistical tests, stress tests, or complex simulations.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("slow")
    public @interface SlowTest {}

    /**
     * Tests that require Minecraft runtime environment.
     * These are excluded from normal CI runs and meant for in-game testing.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("minecraft")
    public @interface MinecraftTest {}
}
