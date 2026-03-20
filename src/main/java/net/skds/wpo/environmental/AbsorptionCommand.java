package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AbsorptionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("absorption")
            .requires(source -> source.hasPermission(2))
            .executes(AbsorptionCommand::showAbsorption)
            .then(Commands.literal("get")
                .executes(AbsorptionCommand::showAbsorption)
            )
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(AbsorptionCommand::setAbsorption)
                )
            )
            .then(Commands.literal("reset")
                .executes(AbsorptionCommand::resetAbsorption)
            )
        );
    }

    private static int showAbsorption(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = EnvironmentalConfig.COMMON.absorptionMultiplierOverride.get();

        source.sendSuccess(() -> Component.literal("Current absorption multiplier: " + value + "x"), false);

        if (value > 1.0) {
            source.sendSuccess(() -> Component.literal("Absorption is " + String.format("%.1f", value) + "x stronger than normal"), false);
        } else if (value == 0.0) {
            source.sendSuccess(() -> Component.literal("Absorption is disabled"), false);
        } else if (value < 1.0) {
            source.sendSuccess(() -> Component.literal("Absorption is " + String.format("%.1f", 1.0 / value) + "x weaker than normal"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Absorption is at default (1.0x)"), false);
        }

        return 1;
    }

    private static int setAbsorption(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        EnvironmentalConfig.COMMON.absorptionMultiplierOverride.set(value);
        EnvironmentalConfig.save();

        if (value > 1.0) {
            source.sendSuccess(() -> Component.literal("Set absorption multiplier to " + value + "x (stronger)"), true);
        } else if (value < 1.0) {
            source.sendSuccess(() -> Component.literal("Set absorption multiplier to " + value + "x (weaker)"), true);
        } else {
            source.sendSuccess(() -> Component.literal("Reset absorption multiplier to 1.0x"), true);
        }

        return 1;
    }

    private static int resetAbsorption(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        EnvironmentalConfig.COMMON.absorptionMultiplierOverride.set(1.0);
        EnvironmentalConfig.save();

        source.sendSuccess(() -> Component.literal("Reset absorption multiplier to 1.0x"), true);

        return 1;
    }
}

