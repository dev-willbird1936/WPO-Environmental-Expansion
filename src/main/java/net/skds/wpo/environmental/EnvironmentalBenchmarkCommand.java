package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

public final class EnvironmentalBenchmarkCommand {
    private EnvironmentalBenchmarkCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wpo")
            .then(Commands.literal("benchmark")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("run")
                    .then(Commands.literal("all")
                        .executes(ctx -> queueSuites(ctx, Arrays.asList(EnvironmentalBenchmarkManager.Suite.values()), true))
                    )
                    .then(Commands.argument("suite", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String id : EnvironmentalBenchmarkManager.Suite.ids()) {
                                builder.suggest(id);
                            }
                            builder.suggest("all");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> queueSuites(ctx, List.of(EnvironmentalBenchmarkManager.Suite.fromId(StringArgumentType.getString(ctx, "suite"))), true))
                    )
                )
                .then(Commands.literal("queue")
                    .then(Commands.literal("all")
                        .executes(ctx -> queueSuites(ctx, Arrays.asList(EnvironmentalBenchmarkManager.Suite.values()), false))
                    )
                    .then(Commands.argument("suite", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String id : EnvironmentalBenchmarkManager.Suite.ids()) {
                                builder.suggest(id);
                            }
                            builder.suggest("all");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> queueSuites(ctx, List.of(EnvironmentalBenchmarkManager.Suite.fromId(StringArgumentType.getString(ctx, "suite"))), false))
                    )
                )
                .then(Commands.literal("status")
                    .executes(EnvironmentalBenchmarkCommand::status)
                )
                .then(Commands.literal("stop")
                    .executes(EnvironmentalBenchmarkCommand::stop)
                )
            )
        );
    }

    private static int queueSuites(CommandContext<CommandSourceStack> ctx, List<EnvironmentalBenchmarkManager.Suite> suites, boolean replace) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String message = replace
            ? EnvironmentalBenchmarkManager.replaceQueue(player, suites)
            : EnvironmentalBenchmarkManager.queue(player, suites);
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(EnvironmentalBenchmarkManager.status(ctx.getSource().getLevel())), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(EnvironmentalBenchmarkManager.stop(ctx.getSource().getLevel())), true);
        return 1;
    }
}
