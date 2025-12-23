package dev.isotope.neoforge;

import dev.isotope.Isotope;
import dev.isotope.ui.IsotopeClientInit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Isotope.MOD_ID)
public final class IsotopeNeoForge {
    public IsotopeNeoForge(IEventBus modEventBus) {
        Isotope.init();

        // Register client setup only on client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        IsotopeClientInit.init();
    }
}
