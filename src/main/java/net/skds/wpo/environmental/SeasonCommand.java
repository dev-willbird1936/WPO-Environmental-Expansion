package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SeasonCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("season")
            .requires(source -> source.hasPermission(2))
            .executes(SeasonCommand::showSeason)
            .then(Commands.literal("set")
                .then(Commands.argument("season", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("spring");
                        builder.suggest("summer");
                        builder.suggest("autumn");
                        builder.suggest("winter");
                        return builder.buildFuture();
                    })
                    .executes(SeasonCommand::setSeason)
                    .then(Commands.argument("phase", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("1");
                            builder.suggest("2");
                            builder.suggest("3");
                            builder.suggest("early");
                            builder.suggest("mid");
                            builder.suggest("late");
                            return builder.buildFuture();
                        })
                        .executes(SeasonCommand::setSeasonWithPhase)
                    )
                )
            )
            .then(Commands.literal("get")
                .executes(SeasonCommand::showSeason)
            )
        );
    }

    private static int showSeason(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!EnvironmentalConfig.COMMON.seasons.get()) {
            source.sendSuccess(() -> Component.literal("Seasons are disabled in config."), false);
            return 1;
        }
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(source.getLevel());
        double progress = SeasonManager.getSeasonProgress(source.getLevel());
        long day = SeasonManager.getWorldDay(source.getLevel());
        
        String phaseStr = switch (subSeason.phase()) {
            case EARLY -> "Early";
            case MID -> "Mid";
            case LATE -> "Late";
        };
        
        String seasonStr = switch (subSeason.season()) {
            case SPRING -> "Spring";
            case SUMMER -> "Summer";
            case AUTUMN -> "Autumn";
            case WINTER -> "Winter";
        };
        
        String message = String.format("Current Season: %s %s (Day %d, Phase Progress: %.0f%%)", 
            phaseStr, seasonStr, day, progress * 100);
        
        source.sendSuccess(() -> Component.literal(message), false);
        
        int phaseLength = SeasonManager.getPhaseLengthDays();
        long daysInPhase = day % phaseLength;
        source.sendSuccess(() -> Component.literal(
            String.format("Phase length: %d days, Days into phase: %d/%d", 
                phaseLength, daysInPhase, phaseLength)), false);
        
        return 1;
    }

    private static int setSeason(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String seasonArg = StringArgumentType.getString(ctx, "season").toLowerCase();
        
        SeasonManager.Season targetSeason;
        switch (seasonArg) {
            case "spring" -> targetSeason = SeasonManager.Season.SPRING;
            case "summer" -> targetSeason = SeasonManager.Season.SUMMER;
            case "autumn", "fall" -> targetSeason = SeasonManager.Season.AUTUMN;
            case "winter" -> targetSeason = SeasonManager.Season.WINTER;
            default -> {
                source.sendFailure(Component.literal("Invalid season: " + seasonArg + ". Use spring, summer, autumn, or winter."));
                return 0;
            }
        }
        
        int phaseLength = SeasonManager.getPhaseLengthDays();
        int seasonIndex = targetSeason.ordinal();
        long targetDay = seasonIndex * phaseLength * 3L + phaseLength;
        long targetTime = targetDay * 24000L + 6000L;
        
        source.getLevel().setDayTime(targetTime);
        
        source.sendSuccess(() -> Component.literal("Set season to " + targetSeason.name() + " (Day " + targetDay + ")"), true);
        
        return 1;
    }

    private static int setSeasonWithPhase(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String seasonArg = StringArgumentType.getString(ctx, "season").toLowerCase();
        String phaseArg = StringArgumentType.getString(ctx, "phase").toLowerCase();
        
        SeasonManager.Season targetSeason;
        switch (seasonArg) {
            case "spring" -> targetSeason = SeasonManager.Season.SPRING;
            case "summer" -> targetSeason = SeasonManager.Season.SUMMER;
            case "autumn", "fall" -> targetSeason = SeasonManager.Season.AUTUMN;
            case "winter" -> targetSeason = SeasonManager.Season.WINTER;
            default -> {
                source.sendFailure(Component.literal("Invalid season: " + seasonArg));
                return 0;
            }
        }
        
        SeasonManager.SeasonPhase targetPhase;
        switch (phaseArg) {
            case "1", "early" -> targetPhase = SeasonManager.SeasonPhase.EARLY;
            case "2", "mid" -> targetPhase = SeasonManager.SeasonPhase.MID;
            case "3", "late" -> targetPhase = SeasonManager.SeasonPhase.LATE;
            default -> {
                source.sendFailure(Component.literal("Invalid phase: " + phaseArg + ". Use 1/2/3 or early/mid/late."));
                return 0;
            }
        }
        
        int phaseLength = SeasonManager.getPhaseLengthDays();
        int seasonIndex = targetSeason.ordinal();
        int phaseIndex = targetPhase.ordinal();
        
        long targetDay = seasonIndex * 3L * phaseLength + phaseIndex * phaseLength;
        long targetTime = targetDay * 24000L + 6000L;
        
        source.getLevel().setDayTime(targetTime);
        
        String phaseName = switch (targetPhase) {
            case EARLY -> "Early";
            case MID -> "Mid";
            case LATE -> "Late";
        };
        
        source.sendSuccess(() -> Component.literal(
            "Set season to " + phaseName + " " + targetSeason.name() + " (Day " + targetDay + ")"), true);
        
        return 1;
    }
}
