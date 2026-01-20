package dev.isotope.mixin;

import dev.isotope.observation.LootTableTracker;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track which loot table is being looked up.
 * Sets the table ID in LootTableTracker so LootTableMixin can read it.
 *
 * Uses string-based target to avoid loading the class at config parse time.
 * This class only exists in 1.21+ (ReloadableServerRegistries.Holder).
 * On 1.20.x, the mixin plugin will skip this mixin.
 */
@Mixin(targets = "net.minecraft.server.ReloadableServerRegistries$Holder")
public class ReloadableRegistriesMixin {

    /**
     * Before getLootTable returns, set the current table ID in the tracker.
     * This is needed both for observation/recording AND for test mode loot replacement.
     *
     * The method signature uses Object to avoid importing ResourceKey<LootTable>.
     * We extract the location via reflection.
     */
    @Inject(
        method = "getLootTable",
        at = @At("HEAD"),
        require = 0  // Don't fail if method not found (version compatibility)
    )
    private void isotope$onGetLootTable(Object key, CallbackInfoReturnable<?> cir) {
        try {
            // key is ResourceKey<LootTable>, get its location
            java.lang.reflect.Method locationMethod = key.getClass().getMethod("location");
            Object location = locationMethod.invoke(key);
            if (location instanceof ResourceLocation) {
                LootTableTracker.setCurrentTableId((ResourceLocation) location);
            }
        } catch (Exception e) {
            // Silently fail - version compatibility
        }
    }
}
