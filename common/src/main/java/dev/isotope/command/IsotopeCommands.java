package dev.isotope.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.isotope.Isotope;
import dev.isotope.data.LootTableInfo;
import dev.isotope.data.StructureInfo;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureRegistry;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Collection;

/**
 * Debug commands for inspecting discovered registries.
 */
public final class IsotopeCommands {

    private IsotopeCommands() {}

    public static void register() {
        CommandRegistrationEvent.EVENT.register(IsotopeCommands::registerCommands);
        Isotope.LOGGER.debug("ISOTOPE commands registered");
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment) {

        dispatcher.register(
            Commands.literal("isotope")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                    .executes(IsotopeCommands::statusCommand))
                .then(Commands.literal("structures")
                    .executes(ctx -> listStructures(ctx, null))
                    .then(Commands.argument("namespace", StringArgumentType.word())
                        .executes(ctx -> listStructures(ctx,
                            StringArgumentType.getString(ctx, "namespace")))))
                .then(Commands.literal("loottables")
                    .executes(ctx -> listLootTables(ctx, null))
                    .then(Commands.argument("namespace", StringArgumentType.word())
                        .executes(ctx -> listLootTables(ctx,
                            StringArgumentType.getString(ctx, "namespace")))))
                .then(Commands.literal("dump")
                    .then(Commands.literal("structures")
                        .executes(IsotopeCommands::dumpStructures))
                    .then(Commands.literal("loottables")
                        .executes(IsotopeCommands::dumpLootTables))
                    .then(Commands.literal("all")
                        .executes(IsotopeCommands::dumpAll)))
        );
    }

    private static int statusCommand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        int structureCount = StructureRegistry.getInstance().count();
        int lootTableCount = LootTableRegistry.getInstance().count();

        source.sendSuccess(() -> Component.literal("=== ISOTOPE Status ==="), false);
        source.sendSuccess(() -> Component.literal(
            "Structures: " + structureCount), false);
        source.sendSuccess(() -> Component.literal(
            "Loot Tables: " + lootTableCount), false);

        return 1;
    }

    private static int listStructures(CommandContext<CommandSourceStack> ctx, String namespace) {
        CommandSourceStack source = ctx.getSource();

        Collection<StructureInfo> structures = namespace != null
            ? StructureRegistry.getInstance().getByNamespace(namespace)
            : StructureRegistry.getInstance().getAll();

        if (structures.isEmpty()) {
            source.sendFailure(Component.literal("No structures found"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "=== Structures (" + structures.size() + ") ==="), false);

        int limit = 20;
        int count = 0;
        for (StructureInfo info : structures) {
            if (count++ >= limit) {
                int remaining = structures.size() - limit;
                source.sendSuccess(() -> Component.literal(
                    "... and " + remaining + " more"), false);
                break;
            }
            String entry = info.fullId();
            source.sendSuccess(() -> Component.literal("  " + entry), false);
        }

        return 1;
    }

    private static int listLootTables(CommandContext<CommandSourceStack> ctx, String namespace) {
        CommandSourceStack source = ctx.getSource();

        Collection<LootTableInfo> tables = namespace != null
            ? LootTableRegistry.getInstance().getByNamespace(namespace)
            : LootTableRegistry.getInstance().getAll();

        if (tables.isEmpty()) {
            source.sendFailure(Component.literal("No loot tables found"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "=== Loot Tables (" + tables.size() + ") ==="), false);

        int limit = 20;
        int count = 0;
        for (LootTableInfo info : tables) {
            if (count++ >= limit) {
                int remaining = tables.size() - limit;
                source.sendSuccess(() -> Component.literal(
                    "... and " + remaining + " more"), false);
                break;
            }
            String entry = info.fullId() + " [" + info.category() + "]";
            source.sendSuccess(() -> Component.literal("  " + entry), false);
        }

        return 1;
    }

    private static int dumpStructures(CommandContext<CommandSourceStack> ctx) {
        Collection<StructureInfo> structures = StructureRegistry.getInstance().getAll();

        Isotope.LOGGER.info("=== Structure Dump ===");
        for (StructureInfo info : structures) {
            Isotope.LOGGER.info("  {}", info.fullId());
        }
        Isotope.LOGGER.info("=== End ({} total) ===", structures.size());

        ctx.getSource().sendSuccess(() -> Component.literal(
            "Dumped " + structures.size() + " structures to log"), false);
        return 1;
    }

    private static int dumpLootTables(CommandContext<CommandSourceStack> ctx) {
        Collection<LootTableInfo> tables = LootTableRegistry.getInstance().getAll();

        Isotope.LOGGER.info("=== Loot Table Dump ===");
        for (LootTableInfo info : tables) {
            Isotope.LOGGER.info("  {} [{}]", info.fullId(), info.category());
        }
        Isotope.LOGGER.info("=== End ({} total) ===", tables.size());

        ctx.getSource().sendSuccess(() -> Component.literal(
            "Dumped " + tables.size() + " loot tables to log"), false);
        return 1;
    }

    private static int dumpAll(CommandContext<CommandSourceStack> ctx) {
        dumpStructures(ctx);
        dumpLootTables(ctx);
        return 1;
    }
}
