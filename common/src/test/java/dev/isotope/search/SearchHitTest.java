package dev.isotope.search;

import dev.isotope.test.TestCategories.UnitTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SearchHit record.
 */
@UnitTest
class SearchHitTest {

    // ===== Constructor and Fields =====

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("creates SearchHit with all fields")
        void createsWithAllFields() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit = new SearchHit(tableId, 0, 2, "Pool 1, Entry 3: diamond");

            assertThat(hit.table()).isEqualTo(tableId);
            assertThat(hit.pool()).isEqualTo(0);
            assertThat(hit.entry()).isEqualTo(2);
            assertThat(hit.context()).isEqualTo("Pool 1, Entry 3: diamond");
        }
    }

    // ===== Record Equality =====

    @Nested
    @DisplayName("Record equality")
    class Equality {

        @Test
        @DisplayName("hits with same values are equal")
        void equalHits() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit1 = new SearchHit(tableId, 0, 2, "diamond");
            SearchHit hit2 = new SearchHit(tableId, 0, 2, "diamond");

            assertThat(hit1).isEqualTo(hit2);
            assertThat(hit1.hashCode()).isEqualTo(hit2.hashCode());
        }

        @Test
        @DisplayName("hits with different pool are not equal")
        void differentPool() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit1 = new SearchHit(tableId, 0, 2, "diamond");
            SearchHit hit2 = new SearchHit(tableId, 1, 2, "diamond");

            assertThat(hit1).isNotEqualTo(hit2);
        }

        @Test
        @DisplayName("hits with different entry are not equal")
        void differentEntry() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit1 = new SearchHit(tableId, 0, 2, "diamond");
            SearchHit hit2 = new SearchHit(tableId, 0, 3, "diamond");

            assertThat(hit1).isNotEqualTo(hit2);
        }

        @Test
        @DisplayName("hits with different table are not equal")
        void differentTable() {
            var tableId1 = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            var tableId2 = TestFixtures.mockId("minecraft", "chests/village");
            SearchHit hit1 = new SearchHit(tableId1, 0, 2, "diamond");
            SearchHit hit2 = new SearchHit(tableId2, 0, 2, "diamond");

            assertThat(hit1).isNotEqualTo(hit2);
        }

        @Test
        @DisplayName("hits with different context are not equal")
        void differentContext() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit1 = new SearchHit(tableId, 0, 2, "diamond");
            SearchHit hit2 = new SearchHit(tableId, 0, 2, "emerald");

            assertThat(hit1).isNotEqualTo(hit2);
        }
    }

    // ===== Edge Cases =====

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles pool index 0")
        void poolIndexZero() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit = new SearchHit(tableId, 0, 0, "First entry of first pool");

            assertThat(hit.pool()).isEqualTo(0);
            assertThat(hit.entry()).isEqualTo(0);
        }

        @Test
        @DisplayName("handles empty context")
        void emptyContext() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit = new SearchHit(tableId, 0, 0, "");

            assertThat(hit.context()).isEmpty();
        }

        @Test
        @DisplayName("handles null context")
        void nullContext() {
            var tableId = TestFixtures.mockId("minecraft", "chests/simple_dungeon");
            SearchHit hit = new SearchHit(tableId, 0, 0, null);

            assertThat(hit.context()).isNull();
        }
    }
}
