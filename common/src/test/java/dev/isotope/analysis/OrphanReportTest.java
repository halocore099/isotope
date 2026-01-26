package dev.isotope.analysis;

import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OrphanDetector.OrphanReport record.
 */
@UnitTest
class OrphanReportTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("empty() factory method")
    class EmptyFactory {

        @Test
        @DisplayName("creates empty report")
        void createsEmptyReport() {
            OrphanDetector.OrphanReport report = OrphanDetector.OrphanReport.empty();

            assertThat(report.orphanLootTables()).isEmpty();
            assertThat(report.orphanStructures()).isEmpty();
            assertThat(report.runtimeOnlyDiscoveries()).isEmpty();
            assertThat(report.hasOrphans()).isFalse();
            assertThat(report.hasRuntimeDiscoveries()).isFalse();
        }
    }

    // ===== Query Methods =====

    @Nested
    @DisplayName("Query methods")
    class QueryMethods {

        @Test
        @DisplayName("hasOrphans() returns true when has orphan loot tables")
        void hasOrphansWithLootTables() {
            var tableId = TestFixtures.mockId("minecraft", "chests/orphan");
            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(tableId),
                Set.of(),
                Set.of()
            );

            assertThat(report.hasOrphans()).isTrue();
        }

        @Test
        @DisplayName("hasOrphans() returns true when has orphan structures")
        void hasOrphansWithStructures() {
            var structureId = TestFixtures.mockId("minecraft", "orphan_structure");
            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(),
                Set.of(structureId),
                Set.of()
            );

            assertThat(report.hasOrphans()).isTrue();
        }

        @Test
        @DisplayName("hasOrphans() returns false when only runtime discoveries")
        void noOrphansOnlyRuntime() {
            var tableId = TestFixtures.mockId("minecraft", "chests/runtime");
            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(),
                Set.of(),
                Set.of(tableId)
            );

            assertThat(report.hasOrphans()).isFalse();
        }

        @Test
        @DisplayName("hasRuntimeDiscoveries() returns correct value")
        void hasRuntimeDiscoveries() {
            var tableId = TestFixtures.mockId("minecraft", "chests/runtime");
            OrphanDetector.OrphanReport reportWith = new OrphanDetector.OrphanReport(
                Set.of(), Set.of(), Set.of(tableId)
            );
            OrphanDetector.OrphanReport reportWithout = OrphanDetector.OrphanReport.empty();

            assertThat(reportWith.hasRuntimeDiscoveries()).isTrue();
            assertThat(reportWithout.hasRuntimeDiscoveries()).isFalse();
        }

        @Test
        @DisplayName("totalOrphans() sums loot tables and structures")
        void totalOrphans() {
            var tableId1 = TestFixtures.mockId("minecraft", "chests/orphan1");
            var tableId2 = TestFixtures.mockId("minecraft", "chests/orphan2");
            var structureId = TestFixtures.mockId("minecraft", "orphan_structure");

            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(tableId1, tableId2),
                Set.of(structureId),
                Set.of()
            );

            assertThat(report.totalOrphans()).isEqualTo(3);
        }
    }

    // ===== Summary =====

    @Nested
    @DisplayName("summary()")
    class Summary {

        @Test
        @DisplayName("returns 'No orphans detected' for empty report")
        void emptyReportSummary() {
            OrphanDetector.OrphanReport report = OrphanDetector.OrphanReport.empty();

            assertThat(report.summary()).isEqualTo("No orphans detected");
        }

        @Test
        @DisplayName("includes orphan loot table count")
        void includesLootTableCount() {
            var tableId1 = TestFixtures.mockId("minecraft", "chests/orphan1");
            var tableId2 = TestFixtures.mockId("minecraft", "chests/orphan2");

            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(tableId1, tableId2),
                Set.of(),
                Set.of()
            );

            assertThat(report.summary()).contains("2 orphan loot table(s)");
        }

        @Test
        @DisplayName("includes orphan structure count")
        void includesStructureCount() {
            var structureId = TestFixtures.mockId("minecraft", "orphan_structure");

            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(),
                Set.of(structureId),
                Set.of()
            );

            assertThat(report.summary()).contains("1 orphan structure(s)");
        }

        @Test
        @DisplayName("includes runtime discovery count")
        void includesRuntimeCount() {
            var tableId = TestFixtures.mockId("minecraft", "chests/runtime");

            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(),
                Set.of(),
                Set.of(tableId)
            );

            assertThat(report.summary()).contains("1 runtime-only discovery(s)");
        }

        @Test
        @DisplayName("combines multiple counts")
        void combinesCounts() {
            var tableId = TestFixtures.mockId("minecraft", "chests/orphan");
            var structureId = TestFixtures.mockId("minecraft", "orphan_structure");
            var runtimeId = TestFixtures.mockId("minecraft", "chests/runtime");

            OrphanDetector.OrphanReport report = new OrphanDetector.OrphanReport(
                Set.of(tableId),
                Set.of(structureId),
                Set.of(runtimeId)
            );

            String summary = report.summary();
            assertThat(summary).contains("1 orphan loot table(s)");
            assertThat(summary).contains("1 orphan structure(s)");
            assertThat(summary).contains("1 runtime-only discovery(s)");
        }
    }

    // ===== OrphanReason Enum =====

    @Nested
    @DisplayName("OrphanReason enum")
    class OrphanReasonEnum {

        @Test
        @DisplayName("all reasons have descriptions")
        void allHaveDescriptions() {
            for (OrphanDetector.OrphanReason reason : OrphanDetector.OrphanReason.values()) {
                assertThat(reason.getDescription()).isNotNull().isNotEmpty();
            }
        }
    }

    // ===== OrphanInfo Record =====

    @Nested
    @DisplayName("OrphanInfo record")
    class OrphanInfoRecord {

        @Test
        @DisplayName("isOrphan() returns true when has non-decorative reasons")
        void isOrphanWithReasons() {
            var id = TestFixtures.mockId("minecraft", "chests/orphan");
            OrphanDetector.OrphanInfo info = new OrphanDetector.OrphanInfo(
                id,
                java.util.List.of(OrphanDetector.OrphanReason.NOT_LINKED)
            );

            assertThat(info.isOrphan()).isTrue();
        }

        @Test
        @DisplayName("isOrphan() returns false for decorative structures")
        void notOrphanForDecorative() {
            var id = TestFixtures.mockId("minecraft", "desert_well");
            OrphanDetector.OrphanInfo info = new OrphanDetector.OrphanInfo(
                id,
                java.util.List.of(OrphanDetector.OrphanReason.DECORATIVE)
            );

            assertThat(info.isOrphan()).isFalse();
            assertThat(info.isDecorativeStructure()).isTrue();
        }

        @Test
        @DisplayName("isOrphan() returns false for empty reasons")
        void notOrphanForEmptyReasons() {
            var id = TestFixtures.mockId("minecraft", "village_plains");
            OrphanDetector.OrphanInfo info = new OrphanDetector.OrphanInfo(
                id,
                java.util.List.of()
            );

            assertThat(info.isOrphan()).isFalse();
        }
    }
}
