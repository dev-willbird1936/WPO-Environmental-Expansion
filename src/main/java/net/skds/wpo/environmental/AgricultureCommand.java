package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AgricultureCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("agriculture")
            .requires(source -> source.hasPermission(2))
            .executes(AgricultureCommand::showAgriculture)
            .then(Commands.literal("get").executes(AgricultureCommand::showAgriculture))
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(AgricultureCommand::setAgriculture)))
            .then(Commands.literal("reset").executes(AgricultureCommand::resetAgriculture)));
    }

    private static int showAgriculture(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = EnvironmentalConfig.COMMON.agricultureMultiplierOverride.get();
        source.sendSuccess(() -> Component.literal("Current agriculture multiplier: " + value + "x"), false);
        if (value > 1.0D) {
            source.sendSuccess(() -> Component.literal("Absorbed-water crop support is " + String.format("%.1f", value) + "x stronger"), false);
        } else if (value == 0.0D) {
            source.sendSuccess(() -> Component.literal("Absorbed-water crop support is disabled"), false);
        } else if (value < 1.0D) {
            source.sendSuccess(() -> Component.literal("Absorbed-water crop support is " + String.format("%.1f", 1.0D / value) + "x weaker"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Absorbed-water crop support is at default (1.0x)"), false);
        }
        return 1;
    }

    private static int setAgriculture(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = DoubleArgumentType.getDouble(ctx, "value");
        EnvironmentalConfig.COMMON.agricultureMultiplierOverride.set(value);
        EnvironmentalConfig.save();
        source.sendSuccess(() -> Component.literal("Set agriculture multiplier to " + value + "x"), true);
        return 1;
    }

    private static int resetAgriculture(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EnvironmentalConfig.COMMON.agricultureMultiplierOverride.set(1.0D);
        EnvironmentalConfig.save();
        source.sendSuccess(() -> Component.literal("Reset agriculture multiplier to 1.0x"), true);
        return 1;
    }
}
