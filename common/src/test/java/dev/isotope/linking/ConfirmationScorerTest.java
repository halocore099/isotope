package dev.isotope.linking;

import dev.isotope.compat.Id;
import dev.isotope.data.StructureLootLink;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ConfirmationScorer.
 */
@UnitTest
class ConfirmationScorerTest {

    private ConfirmationScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = ConfirmationScorer.getInstance();
    }

    // ===== Single Source =====

    @Nested
    @DisplayName("Single source scoring")
    class SingleSource {

        @Test
        @DisplayName("heuristic-only link is not confirmed")
        void heuristicOnlyNotConfirmed() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.HIGH);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(), // no template links
                Map.of(), // no worldgen links
                Map.of(), // no assignment links
                Map.of(), // no observation links
                Map.of()  // no learned links
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isConfirmed()).isFalse();
            assertThat(results.get(0).confirmingCategories()).isEqualTo(1);
        }

        @Test
        @DisplayName("template-only link is not confirmed")
        void templateOnlyNotConfirmed() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            var link = StructureLootLink.fromTemplate(structureId, tableId);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(structureId, Set.of(tableId)), // in template
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isConfirmed()).isFalse();
            assertThat(results.get(0).confirmingCategories()).isEqualTo(1);
            assertThat(results.get(0).categories()).contains(ConfirmationScorer.SourceCategory.STATIC_ANALYSIS);
        }
    }

    // ===== Two Source Confirmation =====

    @Nested
    @DisplayName("Two source confirmation")
    class TwoSourceConfirmation {

        @Test
        @DisplayName("template + observation is confirmed")
        void templatePlusObservation() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            var link = StructureLootLink.fromTemplate(structureId, tableId);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(structureId, Set.of(tableId)), // template
                Map.of(),
                Map.of(),
                Map.of(structureId, Set.of(tableId)), // observation
                Map.of()
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isConfirmed()).isTrue();
            assertThat(results.get(0).confirmingCategories()).isEqualTo(2);
            assertThat(results.get(0).categories())
                .contains(ConfirmationScorer.SourceCategory.STATIC_ANALYSIS)
                .contains(ConfirmationScorer.SourceCategory.DYNAMIC_ANALYSIS);
        }

        @Test
        @DisplayName("heuristic + learned is confirmed")
        void heuristicPlusLearned() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.HIGH);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(structureId, Set.of(tableId)) // learned
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isConfirmed()).isTrue();
            assertThat(results.get(0).confirmingCategories()).isEqualTo(2);
        }

        @Test
        @DisplayName("two sources boosts confidence to at least TEMPLATE")
        void twoSourcesBoostsConfidence() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            // Start with MEDIUM confidence (50)
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.MEDIUM);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(),
                Map.of(),
                Map.of(structureId, Set.of(tableId)), // assignment
                Map.of(),
                Map.of()
            );

            assertThat(results.get(0).isConfirmed()).isTrue();
            // Boosted from MEDIUM to TEMPLATE
            assertThat(results.get(0).effectiveConfidence().getScore())
                .isGreaterThanOrEqualTo(StructureLootLink.Confidence.TEMPLATE.getScore());
        }
    }

    // ===== Three Source Confirmation =====

    @Nested
    @DisplayName("Three source confirmation")
    class ThreeSourceConfirmation {

        @Test
        @DisplayName("three sources gives CONFIRMED confidence")
        void threeSourcesGivesConfirmed() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.MEDIUM);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(structureId, Set.of(tableId)), // template (static)
                Map.of(),
                Map.of(structureId, Set.of(tableId)), // assignment (dynamic)
                Map.of(),
                Map.of(structureId, Set.of(tableId)) // learned (historical)
            );

            assertThat(results.get(0).confirmingCategories()).isEqualTo(3);
            assertThat(results.get(0).effectiveConfidence())
                .isEqualTo(StructureLootLink.Confidence.CONFIRMED);
        }
    }

    // ===== Detail String =====

    @Nested
    @DisplayName("Confirmation detail string")
    class DetailString {

        @Test
        @DisplayName("shows 'heuristic only' when no confirming sources")
        void heuristicOnlyDetail() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            // Use a heuristic link that isn't in any maps
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.MEDIUM);

            // Don't include it in any source maps
            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
            );

            // When it's only a heuristic, it gets STATIC_ANALYSIS category
            // but if not in any source map, it should say "heuristic only"
            assertThat(results.get(0).confirmationDetail())
                .isEqualTo("heuristic only");
        }

        @Test
        @DisplayName("lists sources that confirm the link")
        void listsConfirmingSources() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            var link = StructureLootLink.fromTemplate(structureId, tableId);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(structureId, Set.of(tableId)), // template
                Map.of(),
                Map.of(),
                Map.of(structureId, Set.of(tableId)), // observation
                Map.of()
            );

            assertThat(results.get(0).confirmationDetail())
                .contains("template")
                .contains("observation");
        }
    }

    // ===== applyScoring() =====

    @Nested
    @DisplayName("applyScoring()")
    class ApplyScoring {

        @Test
        @DisplayName("promotes confirmed links")
        void promotesConfirmedLinks() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.MEDIUM);

            List<StructureLootLink> result = scorer.applyScoring(
                List.of(link),
                Map.of(structureId, Set.of(tableId)),
                Map.of(),
                Map.of(),
                Map.of(structureId, Set.of(tableId)),
                Map.of()
            );

            assertThat(result).hasSize(1);
            // Should be promoted to CONFIRMED
            assertThat(result.get(0).confidence())
                .isEqualTo(StructureLootLink.Confidence.CONFIRMED);
        }

        @Test
        @DisplayName("keeps unconfirmed links unchanged")
        void keepsUnconfirmedUnchanged() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            var link = StructureLootLink.heuristic(structureId, tableId, StructureLootLink.Confidence.HIGH);

            List<StructureLootLink> result = scorer.applyScoring(
                List.of(link),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
            );

            assertThat(result).hasSize(1);
            // Not confirmed, keeps original confidence
            assertThat(result.get(0).confidence())
                .isEqualTo(StructureLootLink.Confidence.HIGH);
        }
    }

    // ===== Source Categories =====

    @Nested
    @DisplayName("SourceCategory enum")
    class SourceCategories {

        @Test
        @DisplayName("all categories exist")
        void allCategoriesExist() {
            assertThat(ConfirmationScorer.SourceCategory.values())
                .contains(
                    ConfirmationScorer.SourceCategory.STATIC_ANALYSIS,
                    ConfirmationScorer.SourceCategory.DYNAMIC_ANALYSIS,
                    ConfirmationScorer.SourceCategory.HISTORICAL,
                    ConfirmationScorer.SourceCategory.AUTHOR
                );
        }
    }

    // ===== Author Category =====

    @Nested
    @DisplayName("Author category")
    class AuthorCategory {

        @Test
        @DisplayName("manual links get AUTHOR category")
        void manualLinksGetAuthorCategory() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            var link = StructureLootLink.manual(structureId, tableId);

            List<ConfirmationScorer.ConfirmationResult> results = scorer.score(
                List.of(link),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
            );

            assertThat(results.get(0).categories())
                .contains(ConfirmationScorer.SourceCategory.AUTHOR);
        }
    }
}
