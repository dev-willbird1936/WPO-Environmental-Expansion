package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class FreezingCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("freezing")
            .requires(source -> source.hasPermission(2))
            .executes(FreezingCommand::showFreezing)
            .then(Commands.literal("get").executes(FreezingCommand::showFreezing))
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(FreezingCommand::setFreezing)))
            .then(Commands.literal("reset").executes(FreezingCommand::resetFreezing)));
    }

    private static int showFreezing(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.get();
        source.sendSuccess(() -> Component.literal("Current surface ice multiplier: " + value + "x"), false);
        if (value > 1.0D) {
            source.sendSuccess(() -> Component.literal("Surface freezing and thaw are " + String.format("%.1f", value) + "x more active"), false);
        } else if (value == 0.0D) {
            source.sendSuccess(() -> Component.literal("Surface ice is disabled"), false);
        } else if (value < 1.0D) {
            source.sendSuccess(() -> Component.literal("Surface freezing and thaw are " + String.format("%.1f", 1.0D / value) + "x gentler"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Surface ice is at default (1.0x)"), false);
        }
        return 1;
    }

    private static int setFreezing(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = DoubleArgumentType.getDouble(ctx, "value");
        EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.set(value);
        EnvironmentalConfig.save();
        source.sendSuccess(() -> Component.literal("Set surface ice multiplier to " + value + "x"), true);
        return 1;
    }

    private static int resetFreezing(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.set(1.0D);
        EnvironmentalConfig.save();
        source.sendSuccess(() -> Component.literal("Reset surface ice multiplier to 1.0x"), true);
        return 1;
    }
}
