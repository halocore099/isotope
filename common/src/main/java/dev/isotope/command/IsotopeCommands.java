package dev.isotope.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.isotope.Isotope;
import dev.isotope.analysis.LootContentsRegistry;
import dev.isotope.analysis.StructureLootLinker;
import dev.isotope.data.*;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.registry.StructureRegistry;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Debug commands for inspecting discovered registries.
 */
public final class IsotopeCommands {

    private static final SuggestionProvider<CommandSourceStack> LOOT_TABLE_SUGGESTIONS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            LootTableRegistry.getInstance().getAll().stream().map(LootTableInfo::id),
            builder
        );

    private static final SuggestionProvider<CommandSourceStack> STRUCTURE_SUGGESTIONS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            StructureRegistry.getInstance().getAll().stream().map(StructureInfo::id),
            builder
        );

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
                // M2: Loot table content analysis
                .then(Commands.literal("loot")
                    .then(Commands.argument("table", ResourceLocationArgument.id())
                        .suggests(LOOT_TABLE_SUGGESTIONS)
                        .executes(IsotopeCommands::showLootTable)))
                // M2: Structure-loot linkage
                .then(Commands.literal("analyze")
                    .then(Commands.argument("structure", ResourceLocationArgument.id())
                        .suggests(STRUCTURE_SUGGESTIONS)
                        .executes(IsotopeCommands::analyzeStructure)))
                // M2: Item search
                .then(Commands.literal("items")
                    .then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(ctx -> findItem(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(ctx -> findItem(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
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

    // ========== M2: Loot Table Analysis Commands ==========

    private static int showLootTable(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation tableId = ResourceLocationArgument.getId(ctx, "table");

        LootTableContents contents = LootContentsRegistry.getInstance().get(tableId);

        if (!contents.analyzed()) {
            source.sendFailure(Component.literal("Failed to analyze: " + contents.errorMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== " + tableId + " ==="), false);
        source.sendSuccess(() -> Component.literal("Category: " + contents.info().category()), false);
        source.sendSuccess(() -> Component.literal("Pools: " + contents.poolCount()), false);

        int displayCount = 0;
        int maxDisplay = 20;

        for (LootPoolInfo pool : contents.pools()) {
            String poolHeader = String.format("Pool %d: %s rolls, %d entries",
                pool.poolIndex(), pool.rollsDescription(), pool.entryCount());
            source.sendSuccess(() -> Component.literal(poolHeader), false);

            for (LootEntryInfo entry : pool.entries()) {
                if (displayCount++ >= maxDisplay) {
                    int remaining = contents.totalEntryCount() - maxDisplay;
                    source.sendSuccess(() -> Component.literal("... and " + remaining + " more entries"), false);
                    return 1;
                }
                String entryStr = "  " + entry.formatForDisplay();
                source.sendSuccess(() -> Component.literal(entryStr), false);
            }
        }

        return 1;
    }

    private static int analyzeStructure(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation structureId = ResourceLocationArgument.getId(ctx, "structure");

        Optional<StructureLootLink> linkOpt = StructureLootLinker.getInstance().getLink(structureId);

        if (linkOpt.isEmpty()) {
            source.sendFailure(Component.literal("Structure not found: " + structureId));
            return 0;
        }

        StructureLootLink link = linkOpt.get();

        source.sendSuccess(() -> Component.literal("=== " + structureId + " ==="), false);
        source.sendSuccess(() -> Component.literal("Link method: " + link.linkMethod()), false);
        source.sendSuccess(() -> Component.literal("Confidence: " + link.confidencePercent()), false);

        if (!link.hasLinks()) {
            source.sendSuccess(() -> Component.literal("No linked loot tables found"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Linked tables (" + link.linkCount() + "):"), false);

        int count = 0;
        for (ResourceLocation tableId : link.linkedTables()) {
            if (count++ >= 20) {
                int remaining = link.linkCount() - 20;
                source.sendSuccess(() -> Component.literal("... and " + remaining + " more"), false);
                break;
            }

            LootTableContents contents = LootContentsRegistry.getInstance().get(tableId);
            String tableStr = tableId.toString();
            if (contents.analyzed()) {
                tableStr += " (" + contents.uniqueItemCount() + " items)";
            }
            final String display = "  " + tableStr;
            source.sendSuccess(() -> Component.literal(display), false);
        }

        return 1;
    }

    private static int findItem(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");

        Set<ResourceLocation> tables = LootContentsRegistry.getInstance().findTablesWithItem(itemId);

        if (tables.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Item not found in any loot tables: " + itemId), false);
            return 1;
        }

        int pageSize = 20;
        int totalPages = (tables.size() + pageSize - 1) / pageSize;
        page = Math.min(page, totalPages);

        final int currentPage = page;
        source.sendSuccess(() -> Component.literal(
            String.format("=== %s (page %d/%d) ===", itemId, currentPage, totalPages)), false);
        source.sendSuccess(() -> Component.literal("Found in " + tables.size() + " loot tables:"), false);

        List<ResourceLocation> sortedTables = tables.stream().sorted().toList();
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, sortedTables.size());

        for (int i = start; i < end; i++) {
            ResourceLocation tableId = sortedTables.get(i);
            source.sendSuccess(() -> Component.literal("  " + tableId), false);
        }

        if (page < totalPages) {
            final int nextPage = page + 1;
            source.sendSuccess(() -> Component.literal(
                "Use /isotope items " + itemId + " " + nextPage + " for next page"), false);
        }

        return 1;
    }
}
