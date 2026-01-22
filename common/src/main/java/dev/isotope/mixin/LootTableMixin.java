package dev.isotope.mixin;

import dev.isotope.Isotope;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.editing.LootGenerator;
import dev.isotope.observation.LootObserver;
import dev.isotope.observation.LootTableTracker;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
public abstract class LootTableMixin {

    /**
     * Helper to get the table ID from ThreadLocal tracking.
     * In 1.21/1.21.1, LootTable doesn't have a getLootTableId() method,
     * so we rely on ReloadableRegistriesMixin to track lookups.
     */
    private ResourceLocation getTableId() {
        return LootTableTracker.getCurrentTableId();
    }

    /**
     * Intercept loot generation for observation and test mode (3-param version with seed).
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
        ResourceLocation tableId = getTableId();

        if (tableId != null && LootEditManager.getInstance().isTestModeActive() && LootEditManager.getInstance().hasEdits(tableId)) {
            Optional<LootTableStructure> editedStructure = LootEditManager.getInstance().getEditedStructure(tableId);
            if (editedStructure.isPresent()) {
                LootGenerator.generateFromStructure(editedStructure.get(), params, seed, consumer);
                ci.cancel();
                return;
            }
        }

        // Observation recording
        if (LootObserver.getInstance().isRecording() && tableId != null) {
            LootObserver.getInstance().onLootTableInvoked(tableId, params, Collections.emptyList());
        }
    }

    /**
     * Intercept loot generation (2-param version - LootParams + Consumer).
     * This is the version called by LootTestRunner and other code.
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void isotope$onGetRandomItemsNoSeed(LootParams params, Consumer<ItemStack> consumer, CallbackInfo ci) {
        ResourceLocation tableId = getTableId();

        if (tableId != null && LootEditManager.getInstance().isTestModeActive() && LootEditManager.getInstance().hasEdits(tableId)) {
            Optional<LootTableStructure> editedStructure = LootEditManager.getInstance().getEditedStructure(tableId);
            if (editedStructure.isPresent()) {
                LootGenerator.generateFromStructure(editedStructure.get(), params, System.nanoTime(), consumer);
                ci.cancel();
                return;
            }
        }

        // Observation recording
        if (LootObserver.getInstance().isRecording() && tableId != null) {
            LootObserver.getInstance().onLootTableInvoked(tableId, params, Collections.emptyList());
        }
    }

    /**
     * Also intercept the overload with LootParams and RandomSource.
     * This may be called instead of the seed-based version.
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;Lnet/minecraft/util/RandomSource;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void isotope$onGetRandomItemsWithRandom(LootParams params, RandomSource random, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        ResourceLocation tableId = getTableId();

        if (tableId != null && LootEditManager.getInstance().isTestModeActive() && LootEditManager.getInstance().hasEdits(tableId)) {
            Optional<LootTableStructure> editedStructure = LootEditManager.getInstance().getEditedStructure(tableId);
            if (editedStructure.isPresent()) {
                ObjectArrayList<ItemStack> items = new ObjectArrayList<>();
                LootGenerator.generateFromStructure(editedStructure.get(), params, random.nextLong(), items::add);
                cir.setReturnValue(items);
            }
        }
    }

    /**
     * Also intercept the overload with just LootParams (no seed/random).
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void isotope$onGetRandomItemsSimple(LootParams params, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        ResourceLocation tableId = getTableId();

        if (tableId != null && LootEditManager.getInstance().isTestModeActive() && LootEditManager.getInstance().hasEdits(tableId)) {
            Optional<LootTableStructure> editedStructure = LootEditManager.getInstance().getEditedStructure(tableId);
            if (editedStructure.isPresent()) {
                ObjectArrayList<ItemStack> items = new ObjectArrayList<>();
                LootGenerator.generateFromStructure(editedStructure.get(), params, System.nanoTime(), items::add);
                cir.setReturnValue(items);
            }
        }
    }

    /**
     * Also intercept the overload with LootParams and long seed returning ObjectArrayList.
     */
    @Inject(
        method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;J)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void isotope$onGetRandomItemsWithSeed(LootParams params, long seed, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        ResourceLocation tableId = getTableId();

        if (tableId != null && LootEditManager.getInstance().isTestModeActive() && LootEditManager.getInstance().hasEdits(tableId)) {
            Optional<LootTableStructure> editedStructure = LootEditManager.getInstance().getEditedStructure(tableId);
            if (editedStructure.isPresent()) {
                ObjectArrayList<ItemStack> items = new ObjectArrayList<>();
                LootGenerator.generateFromStructure(editedStructure.get(), params, seed, items::add);
                cir.setReturnValue(items);
            }
        }
    }

    /**
     * CRITICAL: Intercept the fill method - this is what actually fills containers (chests) with loot!
     * This is the method called by RandomizableContainerBlockEntity.unpackLootTable()
     */
    @Inject(
        method = "fill",
        at = @At("HEAD"),
        cancellable = true
    )
    private void isotope$onFill(Container container, LootParams params, long seed, CallbackInfo ci) {
        ResourceLocation tableId = getTableId();

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
