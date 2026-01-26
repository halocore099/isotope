package dev.isotope.visualization;

import dev.isotope.data.LootSourceType;
import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FlowNode class.
 */
@UnitTest
class FlowNodeTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("source() factory method")
    class SourceFactory {

        @Test
        @DisplayName("creates source node with structure type")
        void createsStructureSource() {
            var id = TestFixtures.mockId("minecraft", "village_plains");
            FlowNode node = FlowNode.source(id, "Village Plains", LootSourceType.STRUCTURE);

            assertThat(node.id()).isEqualTo(id);
            assertThat(node.type()).isEqualTo(FlowNode.NodeType.SOURCE);
            assertThat(node.displayName()).isEqualTo("Village Plains");
            assertThat(node.sourceType()).isEqualTo(LootSourceType.STRUCTURE);
            assertThat(node.column()).isEqualTo(0); // Sources in first column
        }

        @Test
        @DisplayName("creates source node with feature type")
        void createsFeatureSource() {
            var id = TestFixtures.mockId("minecraft", "monster_room");
            FlowNode node = FlowNode.source(id, "Monster Room", LootSourceType.FEATURE);

            assertThat(node.sourceType()).isEqualTo(LootSourceType.FEATURE);
        }

        @Test
        @DisplayName("creates source node with mob type")
        void createsMobSource() {
            var id = TestFixtures.mockId("minecraft", "zombie");
            FlowNode node = FlowNode.source(id, "Zombie", LootSourceType.MOB);

            assertThat(node.sourceType()).isEqualTo(LootSourceType.MOB);
        }
    }

    @Nested
    @DisplayName("lootTable() factory method")
    class LootTableFactory {

        @Test
        @DisplayName("creates loot table node")
        void createsLootTableNode() {
            var id = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            FlowNode node = FlowNode.lootTable(id, "Simple Dungeon");

            assertThat(node.type()).isEqualTo(FlowNode.NodeType.LOOT_TABLE);
            assertThat(node.sourceType()).isNull();
            assertThat(node.column()).isEqualTo(1); // Loot tables in second column
        }
    }

    @Nested
    @DisplayName("nestedTable() factory method")
    class NestedTableFactory {

        @Test
        @DisplayName("creates nested table node")
        void createsNestedTableNode() {
            var id = TestFixtures.mockId("minecraft", "chests/village/village_armorer");
            FlowNode node = FlowNode.nestedTable(id, "Village Armorer");

            assertThat(node.type()).isEqualTo(FlowNode.NodeType.NESTED_TABLE);
            assertThat(node.column()).isEqualTo(2); // Nested tables in third column
        }
    }

    // ===== Position Methods =====

    @Nested
    @DisplayName("Position methods")
    class PositionMethods {

        @Test
        @DisplayName("setGridPosition() sets column and row")
        void setGridPosition() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setGridPosition(2, 3);

            assertThat(node.column()).isEqualTo(2);
            assertThat(node.row()).isEqualTo(3);
        }

        @Test
        @DisplayName("setScreenPosition() sets x and y")
        void setScreenPosition() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 200);

            assertThat(node.x()).isEqualTo(100);
            assertThat(node.y()).isEqualTo(200);
        }

        @Test
        @DisplayName("setSize() sets width and height")
        void setSize() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setSize(150, 30);

            assertThat(node.width()).isEqualTo(150);
            assertThat(node.height()).isEqualTo(30);
        }

        @Test
        @DisplayName("default size is NODE_WIDTH x NODE_HEIGHT")
        void defaultSize() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");

            assertThat(node.width()).isEqualTo(FlowNode.NODE_WIDTH);
            assertThat(node.height()).isEqualTo(FlowNode.NODE_HEIGHT);
        }
    }

    // ===== Hit Testing =====

    @Nested
    @DisplayName("contains() hit testing")
    class HitTesting {

        @Test
        @DisplayName("returns true for point inside bounds")
        void pointInside() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 100);
            node.setSize(50, 20);

            assertThat(node.contains(125, 110)).isTrue();
            assertThat(node.contains(100, 100)).isTrue();
            assertThat(node.contains(149, 119)).isTrue();
        }

        @Test
        @DisplayName("returns false for point outside bounds")
        void pointOutside() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 100);
            node.setSize(50, 20);

            assertThat(node.contains(99, 100)).isFalse();
            assertThat(node.contains(150, 100)).isFalse();
            assertThat(node.contains(100, 99)).isFalse();
            assertThat(node.contains(100, 120)).isFalse();
        }
    }

    // ===== Edge Points =====

    @Nested
    @DisplayName("Edge point methods")
    class EdgePoints {

        @Test
        @DisplayName("getRightEdgeX() returns x + width")
        void rightEdgeX() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 100);
            node.setSize(50, 20);

            assertThat(node.getRightEdgeX()).isEqualTo(150);
        }

        @Test
        @DisplayName("getRightEdgeY() returns y + height/2")
        void rightEdgeY() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 100);
            node.setSize(50, 20);

            assertThat(node.getRightEdgeY()).isEqualTo(110);
        }

        @Test
        @DisplayName("getLeftEdgeX() returns x")
        void leftEdgeX() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 100);

            assertThat(node.getLeftEdgeX()).isEqualTo(100);
        }

        @Test
        @DisplayName("getLeftEdgeY() returns y + height/2")
        void leftEdgeY() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 100);
            node.setSize(50, 20);

            assertThat(node.getLeftEdgeY()).isEqualTo(110);
        }
    }

    // ===== Equality =====

    @Nested
    @DisplayName("equals() and hashCode()")
    class Equality {

        @Test
        @DisplayName("nodes with same ID are equal")
        void nodesWithSameIdAreEqual() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node1 = FlowNode.lootTable(id, "Test 1");
            FlowNode node2 = FlowNode.lootTable(id, "Test 2");

            assertThat(node1).isEqualTo(node2);
            assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
        }

        @Test
        @DisplayName("nodes with different IDs are not equal")
        void nodesWithDifferentIdsNotEqual() {
            var id1 = TestFixtures.mockId("minecraft", "test1");
            var id2 = TestFixtures.mockId("minecraft", "test2");
            FlowNode node1 = FlowNode.lootTable(id1, "Test");
            FlowNode node2 = FlowNode.lootTable(id2, "Test");

            assertThat(node1).isNotEqualTo(node2);
        }

        @Test
        @DisplayName("node is equal to itself")
        void nodeEqualToSelf() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");

            assertThat(node).isEqualTo(node);
        }
    }

    // ===== toString =====

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("includes type, id, and position")
        void includesAllInfo() {
            var id = TestFixtures.mockId("minecraft", "test");
            FlowNode node = FlowNode.lootTable(id, "Test");
            node.setScreenPosition(100, 200);

            String str = node.toString();
            assertThat(str).contains("LOOT_TABLE");
            assertThat(str).contains("minecraft:test");
            assertThat(str).contains("100");
            assertThat(str).contains("200");
        }
    }

    // ===== Constants =====

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("layout constants are positive")
        void layoutConstantsPositive() {
            assertThat(FlowNode.NODE_WIDTH).isPositive();
            assertThat(FlowNode.NODE_HEIGHT).isPositive();
            assertThat(FlowNode.COLUMN_SPACING).isPositive();
            assertThat(FlowNode.ROW_SPACING).isPositive();
        }

        @Test
        @DisplayName("NODE_WIDTH > NODE_HEIGHT (nodes are wider than tall)")
        void nodesAreWide() {
            assertThat(FlowNode.NODE_WIDTH).isGreaterThan(FlowNode.NODE_HEIGHT);
        }
    }
}
