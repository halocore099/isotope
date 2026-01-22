package dev.isotope.data;

import net.minecraft.resources.Identifier;

/**
 * Represents a link between a structure and a loot table.
 *
 * Links can be:
 * - Heuristic (path matching, naming conventions)
 * - Verified (observed at runtime)
 * - Manual (author-defined override)
 */
public record StructureLootLink(
    Identifier structureId,
    Identifier lootTableId,
    Confidence confidence,
    LinkSource source
) {
    /**
     * Confidence level for the link.
     *
     * MANUAL - Author explicitly created this link (highest authority)
     * MOD_DECLARED - Mod explicitly declared this link via API (very high confidence)
     * VERIFIED - Observed at runtime (ground truth)
     * CONFIRMED - Multiple independent sources agree on this link
     * RUNTIME_ASSIGNED - Captured via setLootTable() hook with caller context
     * TEMPLATE - Found in structure template .nbt file (deterministic)
     * LEARNED - Previously verified in past sessions (persistent memory)
     * HIGH - Strong heuristic match (exact path match)
     * MEDIUM - Moderate heuristic match (partial path match)
     * LOW - Weak heuristic match (namespace only)
     *
     * Confidence should only ever be PROMOTED, never downgraded.
     */
    public enum Confidence {
        MANUAL(100, "Manual", 0xFF00FF00),      // Green - author defined
        MOD_DECLARED(95, "Mod", 0xFF88FF00),    // Yellow-green - mod declared via API
        VERIFIED(90, "Verified", 0xFF00FFFF),   // Cyan - runtime confirmed
        CONFIRMED(88, "Confirmed", 0xFF00DDAA), // Teal-cyan - multiple sources agree
        RUNTIME_ASSIGNED(85, "Assigned", 0xFF55DDAA), // Teal - captured from setLootTable() call
        TEMPLATE(80, "Template", 0xFF44DDFF),   // Light blue - parsed from .nbt
        LEARNED(75, "Learned", 0xFF66AAFF),     // Blue - from past sessions
        HIGH(70, "High", 0xFF88FF88),           // Light green
        MEDIUM(50, "Medium", 0xFFFFFF00),       // Yellow
        LOW(30, "Low", 0xFFFF8800);             // Orange

        private final int score;
        private final String label;
        private final int color;

        Confidence(int score, String label, int color) {
            this.score = score;
            this.label = label;
            this.color = color;
        }

        public int getScore() { return score; }
        public String getLabel() { return label; }
        public int getColor() { return color; }
    }

    /**
     * How the link was established.
     */
    public enum LinkSource {
        HEURISTIC_PATH,      // Path matching (chests/village_* -> village structure)
        HEURISTIC_NAMESPACE, // Same mod namespace
        TEMPLATE_PARSE,      // Extracted from structure template .nbt file
        OBSERVATION,         // Runtime observation (spatial correlation)
        RUNTIME_ASSIGNED,    // Captured from setLootTable() call with caller context
        AUTHOR_ADDED,        // Author manually added
        AUTHOR_REMOVED,      // Author manually removed (negative link)
        FEATURE_MAPPING,     // From known feature-to-loot mappings (dungeons, etc.)
        WORLDGEN_JSON,       // Parsed from worldgen JSON files (configured_feature)
        LEARNED,             // Loaded from persistent learned links file
        MULTI_SOURCE,        // Confirmed by multiple independent sources
        MOD_DECLARED,        // Declared by mod via ModLinkRegistry API or isotope_links.json
        SPAWNER_ENTITY,      // Entity loot table from spawner block in structure template
        CONTENT_ANALYSIS     // Inferred from signature items in loot table content
    }

    /**
     * Create a heuristic link with path-based confidence.
     */
    public static StructureLootLink heuristic(Identifier structureId, Identifier lootTableId,
                                               Confidence confidence) {
        return new StructureLootLink(structureId, lootTableId, confidence,
            confidence == Confidence.LOW ? LinkSource.HEURISTIC_NAMESPACE : LinkSource.HEURISTIC_PATH);
    }

    /**
     * Create a verified link from runtime observation.
     */
    public static StructureLootLink verified(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.VERIFIED, LinkSource.OBSERVATION);
    }

    /**
     * Create a template-based link from .nbt file parsing.
     */
    public static StructureLootLink fromTemplate(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.TEMPLATE, LinkSource.TEMPLATE_PARSE);
    }

    /**
     * Create a manual link from author override.
     */
    public static StructureLootLink manual(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.MANUAL, LinkSource.AUTHOR_ADDED);
    }

    /**
     * Create a feature link from known feature-to-loot mappings.
     * Features are treated like structures but are actually fire-and-forget decorations.
     */
    public static StructureLootLink feature(Identifier featureId, Identifier lootTableId) {
        return new StructureLootLink(featureId, lootTableId, Confidence.HIGH, LinkSource.FEATURE_MAPPING);
    }

    /**
     * Create a runtime-assigned link from setLootTable() capture.
     * This captures the exact moment a loot table is assigned to a container during generation.
     */
    public static StructureLootLink runtimeAssigned(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.RUNTIME_ASSIGNED, LinkSource.RUNTIME_ASSIGNED);
    }

    /**
     * Create a link from worldgen JSON parsing (configured_feature files).
     */
    public static StructureLootLink fromWorldgen(Identifier featureId, Identifier lootTableId) {
        return new StructureLootLink(featureId, lootTableId, Confidence.HIGH, LinkSource.WORLDGEN_JSON);
    }

    /**
     * Create a link from persistent learned history.
     * Previously verified links are pre-loaded with LEARNED confidence.
     */
    public static StructureLootLink learned(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.LEARNED, LinkSource.LEARNED);
    }

    /**
     * Create a link from persistent learned history with confidence decay.
     * Version age determines confidence level:
     *   0-1 versions: LEARNED (75)
     *   2 versions: MEDIUM (50)
     *   3+ versions: LOW (30)
     *
     * @param versionAge Number of MC minor versions since link was learned
     */
    public static StructureLootLink learned(Identifier structureId, Identifier lootTableId, int versionAge) {
        Confidence confidence;
        if (versionAge <= 1) {
            confidence = Confidence.LEARNED;
        } else if (versionAge == 2) {
            confidence = Confidence.MEDIUM;
        } else {
            confidence = Confidence.LOW;
        }
        return new StructureLootLink(structureId, lootTableId, confidence, LinkSource.LEARNED);
    }

    /**
     * Create a confirmed link when multiple independent sources agree.
     */
    public static StructureLootLink confirmed(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.CONFIRMED, LinkSource.MULTI_SOURCE);
    }

    /**
     * Create a mod-declared link from ModLinkRegistry API or isotope_links.json.
     * These links have very high confidence (95) since the mod author explicitly declared them.
     */
    public static StructureLootLink modDeclared(Identifier structureId, Identifier lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.MOD_DECLARED, LinkSource.MOD_DECLARED);
    }

    /**
     * Create a link from spawner entity analysis.
     * When a structure contains a spawner block, the spawned entity's loot table
     * is linked to the structure with TEMPLATE confidence (deterministic from NBT).
     *
     * Example: A dungeon with a zombie spawner links to minecraft:entities/zombie
     */
    public static StructureLootLink spawnerEntity(Identifier structureId, Identifier entityLootTableId) {
        return new StructureLootLink(structureId, entityLootTableId, Confidence.TEMPLATE, LinkSource.SPAWNER_ENTITY);
    }

    /**
     * Check if this link is from spawner entity analysis.
     */
    public boolean isFromSpawner() {
        return source == LinkSource.SPAWNER_ENTITY;
    }

    /**
     * Create a link from content analysis (signature items in loot table).
     * Confidence is derived from the signature item's confidence score.
     *
     * @param hintConfidence 0-100 confidence from signature item analysis
     */
    public static StructureLootLink contentAnalysis(Identifier structureId, Identifier lootTableId, int hintConfidence) {
        // Map hint confidence to our confidence levels
        Confidence confidence;
        if (hintConfidence >= 85) {
            confidence = Confidence.HIGH;
        } else if (hintConfidence >= 60) {
            confidence = Confidence.MEDIUM;
        } else {
            confidence = Confidence.LOW;
        }
        return new StructureLootLink(structureId, lootTableId, confidence, LinkSource.CONTENT_ANALYSIS);
    }

    /**
     * Check if this link is from content analysis.
     */
    public boolean isFromContentAnalysis() {
        return source == LinkSource.CONTENT_ANALYSIS;
    }

    /**
     * Promote confidence if a higher level is available.
     * Confidence only goes UP, never down.
     *
     * Promotion order: LOW → MEDIUM → HIGH → TEMPLATE → VERIFIED → MANUAL
     */
    public StructureLootLink promoteConfidence(Confidence newConfidence, LinkSource newSource) {
        if (newConfidence.getScore() > confidence.getScore()) {
            return new StructureLootLink(structureId, lootTableId, newConfidence, newSource);
        }
        return this; // Keep current (higher or equal) confidence
    }

    /**
     * Upgrade confidence if this link is verified by observation.
     */
    public StructureLootLink withVerification() {
        return promoteConfidence(Confidence.VERIFIED, LinkSource.OBSERVATION);
    }

    /**
     * Upgrade confidence if found in template.
     */
    public StructureLootLink withTemplateEvidence() {
        return promoteConfidence(Confidence.TEMPLATE, LinkSource.TEMPLATE_PARSE);
    }

    /**
     * Check if this is an author-defined link.
     */
    public boolean isAuthorDefined() {
        return source == LinkSource.AUTHOR_ADDED || source == LinkSource.AUTHOR_REMOVED;
    }

    /**
     * Check if this link was verified by runtime observation.
     */
    public boolean isVerified() {
        return confidence == Confidence.VERIFIED || source == LinkSource.OBSERVATION;
    }

    /**
     * Check if this link was found in a structure template.
     */
    public boolean isFromTemplate() {
        return confidence == Confidence.TEMPLATE || source == LinkSource.TEMPLATE_PARSE;
    }

    /**
     * Check if this link is based on heuristics (path/namespace matching).
     */
    public boolean isHeuristic() {
        return source == LinkSource.HEURISTIC_PATH || source == LinkSource.HEURISTIC_NAMESPACE;
    }

    /**
     * Check if this link is from a feature mapping (dungeons, etc.).
     */
    public boolean isFeatureMapping() {
        return source == LinkSource.FEATURE_MAPPING;
    }

    /**
     * Check if this link was loaded from persistent learned history.
     */
    public boolean isLearned() {
        return confidence == Confidence.LEARNED || source == LinkSource.LEARNED;
    }

    /**
     * Check if this link is confirmed by multiple independent sources.
     */
    public boolean isConfirmed() {
        return confidence == Confidence.CONFIRMED || source == LinkSource.MULTI_SOURCE;
    }

    /**
     * Check if this link was declared by a mod via the ModLinkRegistry API.
     */
    public boolean isModDeclared() {
        return confidence == Confidence.MOD_DECLARED || source == LinkSource.MOD_DECLARED;
    }

    /**
     * Check if this link was discovered from worldgen JSON files.
     */
    public boolean isFromWorldgen() {
        return source == LinkSource.WORLDGEN_JSON;
    }

    /**
     * Upgrade to confirmed status when multiple sources agree.
     */
    public StructureLootLink withConfirmation() {
        return promoteConfidence(Confidence.CONFIRMED, LinkSource.MULTI_SOURCE);
    }

    /**
     * Upgrade with learned evidence from past sessions.
     */
    public StructureLootLink withLearnedEvidence() {
        return promoteConfidence(Confidence.LEARNED, LinkSource.LEARNED);
    }
}
