package dev.isotope.neoforge;

import dev.isotope.Isotope;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@Mod(Isotope.MOD_ID)
public final class IsotopeNeoForge {
    public IsotopeNeoForge(IEventBus modEventBus) {
        Isotope.init();

        // Register client setup only on client side
        if (DistHelper.isClient()) {
            modEventBus.addListener(ClientSetup::onClientSetup);
        }
    }

    // Separate class to defer loading of client-only code until actually needed
    // This prevents IsotopeClientInit from being loaded on the server side
    @OnlyIn(Dist.CLIENT)
    private static final class ClientSetup {
        private static void onClientSetup(FMLClientSetupEvent event) {
            dev.isotope.ui.IsotopeClientInit.init();
        }
    }
}
