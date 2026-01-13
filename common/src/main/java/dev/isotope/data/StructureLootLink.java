package dev.isotope.data;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a link between a structure and a loot table.
 *
 * Links can be:
 * - Heuristic (path matching, naming conventions)
 * - Verified (observed at runtime)
 * - Manual (author-defined override)
 */
public record StructureLootLink(
    ResourceLocation structureId,
    ResourceLocation lootTableId,
    Confidence confidence,
    LinkSource source
) {
    /**
     * Confidence level for the link.
     *
     * MANUAL - Author explicitly created this link (highest authority)
     * VERIFIED - Observed at runtime (ground truth)
     * TEMPLATE - Found in structure template .nbt file (deterministic)
     * HIGH - Strong heuristic match (exact path match)
     * MEDIUM - Moderate heuristic match (partial path match)
     * LOW - Weak heuristic match (namespace only)
     *
     * Confidence should only ever be PROMOTED, never downgraded.
     */
    public enum Confidence {
        MANUAL(100, "Manual", 0xFF00FF00),      // Green - author defined
        VERIFIED(90, "Verified", 0xFF00FFFF),   // Cyan - runtime confirmed
        TEMPLATE(80, "Template", 0xFF44DDFF),   // Light blue - parsed from .nbt
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
        OBSERVATION,         // Runtime observation
        AUTHOR_ADDED,        // Author manually added
        AUTHOR_REMOVED,      // Author manually removed (negative link)
        FEATURE_MAPPING      // From known feature-to-loot mappings (dungeons, etc.)
    }

    /**
     * Create a heuristic link with path-based confidence.
     */
    public static StructureLootLink heuristic(ResourceLocation structureId, ResourceLocation lootTableId,
                                               Confidence confidence) {
        return new StructureLootLink(structureId, lootTableId, confidence,
            confidence == Confidence.LOW ? LinkSource.HEURISTIC_NAMESPACE : LinkSource.HEURISTIC_PATH);
    }

    /**
     * Create a verified link from runtime observation.
     */
    public static StructureLootLink verified(ResourceLocation structureId, ResourceLocation lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.VERIFIED, LinkSource.OBSERVATION);
    }

    /**
     * Create a template-based link from .nbt file parsing.
     */
    public static StructureLootLink fromTemplate(ResourceLocation structureId, ResourceLocation lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.TEMPLATE, LinkSource.TEMPLATE_PARSE);
    }

    /**
     * Create a manual link from author override.
     */
    public static StructureLootLink manual(ResourceLocation structureId, ResourceLocation lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.MANUAL, LinkSource.AUTHOR_ADDED);
    }

    /**
     * Create a feature link from known feature-to-loot mappings.
     * Features are treated like structures but are actually fire-and-forget decorations.
     */
    public static StructureLootLink feature(ResourceLocation featureId, ResourceLocation lootTableId) {
        return new StructureLootLink(featureId, lootTableId, Confidence.HIGH, LinkSource.FEATURE_MAPPING);
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
}
