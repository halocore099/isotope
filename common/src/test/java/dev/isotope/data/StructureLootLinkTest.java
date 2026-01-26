package dev.isotope.data;

import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StructureLootLink record.
 */
@UnitTest
class StructureLootLinkTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("heuristic() factory method")
    class HeuristicFactory {

        @Test
        @DisplayName("creates link with HIGH confidence")
        void highConfidence() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            StructureLootLink link = StructureLootLink.heuristic(structureId, tableId,
                StructureLootLink.Confidence.HIGH);

            assertThat(link.structureId()).isEqualTo(structureId);
            assertThat(link.lootTableId()).isEqualTo(tableId);
            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.HIGH);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.HEURISTIC_PATH);
        }

        @Test
        @DisplayName("creates link with LOW confidence (namespace source)")
        void lowConfidenceUsesNamespaceSource() {
            var structureId = TestFixtures.mockId("mymod", "custom_structure");
            var tableId = TestFixtures.mockId("mymod", "chests/custom");
            StructureLootLink link = StructureLootLink.heuristic(structureId, tableId,
                StructureLootLink.Confidence.LOW);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.LOW);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.HEURISTIC_NAMESPACE);
        }
    }

    @Nested
    @DisplayName("verified() factory method")
    class VerifiedFactory {

        @Test
        @DisplayName("creates verified link from observation")
        void createsVerifiedLink() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            StructureLootLink link = StructureLootLink.verified(structureId, tableId);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.VERIFIED);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.OBSERVATION);
        }
    }

    @Nested
    @DisplayName("fromTemplate() factory method")
    class TemplateFactory {

        @Test
        @DisplayName("creates template link")
        void createsTemplateLink() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            StructureLootLink link = StructureLootLink.fromTemplate(structureId, tableId);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.TEMPLATE);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.TEMPLATE_PARSE);
        }
    }

    @Nested
    @DisplayName("manual() factory method")
    class ManualFactory {

        @Test
        @DisplayName("creates manual link")
        void createsManualLink() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/custom");
            StructureLootLink link = StructureLootLink.manual(structureId, tableId);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.MANUAL);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.AUTHOR_ADDED);
        }
    }

    @Nested
    @DisplayName("feature() factory method")
    class FeatureFactory {

        @Test
        @DisplayName("creates feature link")
        void createsFeatureLink() {
            var featureId = TestFixtures.mockId("minecraft", "monster_room");
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            StructureLootLink link = StructureLootLink.feature(featureId, tableId);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.HIGH);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.FEATURE_MAPPING);
        }
    }

    @Nested
    @DisplayName("learned() factory methods")
    class LearnedFactory {

        @Test
        @DisplayName("creates learned link without version age")
        void createsLearnedLink() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink link = StructureLootLink.learned(structureId, tableId);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.LEARNED);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.LEARNED);
        }

        @Test
        @DisplayName("creates learned link with version decay - recent")
        void learnedWithRecentVersion() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink link = StructureLootLink.learned(structureId, tableId, 1);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.LEARNED);
        }

        @Test
        @DisplayName("creates learned link with version decay - medium age")
        void learnedWithMediumVersion() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink link = StructureLootLink.learned(structureId, tableId, 2);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.MEDIUM);
        }

        @Test
        @DisplayName("creates learned link with version decay - old")
        void learnedWithOldVersion() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink link = StructureLootLink.learned(structureId, tableId, 5);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.LOW);
        }
    }

    @Nested
    @DisplayName("contentAnalysis() factory method")
    class ContentAnalysisFactory {

        @Test
        @DisplayName("creates HIGH confidence for hint >= 85")
        void highConfidenceForHighHint() {
            var structureId = TestFixtures.mockId("minecraft", "ancient_city");
            var tableId = TestFixtures.mockId("minecraft", "chests/ancient_city");
            StructureLootLink link = StructureLootLink.contentAnalysis(structureId, tableId, 90);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.HIGH);
            assertThat(link.source()).isEqualTo(StructureLootLink.LinkSource.CONTENT_ANALYSIS);
        }

        @Test
        @DisplayName("creates MEDIUM confidence for hint 60-84")
        void mediumConfidenceForMediumHint() {
            var structureId = TestFixtures.mockId("minecraft", "ancient_city");
            var tableId = TestFixtures.mockId("minecraft", "chests/ancient_city");
            StructureLootLink link = StructureLootLink.contentAnalysis(structureId, tableId, 70);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.MEDIUM);
        }

        @Test
        @DisplayName("creates LOW confidence for hint < 60")
        void lowConfidenceForLowHint() {
            var structureId = TestFixtures.mockId("minecraft", "ancient_city");
            var tableId = TestFixtures.mockId("minecraft", "chests/ancient_city");
            StructureLootLink link = StructureLootLink.contentAnalysis(structureId, tableId, 40);

            assertThat(link.confidence()).isEqualTo(StructureLootLink.Confidence.LOW);
        }
    }

    // ===== Confidence Promotion =====

    @Nested
    @DisplayName("promoteConfidence()")
    class ConfidencePromotion {

        @Test
        @DisplayName("promotes to higher confidence")
        void promotesToHigher() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink original = StructureLootLink.heuristic(structureId, tableId,
                StructureLootLink.Confidence.MEDIUM);
            StructureLootLink promoted = original.promoteConfidence(
                StructureLootLink.Confidence.HIGH, StructureLootLink.LinkSource.HEURISTIC_PATH);

            assertThat(promoted.confidence()).isEqualTo(StructureLootLink.Confidence.HIGH);
        }

        @Test
        @DisplayName("keeps current if new confidence is lower")
        void keepsIfLower() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink original = StructureLootLink.verified(structureId, tableId);
            StructureLootLink promoted = original.promoteConfidence(
                StructureLootLink.Confidence.HIGH, StructureLootLink.LinkSource.HEURISTIC_PATH);

            assertThat(promoted).isSameAs(original);
            assertThat(promoted.confidence()).isEqualTo(StructureLootLink.Confidence.VERIFIED);
        }

        @Test
        @DisplayName("keeps current if new confidence is equal")
        void keepsIfEqual() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink original = StructureLootLink.fromTemplate(structureId, tableId);
            StructureLootLink promoted = original.promoteConfidence(
                StructureLootLink.Confidence.TEMPLATE, StructureLootLink.LinkSource.WORLDGEN_JSON);

            assertThat(promoted).isSameAs(original);
        }
    }

    @Nested
    @DisplayName("Convenience promotion methods")
    class ConveniencePromotion {

        @Test
        @DisplayName("withVerification() promotes to VERIFIED")
        void withVerification() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink original = StructureLootLink.heuristic(structureId, tableId,
                StructureLootLink.Confidence.HIGH);
            StructureLootLink verified = original.withVerification();

            assertThat(verified.confidence()).isEqualTo(StructureLootLink.Confidence.VERIFIED);
        }

        @Test
        @DisplayName("withTemplateEvidence() promotes to TEMPLATE")
        void withTemplateEvidence() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink original = StructureLootLink.heuristic(structureId, tableId,
                StructureLootLink.Confidence.MEDIUM);
            StructureLootLink withTemplate = original.withTemplateEvidence();

            assertThat(withTemplate.confidence()).isEqualTo(StructureLootLink.Confidence.TEMPLATE);
        }

        @Test
        @DisplayName("withConfirmation() promotes to CONFIRMED")
        void withConfirmation() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink original = StructureLootLink.fromTemplate(structureId, tableId);
            StructureLootLink confirmed = original.withConfirmation();

            assertThat(confirmed.confidence()).isEqualTo(StructureLootLink.Confidence.CONFIRMED);
        }
    }

    // ===== Type Checking Methods =====

    @Nested
    @DisplayName("Type checking methods")
    class TypeChecking {

        @Test
        @DisplayName("isAuthorDefined() returns true for manual links")
        void isAuthorDefined() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink manual = StructureLootLink.manual(structureId, tableId);

            assertThat(manual.isAuthorDefined()).isTrue();
        }

        @Test
        @DisplayName("isVerified() returns true for verified links")
        void isVerified() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink verified = StructureLootLink.verified(structureId, tableId);

            assertThat(verified.isVerified()).isTrue();
        }

        @Test
        @DisplayName("isFromTemplate() returns true for template links")
        void isFromTemplate() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink template = StructureLootLink.fromTemplate(structureId, tableId);

            assertThat(template.isFromTemplate()).isTrue();
        }

        @Test
        @DisplayName("isHeuristic() returns true for heuristic links")
        void isHeuristic() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink heuristic = StructureLootLink.heuristic(structureId, tableId,
                StructureLootLink.Confidence.HIGH);

            assertThat(heuristic.isHeuristic()).isTrue();
        }

        @Test
        @DisplayName("isFeatureMapping() returns true for feature links")
        void isFeatureMapping() {
            var featureId = TestFixtures.mockId("minecraft", "monster_room");
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            StructureLootLink feature = StructureLootLink.feature(featureId, tableId);

            assertThat(feature.isFeatureMapping()).isTrue();
        }

        @Test
        @DisplayName("isLearned() returns true for learned links")
        void isLearned() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink learned = StructureLootLink.learned(structureId, tableId);

            assertThat(learned.isLearned()).isTrue();
        }

        @Test
        @DisplayName("isConfirmed() returns true for confirmed links")
        void isConfirmed() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village");
            StructureLootLink confirmed = StructureLootLink.confirmed(structureId, tableId);

            assertThat(confirmed.isConfirmed()).isTrue();
        }

        @Test
        @DisplayName("isModDeclared() returns true for mod-declared links")
        void isModDeclared() {
            var structureId = TestFixtures.mockId("mymod", "custom_structure");
            var tableId = TestFixtures.mockId("mymod", "chests/custom");
            StructureLootLink modDeclared = StructureLootLink.modDeclared(structureId, tableId);

            assertThat(modDeclared.isModDeclared()).isTrue();
        }

        @Test
        @DisplayName("isFromContentAnalysis() returns true for content analysis links")
        void isFromContentAnalysis() {
            var structureId = TestFixtures.mockId("minecraft", "ancient_city");
            var tableId = TestFixtures.mockId("minecraft", "chests/ancient_city");
            StructureLootLink content = StructureLootLink.contentAnalysis(structureId, tableId, 80);

            assertThat(content.isFromContentAnalysis()).isTrue();
        }

        @Test
        @DisplayName("isFromSpawner() returns true for spawner entity links")
        void isFromSpawner() {
            var structureId = TestFixtures.mockId("minecraft", "monster_room");
            var entityTableId = TestFixtures.mockId("minecraft", "entities/zombie");
            StructureLootLink spawner = StructureLootLink.spawnerEntity(structureId, entityTableId);

            assertThat(spawner.isFromSpawner()).isTrue();
        }
    }

    // ===== Confidence Enum =====

    @Nested
    @DisplayName("Confidence enum")
    class ConfidenceEnum {

        @Test
        @DisplayName("MANUAL has highest score")
        void manualHasHighestScore() {
            assertThat(StructureLootLink.Confidence.MANUAL.getScore())
                .isGreaterThan(StructureLootLink.Confidence.VERIFIED.getScore());
        }

        @Test
        @DisplayName("scores are in descending order")
        void scoresDescending() {
            var manual = StructureLootLink.Confidence.MANUAL.getScore();
            var modDeclared = StructureLootLink.Confidence.MOD_DECLARED.getScore();
            var verified = StructureLootLink.Confidence.VERIFIED.getScore();
            var confirmed = StructureLootLink.Confidence.CONFIRMED.getScore();
            var template = StructureLootLink.Confidence.TEMPLATE.getScore();
            var learned = StructureLootLink.Confidence.LEARNED.getScore();
            var high = StructureLootLink.Confidence.HIGH.getScore();
            var medium = StructureLootLink.Confidence.MEDIUM.getScore();
            var low = StructureLootLink.Confidence.LOW.getScore();

            assertThat(manual).isGreaterThan(modDeclared);
            assertThat(modDeclared).isGreaterThan(verified);
            assertThat(verified).isGreaterThan(confirmed);
            assertThat(confirmed).isGreaterThan(template);
            assertThat(template).isGreaterThan(learned);
            assertThat(learned).isGreaterThan(high);
            assertThat(high).isGreaterThan(medium);
            assertThat(medium).isGreaterThan(low);
        }

        @Test
        @DisplayName("all confidences have labels")
        void allHaveLabels() {
            for (StructureLootLink.Confidence conf : StructureLootLink.Confidence.values()) {
                assertThat(conf.getLabel()).isNotNull().isNotEmpty();
            }
        }

        @Test
        @DisplayName("all confidences have colors")
        void allHaveColors() {
            for (StructureLootLink.Confidence conf : StructureLootLink.Confidence.values()) {
                assertThat(conf.getColor()).isNotZero();
            }
        }
    }
}
