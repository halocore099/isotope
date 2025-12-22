package dev.isotope.neoforge;

import dev.isotope.Isotope;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Isotope.MOD_ID)
public final class IsotopeNeoForge {
    public IsotopeNeoForge(IEventBus modEventBus) {
        Isotope.init();
    }
}
