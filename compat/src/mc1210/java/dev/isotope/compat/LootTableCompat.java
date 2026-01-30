package dev.isotope.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootTable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * MC 1.21.0-1.21.10 loot table compatibility.
 *
 * Uses reflection to handle varying Entity.getLootTable() API:
 * - MC 1.21.1-1.21.3: Returns ResourceKey<LootTable> directly (or null)
 * - MC 1.21.4+: Returns Optional<ResourceKey<LootTable>>
 */
public final class LootTableCompat {

    private static final Method GET_LOOT_TABLE_METHOD;
    private static final boolean RETURNS_OPTIONAL;

    static {
        Method method = null;
        boolean returnsOptional = false;

        try {
            method = Entity.class.getMethod("getLootTable");
            returnsOptional = method.getReturnType() == Optional.class;
        } catch (NoSuchMethodException e) {
            // Should not happen
        }

        GET_LOOT_TABLE_METHOD = method;
        RETURNS_OPTIONAL = returnsOptional;
    }

    private LootTableCompat() {}

    /**
     * Get the loot table key for an entity.
     * Returns Optional<ResourceKey<LootTable>> across all versions.
     */
    @SuppressWarnings("unchecked")
    public static Optional<ResourceKey<LootTable>> getLootTable(Entity entity) {
        if (GET_LOOT_TABLE_METHOD == null) {
            return Optional.empty();
        }

        try {
            Object result = GET_LOOT_TABLE_METHOD.invoke(entity);

            if (RETURNS_OPTIONAL) {
                // MC 1.21.4+: Returns Optional<ResourceKey<LootTable>>
                return (Optional<ResourceKey<LootTable>>) result;
            } else {
                // MC 1.21.1-1.21.3: Returns ResourceKey<LootTable> directly
                ResourceKey<LootTable> key = (ResourceKey<LootTable>) result;
                return Optional.ofNullable(key);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
