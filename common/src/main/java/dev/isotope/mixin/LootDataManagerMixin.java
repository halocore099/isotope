package dev.isotope.mixin;

import dev.isotope.observation.LootTableTracker;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track which loot table is being looked up on Minecraft 1.20.1.
 * Sets the table ID in LootTableTracker so LootTableMixin can read it.
 *
 * This mixin targets LootDataManager which is used in 1.20.1 for loot table access.
 * In 1.20.2+, ReloadableServerRegistries.Holder is used instead (see ReloadableRegistriesMixin).
 *
 * Uses string-based target to avoid loading the class at config parse time.
 * On 1.20.2+, this class still exists but isn't used for loot table lookup,
 * so the mixin will apply but the method may not be called.
 */
@Mixin(targets = "net.minecraft.world.level.storage.loot.LootDataManager")
public class LootDataManagerMixin {

    /**
     * Before getElement returns, check if it's a loot table lookup and track it.
     *
     * In 1.20.1: LootDataManager.getElement(LootDataId) returns the element (LootTable, etc.)
     * The LootDataId contains a LootDataType and a ResourceLocation.
     * We only care about LOOT_TABLES type.
     *
     * Method signature: public <T> T getElement(LootDataId<T> id)
     * We use Object to avoid importing LootDataId which might not exist in all versions.
     */
    @Inject(
        method = "getElement",
        at = @At("HEAD"),
        require = 0  // Don't fail if method not found (version compatibility)
    )
    private void isotope$onGetElement(Object lootDataId, CallbackInfoReturnable<?> cir) {
        try {
            // Get the LootDataType from the id to check if it's a loot table
            java.lang.reflect.Method typeMethod = lootDataId.getClass().getMethod("type");
            Object type = typeMethod.invoke(lootDataId);

            // Check if this is a loot table lookup by checking the type name
            String typeName = type.toString();
            if (typeName.contains("loot_tables") || typeName.contains("LOOT_TABLE")) {
                // Get the ResourceLocation from the id
                java.lang.reflect.Method locationMethod = lootDataId.getClass().getMethod("location");
                Object location = locationMethod.invoke(lootDataId);
                if (location instanceof ResourceLocation) {
                    LootTableTracker.setCurrentTableId((ResourceLocation) location);
                }
            }
        } catch (Exception e) {
            // Silently fail - version compatibility
            // This is expected on 1.20.2+ where LootDataId might not exist or have different structure
        }
    }
}
