package dev.isotope.compat;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.GameRules;

/**
 * MC 1.21.0-1.21.10 GameRules compatibility.
 *
 * In MC 1.21.10 and earlier, GameRules is at net.minecraft.world.level.GameRules.
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
