package dev.isotope.registry;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.isotope.Isotope;
import net.minecraft.server.MinecraftServer;

/**
 * Orchestrates registry scanning on server lifecycle events.
 */
public final class RegistryScanner {
    private static boolean initialized = false;

    private RegistryScanner() {}

    public static void init() {
        if (initialized) {
            Isotope.LOGGER.warn("RegistryScanner already initialized");
            return;
        }

        LifecycleEvent.SERVER_STARTED.register(RegistryScanner::onServerStarted);
        LifecycleEvent.SERVER_STOPPING.register(RegistryScanner::onServerStopping);

        initialized = true;
        Isotope.LOGGER.debug("RegistryScanner lifecycle hooks registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        Isotope.LOGGER.info("Server started - beginning registry discovery...");

        long startTime = System.currentTimeMillis();

        StructureRegistry.getInstance().scan(server);
        LootTableRegistry.getInstance().scan(server);

        long elapsed = System.currentTimeMillis() - startTime;
        Isotope.LOGGER.info("Registry discovery completed in {}ms", elapsed);
    }

    private static void onServerStopping(MinecraftServer server) {
        Isotope.LOGGER.debug("Server stopping - resetting registry state");
        StructureRegistry.getInstance().reset();
        LootTableRegistry.getInstance().reset();
    }
}
