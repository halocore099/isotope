package dev.isotope.visualization;

import dev.isotope.data.StructureLootLink;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FlowEdge record.
 */
@UnitTest
class FlowEdgeTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("fromLink() factory method")
    class FromLinkFactory {

        @Test
        @DisplayName("creates edge from StructureLootLink")
        void createsEdgeFromLink() {
            var structureId = TestFixtures.mockId("minecraft", "village_plains");
            var tableId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            StructureLootLink link = StructureLootLink.fromTemplate(structureId, tableId);

            FlowEdge edge = FlowEdge.fromLink(link);

            assertThat(edge.fromId()).isEqualTo(structureId);
            assertThat(edge.toId()).isEqualTo(tableId);
            assertThat(edge.type()).isEqualTo(FlowEdge.EdgeType.SOURCE_TO_TABLE);
            assertThat(edge.confidence()).isEqualTo(StructureLootLink.Confidence.TEMPLATE.getScore());
            assertThat(edge.sourceLabel()).isEqualTo(StructureLootLink.Confidence.TEMPLATE.getLabel());
        }
    }

    @Nested
    @DisplayName("nestedReference() factory method")
    class NestedReferenceFactory {

        @Test
        @DisplayName("creates edge for nested table reference")
        void createsNestedEdge() {
            var fromId = TestFixtures.mockId("minecraft", "chests/village/village_weaponsmith");
            var toId = TestFixtures.mockId("minecraft", "chests/village/village_toolsmith");

            FlowEdge edge = FlowEdge.nestedReference(fromId, toId);

            assertThat(edge.fromId()).isEqualTo(fromId);
            assertThat(edge.toId()).isEqualTo(toId);
            assertThat(edge.type()).isEqualTo(FlowEdge.EdgeType.TABLE_TO_NESTED);
            assertThat(edge.confidence()).isEqualTo(70);
            assertThat(edge.sourceLabel()).isEqualTo("Reference");
        }
    }

    // ===== Alpha Calculation =====

    @Nested
    @DisplayName("getAlpha()")
    class AlphaCalculation {

        @Test
        @DisplayName("returns 0xFF for confidence 100")
        void maxAlphaForMaxConfidence() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge edge = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 100, "Manual");

            assertThat(edge.getAlpha()).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("returns minimum 0x40 for confidence 0")
        void minAlphaForZeroConfidence() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge edge = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 0, "None");

            assertThat(edge.getAlpha()).isEqualTo(0x40);
        }

        @Test
        @DisplayName("returns proportional alpha for mid confidence")
        void proportionalAlpha() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge edge = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 50, "Medium");

            int alpha = edge.getAlpha();
            assertThat(alpha).isBetween(0x40, 0xFF);
            assertThat(alpha).isGreaterThan(0x40);
            assertThat(alpha).isLessThan(0xFF);
        }

        @Test
        @DisplayName("higher confidence produces higher alpha")
        void higherConfidenceHigherAlpha() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge low = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 30, "Low");
            FlowEdge high = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 80, "High");

            assertThat(high.getAlpha()).isGreaterThan(low.getAlpha());
        }
    }

    // ===== Edge Types =====

    @Nested
    @DisplayName("EdgeType enum")
    class EdgeTypes {

        @Test
        @DisplayName("SOURCE_TO_TABLE exists")
        void sourceToTableExists() {
            assertThat(FlowEdge.EdgeType.SOURCE_TO_TABLE).isNotNull();
        }

        @Test
        @DisplayName("TABLE_TO_NESTED exists")
        void tableToNestedExists() {
            assertThat(FlowEdge.EdgeType.TABLE_TO_NESTED).isNotNull();
        }
    }

    // ===== Record Equality =====

    @Nested
    @DisplayName("Record equality")
    class Equality {

        @Test
        @DisplayName("edges with same values are equal")
        void equalEdges() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge edge1 = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 80, "Template");
            FlowEdge edge2 = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 80, "Template");

            assertThat(edge1).isEqualTo(edge2);
            assertThat(edge1.hashCode()).isEqualTo(edge2.hashCode());
        }

        @Test
        @DisplayName("edges with different confidence are not equal")
        void differentConfidence() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge edge1 = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 80, "Template");
            FlowEdge edge2 = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 90, "Template");

            assertThat(edge1).isNotEqualTo(edge2);
        }

        @Test
        @DisplayName("edges with different types are not equal")
        void differentTypes() {
            var fromId = TestFixtures.mockId("minecraft", "structure");
            var toId = TestFixtures.mockId("minecraft", "table");
            FlowEdge edge1 = new FlowEdge(fromId, toId, FlowEdge.EdgeType.SOURCE_TO_TABLE, 80, "Test");
            FlowEdge edge2 = new FlowEdge(fromId, toId, FlowEdge.EdgeType.TABLE_TO_NESTED, 80, "Test");

            assertThat(edge1).isNotEqualTo(edge2);
        }
    }
}
