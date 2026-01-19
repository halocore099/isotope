package dev.isotope.compat;

/**
 * Feature availability flags for different Minecraft versions.
 *
 * Use these flags to disable features gracefully on incompatible versions
 * rather than removing code (per CLAUDE.md requirements).
 *
 * Note: When Stonecutter is configured, version conditionals will enable
 * different values for different MC versions.
 */
public final class FeatureFlags {

    private FeatureFlags() {}

    // ========== Registry API Features ==========

    /**
     * 1.21+ uses Optional-based registry lookups.
     * 1.20.x uses nullable returns.
     */
    public static final boolean OPTIONAL_REGISTRY_LOOKUP = MCVersion.IS_1_21_PLUS;

    /**
     * 1.21+ uses lookupOrThrow() for registry access.
     * 1.20.x uses .registry().orElseThrow().
     */
    public static final boolean LOOKUP_OR_THROW_METHOD = MCVersion.IS_1_21_PLUS;

    // ========== Loot Table Features ==========

    /**
     * Random sequence editing (available in 1.20+).
     */
    public static final boolean RANDOM_SEQUENCE_EDITING = true;

    /**
     * All loot IDE features available (editing, testing, etc.).
     * Available on all supported versions.
     */
    public static final boolean LOOT_IDE_FEATURES = true;

    // ========== Structure Features ==========

    /**
     * Structure observation and linking available.
     */
    public static final boolean STRUCTURE_OBSERVATION = true;

    /**
     * Template pool parsing for jigsaw structures.
     */
    public static final boolean TEMPLATE_POOL_PARSING = true;

    // ========== Mixin Compatibility ==========

    /**
     * ReloadableServerRegistries.Holder exists (1.20.2+).
     * In 1.20.1, loot tables are accessed via LootDataManager instead.
     */
    public static final boolean HAS_RELOADABLE_REGISTRIES_HOLDER = MCVersion.IS_1_20_2_PLUS;

    // ========== UI Messages ==========

    /**
     * Get a user-friendly message when a feature is disabled.
     */
    public static String getDisabledMessage(String featureName) {
        return featureName + " requires Minecraft 1.21+";
    }

    /**
     * Get a user-friendly message for minimum required version.
     */
    public static String getMinVersionMessage(String feature, String minVersion) {
        return feature + " requires Minecraft " + minVersion + " or newer";
    }
}
