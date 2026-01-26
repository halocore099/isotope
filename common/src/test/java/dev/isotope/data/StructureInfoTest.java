package dev.isotope.data;

import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StructureInfo record.
 */
@UnitTest
class StructureInfoTest {

    // ===== Factory Methods =====

    @Nested
    @DisplayName("fromId() factory method")
    class FromIdFactory {

        @Test
        @DisplayName("creates StructureInfo from Id")
        void createsFromId() {
            var id = TestFixtures.mockId("minecraft", "village_plains");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.id()).isEqualTo(id);
            assertThat(info.namespace()).isEqualTo("minecraft");
            assertThat(info.path()).isEqualTo("village_plains");
        }

        @Test
        @DisplayName("handles modded namespace")
        void handlesModdedNamespace() {
            var id = TestFixtures.mockId("mymod", "custom_structure");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.namespace()).isEqualTo("mymod");
            assertThat(info.path()).isEqualTo("custom_structure");
        }
    }

    // ===== Helper Methods =====

    @Nested
    @DisplayName("fullId()")
    class FullId {

        @Test
        @DisplayName("returns full namespaced ID")
        void returnsFullId() {
            var id = TestFixtures.mockId("minecraft", "village_plains");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.fullId()).isEqualTo("minecraft:village_plains");
        }

        @Test
        @DisplayName("handles modded namespace")
        void handlesModdedNamespace() {
            var id = TestFixtures.mockId("mymod", "custom_structure");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.fullId()).isEqualTo("mymod:custom_structure");
        }
    }

    @Nested
    @DisplayName("isVanilla()")
    class IsVanilla {

        @Test
        @DisplayName("returns true for minecraft namespace")
        void trueForMinecraft() {
            var id = TestFixtures.mockId("minecraft", "village_plains");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.isVanilla()).isTrue();
        }

        @Test
        @DisplayName("returns false for modded namespace")
        void falseForModded() {
            var id = TestFixtures.mockId("mymod", "custom_structure");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.isVanilla()).isFalse();
        }

        @Test
        @DisplayName("returns false for other known mod namespaces")
        void falseForOtherMods() {
            var id = TestFixtures.mockId("fabric", "test_structure");
            StructureInfo info = StructureInfo.fromId(id);

            assertThat(info.isVanilla()).isFalse();
        }
    }

    // ===== Record Equality =====

    @Nested
    @DisplayName("Record equality")
    class Equality {

        @Test
        @DisplayName("structures with same ID are equal")
        void equalStructures() {
            var id = TestFixtures.mockId("minecraft", "village_plains");
            StructureInfo info1 = StructureInfo.fromId(id);
            StructureInfo info2 = StructureInfo.fromId(id);

            assertThat(info1).isEqualTo(info2);
            assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
        }

        @Test
        @DisplayName("structures with different IDs are not equal")
        void differentStructures() {
            var id1 = TestFixtures.mockId("minecraft", "village_plains");
            var id2 = TestFixtures.mockId("minecraft", "village_desert");
            StructureInfo info1 = StructureInfo.fromId(id1);
            StructureInfo info2 = StructureInfo.fromId(id2);

            assertThat(info1).isNotEqualTo(info2);
        }
    }
}
