package dev.isotope.fabric;

import dev.isotope.ui.IsotopeClientInit;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client-side entry point for Isotope.
 */
public final class IsotopeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        IsotopeClientInit.init();
    }
}
