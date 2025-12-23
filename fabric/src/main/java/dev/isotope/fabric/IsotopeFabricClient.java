package dev.isotope.fabric;

import dev.isotope.ui.IsotopeClientInit;
import net.fabricmc.api.ClientModInitializer;

public final class IsotopeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        IsotopeClientInit.init();
    }
}
