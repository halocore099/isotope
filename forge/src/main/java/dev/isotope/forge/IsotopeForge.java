package dev.isotope.forge;

import dev.isotope.Isotope;
import dev.isotope.ui.IsotopeClientInit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Isotope.MOD_ID)
public final class IsotopeForge {
    public IsotopeForge() {
        Isotope.init();

        // Register client setup only on client side
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(this::onClientSetup);
        });
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        IsotopeClientInit.init();
    }
}
