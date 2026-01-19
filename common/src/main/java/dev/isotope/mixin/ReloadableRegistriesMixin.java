package dev.isotope.mixin;

import dev.isotope.observation.LootTableTracker;
//? if >=1.20.2 {
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootTable;
//?} else {
/*
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
*/
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to track which loot table is being looked up.
 * Sets the table ID in LootTableTracker so LootTableMixin can read it.
 *
 * On MC 1.20.2+: Targets ReloadableServerRegistries.Holder
 * On MC 1.20.1:  Targets LootDataManager
 */
//? if >=1.20.2 {
@Mixin(ReloadableServerRegistries.Holder.class)
//?} else {
/*@Mixin(LootDataManager.class)*/
//?}
public class ReloadableRegistriesMixin {

    //? if >=1.20.2 {
    /**
     * Before getLootTable returns, set the current table ID in the tracker.
     * This is needed both for observation/recording AND for test mode loot replacement.
     */
    @Inject(
        method = "getLootTable",
        at = @At("HEAD")
    )
    private void isotope$onGetLootTable(ResourceKey<LootTable> key, CallbackInfoReturnable<LootTable> cir) {
        LootTableTracker.setCurrentTableId(key.location());
    }
    //?} else {
    /*
    /**
     * Before getElement returns for loot tables, set the current table ID in the tracker.
     * In 1.20.1, loot tables are retrieved via LootDataManager.getElement(LootDataType.TABLE, id).
     */
    /*
    @Inject(
        method = "getElement",
        at = @At("HEAD")
    )
    private <T> void isotope$onGetElement(LootDataType<T> type, ResourceLocation id, CallbackInfoReturnable<T> cir) {
        if (type == LootDataType.TABLE) {
            LootTableTracker.setCurrentTableId(id);
        }
    }
    */
    //?}
}
