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
     * MANUAL - Author explicitly created this link
     * VERIFIED - Observed at runtime (ground truth)
     * HIGH - Strong heuristic match (exact path match)
     * MEDIUM - Moderate heuristic match (partial path match)
     * LOW - Weak heuristic match (namespace only)
     */
    public enum Confidence {
        MANUAL(100, "Manual", 0xFF00FF00),      // Green - author defined
        VERIFIED(90, "Verified", 0xFF00FFFF),   // Cyan - runtime confirmed
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
        OBSERVATION,         // Runtime observation
        AUTHOR_ADDED,        // Author manually added
        AUTHOR_REMOVED       // Author manually removed (negative link)
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
     * Create a manual link from author override.
     */
    public static StructureLootLink manual(ResourceLocation structureId, ResourceLocation lootTableId) {
        return new StructureLootLink(structureId, lootTableId, Confidence.MANUAL, LinkSource.AUTHOR_ADDED);
    }

    /**
     * Upgrade confidence if this link is verified by observation.
     */
    public StructureLootLink withVerification() {
        if (confidence == Confidence.MANUAL) {
            // Don't downgrade manual links
            return this;
        }
        return new StructureLootLink(structureId, lootTableId, Confidence.VERIFIED, LinkSource.OBSERVATION);
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
}
