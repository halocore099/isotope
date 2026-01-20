package dev.isotope.mixin;

import dev.isotope.observation.LootTableTracker;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track which loot table is being looked up on Minecraft 1.20.x.
 * Sets the table ID in LootTableTracker so LootTableMixin can read it.
 *
 * This mixin targets LootDataManager which is used in 1.20.x for loot table access.
 * In 1.21+, ReloadableServerRegistries.Holder is used instead (see ReloadableRegistriesMixin).
 *
 * Uses string-based target to avoid loading the class at config parse time.
 */
@Mixin(targets = "net.minecraft.world.level.storage.loot.LootDataManager")
public class LootDataManagerMixin {

    /**
     * Track loot table lookups via getLootTable(ResourceLocation).
     * This method exists in 1.20.x for direct loot table access.
     *
     * Note: In 1.20.1, getElement(LootDataId) is also used but requires exact type matching
     * which is problematic for cross-version compatibility. The getLootTable method is
     * sufficient for most loot table tracking needs.
     */
    @Inject(
        method = "getLootTable",
        at = @At("HEAD"),
        require = 0  // Don't fail if method not found (version compatibility)
    )
    private void isotope$onGetLootTable(ResourceLocation location, CallbackInfoReturnable<?> cir) {
        LootTableTracker.setCurrentTableId(location);
    }
}
