package dev.isotope.mixin;

import dev.isotope.observation.LootObserver;
import dev.isotope.observation.LootTableTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track which loot table is being looked up.
 * Sets the table ID in LootTableTracker so LootTableMixin can read it.
 */
@Mixin(ReloadableServerRegistries.Holder.class)
public class ReloadableRegistriesMixin {

    /**
     * Before getLootTable returns, set the current table ID in the tracker.
     */
    @Inject(
        method = "getLootTable",
        at = @At("HEAD")
    )
    private void isotope$onGetLootTable(ResourceKey<LootTable> key, CallbackInfoReturnable<LootTable> cir) {
        if (LootObserver.getInstance().isRecording()) {
            LootTableTracker.setCurrentTableId(key.location());
        }
    }
}
