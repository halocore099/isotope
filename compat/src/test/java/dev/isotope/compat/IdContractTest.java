package dev.isotope.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Id interface contract.
 * Uses a test implementation to verify interface behavior
 * without requiring Minecraft classes.
 */
@DisplayName("Id Interface Contract")
class IdContractTest {

    /**
     * Test implementation of Id that doesn't depend on Minecraft.
     */
    static class TestId implements Id {
        private final String namespace;
        private final String path;

        TestId(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        static TestId of(String namespace, String path) {
            return new TestId(namespace, path);
        }

        static TestId parse(String full) {
            int colon = full.indexOf(':');
            if (colon == -1) {
                return new TestId("minecraft", full);
            }
            return new TestId(full.substring(0, colon), full.substring(colon + 1));
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
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        @DisplayName("getNamespace returns namespace")
        void getNamespace() {
            Id id = TestId.of("minecraft", "stone");
            assertThat(id.getNamespace()).isEqualTo("minecraft");
        }

        @Test
        @DisplayName("getPath returns path")
        void getPath() {
            Id id = TestId.of("minecraft", "stone");
            assertThat(id.getPath()).isEqualTo("stone");
        }

        @Test
        @DisplayName("toString returns namespace:path format")
        void toStringFormat() {
            Id id = TestId.of("minecraft", "diamond");
            assertThat(id.toString()).isEqualTo("minecraft:diamond");
        }

        @Test
        @DisplayName("handles modded namespace")
        void moddedNamespace() {
            Id id = TestId.of("mymod", "custom_item");
            assertThat(id.getNamespace()).isEqualTo("mymod");
            assertThat(id.getPath()).isEqualTo("custom_item");
            assertThat(id.toString()).isEqualTo("mymod:custom_item");
        }

        @Test
        @DisplayName("handles nested paths")
        void nestedPaths() {
            Id id = TestId.of("minecraft", "chests/simple_dungeon");
            assertThat(id.getPath()).isEqualTo("chests/simple_dungeon");
            assertThat(id.toString()).isEqualTo("minecraft:chests/simple_dungeon");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal ids are equal")
        void equalIds() {
            Id id1 = TestId.of("minecraft", "stone");
            Id id2 = TestId.of("minecraft", "stone");
            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("different namespaces are not equal")
        void differentNamespaces() {
            Id id1 = TestId.of("minecraft", "stone");
            Id id2 = TestId.of("mymod", "stone");
            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("different paths are not equal")
        void differentPaths() {
            Id id1 = TestId.of("minecraft", "stone");
            Id id2 = TestId.of("minecraft", "cobblestone");
            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("handles null comparison")
        void nullComparison() {
            Id id = TestId.of("minecraft", "stone");
            assertThat(id).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("Comparison")
    class Comparison {

        @Test
        @DisplayName("compareTo sorts by namespace first")
        void compareByNamespace() {
            Id a = TestId.of("aaa", "zzz");
            Id b = TestId.of("zzz", "aaa");
            assertThat(a.compareTo(b)).isLessThan(0);
            assertThat(b.compareTo(a)).isGreaterThan(0);
        }

        @Test
        @DisplayName("compareTo sorts by path when namespace equal")
        void compareByPath() {
            Id a = TestId.of("minecraft", "apple");
            Id b = TestId.of("minecraft", "banana");
            assertThat(a.compareTo(b)).isLessThan(0);
            assertThat(b.compareTo(a)).isGreaterThan(0);
        }

        @Test
        @DisplayName("compareTo returns 0 for equal ids")
        void compareEqual() {
            Id a = TestId.of("minecraft", "stone");
            Id b = TestId.of("minecraft", "stone");
            assertThat(a.compareTo(b)).isEqualTo(0);
        }

        @Test
        @DisplayName("minecraft namespace sorts before modded")
        void minecraftBeforeModded() {
            Id mc = TestId.of("minecraft", "item");
            Id mod = TestId.of("mymod", "item");
            assertThat(mc.compareTo(mod)).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        @DisplayName("parses full qualified name")
        void parseFullName() {
            Id id = TestId.parse("minecraft:diamond");
            assertThat(id.getNamespace()).isEqualTo("minecraft");
            assertThat(id.getPath()).isEqualTo("diamond");
        }

        @Test
        @DisplayName("defaults to minecraft namespace")
        void defaultNamespace() {
            Id id = TestId.parse("stone");
            assertThat(id.getNamespace()).isEqualTo("minecraft");
            assertThat(id.getPath()).isEqualTo("stone");
        }

        @Test
        @DisplayName("parses modded namespace")
        void parseModded() {
            Id id = TestId.parse("mymod:custom_ore");
            assertThat(id.getNamespace()).isEqualTo("mymod");
            assertThat(id.getPath()).isEqualTo("custom_ore");
        }

        @Test
        @DisplayName("parses nested path")
        void parseNested() {
            Id id = TestId.parse("minecraft:loot_tables/chests/village");
            assertThat(id.getNamespace()).isEqualTo("minecraft");
            assertThat(id.getPath()).isEqualTo("loot_tables/chests/village");
        }
    }
}
