package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class CondensationCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("condensation")
            .requires(source -> source.hasPermission(2))
            .executes(CondensationCommand::showCondensation)
            .then(Commands.literal("get").executes(CondensationCommand::showCondensation))
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(CondensationCommand::setCondensation)))
            .then(Commands.literal("reset").executes(CondensationCommand::resetCondensation)));
    }

    private static int showCondensation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = EnvironmentalConfig.COMMON.condensationMultiplierOverride.get();
        source.sendSuccess(() -> Component.literal("Current condensation multiplier: " + value + "x"), false);
        if (value > 1.0D) {
            source.sendSuccess(() -> Component.literal("Condensation is " + String.format("%.1f", value) + "x stronger than normal"), false);
        } else if (value == 0.0D) {
            source.sendSuccess(() -> Component.literal("Condensation is disabled"), false);
        } else if (value < 1.0D) {
            source.sendSuccess(() -> Component.literal("Condensation is " + String.format("%.1f", 1.0D / value) + "x weaker than normal"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Condensation is at default (1.0x)"), false);
        }
        return 1;
    }

    private static int setCondensation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = DoubleArgumentType.getDouble(ctx, "value");
        EnvironmentalConfig.COMMON.condensationMultiplierOverride.set(value);
        EnvironmentalConfig.save();
        source.sendSuccess(() -> Component.literal("Set condensation multiplier to " + value + "x"), true);
        return 1;
    }

    private static int resetCondensation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EnvironmentalConfig.COMMON.condensationMultiplierOverride.set(1.0D);
        EnvironmentalConfig.save();
        source.sendSuccess(() -> Component.literal("Reset condensation multiplier to 1.0x"), true);
        return 1;
    }
}
