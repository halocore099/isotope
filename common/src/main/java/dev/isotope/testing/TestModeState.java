package dev.isotope.testing;

import dev.isotope.Isotope;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Singleton tracking test mode state.
 *
 * Stores whether we're in a test world and which loot tables are being tested.
 */
public final class TestModeState {

    private static final TestModeState INSTANCE = new TestModeState();

    private boolean inTestMode = false;
    private String testWorldName = null;
    private final Set<ResourceLocation> testedTables = new HashSet<>();
    private WorldType worldType = WorldType.VOID;

    public enum WorldType {
        VOID("Superflat Void"),
        NORMAL("Normal Generation");

        public final String displayName;

        WorldType(String displayName) {
            this.displayName = displayName;
        }
    }

    private TestModeState() {}

    public static TestModeState getInstance() {
        return INSTANCE;
    }

    /**
     * Enter test mode with the given world name and tested tables.
     */
    public void enterTestMode(String worldName, Set<ResourceLocation> tables, WorldType type) {
        this.inTestMode = true;
        this.testWorldName = worldName;
        this.testedTables.clear();
        this.testedTables.addAll(tables);
        this.worldType = type;
        Isotope.LOGGER.info("Entered test mode: world={}, tables={}, type={}",
            worldName, tables.size(), type.displayName);
    }

    /**
     * Exit test mode and clear state.
     */
    public void exitTestMode() {
        Isotope.LOGGER.info("Exiting test mode: world={}", testWorldName);
        this.inTestMode = false;
        this.testWorldName = null;
        this.testedTables.clear();
        this.worldType = WorldType.VOID;
    }

    /**
     * Check if we're currently in test mode.
     */
    public boolean isInTestMode() {
        return inTestMode;
    }

    /**
     * Get the test world name (for deletion).
     */
    public String getTestWorldName() {
        return testWorldName;
    }

    /**
     * Get the set of loot tables being tested.
     */
    public Set<ResourceLocation> getTestedTables() {
        return new HashSet<>(testedTables);
    }

    /**
     * Get the world type.
     */
    public WorldType getWorldType() {
        return worldType;
    }

    /**
     * Check if a specific table is being tested.
     */
    public boolean isTableBeingTested(ResourceLocation tableId) {
        return testedTables.contains(tableId);
    }
}
