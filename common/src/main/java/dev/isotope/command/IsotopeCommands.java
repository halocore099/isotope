package dev.isotope.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.isotope.Isotope;
import dev.isotope.observation.ObservationSession;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Debug commands for inspecting observation data.
 *
 * In the observation model, ISOTOPE watches actual structure generation
 * and loot table invocations rather than guessing from registry data.
 */
public final class IsotopeCommands {

    private static final SuggestionProvider<CommandSourceStack> STRUCTURE_SUGGESTIONS =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            ObservationSession.getInstance().getAllStructureData().stream()
                .map(s -> s.structureId()),
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
                    .executes(IsotopeCommands::listLootTables))
                .then(Commands.literal("analyze")
                    .then(Commands.argument("structure", ResourceLocationArgument.id())
                        .suggests(STRUCTURE_SUGGESTIONS)
                        .executes(IsotopeCommands::analyzeStructure)))
                .then(Commands.literal("session")
                    .executes(IsotopeCommands::sessionStatus))
        );
    }

    private static int statusCommand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        var session = ObservationSession.getInstance();
        var state = session.getState();
        var allData = session.getAllStructureData();

        source.sendSuccess(() -> Component.literal("=== ISOTOPE Status ==="), false);
        source.sendSuccess(() -> Component.literal("Session state: " + state), false);
        source.sendSuccess(() -> Component.literal("Structures observed: " + allData.size()), false);

        long withLoot = allData.stream().filter(ObservationSession.ObservedStructureData::hasLoot).count();
        source.sendSuccess(() -> Component.literal("Structures with loot: " + withLoot), false);

        Set<ResourceLocation> uniqueTables = new HashSet<>();
        for (var data : allData) {
            uniqueTables.addAll(data.lootTables());
        }
        source.sendSuccess(() -> Component.literal("Unique loot tables: " + uniqueTables.size()), false);

        return 1;
    }

    private static int listStructures(CommandContext<CommandSourceStack> ctx, String namespace) {
        CommandSourceStack source = ctx.getSource();

        var allData = ObservationSession.getInstance().getAllStructureData();

        List<ObservationSession.ObservedStructureData> structures = namespace != null
            ? allData.stream()
                .filter(s -> s.structureId().getNamespace().equals(namespace))
                .toList()
            : allData;

        if (structures.isEmpty()) {
            source.sendFailure(Component.literal("No observed structures found" +
                (namespace != null ? " in namespace: " + namespace : "")));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "=== Observed Structures (" + structures.size() + ") ==="), false);

        int limit = 20;
        int count = 0;
        for (var data : structures) {
            if (count++ >= limit) {
                int remaining = structures.size() - limit;
                source.sendSuccess(() -> Component.literal(
                    "... and " + remaining + " more"), false);
                break;
            }
            String entry = data.structureId().toString();
            if (data.hasLoot()) {
                entry += " [" + data.lootTableCount() + " tables]";
            } else {
                entry += " [NO LOOT]";
            }
            final String display = "  " + entry;
            source.sendSuccess(() -> Component.literal(display), false);
        }

        return 1;
    }

    private static int listLootTables(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        Set<ResourceLocation> uniqueTables = new HashSet<>();
        for (var data : ObservationSession.getInstance().getAllStructureData()) {
            uniqueTables.addAll(data.lootTables());
        }

        if (uniqueTables.isEmpty()) {
            source.sendFailure(Component.literal("No loot tables observed"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "=== Observed Loot Tables (" + uniqueTables.size() + ") ==="), false);

        List<ResourceLocation> sorted = uniqueTables.stream()
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();

        int limit = 20;
        int count = 0;
        for (var tableId : sorted) {
            if (count++ >= limit) {
                int remaining = sorted.size() - limit;
                source.sendSuccess(() -> Component.literal(
                    "... and " + remaining + " more"), false);
                break;
            }
            source.sendSuccess(() -> Component.literal("  " + tableId), false);
        }

        return 1;
    }

    private static int analyzeStructure(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation structureId = ResourceLocationArgument.getId(ctx, "structure");

        var dataOpt = ObservationSession.getInstance().getStructureData(structureId);

        if (dataOpt.isEmpty()) {
            source.sendFailure(Component.literal("Structure not observed: " + structureId));
            source.sendFailure(Component.literal("Run an observation session first."));
            return 0;
        }

        var data = dataOpt.get();

        source.sendSuccess(() -> Component.literal("=== " + structureId + " ==="), false);
        source.sendSuccess(() -> Component.literal("Source: OBSERVED (ground truth)"), false);

        if (!data.hasLoot()) {
            source.sendSuccess(() -> Component.literal("No loot tables observed"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Loot tables (" + data.lootTableCount() + "):"), false);

        int count = 0;
        for (ResourceLocation tableId : data.lootTables()) {
            if (count++ >= 20) {
                int remaining = data.lootTableCount() - 20;
                source.sendSuccess(() -> Component.literal("... and " + remaining + " more"), false);
                break;
            }

            int invocations = data.invocationCounts().getOrDefault(tableId, 0);
            String tableStr = "  " + tableId + " (invoked " + invocations + "x)";
            source.sendSuccess(() -> Component.literal(tableStr), false);
        }

        // Show observed items if any
        if (!data.observedItems().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Observed items (" + data.observedItems().size() + "):"), false);
            count = 0;
            for (ResourceLocation itemId : data.observedItems()) {
                if (count++ >= 10) {
                    int remaining = data.observedItems().size() - 10;
                    source.sendSuccess(() -> Component.literal("... and " + remaining + " more"), false);
                    break;
                }
                source.sendSuccess(() -> Component.literal("  " + itemId), false);
            }
        }

        return 1;
    }

    private static int sessionStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var session = ObservationSession.getInstance();

        source.sendSuccess(() -> Component.literal("=== Observation Session ==="), false);
        source.sendSuccess(() -> Component.literal("State: " + session.getState()), false);

        session.getLastResult().ifPresent(result -> {
            source.sendSuccess(() -> Component.literal("Last result:"), false);
            source.sendSuccess(() -> Component.literal("  Success: " + result.success()), false);
            source.sendSuccess(() -> Component.literal("  Structures placed: " + result.structuresPlaced()), false);
            source.sendSuccess(() -> Component.literal("  Structures failed: " + result.structuresFailed()), false);
            source.sendSuccess(() -> Component.literal("  Loot invocations: " + result.lootInvocations()), false);
            source.sendSuccess(() -> Component.literal("  Structures with loot: " + result.structuresWithLoot()), false);
            source.sendSuccess(() -> Component.literal("  Unique loot tables: " + result.uniqueLootTables()), false);

            if (!result.failedStructures().isEmpty()) {
                source.sendSuccess(() -> Component.literal("Failed structures:"), false);
                for (String failed : result.failedStructures().subList(0, Math.min(5, result.failedStructures().size()))) {
                    source.sendSuccess(() -> Component.literal("  " + failed), false);
                }
            }
        });

        return 1;
    }
}
