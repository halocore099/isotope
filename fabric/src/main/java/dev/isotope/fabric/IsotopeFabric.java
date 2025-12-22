package dev.isotope.fabric;

import dev.isotope.Isotope;
import net.fabricmc.api.ModInitializer;

public final class IsotopeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Isotope.init();
    }
}
