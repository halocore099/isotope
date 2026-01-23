package dev.isotope.compat.impl;

import dev.isotope.compat.McVersion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * MC 1.21.11+ implementation of McVersion adapter.
 */
public class McVersionImpl implements McVersion {

    @Override
    public String getMinecraftVersion() {
        return "1.21.11";
    }

    @Override
    public String getApiVersion() {
        return "mc1211";
    }

    @Override
    public boolean hasGamemasterPermission(CommandSourceStack source) {
        // MC 1.21.11+ uses the new permissions API
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
