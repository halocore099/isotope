package dev.isotope.linking;

import dev.isotope.Isotope;
import dev.isotope.data.StructureLootLink;
import dev.isotope.data.StructureLootLink.Confidence;
import dev.isotope.compat.Id;

import java.util.*;

/**
 * Calculates confirmation scores when multiple independent sources agree.
 *
 * Independent source categories:
 * 1. STATIC_ANALYSIS: Heuristics, Template parsing, Worldgen parsing
 * 2. DYNAMIC_ANALYSIS: Runtime assignment, Runtime observation
 * 3. HISTORICAL: Learned links from past sessions
 * 4. AUTHOR: Manual overrides
 *
 * When 2+ categories agree on a link → boost to higher confidence
 * When 3+ categories agree → near-certain (CONFIRMED)
 */
public final class ConfirmationScorer {

    private static final ConfirmationScorer INSTANCE = new ConfirmationScorer();

    /**
     * Categories of evidence sources (for multi-source corroboration).
     */
    public enum SourceCategory {
        STATIC_ANALYSIS,   // Heuristics, templates, worldgen JSON
        DYNAMIC_ANALYSIS,  // Runtime assignment, runtime observation
        HISTORICAL,        // Learned from past sessions
        AUTHOR             // Manual overrides
    }

    /**
     * Result of scoring a link with multi-source confirmation.
     */
    public record ConfirmationResult(
        StructureLootLink originalLink,
        int confirmingCategories,
        Set<SourceCategory> categories,
        Confidence effectiveConfidence,
        String confirmationDetail
    ) {
        public boolean isConfirmed() {
            return confirmingCategories >= 2;
        }
    }

    private ConfirmationScorer() {}

    public static ConfirmationScorer getInstance() {
        return INSTANCE;
    }

    /**
     * Score links based on multi-source corroboration.
     *
     * @param links            All candidate links (after initial linking)
     * @param templateLinks    Links found in templates (structure -> loot tables)
     * @param worldgenLinks    Links found in worldgen JSON (feature -> loot tables)
     * @param assignmentLinks  Links from runtime assignment capture
     * @param observationLinks Links from runtime observation
     * @param learnedLinks     Links from persistent learned history
     * @return Scored links with confirmation data
     */
    public List<ConfirmationResult> score(
            Collection<StructureLootLink> links,
            Map<Id, Set<Id>> templateLinks,
            Map<Id, Set<Id>> worldgenLinks,
            Map<Id, Set<Id>> assignmentLinks,
            Map<Id, Set<Id>> observationLinks,
            Map<Id, Set<Id>> learnedLinks) {

        List<ConfirmationResult> results = new ArrayList<>();

        for (StructureLootLink link : links) {
            Id structureId = link.structureId();
            Id tableId = link.lootTableId();

            // Count confirming source categories
            Set<SourceCategory> categories = new HashSet<>();

            // Static analysis sources
            boolean inTemplate = templateLinks.getOrDefault(structureId, Set.of()).contains(tableId);
            boolean inWorldgen = worldgenLinks.getOrDefault(structureId, Set.of()).contains(tableId);
            boolean isHeuristic = link.isHeuristic() || link.isFeatureMapping();
            boolean isContentAnalysis = link.isFromContentAnalysis();
            if (inTemplate || inWorldgen || isHeuristic || isContentAnalysis) {
                categories.add(SourceCategory.STATIC_ANALYSIS);
            }

            // Dynamic analysis sources
            boolean inAssignment = assignmentLinks.getOrDefault(structureId, Set.of()).contains(tableId);
            boolean inObservation = observationLinks.getOrDefault(structureId, Set.of()).contains(tableId);
            if (inAssignment || inObservation) {
                categories.add(SourceCategory.DYNAMIC_ANALYSIS);
            }

            // Historical sources
            boolean inLearned = learnedLinks.getOrDefault(structureId, Set.of()).contains(tableId);
            if (inLearned) {
                categories.add(SourceCategory.HISTORICAL);
            }

            // Author sources
            if (link.isAuthorDefined()) {
                categories.add(SourceCategory.AUTHOR);
            }

            // Calculate effective confidence
            Confidence effective = calculateEffectiveConfidence(link.confidence(), categories.size());

            // Build detail string
            String detail = buildDetailString(categories, inTemplate, inWorldgen, inAssignment, inObservation, inLearned);

            results.add(new ConfirmationResult(
                link,
                categories.size(),
                categories,
                effective,
                detail
            ));
        }

        return results;
    }

    /**
     * Calculate effective confidence based on number of confirming categories.
     */
    private Confidence calculateEffectiveConfidence(Confidence base, int categoryCount) {
        if (categoryCount >= 3) {
            // 3+ independent categories = CONFIRMED (near-certain)
            return Confidence.CONFIRMED;
        } else if (categoryCount >= 2) {
            // 2 categories = significant boost
            // Boost to at least TEMPLATE if currently lower
            if (base.getScore() < Confidence.TEMPLATE.getScore()) {
                return Confidence.TEMPLATE;
            }
        }
        return base;
    }

    /**
     * Build a human-readable detail string of confirming sources.
     */
    private String buildDetailString(Set<SourceCategory> categories,
                                     boolean template, boolean worldgen,
                                     boolean assignment, boolean observation,
                                     boolean learned) {
        List<String> sources = new ArrayList<>();

        if (template) sources.add("template");
        if (worldgen) sources.add("worldgen");
        if (assignment) sources.add("assignment");
        if (observation) sources.add("observation");
        if (learned) sources.add("learned");

        if (sources.isEmpty()) {
            return "heuristic only";
        }

        return String.join(" + ", sources);
    }

    /**
     * Apply confirmation scoring to update link confidence.
     * Returns a new list with promoted confidence where applicable.
     */
    public List<StructureLootLink> applyScoring(
            List<StructureLootLink> links,
            Map<Id, Set<Id>> templateLinks,
            Map<Id, Set<Id>> worldgenLinks,
            Map<Id, Set<Id>> assignmentLinks,
            Map<Id, Set<Id>> observationLinks,
            Map<Id, Set<Id>> learnedLinks) {

        List<ConfirmationResult> scored = score(links, templateLinks, worldgenLinks,
            assignmentLinks, observationLinks, learnedLinks);

        List<StructureLootLink> result = new ArrayList<>();
        int confirmations = 0;

        for (ConfirmationResult cr : scored) {
            if (cr.isConfirmed() && cr.effectiveConfidence() != cr.originalLink().confidence()) {
                // Promote to confirmed confidence
                StructureLootLink promoted = cr.originalLink().withConfirmation();
                result.add(promoted);
                confirmations++;
            } else {
                result.add(cr.originalLink());
            }
        }

        if (confirmations > 0) {
            Isotope.LOGGER.debug("[ConfirmationScorer] Promoted {} links via multi-source confirmation", confirmations);
        }

        return result;
    }
}
