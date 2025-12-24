package dev.isotope.mixin;

import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootGenerator;
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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mixin to observe and intercept loot table execution.
 *
 * This mixin serves two purposes:
 * 1. Observation - Records loot table invocations during analysis
 * 2. Test mode - Replaces loot generation with edited structures when test mode is active
 */
@Mixin(LootTable.class)
public class LootTableMixin {

    /**
     * Intercept loot generation for observation and test mode.
     *
     * When ISOTOPE test mode is active and we have edits for the current table,
     * we generate loot from our edited structure instead of the vanilla table.
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void isotope$onGetRandomItems(LootParams params, long seed, Consumer<ItemStack> consumer, CallbackInfo ci) {
        // Get the table ID from the tracker (set by ReloadableRegistriesMixin)
        ResourceLocation tableId = LootTableTracker.getCurrentTableId();

        // Check if we should intercept for test mode
        if (tableId != null && LootEditManager.getInstance().isTestModeActive()) {
            if (LootEditManager.getInstance().hasEdits(tableId)) {
                Optional<LootTableStructure> editedStructure =
                    LootEditManager.getInstance().getEditedStructure(tableId);

                if (editedStructure.isPresent()) {
                    // Generate loot from our edited structure
                    LootGenerator.generateFromStructure(editedStructure.get(), params, seed, consumer);
                    ci.cancel(); // Skip vanilla generation
                    return;
                }
            }
        }

        // Observation recording
        if (LootObserver.getInstance().isRecording() && tableId != null) {
            // Record invocation without item details for now
            LootObserver.getInstance().onLootTableInvoked(tableId, params, Collections.emptyList());
        }
    }
}
