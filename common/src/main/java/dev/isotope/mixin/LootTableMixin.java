package dev.isotope.mixin;

import dev.isotope.Isotope;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootGenerator;
import dev.isotope.observation.LootObserver;
import dev.isotope.observation.LootTableTracker;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
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
 *
 * Only targets methods that exist in both 1.20.x and 1.21+ for cross-version compatibility.
 */
@Mixin(LootTable.class)
public class LootTableMixin {

    /**
     * Intercept loot generation for observation and test mode.
     * This is the core method that exists in both 1.20.x and 1.21+.
     *
     * When ISOTOPE test mode is active and we have edits for the current table,
     * we generate loot from our edited structure instead of the vanilla table.
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void isotope$onGetRandomItems(LootParams params, long seed, Consumer<ItemStack> consumer, CallbackInfo ci) {
        if (isotope$tryGenerateEdited(params, seed, consumer)) {
            ci.cancel();
        }
    }

    /**
     * Common logic for intercepting loot generation.
     * Returns true if we handled the generation (caller should cancel).
     */
    private boolean isotope$tryGenerateEdited(LootParams params, long seed, Consumer<ItemStack> consumer) {
        ResourceLocation tableId = LootTableTracker.getCurrentTableId();
        LootEditManager editManager = LootEditManager.getInstance();

        if (tableId != null && editManager.isTestModeActive()) {
            boolean hasEdits = editManager.hasEdits(tableId);
            Isotope.LOGGER.info("ISOTOPE mixin: table={}, testMode=true, hasEdits={}", tableId, hasEdits);

            if (hasEdits) {
                Optional<LootTableStructure> editedStructure = editManager.getEditedStructure(tableId);

                if (editedStructure.isPresent()) {
                    Isotope.LOGGER.info("ISOTOPE mixin: Generating from edited structure for {}", tableId);
                    LootGenerator.generateFromStructure(editedStructure.get(), params, seed, consumer);
                    return true;
                } else {
                    Isotope.LOGGER.warn("ISOTOPE mixin: hasEdits=true but no edited structure for {}", tableId);
                }
            }
        }

        // Observation recording
        if (LootObserver.getInstance().isRecording() && tableId != null) {
            // Record invocation without item details for now
            LootObserver.getInstance().onLootTableInvoked(tableId, params, Collections.emptyList());
        }

        return false;
    }

    /**
     * CRITICAL: Intercept the fill method - this is what actually fills containers (chests) with loot!
     * This is the method called by RandomizableContainerBlockEntity.unpackLootTable()
     * This method exists in both 1.20.x and 1.21+.
     */
    @Inject(
        method = "fill",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void isotope$onFill(Container container, LootParams params, long seed, CallbackInfo ci) {
        ResourceLocation tableId = LootTableTracker.getCurrentTableId();

        if (tableId != null && LootEditManager.getInstance().isTestModeActive() && LootEditManager.getInstance().hasEdits(tableId)) {
            Optional<LootTableStructure> editedStructure = LootEditManager.getInstance().getEditedStructure(tableId);

            if (editedStructure.isPresent()) {
                // Generate items from edited structure
                ObjectArrayList<ItemStack> items = new ObjectArrayList<>();
                LootGenerator.generateFromStructure(editedStructure.get(), params, seed, items::add);

                // Distribute items into container slots
                java.util.Random random = new java.util.Random(seed);
                int containerSize = container.getContainerSize();

                for (ItemStack item : items) {
                    if (item.isEmpty()) continue;

                    // Find a random empty slot
                    int slot = random.nextInt(containerSize);
                    for (int i = 0; i < containerSize; i++) {
                        int trySlot = (slot + i) % containerSize;
                        ItemStack existing = container.getItem(trySlot);
                        if (existing.isEmpty()) {
                            container.setItem(trySlot, item);
                            break;
                        }
                    }
                }

                ci.cancel(); // Skip vanilla fill
            }
        }
    }
}
