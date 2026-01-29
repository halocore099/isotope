package dev.isotope.compat;

import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the McVersion interface contract.
 * Uses test implementations to verify interface behavior.
 */
@DisplayName("McVersion Interface Contract")
class McVersionContractTest {

    /**
     * Test implementation for MC 1.21.11+ API version.
     */
    static class TestMc1211Version implements McVersion {
        @Override
        public String getMinecraftVersion() {
            return "1.21.11";
        }

        @Override
        public String getApiVersion() {
            return "mc1211";
        }

        @Override
        public boolean hasGamemasterPermission(CommandSourceStack source) {
            return false; // Not testing this without real MC
        }
    }

    /**
     * Test implementation for MC 1.21.0-1.21.10 API version.
     */
    static class TestMc1210Version implements McVersion {
        @Override
        public String getMinecraftVersion() {
            return "1.21.4";
        }

        @Override
        public String getApiVersion() {
            return "mc1210";
        }

        @Override
        public boolean hasGamemasterPermission(CommandSourceStack source) {
            return false; // Not testing this without real MC
        }
    }

    @Nested
    @DisplayName("MC 1.21.11+ Version")
    class Mc1211Version {

        private final McVersion version = new TestMc1211Version();

        @Test
        @DisplayName("getApiVersion returns mc1211")
        void apiVersion() {
            assertThat(version.getApiVersion()).isEqualTo("mc1211");
        }

        @Test
        @DisplayName("is1211OrNewer returns true")
        void is1211OrNewer() {
            assertThat(version.is1211OrNewer()).isTrue();
        }

        @Test
        @DisplayName("is1210OrOlder returns false")
        void is1210OrOlder() {
            assertThat(version.is1210OrOlder()).isFalse();
        }

        @Test
        @DisplayName("getMinecraftVersion returns version string")
        void minecraftVersion() {
            assertThat(version.getMinecraftVersion()).isEqualTo("1.21.11");
        }
    }

    @Nested
    @DisplayName("MC 1.21.0-1.21.10 Version")
    class Mc1210Version {

        private final McVersion version = new TestMc1210Version();

        @Test
        @DisplayName("getApiVersion returns mc1210")
        void apiVersion() {
            assertThat(version.getApiVersion()).isEqualTo("mc1210");
        }

        @Test
        @DisplayName("is1211OrNewer returns false")
        void is1211OrNewer() {
            assertThat(version.is1211OrNewer()).isFalse();
        }

        @Test
        @DisplayName("is1210OrOlder returns true")
        void is1210OrOlder() {
            assertThat(version.is1210OrOlder()).isTrue();
        }

        @Test
        @DisplayName("getMinecraftVersion returns version string")
        void minecraftVersion() {
            assertThat(version.getMinecraftVersion()).isEqualTo("1.21.4");
        }
    }

    @Nested
    @DisplayName("Version Detection Logic")
    class VersionDetection {

        @Test
        @DisplayName("mc1211 api version implies 1.21.11+")
        void mc1211Implies1211Plus() {
            McVersion v = new McVersion() {
                @Override
                public String getMinecraftVersion() { return "1.21.11"; }
                @Override
                public String getApiVersion() { return "mc1211"; }
                @Override
                public boolean hasGamemasterPermission(CommandSourceStack source) { return false; }
            };

            assertThat(v.is1211OrNewer()).isTrue();
            assertThat(v.is1210OrOlder()).isFalse();
        }

        @Test
        @DisplayName("mc1210 api version implies 1.21.0-1.21.10")
        void mc1210Implies1210OrOlder() {
            McVersion v = new McVersion() {
                @Override
                public String getMinecraftVersion() { return "1.21.0"; }
                @Override
                public String getApiVersion() { return "mc1210"; }
                @Override
                public boolean hasGamemasterPermission(CommandSourceStack source) { return false; }
            };

            assertThat(v.is1210OrOlder()).isTrue();
            assertThat(v.is1211OrNewer()).isFalse();
        }

        @Test
        @DisplayName("unknown api version returns false for both checks")
        void unknownApiVersion() {
            McVersion v = new McVersion() {
                @Override
                public String getMinecraftVersion() { return "1.22.0"; }
                @Override
                public String getApiVersion() { return "mc1220"; }
                @Override
                public boolean hasGamemasterPermission(CommandSourceStack source) { return false; }
            };

            // Neither check matches the unknown api version
            assertThat(v.is1210OrOlder()).isFalse();
            assertThat(v.is1211OrNewer()).isFalse();
        }
    }
}
