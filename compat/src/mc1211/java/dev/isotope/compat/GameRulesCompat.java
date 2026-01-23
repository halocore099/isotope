package dev.isotope.compat;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * MC 1.21.11+ GameRules compatibility.
 *
 * In MC 1.21.11+, GameRules is at net.minecraft.world.level.gamerules.GameRules.
 */
public final class GameRulesCompat {

    private GameRulesCompat() {}

    /**
     * Create a new GameRules instance with the given feature flags.
     */
    public static GameRules create(FeatureFlagSet flags) {
        return new GameRules(flags);
    }

    /**
     * Get the GameRules class for type references.
     */
    public static Class<GameRules> getGameRulesClass() {
        return GameRules.class;
    }
}
