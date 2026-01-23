package dev.isotope.linking;

import dev.isotope.Isotope;
import dev.isotope.data.StructureLootLink;

import java.util.*;

/**
 * Validates structure-loot links by namespace coherence.
 *
 * Cross-references discovered links against namespace patterns:
 * - Same namespace: Normal (structure and loot from same mod)
 * - Mod to vanilla: Common (mod structure uses vanilla loot tables)
 * - Vanilla to mod: Rare, flagged for review
 * - Cross-mod: Very rare, warning
 *
 * This helps identify potentially incorrect links that may have been
 * created by overly aggressive heuristics.
 */
public final class NamespaceValidator {

    private static final NamespaceValidator INSTANCE = new NamespaceValidator();

    /**
     * Classification of namespace relationships.
     */
    public enum NamespaceMatch {
        EXACT,              // Both same namespace (minecraft:x -> minecraft:y)
        VANILLA_TO_VANILLA, // Both minecraft namespace
        MOD_TO_VANILLA,     // Mod structure uses vanilla loot (common pattern)
        VANILLA_TO_MOD,     // Vanilla structure uses mod loot (rare, flag)
        CROSS_MOD           // Different mod namespaces (very rare, warning)
    }

    /**
     * Result of validating a single link.
     */
    public record ValidationResult(
        StructureLootLink link,
        boolean isValid,
        NamespaceMatch matchType,
        String note
    ) {
        public boolean needsReview() {
            return matchType == NamespaceMatch.VANILLA_TO_MOD ||
                   matchType == NamespaceMatch.CROSS_MOD;
        }
    }

    /**
     * Summary of validation for a batch of links.
     */
    public record ValidationSummary(
        int totalLinks,
        int validLinks,
        int flaggedLinks,
        List<ValidationResult> flagged
    ) {}

    private NamespaceValidator() {}

    public static NamespaceValidator getInstance() {
        return INSTANCE;
    }

    /**
     * Validate a collection of links.
     * Returns validation results for all links.
     */
    public List<ValidationResult> validate(Collection<StructureLootLink> links) {
        List<ValidationResult> results = new ArrayList<>();

        for (StructureLootLink link : links) {
            results.add(validateLink(link));
        }

        return results;
    }

    /**
     * Validate a single link.
     */
    public ValidationResult validateLink(StructureLootLink link) {
        String structureNs = link.structureId().getNamespace();
        String lootNs = link.lootTableId().getNamespace();

        NamespaceMatch matchType = categorizeNamespaceMatch(structureNs, lootNs);
        boolean isValid = matchType != NamespaceMatch.CROSS_MOD;
        String note = generateNote(matchType, structureNs, lootNs);

        return new ValidationResult(link, isValid, matchType, note);
    }

    /**
     * Categorize the namespace relationship between structure and loot table.
     */
    private NamespaceMatch categorizeNamespaceMatch(String structureNs, String lootNs) {
        boolean structureIsVanilla = "minecraft".equals(structureNs);
        boolean lootIsVanilla = "minecraft".equals(lootNs);

        if (structureNs.equals(lootNs)) {
            if (structureIsVanilla) {
                return NamespaceMatch.VANILLA_TO_VANILLA;
            }
            return NamespaceMatch.EXACT;
        }

        if (!structureIsVanilla && lootIsVanilla) {
            // Mod structure using vanilla loot - common pattern
            return NamespaceMatch.MOD_TO_VANILLA;
        }

        if (structureIsVanilla && !lootIsVanilla) {
            // Vanilla structure using mod loot - unusual, flag for review
            return NamespaceMatch.VANILLA_TO_MOD;
        }

        // Different mod namespaces - very unusual
        return NamespaceMatch.CROSS_MOD;
    }

    /**
     * Generate a human-readable note about the namespace relationship.
     */
    private String generateNote(NamespaceMatch matchType, String structureNs, String lootNs) {
        return switch (matchType) {
            case EXACT -> "Same mod namespace (" + structureNs + ")";
            case VANILLA_TO_VANILLA -> "Both vanilla";
            case MOD_TO_VANILLA -> structureNs + " structure uses vanilla loot";
            case VANILLA_TO_MOD -> "Review: vanilla structure uses " + lootNs + " loot";
            case CROSS_MOD -> "Warning: " + structureNs + " structure uses " + lootNs + " loot";
        };
    }

    /**
     * Validate and summarize a batch of links.
     */
    public ValidationSummary validateAndSummarize(Collection<StructureLootLink> links) {
        List<ValidationResult> results = validate(links);

        int valid = 0;
        int flagged = 0;
        List<ValidationResult> flaggedLinks = new ArrayList<>();

        for (ValidationResult result : results) {
            if (result.isValid()) {
                valid++;
            } else {
                flagged++;
            }
            if (result.needsReview()) {
                flaggedLinks.add(result);
            }
        }

        return new ValidationSummary(links.size(), valid, flagged, flaggedLinks);
    }

    /**
     * Filter links to only include those that pass validation.
     * Does NOT remove flagged links, just logs warnings.
     */
    public List<StructureLootLink> filterAndWarn(List<StructureLootLink> links) {
        List<StructureLootLink> result = new ArrayList<>();
        int warnings = 0;

        for (StructureLootLink link : links) {
            ValidationResult vr = validateLink(link);

            if (vr.matchType() == NamespaceMatch.CROSS_MOD) {
                // Cross-mod links are suspicious but we don't remove them
                Isotope.LOGGER.warn("[NamespaceValidator] Suspicious cross-mod link: {} -> {} ({})",
                    link.structureId(), link.lootTableId(), vr.note());
                warnings++;
            } else if (vr.matchType() == NamespaceMatch.VANILLA_TO_MOD) {
                // Vanilla using mod is unusual
                Isotope.LOGGER.debug("[NamespaceValidator] Unusual link: {} -> {} ({})",
                    link.structureId(), link.lootTableId(), vr.note());
            }

            // We don't filter out links, just warn about suspicious ones
            result.add(link);
        }

        if (warnings > 0) {
            Isotope.LOGGER.info("[NamespaceValidator] {} suspicious cross-namespace links detected", warnings);
        }

        return result;
    }

    /**
     * Get summary statistics for a set of validation results.
     */
    public Map<NamespaceMatch, Integer> getMatchTypeCounts(List<ValidationResult> results) {
        Map<NamespaceMatch, Integer> counts = new EnumMap<>(NamespaceMatch.class);

        for (ValidationResult result : results) {
            counts.merge(result.matchType(), 1, Integer::sum);
        }

        return counts;
    }
}
