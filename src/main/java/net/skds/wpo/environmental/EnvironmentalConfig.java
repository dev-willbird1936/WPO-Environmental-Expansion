package net.skds.wpo.environmental;

import java.nio.file.Paths;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class EnvironmentalConfig {

    public static final Common COMMON;
    private static final ModConfigSpec SPEC;

    static {
        Pair<Common, ModConfigSpec> common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        SPEC = common.getRight();
    }

    private EnvironmentalConfig() {
    }

    public static void init(ModContainer container) {
        Paths.get(System.getProperty("user.dir"), "config", EnvironmentalExpansion.MOD_ID).toFile().mkdirs();
        container.registerConfig(ModConfig.Type.COMMON, SPEC, Paths.get(EnvironmentalExpansion.MOD_ID, "common.toml").toString());
    }

    public static void save() {
        SPEC.save();
    }

    public static final class Common {
        public final ModConfigSpec.BooleanValue rainAccumulation;
        public final ModConfigSpec.BooleanValue puddles;
        public final ModConfigSpec.BooleanValue distantRainCatchup;
        public final ModConfigSpec.BooleanValue evaporation;
        public final ModConfigSpec.BooleanValue droughts;
        public final ModConfigSpec.BooleanValue floods;
        public final ModConfigSpec.BooleanValue snowmelt;
        public final ModConfigSpec.BooleanValue absorption;
        public final ModConfigSpec.BooleanValue condensation;
        public final ModConfigSpec.BooleanValue surfaceIce;
        public final ModConfigSpec.BooleanValue agriculture;
        public final ModConfigSpec.BooleanValue seasons;

        public final ModConfigSpec.IntValue updateInterval;
        public final ModConfigSpec.IntValue sampleRadius;
        public final ModConfigSpec.IntValue columnChecksPerPlayer;
        public final ModConfigSpec.IntValue arrivalColumnChecks;

        public final ModConfigSpec.DoubleValue rainChance;
        public final ModConfigSpec.DoubleValue rainIntensity;
        public final ModConfigSpec.DoubleValue stormIntensity;
        public final ModConfigSpec.DoubleValue collectorEfficiency;
        public final ModConfigSpec.DoubleValue condensationChance;
        public final ModConfigSpec.DoubleValue condensationMultiplierOverride;
        public final ModConfigSpec.DoubleValue surfaceFreezeChance;
        public final ModConfigSpec.DoubleValue surfaceIceMultiplierOverride;
        public final ModConfigSpec.DoubleValue evaporationChance;
        public final ModConfigSpec.DoubleValue droughtEvaporationBonus;
        public final ModConfigSpec.DoubleValue sunlightEvaporationBonus;
        public final ModConfigSpec.DoubleValue hotBiomeEvaporationBonus;
        public final ModConfigSpec.DoubleValue lavaEvaporationBonus;
        public final ModConfigSpec.DoubleValue absorptionChance;
        public final ModConfigSpec.DoubleValue absorptionMultiplierOverride;
        public final ModConfigSpec.DoubleValue releaseChance;
        public final ModConfigSpec.DoubleValue snowmeltChance;
        public final ModConfigSpec.DoubleValue agricultureGrowthBoostChance;
        public final ModConfigSpec.DoubleValue agricultureMultiplierOverride;
        public final ModConfigSpec.DoubleValue springRunoffMultiplier;
        public final ModConfigSpec.DoubleValue summerEvaporationMultiplier;
        public final ModConfigSpec.DoubleValue evaporationMultiplierOverride;

        public final ModConfigSpec.IntValue droughtThreshold;
        public final ModConfigSpec.IntValue droughtDryStep;
        public final ModConfigSpec.IntValue droughtWetStep;
        public final ModConfigSpec.IntValue seasonLengthDays;
        public final ModConfigSpec.IntValue seasonPhaseLengthDays;
        public final ModConfigSpec.BooleanValue tropicalSeasons;
        public final ModConfigSpec.IntValue ambientWetnessCap;
        public final ModConfigSpec.IntValue ambientWetnessRainGain;
        public final ModConfigSpec.IntValue ambientWetnessDryDecay;
        public final ModConfigSpec.IntValue ambientMaxPuddleLevels;

        public final ModConfigSpec.IntValue rainBarrelBuckets;
        public final ModConfigSpec.IntValue cisternBuckets;
        public final ModConfigSpec.IntValue roofCollectorBuckets;
        public final ModConfigSpec.IntValue groundBasinBuckets;
        public final ModConfigSpec.IntValue intakeCollectorBuckets;
        public final ModConfigSpec.IntValue roofCollectorTransferMb;
        public final ModConfigSpec.IntValue basinSurfaceDrainLevels;
        public final ModConfigSpec.IntValue grateSurfaceDrainLevels;
        public final ModConfigSpec.IntValue absorptionEvictionDays;

        private Common(ModConfigSpec.Builder builder) {
            Function<String, ModConfigSpec.Builder> translate = key -> builder.translation(EnvironmentalExpansion.MOD_ID + ".config." + key);

            builder.push("Systems");
            rainAccumulation = translate.apply("rainAccumulation").define("rainAccumulation", true);
            puddles = translate.apply("puddles").define("puddles", true);
            distantRainCatchup = translate.apply("distantRainCatchup").define("distantRainCatchup", true);
            evaporation = translate.apply("evaporation").define("evaporation", true);
            droughts = translate.apply("droughts").define("droughts", true);
            floods = translate.apply("floods").define("floods", true);
            snowmelt = translate.apply("snowmelt").define("snowmelt", true);
            absorption = translate.apply("absorption").define("absorption", true);
            condensation = translate.apply("condensation").define("condensation", true);
            surfaceIce = translate.apply("surfaceIce").define("surfaceIce", true);
            agriculture = translate.apply("agriculture").define("agriculture", true);
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
                .defineInRange("rainChance", 0.0018D, 0.0D, 4.0D);
            rainIntensity = translate.apply("rainIntensity")
                .comment("General rain buildup multiplier.")
                .defineInRange("rainIntensity", 1.0D, 0.0D, 8.0D);
            stormIntensity = translate.apply("stormIntensity")
                .comment("Additional multiplier applied during thunderstorms when floods are enabled.")
                .defineInRange("stormIntensity", 2.0D, 1.0D, 12.0D);
            collectorEfficiency = translate.apply("collectorEfficiency")
                .comment("Multiplier applied to collector fill rates.")
                .defineInRange("collectorEfficiency", 1.0D, 0.0D, 8.0D);
            condensationChance = translate.apply("condensationChance")
                .comment("Base chance that a sampled dry outdoor column forms a dew step.")
                .defineInRange("condensationChance", 0.012D, 0.0D, 4.0D);
            condensationMultiplierOverride = translate.apply("condensationMultiplierOverride")
                .comment("Global multiplier for condensation and dew formation. Use /condensation to change in-game.")
                .defineInRange("condensationMultiplierOverride", 1.0D, 0.0D, Double.MAX_VALUE);
            surfaceFreezeChance = translate.apply("surfaceFreezeChance")
                .comment("Base chance that a sampled exposed surface-water step freezes into surface ice.")
                .defineInRange("surfaceFreezeChance", 0.08D, 0.0D, 4.0D);
            surfaceIceMultiplierOverride = translate.apply("surfaceIceMultiplierOverride")
                .comment("Global multiplier for surface freezing and thaw. Use /freezing to change in-game.")
                .defineInRange("surfaceIceMultiplierOverride", 1.0D, 0.0D, Double.MAX_VALUE);
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
                .comment("Length of each main season (Spring/Summer/Autumn/Winter) in in-game days when seasons are enabled.")
                .defineInRange("seasonLengthDays", 24, 3, 365);
            seasonPhaseLengthDays = translate.apply("seasonPhaseLengthDays")
                .comment("Length of each sub-season phase (Early/Mid/Late) in in-game days. A full year is 12 * phaseLength days.")
                .defineInRange("seasonPhaseLengthDays", 8, 1, 120);
            tropicalSeasons = translate.apply("tropicalSeasons")
                .comment("Use tropical wet/dry cycle instead of temperate 4-season cycle. Tropical biomes (desert, savanna, jungle) follow wet/dry instead of Spring/Summer/Autumn/Winter.")
                .define("tropicalSeasons", false);
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
            evaporationMultiplierOverride = translate.apply("evaporationMultiplierOverride")
                .comment("Global multiplier for all evaporation. Use /evaporation command to change in-game.")
                .defineInRange("evaporationMultiplierOverride", 1.0D, 0.0D, Double.MAX_VALUE);
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
                .comment("Additional evaporation multiplier when lava or magma is nearby.")
                .defineInRange("lavaEvaporationBonus", 1.0D, 0.0D, 8.0D);
            absorptionChance = translate.apply("absorptionChance")
                .comment("Base chance that absorbent terrain stores one water step from rainfall or a surface puddle.")
                .defineInRange("absorptionChance", 0.35D, 0.0D, 4.0D);
            absorptionMultiplierOverride = translate.apply("absorptionMultiplierOverride")
                .comment("Global multiplier for absorption capacity and soak rate. Use /absorption command to change in-game.")
                .defineInRange("absorptionMultiplierOverride", 1.0D, 0.0D, Double.MAX_VALUE);
            releaseChance = translate.apply("releaseChance")
                .comment("Base chance that saturated terrain releases one stored water step.")
                .defineInRange("releaseChance", 0.10D, 0.0D, 4.0D);
            snowmeltChance = translate.apply("snowmeltChance")
                .comment("Base chance that a sampled snow column melts one layer.")
                .defineInRange("snowmeltChance", 0.12D, 0.0D, 4.0D);
            agricultureGrowthBoostChance = translate.apply("agricultureGrowthBoostChance")
                .comment("Base chance that absorbed water in farmland forces an extra crop growth step.")
                .defineInRange("agricultureGrowthBoostChance", 0.10D, 0.0D, 4.0D);
            agricultureMultiplierOverride = translate.apply("agricultureMultiplierOverride")
                .comment("Global multiplier for absorbed-water farmland support. Use /agriculture to change in-game.")
                .defineInRange("agricultureMultiplierOverride", 1.0D, 0.0D, Double.MAX_VALUE);
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
            absorptionEvictionDays = translate.apply("absorptionEvictionDays")
                .comment("Days of inactivity before a chunk's absorbed-water data is evicted from memory. Lower values reduce memory usage; higher values preserve state longer for returning players.")
                .defineInRange("absorptionEvictionDays", 7, 1, 90);
            builder.pop();
        }
    }
}
