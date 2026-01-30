package dev.isotope.compat;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.GameRules;

import java.lang.reflect.Constructor;

/**
 * MC 1.21.0-1.21.10 GameRules compatibility.
 *
 * In MC 1.21.10 and earlier, GameRules is at net.minecraft.world.level.GameRules.
 * The constructor signature varies between minor versions:
 * - MC 1.21.1-1.21.3: GameRules(DynamicLike)
 * - MC 1.21.4-1.21.10: GameRules(FeatureFlagSet)
 *
 * We use reflection to handle both cases.
 */
public final class GameRulesCompat {

    private GameRulesCompat() {}

    /**
     * Create a new GameRules instance with the given feature flags.
     * Uses reflection to handle varying constructor signatures across MC versions.
     */
    public static GameRules create(FeatureFlagSet flags) {
        // Try FeatureFlagSet constructor first (MC 1.21.4+)
        try {
            Constructor<GameRules> flagsConstructor = GameRules.class.getConstructor(FeatureFlagSet.class);
            return flagsConstructor.newInstance(flags);
        } catch (NoSuchMethodException e) {
            // Fall through to try DynamicLike constructor
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GameRules with FeatureFlagSet", e);
        }

        // Try DynamicLike constructor (MC 1.21.1-1.21.3)
        try {
            // Find constructor that takes DynamicLike
            for (Constructor<?> ctor : GameRules.class.getConstructors()) {
                if (ctor.getParameterCount() == 1) {
                    Class<?> paramType = ctor.getParameterTypes()[0];
                    if (paramType.getSimpleName().contains("Dynamic")) {
                        Dynamic<?> dynamic = new Dynamic<>(NbtOps.INSTANCE, new CompoundTag());
                        return (GameRules) ctor.newInstance(dynamic);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GameRules with DynamicLike", e);
        }

        throw new RuntimeException("Could not find suitable GameRules constructor");
    }

    /**
     * Get the GameRules class for type references.
     */
    public static Class<GameRules> getGameRulesClass() {
        return GameRules.class;
    }
}
