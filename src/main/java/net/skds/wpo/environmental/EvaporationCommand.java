package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class EvaporationCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evaporation")
            .requires(source -> source.hasPermission(2))
            .executes(EvaporationCommand::showEvaporation)
            .then(Commands.literal("get")
                .executes(EvaporationCommand::showEvaporation)
            )
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(EvaporationCommand::setEvaporation)
                )
            )
            .then(Commands.literal("reset")
                .executes(EvaporationCommand::resetEvaporation)
            )
        );
    }

    private static int showEvaporation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = EnvironmentalConfig.COMMON.evaporationMultiplierOverride.get();
        
        source.sendSuccess(() -> Component.literal("Current evaporation multiplier: " + value + "x"), false);
        
        if (value > 1.0) {
            source.sendSuccess(() -> Component.literal("Evaporation is " + String.format("%.1f", value) + "x faster than normal"), false);
        } else if (value == 0.0) {
            source.sendSuccess(() -> Component.literal("Evaporation is disabled"), false);
        } else if (value < 1.0) {
            source.sendSuccess(() -> Component.literal("Evaporation is " + String.format("%.1f", 1.0/value) + "x slower than normal"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Evaporation is at default (1.0x)"), false);
        }
        
        return 1;
    }

    private static int setEvaporation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = DoubleArgumentType.getDouble(ctx, "value");
        
        EnvironmentalConfig.COMMON.evaporationMultiplierOverride.set(value);
        EnvironmentalConfig.save();
        
        if (value > 1.0) {
            source.sendSuccess(() -> Component.literal("Set evaporation multiplier to " + value + "x (faster)"), true);
        } else if (value < 1.0) {
            source.sendSuccess(() -> Component.literal("Set evaporation multiplier to " + value + "x (slower)"), true);
        } else {
            source.sendSuccess(() -> Component.literal("Reset evaporation multiplier to 1.0x"), true);
        }
        
        return 1;
    }

    private static int resetEvaporation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        EnvironmentalConfig.COMMON.evaporationMultiplierOverride.set(1.0);
        EnvironmentalConfig.save();
        
        source.sendSuccess(() -> Component.literal("Reset evaporation multiplier to 1.0x"), true);
        
        return 1;
    }
}
