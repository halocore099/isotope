package dev.isotope.mixin;

import dev.isotope.observation.LootObserver;
import dev.isotope.observation.LootTableTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * Mixin to observe loot table execution.
 * Works in conjunction with LootTableTracker to know which table is being executed.
 *
 * Note: This is an OPTIONAL verification system. The primary discovery
 * comes from registry scanning and heuristic linking.
 */
@Mixin(LootTable.class)
public class LootTableMixin {

    /**
     * Track when a loot table is invoked.
     * For now, we just record the invocation without capturing items.
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V",
        at = @At("HEAD")
    )
    private void isotope$onGetRandomItems(LootParams params, long seed, Consumer<ItemStack> consumer, CallbackInfo ci) {
        if (!LootObserver.getInstance().isRecording()) {
            return;
        }

        // Get the table ID from the tracker (set by ReloadableRegistriesMixin)
        ResourceLocation tableId = LootTableTracker.getCurrentTableId();

        if (tableId != null) {
            // Record invocation without item details for now
            LootObserver.getInstance().onLootTableInvoked(tableId, params, Collections.emptyList());
        }
    }
}
