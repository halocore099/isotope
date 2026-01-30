package dev.isotope.compat.impl;

import dev.isotope.compat.McVersion;
import net.minecraft.commands.CommandSourceStack;

/**
 * MC 1.21.9-1.21.10 implementation of McVersion adapter.
 */
public class McVersionImpl implements McVersion {

    @Override
    public String getMinecraftVersion() {
        return "1.21.10";
    }

    @Override
    public String getApiVersion() {
        return "mc1219";
    }

    @Override
    public boolean hasGamemasterPermission(CommandSourceStack source) {
        // MC 1.21.9-1.21.10 uses the old permission check (same as mc1210)
        return source.hasPermission(2);
    }
}
