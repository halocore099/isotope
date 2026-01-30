package net.minecraft.commands.arguments;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

/**
 * Shim class for MC 1.21.10 and earlier.
 * Maps IdentifierArgument (1.21.11+) to ResourceLocationArgument.
 */
public final class IdentifierArgument {

    private IdentifierArgument() {}

    public static ResourceLocationArgument id() {
        return ResourceLocationArgument.id();
    }

    public static ResourceLocation getId(CommandContext<CommandSourceStack> ctx, String name) {
        return ResourceLocationArgument.getId(ctx, name);
    }
}
