package net.skds.wpo.environmental;

import java.nio.file.Paths;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class EnvironmentalConfig {

    public static final Common COMMON;
    private static final ForgeConfigSpec SPEC;

    static {
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        SPEC = common.getRight();
    }

    private EnvironmentalConfig() {
    }

    public static void init() {
        Paths.get(System.getProperty("user.dir"), "config", EnvironmentalExpansion.MOD_ID).toFile().mkdirs();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, Paths.get(EnvironmentalExpansion.MOD_ID, "common.toml").toString());
    }

    public static void save() {
        SPEC.save();
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue rainAccumulation;
        public final ForgeConfigSpec.BooleanValue puddles;
        public final ForgeConfigSpec.BooleanValue distantRainCatchup;
        public final ForgeConfigSpec.BooleanValue evaporation;
        public final ForgeConfigSpec.BooleanValue droughts;
        public final ForgeConfigSpec.BooleanValue floods;
        public final ForgeConfigSpec.BooleanValue snowmelt;
        public final ForgeConfigSpec.BooleanValue absorption;
        public final ForgeConfigSpec.BooleanValue seasons;

        public final ForgeConfigSpec.IntValue updateInterval;
        public final ForgeConfigSpec.IntValue sampleRadius;
        public final ForgeConfigSpec.IntValue columnChecksPerPlayer;
        public final ForgeConfigSpec.IntValue arrivalColumnChecks;

        public final ForgeConfigSpec.DoubleValue rainChance;
        public final ForgeConfigSpec.DoubleValue rainIntensity;
        public final ForgeConfigSpec.DoubleValue stormIntensity;
        public final ForgeConfigSpec.DoubleValue collectorEfficiency;
        public final ForgeConfigSpec.DoubleValue evaporationChance;
        public final ForgeConfigSpec.DoubleValue droughtEvaporationBonus;
        public final ForgeConfigSpec.DoubleValue sunlightEvaporationBonus;
        public final ForgeConfigSpec.DoubleValue hotBiomeEvaporationBonus;
        public final ForgeConfigSpec.DoubleValue lavaEvaporationBonus;
        public final ForgeConfigSpec.DoubleValue absorptionChance;
        public final ForgeConfigSpec.DoubleValue releaseChance;
        public final ForgeConfigSpec.DoubleValue snowmeltChance;
        public final ForgeConfigSpec.DoubleValue springRunoffMultiplier;
        public final ForgeConfigSpec.DoubleValue summerEvaporationMultiplier;

        public final ForgeConfigSpec.IntValue droughtThreshold;
        public final ForgeConfigSpec.IntValue droughtDryStep;
        public final ForgeConfigSpec.IntValue droughtWetStep;
        public final ForgeConfigSpec.IntValue seasonLengthDays;
        public final ForgeConfigSpec.IntValue ambientWetnessCap;
        public final ForgeConfigSpec.IntValue ambientWetnessRainGain;
        public final ForgeConfigSpec.IntValue ambientWetnessDryDecay;
        public final ForgeConfigSpec.IntValue ambientMaxPuddleLevels;

        public final ForgeConfigSpec.IntValue rainBarrelBuckets;
        public final ForgeConfigSpec.IntValue cisternBuckets;
        public final ForgeConfigSpec.IntValue roofCollectorBuckets;
        public final ForgeConfigSpec.IntValue groundBasinBuckets;
        public final ForgeConfigSpec.IntValue intakeCollectorBuckets;
        public final ForgeConfigSpec.IntValue roofCollectorTransferMb;
        public final ForgeConfigSpec.IntValue basinSurfaceDrainLevels;
        public final ForgeConfigSpec.IntValue grateSurfaceDrainLevels;

        private Common(ForgeConfigSpec.Builder builder) {
            Function<String, ForgeConfigSpec.Builder> translate = key -> builder.translation(EnvironmentalExpansion.MOD_ID + ".config." + key);

            builder.push("Systems");
            rainAccumulation = translate.apply("rainAccumulation").define("rainAccumulation", true);
            puddles = translate.apply("puddles").define("puddles", true);
            distantRainCatchup = translate.apply("distantRainCatchup").define("distantRainCatchup", true);
            evaporation = translate.apply("evaporation").define("evaporation", true);
            droughts = translate.apply("droughts").define("droughts", true);
            floods = translate.apply("floods").define("floods", true);
            snowmelt = translate.apply("snowmelt").define("snowmelt", true);
            absorption = translate.apply("absorption").define("absorption", true);
            seasons = translate.apply("seasons").define("seasons", true);
            builder.pop();

            builder.push("Simulation");
            updateInterval = translate.apply("updateInterval")
                .comment("Server ticks between environmental scans.")
                .defineInRange("updateInterval", 5, 1, 40);
            sampleRadius = translate.apply("sampleRadius")
                .comment("Horizontal radius sampled around each player for rainfall, evaporation, and snowmelt.")
                .defineInRange("sampleRadius", 24, 4, 96);
            columnChecksPerPlayer = translate.apply("columnChecksPerPlayer")
                .comment("Column samples per active player on each update.")
                .defineInRange("columnChecksPerPlayer", 10, 1, 64);
            arrivalColumnChecks = translate.apply("arrivalColumnChecks")
                .comment("Extra columns processed around a player when entering a new chunk, used to materialize deferred rainfall quickly.")
                .defineInRange("arrivalColumnChecks", 36, 0, 256);
            builder.pop();

            builder.push("Weather");
            rainChance = translate.apply("rainChance")
                .comment("Base chance that a sampled rainy column receives a puddle step.")
                .defineInRange("rainChance", 0.18D, 0.0D, 4.0D);
            rainIntensity = translate.apply("rainIntensity")
                .comment("General rain buildup multiplier.")
                .defineInRange("rainIntensity", 1.0D, 0.0D, 8.0D);
            stormIntensity = translate.apply("stormIntensity")
                .comment("Additional multiplier applied during thunderstorms when floods are enabled.")
                .defineInRange("stormIntensity", 2.0D, 1.0D, 12.0D);
            collectorEfficiency = translate.apply("collectorEfficiency")
                .comment("Multiplier applied to collector fill rates.")
                .defineInRange("collectorEfficiency", 1.0D, 0.0D, 8.0D);
            droughtThreshold = translate.apply("droughtThreshold")
                .comment("Dryness score at which drought behavior starts to apply.")
                .defineInRange("droughtThreshold", 24000, 0, 2_000_000);
            droughtDryStep = translate.apply("droughtDryStep")
                .comment("Dryness added each environmental update while the level is dry.")
                .defineInRange("droughtDryStep", 25, 0, 10000);
            droughtWetStep = translate.apply("droughtWetStep")
                .comment("Dryness removed each environmental update while the level is raining.")
                .defineInRange("droughtWetStep", 100, 0, 10000);
            seasonLengthDays = translate.apply("seasonLengthDays")
                .comment("Length of each season in in-game days when seasons are enabled.")
                .defineInRange("seasonLengthDays", 16, 1, 365);
            ambientWetnessCap = translate.apply("ambientWetnessCap")
                .comment("Upper limit for deferred world wetness used to materialize puddles in newly visited areas.")
                .defineInRange("ambientWetnessCap", 1800, 0, 200000);
            ambientWetnessRainGain = translate.apply("ambientWetnessRainGain")
                .comment("Deferred world wetness gained each environmental update while it is raining.")
                .defineInRange("ambientWetnessRainGain", 3, 0, 1000);
            ambientWetnessDryDecay = translate.apply("ambientWetnessDryDecay")
                .comment("Deferred world wetness removed each environmental update while the level is dry.")
                .defineInRange("ambientWetnessDryDecay", 1, 0, 1000);
            ambientMaxPuddleLevels = translate.apply("ambientMaxPuddleLevels")
                .comment("Maximum puddle depth created from deferred rain catch-up in newly visited areas.")
                .defineInRange("ambientMaxPuddleLevels", 3, 0, 8);
            springRunoffMultiplier = translate.apply("springRunoffMultiplier")
                .comment("Multiplier applied to snowmelt and soil release in spring.")
                .defineInRange("springRunoffMultiplier", 1.5D, 0.0D, 8.0D);
            summerEvaporationMultiplier = translate.apply("summerEvaporationMultiplier")
                .comment("Multiplier applied to evaporation in summer.")
                .defineInRange("summerEvaporationMultiplier", 1.4D, 0.0D, 8.0D);
            builder.pop();

            builder.push("Hydrology");
            evaporationChance = translate.apply("evaporationChance")
                .comment("Base chance that a sampled surface-water cell evaporates.")
                .defineInRange("evaporationChance", 0.06D, 0.0D, 4.0D);
            droughtEvaporationBonus = translate.apply("droughtEvaporationBonus")
                .comment("Additional evaporation multiplier while drought is active.")
                .defineInRange("droughtEvaporationBonus", 0.75D, 0.0D, 8.0D);
            sunlightEvaporationBonus = translate.apply("sunlightEvaporationBonus")
                .comment("Additional evaporation multiplier for open sky and bright light.")
                .defineInRange("sunlightEvaporationBonus", 0.4D, 0.0D, 8.0D);
            hotBiomeEvaporationBonus = translate.apply("hotBiomeEvaporationBonus")
                .comment("Additional evaporation multiplier for hot biomes.")
                .defineInRange("hotBiomeEvaporationBonus", 0.5D, 0.0D, 8.0D);
            lavaEvaporationBonus = translate.apply("lavaEvaporationBonus")
                .comment("Additional evaporation multiplier when lava is nearby.")
                .defineInRange("lavaEvaporationBonus", 0.7D, 0.0D, 8.0D);
            absorptionChance = translate.apply("absorptionChance")
                .comment("Base chance that absorbent terrain stores one water step from rainfall or a surface puddle.")
                .defineInRange("absorptionChance", 0.35D, 0.0D, 4.0D);
            releaseChance = translate.apply("releaseChance")
                .comment("Base chance that saturated terrain releases one stored water step.")
                .defineInRange("releaseChance", 0.10D, 0.0D, 4.0D);
            snowmeltChance = translate.apply("snowmeltChance")
                .comment("Base chance that a sampled snow column melts one layer.")
                .defineInRange("snowmeltChance", 0.12D, 0.0D, 4.0D);
            builder.pop();

            builder.push("Storage");
            rainBarrelBuckets = translate.apply("rainBarrelBuckets").defineInRange("rainBarrelBuckets", 8, 1, 256);
            cisternBuckets = translate.apply("cisternBuckets").defineInRange("cisternBuckets", 32, 1, 2048);
            roofCollectorBuckets = translate.apply("roofCollectorBuckets").defineInRange("roofCollectorBuckets", 4, 1, 256);
            groundBasinBuckets = translate.apply("groundBasinBuckets").defineInRange("groundBasinBuckets", 6, 1, 256);
            intakeCollectorBuckets = translate.apply("intakeCollectorBuckets").defineInRange("intakeCollectorBuckets", 6, 1, 256);
            roofCollectorTransferMb = translate.apply("roofCollectorTransferMb")
                .comment("Maximum water the roof collector pushes downward per server tick cycle.")
                .defineInRange("roofCollectorTransferMb", 250, 0, 8000);
            basinSurfaceDrainLevels = translate.apply("basinSurfaceDrainLevels")
                .comment("Surface-water levels the ground basin can collect each cycle.")
                .defineInRange("basinSurfaceDrainLevels", 2, 0, 8);
            grateSurfaceDrainLevels = translate.apply("grateSurfaceDrainLevels")
                .comment("Surface-water levels the intake grate collector can collect each cycle.")
                .defineInRange("grateSurfaceDrainLevels", 3, 0, 8);
            builder.pop();
        }
    }
}
