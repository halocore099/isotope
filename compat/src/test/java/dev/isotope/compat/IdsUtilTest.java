package dev.isotope.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Ids utility class.
 * Tests methods that don't require Minecraft classes at runtime.
 */
@DisplayName("Ids Utility Class")
class IdsUtilTest {

    /**
     * Test implementation of Id for utility method testing.
     */
    static class TestId implements Id {
        private final String namespace;
        private final String path;

        TestId(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Object toMc() {
            throw new UnsupportedOperationException("Test implementation");
        }

        @Override
        public String toString() {
            return namespace + ":" + path;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Id other) {
                return namespace.equals(other.getNamespace()) && path.equals(other.getPath());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31 * namespace.hashCode() + path.hashCode();
        }
    }

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("MINECRAFT constant is 'minecraft'")
        void minecraftConstant() {
            assertThat(Ids.MINECRAFT).isEqualTo("minecraft");
        }
    }

    @Nested
    @DisplayName("compare()")
    class CompareMethod {

        @Test
        @DisplayName("compares by namespace first")
        void compareByNamespace() {
            Id a = new TestId("aaa", "zzz");
            Id b = new TestId("bbb", "aaa");

            assertThat(Ids.compare(a, b)).isLessThan(0);
            assertThat(Ids.compare(b, a)).isGreaterThan(0);
        }

        @Test
        @DisplayName("compares by path when namespace equal")
        void compareByPath() {
            Id a = new TestId("minecraft", "apple");
            Id b = new TestId("minecraft", "banana");

            assertThat(Ids.compare(a, b)).isLessThan(0);
            assertThat(Ids.compare(b, a)).isGreaterThan(0);
        }

        @Test
        @DisplayName("returns 0 for equal ids")
        void compareEqual() {
            Id a = new TestId("minecraft", "stone");
            Id b = new TestId("minecraft", "stone");

            assertThat(Ids.compare(a, b)).isEqualTo(0);
        }

        @Test
        @DisplayName("can be used for sorting")
        void usableForSorting() {
            List<Id> ids = Arrays.asList(
                new TestId("minecraft", "zombie"),
                new TestId("mymod", "alpha"),
                new TestId("minecraft", "creeper"),
                new TestId("anothermod", "item")
            );

            ids.sort(Ids::compare);

            assertThat(ids).extracting(Id::toString).containsExactly(
                "anothermod:item",
                "minecraft:creeper",
                "minecraft:zombie",
                "mymod:alpha"
            );
        }

        @Test
        @DisplayName("minecraft sorts before most mods")
        void minecraftSortOrder() {
            Id minecraft = new TestId("minecraft", "item");
            Id mod = new TestId("zmod", "item");

            // 'minecraft' < 'zmod' alphabetically
            assertThat(Ids.compare(minecraft, mod)).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("Id Interface Default compareTo")
    class DefaultCompareTo {

        @Test
        @DisplayName("default compareTo matches Ids.compare")
        void matchesIdsCompare() {
            Id a = new TestId("minecraft", "apple");
            Id b = new TestId("minecraft", "banana");

            // Both should give same result
            assertThat(a.compareTo(b)).isEqualTo(Ids.compare(a, b));
            assertThat(b.compareTo(a)).isEqualTo(Ids.compare(b, a));
        }
    }
}
