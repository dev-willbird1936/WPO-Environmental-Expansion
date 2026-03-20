package net.skds.wpo.environmental;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record BiomeEnvironmentProfile(
    Archetype archetype,
    double confidence,
    double rainChanceMultiplier,
    double rainIntensityMultiplier,
    double stormMultiplier,
    double evaporationMultiplier,
    double absorptionMultiplier,
    double releaseMultiplier,
    double snowmeltMultiplier,
    double collectorMultiplier,
    double retentionMultiplier,
    double droughtSensitivity
) {
    private static final BiomeEnvironmentProfile ARID_DROUGHT = new BiomeEnvironmentProfile(
        Archetype.ARID_DROUGHT, 1.0D, 0.5D, 0.65D, 0.8D, 1.55D, 0.75D, 0.85D, 0.35D, 0.6D, 0.55D, 1.2D
    );
    private static final BiomeEnvironmentProfile DRY_HIGHLANDS = new BiomeEnvironmentProfile(
        Archetype.DRY_HIGHLANDS, 1.0D, 0.66D, 0.76D, 0.86D, 1.42D, 0.82D, 0.92D, 0.76D, 0.7D, 0.7D, 1.12D
    );
    private static final BiomeEnvironmentProfile VOLCANIC = new BiomeEnvironmentProfile(
        Archetype.VOLCANIC, 1.0D, 0.78D, 0.74D, 0.78D, 1.32D, 0.78D, 0.84D, 0.46D, 0.68D, 0.58D, 1.04D
    );
    private static final BiomeEnvironmentProfile COLD_HIGHLANDS = new BiomeEnvironmentProfile(
        Archetype.COLD_HIGHLANDS, 1.0D, 0.88D, 0.92D, 0.96D, 0.62D, 0.98D, 1.1D, 1.28D, 0.92D, 1.0D, 0.84D
    );
    private static final BiomeEnvironmentProfile TROPICAL_MONSOON = new BiomeEnvironmentProfile(
        Archetype.TROPICAL_MONSOON, 1.0D, 1.4D, 1.35D, 1.3D, 0.9D, 1.15D, 1.05D, 0.4D, 1.45D, 1.35D, 0.55D
    );
    private static final BiomeEnvironmentProfile RIPARIAN_WETLAND = new BiomeEnvironmentProfile(
        Archetype.RIPARIAN_WETLAND, 1.0D, 1.22D, 1.16D, 1.04D, 0.84D, 1.28D, 1.02D, 0.82D, 1.42D, 1.42D, 0.62D
    );
    private static final BiomeEnvironmentProfile BOREAL_WET = new BiomeEnvironmentProfile(
        Archetype.BOREAL_WET, 1.0D, 1.1D, 1.05D, 0.95D, 0.7D, 1.2D, 1.0D, 1.15D, 1.1D, 1.2D, 0.8D
    );
    private static final BiomeEnvironmentProfile TEMPERATE_HIGHLANDS = new BiomeEnvironmentProfile(
        Archetype.TEMPERATE_HIGHLANDS, 1.0D, 0.94D, 0.96D, 0.98D, 0.88D, 0.96D, 1.08D, 1.18D, 0.92D, 0.96D, 0.92D
    );
    private static final BiomeEnvironmentProfile SNOWMELT_ALPINE = new BiomeEnvironmentProfile(
        Archetype.SNOWMELT_ALPINE, 1.0D, 0.8D, 0.85D, 0.9D, 0.45D, 1.0D, 1.25D, 1.75D, 0.75D, 1.0D, 0.7D
    );
    private static final BiomeEnvironmentProfile COASTAL_FLUVIAL = new BiomeEnvironmentProfile(
        Archetype.COASTAL_FLUVIAL, 1.0D, 1.14D, 1.14D, 1.06D, 0.9D, 1.16D, 1.05D, 0.92D, 1.38D, 1.3D, 0.72D
    );
    private static final BiomeEnvironmentProfile TUNDRA_STEPPE = new BiomeEnvironmentProfile(
        Archetype.TUNDRA_STEPPE, 1.0D, 0.74D, 0.78D, 0.86D, 1.08D, 0.86D, 0.98D, 0.96D, 0.78D, 0.74D, 0.94D
    );
    public static final BiomeEnvironmentProfile BALANCED_TEMPERATE = new BiomeEnvironmentProfile(
        Archetype.BALANCED_TEMPERATE, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D
    );
    private static final int[][] HYDROLOGY_SAMPLE_OFFSETS = {
        {0, 0},
        {3, 0}, {-3, 0}, {0, 3}, {0, -3},
        {3, 3}, {3, -3}, {-3, 3}, {-3, -3},
        {7, 0}, {-7, 0}, {0, 7}, {0, -7}
    };
    private static final String CURATED_BIOME_PRESETS_RESOURCE = "/wpo_environmental_expansion/biome-archetype-presets.json";
    private static final Map<String, CuratedBiomePreset> CURATED_BIOME_PRESETS = loadCuratedBiomePresets();

    private static final Map<String, BiomeEnvironmentProfile> VANILLA_BIOME_PROFILES = Map.ofEntries(
        // Hot + Dry = Arid Drought
        Map.entry("minecraft:desert", ARID_DROUGHT),
        Map.entry("minecraft:badlands", ARID_DROUGHT),
        Map.entry("minecraft:wooded_badlands", ARID_DROUGHT),
        Map.entry("minecraft:eroded_badlands", ARID_DROUGHT),
        Map.entry("minecraft:savanna", ARID_DROUGHT),
        Map.entry("minecraft:savanna_plateau", ARID_DROUGHT),
        Map.entry("minecraft:windswept_savanna", ARID_DROUGHT),
        Map.entry("minecraft:sunflower_plains", ARID_DROUGHT),
        Map.entry("minecraft:snowy_plains", TUNDRA_STEPPE),
        Map.entry("minecraft:ice_plains", ARID_DROUGHT),
        Map.entry("minecraft:nether_wastes", VOLCANIC),
        Map.entry("minecraft:soul_sand_valley", VOLCANIC),
        Map.entry("minecraft:basalt_deltas", VOLCANIC),
        // Hot + Wet = Tropical Monsoon
        Map.entry("minecraft:jungle", TROPICAL_MONSOON),
        Map.entry("minecraft:sparse_jungle", TROPICAL_MONSOON),
        Map.entry("minecraft:bamboo_jungle", TROPICAL_MONSOON),
        Map.entry("minecraft:swamp", RIPARIAN_WETLAND),
        Map.entry("minecraft:mangrove_swamp", RIPARIAN_WETLAND),
        Map.entry("minecraft:crimson_forest", TROPICAL_MONSOON),
        Map.entry("minecraft:warped_forest", TROPICAL_MONSOON),
        // Cool + Wet = Boreal Wet
        Map.entry("minecraft:taiga", BOREAL_WET),
        Map.entry("minecraft:old_growth_taiga", BOREAL_WET),
        Map.entry("minecraft:old_growth_pine_taiga", BOREAL_WET),
        Map.entry("minecraft:old_growth_spruce_taiga", BOREAL_WET),
        Map.entry("minecraft:giant_tree_taiga", BOREAL_WET),
        Map.entry("minecraft:dark_forest", BOREAL_WET),
        // Cold + Wet = Snowmelt Alpine
        Map.entry("minecraft:grove", SNOWMELT_ALPINE),
        Map.entry("minecraft:ice_spikes", SNOWMELT_ALPINE),
        Map.entry("minecraft:snowy_taiga", SNOWMELT_ALPINE),
        Map.entry("minecraft:snowy_beach", SNOWMELT_ALPINE),
        Map.entry("minecraft:snowy_mountains", SNOWMELT_ALPINE),
        Map.entry("minecraft:mountains", SNOWMELT_ALPINE),
        Map.entry("minecraft:wooded_mountains", SNOWMELT_ALPINE),
        Map.entry("minecraft:gravelly_mountains", SNOWMELT_ALPINE),
        Map.entry("minecraft:mountain_edge", SNOWMELT_ALPINE),
        Map.entry("minecraft:frozen_river", SNOWMELT_ALPINE),
        Map.entry("minecraft:frozen_ocean", SNOWMELT_ALPINE),
        Map.entry("minecraft:deep_frozen_ocean", SNOWMELT_ALPINE),
        Map.entry("minecraft:icebergs", SNOWMELT_ALPINE),
        Map.entry("minecraft:stony_shore", COASTAL_FLUVIAL),
        Map.entry("minecraft:jagged_peaks", SNOWMELT_ALPINE),
        Map.entry("minecraft:frozen_peaks", SNOWMELT_ALPINE),
        Map.entry("minecraft:snowy_slopes", SNOWMELT_ALPINE),
        Map.entry("minecraft:windswept_hills", COLD_HIGHLANDS),
        Map.entry("minecraft:windswept_gravelly_hills", COLD_HIGHLANDS),
        Map.entry("minecraft:windswept_forest", COLD_HIGHLANDS),
        Map.entry("minecraft:stony_peaks", COLD_HIGHLANDS),
        // Temperate + Medium Wet = Balanced Temperate (default for everything else)
        Map.entry("minecraft:plains", BALANCED_TEMPERATE),
        Map.entry("minecraft:forest", BALANCED_TEMPERATE),
        Map.entry("minecraft:flower_forest", BALANCED_TEMPERATE),
        Map.entry("minecraft:birch_forest", BALANCED_TEMPERATE),
        Map.entry("minecraft:old_growth_birch_forest", BALANCED_TEMPERATE),
        Map.entry("minecraft:meadow", BALANCED_TEMPERATE),
        Map.entry("minecraft:cherry_grove", BALANCED_TEMPERATE),
        Map.entry("minecraft:beach", COASTAL_FLUVIAL),
        Map.entry("minecraft:river", COASTAL_FLUVIAL),
        Map.entry("minecraft:ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:deep_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:cold_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:deep_cold_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:lukewarm_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:deep_lukewarm_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:warm_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:deep_warm_ocean", COASTAL_FLUVIAL),
        Map.entry("minecraft:the_end", BALANCED_TEMPERATE),
        Map.entry("minecraft:end_highlands", BALANCED_TEMPERATE),
        Map.entry("minecraft:end_barrens", BALANCED_TEMPERATE),
        Map.entry("minecraft:small_end_islands", BALANCED_TEMPERATE),
        Map.entry("minecraft:dripstone_caves", BALANCED_TEMPERATE),
        Map.entry("minecraft:lush_caves", BALANCED_TEMPERATE)
    );

    public static BiomeEnvironmentProfile getVanillaProfile(ResourceLocation biomeId) {
        return VANILLA_BIOME_PROFILES.get(biomeId.toString());
    }

    public static boolean hasCuratedPreset(ResourceLocation biomeId) {
        return CURATED_BIOME_PRESETS.containsKey(biomeId.toString());
    }

    public static boolean hasVanillaProfile(ResourceLocation biomeId) {
        return VANILLA_BIOME_PROFILES.containsKey(biomeId.toString());
    }

    public static BiomeEnvironmentProfile fromSignals(Signals signals) {
        return fromSignals(null, signals);
    }

    public static BiomeEnvironmentProfile fromSignals(ResourceLocation biomeId, Signals signals) {
        ClassificationResult result = classify(biomeId, signals);
        return softenProfile(profileFor(result.archetype()), result.confidence());
    }

    public static BiomeEnvironmentProfile resolve(Signals baseSignals, Signals observedAverage, int observedSamples, int maxObservedSamples) {
        return resolve(null, baseSignals, observedAverage, observedSamples, maxObservedSamples);
    }

    public static BiomeEnvironmentProfile resolve(ResourceLocation biomeId, Signals baseSignals, Signals observedAverage, int observedSamples, int maxObservedSamples) {
        if (observedSamples <= 0 || maxObservedSamples <= 0) {
            return fromSignals(biomeId, baseSignals);
        }
        double observedWeight = Mth.clamp(observedSamples / (double) maxObservedSamples, 0.0D, 1.0D) * 0.65D;
        double baseWeight = 1.0D - observedWeight;
        return fromSignals(biomeId, new Signals(
            (baseSignals.heat() * baseWeight) + (observedAverage.heat() * observedWeight),
            (baseSignals.moisture() * baseWeight) + (observedAverage.moisture() * observedWeight),
            (baseSignals.retention() * baseWeight) + (observedAverage.retention() * observedWeight),
            (baseSignals.biomeTemp() * baseWeight) + (observedAverage.biomeTemp() * observedWeight),
            (baseSignals.biomeHumid() * baseWeight) + (observedAverage.biomeHumid() * observedWeight),
            (baseSignals.openness() * baseWeight) + (observedAverage.openness() * observedWeight),
            (baseSignals.hydrology() * baseWeight) + (observedAverage.hydrology() * observedWeight),
            (baseSignals.lowlandness() * baseWeight) + (observedAverage.lowlandness() * observedWeight),
            Math.max(baseSignals.marineExposure(), observedAverage.marineExposure())
        ));
    }

    public static BiomeEnvironmentProfile blend(List<BiomeEnvironmentProfile> profiles) {
        if (profiles.isEmpty()) {
            return BALANCED_TEMPERATE;
        }

        double rainChance = 0.0D;
        double rainIntensity = 0.0D;
        double storm = 0.0D;
        double evaporation = 0.0D;
        double absorption = 0.0D;
        double release = 0.0D;
        double snowmelt = 0.0D;
        double collector = 0.0D;
        double retention = 0.0D;
        double drought = 0.0D;

        for (BiomeEnvironmentProfile profile : profiles) {
            rainChance += profile.rainChanceMultiplier;
            rainIntensity += profile.rainIntensityMultiplier;
            storm += profile.stormMultiplier;
            evaporation += profile.evaporationMultiplier;
            absorption += profile.absorptionMultiplier;
            release += profile.releaseMultiplier;
            snowmelt += profile.snowmeltMultiplier;
            collector += profile.collectorMultiplier;
            retention += profile.retentionMultiplier;
            drought += profile.droughtSensitivity;
        }

        int count = profiles.size();
        return new BiomeEnvironmentProfile(
            Archetype.BALANCED_TEMPERATE,
            1.0D,
            rainChance / count,
            rainIntensity / count,
            storm / count,
            evaporation / count,
            absorption / count,
            release / count,
            snowmelt / count,
            collector / count,
            retention / count,
            drought / count
        );
    }

    private static ClassificationResult classify(ResourceLocation biomeId, Signals signals) {
        EnumMap<Archetype, Double> scores = seedClimatePrior(signals);
        applyTerrainRefinement(scores, signals);
        applyHydrologyOverride(scores, signals);
        applyNameKeywordBias(scores, biomeId, signals);

        if (signals.heat() > 0.72D && signals.moisture() < -0.08D && signals.retention() < -0.08D) {
            scores.put(Archetype.VOLCANIC, scores.get(Archetype.VOLCANIC) + 0.42D);
            scores.put(Archetype.ARID_DROUGHT, scores.get(Archetype.ARID_DROUGHT) - 0.08D);
            scores.put(Archetype.BALANCED_TEMPERATE, scores.get(Archetype.BALANCED_TEMPERATE) - 0.12D);
        }
        if (signals.lowlandness() < -0.2D && signals.openness() > 0.08D && signals.biomeTemp() < 0.18D && signals.biomeTemp() > -0.75D && signals.hydrology() < 0.3D) {
            scores.put(Archetype.COLD_HIGHLANDS, scores.get(Archetype.COLD_HIGHLANDS) + 0.46D);
            scores.put(Archetype.BOREAL_WET, scores.get(Archetype.BOREAL_WET) + 0.08D);
            scores.put(Archetype.SNOWMELT_ALPINE, scores.get(Archetype.SNOWMELT_ALPINE) - 0.08D);
        }
        if (signals.heat() > 0.3D && signals.moisture() < -0.12D && signals.lowlandness() < -0.18D) {
            scores.put(Archetype.DRY_HIGHLANDS, scores.get(Archetype.DRY_HIGHLANDS) + 0.32D);
            scores.put(Archetype.BALANCED_TEMPERATE, scores.get(Archetype.BALANCED_TEMPERATE) - 0.16D);
        }

        ClassificationResult best = new ClassificationResult(Archetype.BALANCED_TEMPERATE, Double.NEGATIVE_INFINITY, 0.0D);
        ClassificationResult runnerUp = best;
        for (Map.Entry<Archetype, Double> entry : scores.entrySet()) {
            ClassificationResult candidate = new ClassificationResult(entry.getKey(), entry.getValue(), 1.0D);
            if (candidate.score() > best.score()) {
                runnerUp = best;
                best = candidate;
            } else if (candidate.score() > runnerUp.score()) {
                runnerUp = candidate;
            }
        }

        double gap = Math.max(0.0D, best.score() - runnerUp.score());
        double confidence = Mth.clamp(0.36D + gap * 1.6D, 0.0D, 1.0D);
        if (signals.marineExposure() > 0.65D && best.archetype() == Archetype.COASTAL_FLUVIAL) {
            confidence = Math.min(1.0D, confidence + 0.08D);
        }
        if (signals.hydrology() > 0.55D && signals.lowlandness() > 0.2D && best.archetype() == Archetype.RIPARIAN_WETLAND) {
            confidence = Math.min(1.0D, confidence + 0.06D);
        }
        return new ClassificationResult(best.archetype(), best.score(), confidence);
    }

    private static EnumMap<Archetype, Double> seedClimatePrior(Signals signals) {
        EnumMap<Archetype, Double> scores = new EnumMap<>(Archetype.class);
        for (Archetype archetype : Archetype.values()) {
            Signals seed = seedSignalsFor(archetype);
            double distance =
                weightedSquare(signals.heat(), seed.heat(), 1.15D) +
                weightedSquare(signals.moisture(), seed.moisture(), 1.1D) +
                weightedSquare(signals.biomeTemp(), seed.biomeTemp(), 1.3D) +
                weightedSquare(signals.biomeHumid(), seed.biomeHumid(), 1.15D) +
                weightedSquare(signals.retention(), seed.retention(), 0.2D);
            scores.put(archetype, 1.0D / (1.0D + distance));
        }
        return scores;
    }

    private static void applyTerrainRefinement(EnumMap<Archetype, Double> scores, Signals signals) {
        for (Archetype archetype : Archetype.values()) {
            Signals seed = seedSignalsFor(archetype);
            double distance =
                weightedSquare(signals.retention(), seed.retention(), 0.68D) +
                weightedSquare(signals.openness(), seed.openness(), 0.22D) +
                weightedSquare(signals.hydrology(), seed.hydrology(), 0.82D) +
                weightedSquare(signals.lowlandness(), seed.lowlandness(), 0.9D) +
                weightedSquare(signals.marineExposure(), seed.marineExposure(), 1.15D);
            scores.put(archetype, scores.get(archetype) + (0.78D / (1.0D + distance)));
        }

        if (signals.lowlandness() < -0.35D) {
            scores.put(Archetype.TEMPERATE_HIGHLANDS, scores.get(Archetype.TEMPERATE_HIGHLANDS) + 0.18D);
            scores.put(Archetype.COASTAL_FLUVIAL, scores.get(Archetype.COASTAL_FLUVIAL) - 0.12D);
            scores.put(Archetype.RIPARIAN_WETLAND, scores.get(Archetype.RIPARIAN_WETLAND) - 0.14D);
            if (signals.moisture() < 0.0D || signals.heat() > 0.2D) {
                scores.put(Archetype.DRY_HIGHLANDS, scores.get(Archetype.DRY_HIGHLANDS) + 0.24D);
            }
        }
        if (signals.biomeTemp() < -0.52D) {
            if (signals.moisture() > 0.12D || signals.hydrology() > 0.18D) {
                scores.put(Archetype.SNOWMELT_ALPINE, scores.get(Archetype.SNOWMELT_ALPINE) + 0.2D);
            } else {
                scores.put(Archetype.TUNDRA_STEPPE, scores.get(Archetype.TUNDRA_STEPPE) + 0.2D);
            }
        }
        if (signals.lowlandness() < -0.18D && signals.openness() > 0.16D && signals.moisture() > -0.2D) {
            scores.put(Archetype.COLD_HIGHLANDS, scores.get(Archetype.COLD_HIGHLANDS) + 0.18D);
        }
        if (signals.heat() > 0.45D && signals.moisture() < -0.18D && signals.lowlandness() < -0.12D) {
            scores.put(Archetype.DRY_HIGHLANDS, scores.get(Archetype.DRY_HIGHLANDS) + 0.22D);
            scores.put(Archetype.BALANCED_TEMPERATE, scores.get(Archetype.BALANCED_TEMPERATE) - 0.12D);
        }
        if (signals.heat() > 0.55D && signals.moisture() < -0.05D && signals.retention() < 0.0D && signals.hydrology() < 0.1D) {
            scores.put(Archetype.VOLCANIC, scores.get(Archetype.VOLCANIC) + 0.18D);
        }
        if (signals.biomeTemp() < 0.08D && signals.biomeTemp() > -0.75D && signals.lowlandness() < -0.28D && signals.hydrology() < 0.22D) {
            scores.put(Archetype.COLD_HIGHLANDS, scores.get(Archetype.COLD_HIGHLANDS) + 0.22D);
        }
    }

    private static void applyNameKeywordBias(EnumMap<Archetype, Double> scores, ResourceLocation biomeId, Signals signals) {
        if (biomeId == null) {
            return;
        }
        String biomeKey = biomeId.toString();
        String path = biomeId.getPath().replace('-', '_');
        CuratedBiomePreset preset = CURATED_BIOME_PRESETS.get(biomeKey);
        if (preset != null) {
            nudge(scores, preset.archetype(), 0.48D + preset.confidence() * 0.62D);
        }

        addKeywordBias(scores, path, Archetype.VOLCANIC, 0.62D,
            "volcan", "caldera", "crater", "lava", "magma", "basalt", "obsidian", "blackstone", "scoria", "pumice",
            "sulfur", "smolder", "ashen", "ash", "inferno", "charred", "burnt", "searing");
        addKeywordBias(scores, path, Archetype.ARID_DROUGHT, 0.54D,
            "desert", "dunes", "dune", "badlands", "mesa", "arid", "dry", "wasteland", "wastes", "scrub", "shrubland",
            "brushland", "outback", "sands", "sandy", "savanna", "xeric", "dust", "barren");
        addKeywordBias(scores, path, Archetype.DRY_HIGHLANDS, 0.52D,
            "steppe", "upland", "uplands", "foothills", "foothill", "plateau", "ridge", "spires", "crags", "crag",
            "barrens", "highland", "highlands");
        addKeywordBias(scores, path, Archetype.COLD_HIGHLANDS, 0.52D,
            "alpine", "peak", "peaks", "mountain", "mountains", "cliff", "cliffs", "ridge", "slopes", "slope", "summit",
            "skylands", "windswept", "shield");
        addKeywordBias(scores, path, Archetype.SNOWMELT_ALPINE, 0.56D,
            "snow", "snowy", "glacial", "glacier", "ice", "icy", "frozen", "frost", "wintry", "winter", "blizzard");
        addKeywordBias(scores, path, Archetype.TUNDRA_STEPPE, 0.54D,
            "tundra", "arctic", "polar", "cold_steppe", "cold_desert", "permafrost");
        addKeywordBias(scores, path, Archetype.BOREAL_WET, 0.5D,
            "taiga", "boreal", "spruce", "fir", "pine", "redwood", "conifer", "muskeg", "mire");
        addKeywordBias(scores, path, Archetype.TROPICAL_MONSOON, 0.54D,
            "jungle", "rainforest", "tropical", "tropics", "bamboo", "mangrove", "orchid", "palm", "paradise", "canopy");
        addKeywordBias(scores, path, Archetype.RIPARIAN_WETLAND, 0.58D,
            "swamp", "marsh", "bog", "fen", "wetland", "bayou", "delta", "floodplain", "quagmire", "moor", "slough");
        addKeywordBias(scores, path, Archetype.COASTAL_FLUVIAL, 0.56D,
            "coast", "coastal", "shore", "beach", "ocean", "sea", "reef", "archipelago", "island", "isles", "cove", "lagoon");
        addKeywordBias(scores, path, Archetype.TEMPERATE_HIGHLANDS, 0.46D,
            "valley", "vale", "grove", "highland", "highlands", "meadow", "orchard", "downs", "heath");
        addKeywordBias(scores, path, Archetype.BALANCED_TEMPERATE, 0.34D,
            "forest", "woods", "woodland", "plains", "field", "prairie", "garden", "thicket", "orchard", "pasture");

        if (containsAny(path, "beach", "coast", "shore", "ocean", "sea", "reef", "archipelago", "island", "isles", "cove", "lagoon")) {
            nudge(scores, Archetype.RIPARIAN_WETLAND, -0.08D);
            nudge(scores, Archetype.BALANCED_TEMPERATE, -0.06D);
        }
        if (containsAny(path, "river", "delta", "floodplain", "wetland", "marsh", "bog", "fen", "bayou", "swamp")) {
            nudge(scores, Archetype.RIPARIAN_WETLAND, 0.24D);
            nudge(scores, Archetype.COASTAL_FLUVIAL, 0.08D);
            if (containsAny(path, "river", "delta")) {
                nudge(scores, Archetype.COASTAL_FLUVIAL, 0.16D);
            }
        }
        if (containsAny(path, "volcan", "caldera", "crater", "lava", "magma", "basalt", "obsidian", "ash", "inferno")) {
            nudge(scores, Archetype.BALANCED_TEMPERATE, -0.16D);
            nudge(scores, Archetype.RIPARIAN_WETLAND, -0.18D);
        }
        if (containsAny(path, "steppe", "shrubland", "brushland", "outback", "savanna", "desert", "badlands")) {
            nudge(scores, Archetype.BALANCED_TEMPERATE, -0.14D);
            nudge(scores, Archetype.RIPARIAN_WETLAND, -0.18D);
            if (signals.lowlandness() < -0.12D || containsAny(path, "highland", "highlands", "ridge", "plateau", "foothills", "upland")) {
                nudge(scores, Archetype.DRY_HIGHLANDS, 0.24D);
            }
        }
        if (containsAny(path, "alpine", "peak", "peaks", "mountain", "mountains", "cliff", "cliffs", "summit", "skylands", "windswept")) {
            nudge(scores, Archetype.COASTAL_FLUVIAL, -0.18D);
            nudge(scores, Archetype.RIPARIAN_WETLAND, -0.18D);
            nudge(scores, Archetype.TEMPERATE_HIGHLANDS, 0.16D);
            if (containsAny(path, "snow", "snowy", "glacial", "ice", "icy", "frozen", "frost", "wintry", "winter")) {
                nudge(scores, Archetype.SNOWMELT_ALPINE, 0.24D);
            } else if (signals.biomeTemp() < -0.2D) {
                nudge(scores, Archetype.COLD_HIGHLANDS, 0.22D);
            }
        }
        if (containsAny(path, "taiga", "boreal", "spruce", "pine", "fir", "redwood", "muskeg")) {
            nudge(scores, Archetype.ARID_DROUGHT, -0.12D);
            nudge(scores, Archetype.TROPICAL_MONSOON, -0.08D);
        }
        if (containsAny(path, "jungle", "rainforest", "tropical", "bamboo", "mangrove", "orchid", "palm")) {
            nudge(scores, Archetype.ARID_DROUGHT, -0.18D);
            nudge(scores, Archetype.TUNDRA_STEPPE, -0.12D);
        }
        if (containsAny(path, "snow", "snowy", "frozen", "glacial", "ice", "icy", "tundra", "arctic", "polar")) {
            nudge(scores, Archetype.ARID_DROUGHT, -0.16D);
            nudge(scores, Archetype.TROPICAL_MONSOON, -0.18D);
            if (containsAny(path, "steppe", "plains", "desert")) {
                nudge(scores, Archetype.TUNDRA_STEPPE, 0.2D);
            }
        }
    }

    private static void addKeywordBias(EnumMap<Archetype, Double> scores, String path, Archetype archetype, double delta, String... tokens) {
        if (containsAny(path, tokens)) {
            nudge(scores, archetype, delta);
        }
    }

    private static void nudge(EnumMap<Archetype, Double> scores, Archetype archetype, double delta) {
        scores.put(archetype, scores.get(archetype) + delta);
    }

    private static void applyHydrologyOverride(EnumMap<Archetype, Double> scores, Signals signals) {
        if (signals.marineExposure() > 0.45D && signals.hydrology() > 0.18D) {
            scores.put(Archetype.COASTAL_FLUVIAL, scores.get(Archetype.COASTAL_FLUVIAL) + 0.4D);
            scores.put(Archetype.RIPARIAN_WETLAND, scores.get(Archetype.RIPARIAN_WETLAND) - 0.1D);
        }
        if (signals.hydrology() > 0.52D && signals.lowlandness() > 0.18D && signals.marineExposure() < 0.35D) {
            scores.put(Archetype.RIPARIAN_WETLAND, scores.get(Archetype.RIPARIAN_WETLAND) + 0.42D);
            scores.put(Archetype.COASTAL_FLUVIAL, scores.get(Archetype.COASTAL_FLUVIAL) - 0.08D);
        }
        if (signals.hydrology() < -0.18D) {
            scores.put(Archetype.COASTAL_FLUVIAL, scores.get(Archetype.COASTAL_FLUVIAL) - 0.18D);
            scores.put(Archetype.RIPARIAN_WETLAND, scores.get(Archetype.RIPARIAN_WETLAND) - 0.22D);
        }
        if (signals.lowlandness() < -0.45D) {
            scores.put(Archetype.COASTAL_FLUVIAL, scores.get(Archetype.COASTAL_FLUVIAL) - 0.12D);
            scores.put(Archetype.RIPARIAN_WETLAND, scores.get(Archetype.RIPARIAN_WETLAND) - 0.16D);
        }
    }

    public static Signals seedSignalsFor(Archetype archetype) {
        return switch (archetype) {
            case ARID_DROUGHT -> new Signals(1.15D, -1.0D, -0.42D, 0.85D, -0.36D, 0.42D, -0.38D, 0.02D, 0.0D);
            case DRY_HIGHLANDS -> new Signals(0.72D, -0.56D, -0.16D, 0.58D, -0.22D, 0.46D, -0.32D, -0.58D, 0.02D);
            case VOLCANIC -> new Signals(1.2D, -0.72D, -0.62D, 0.98D, -0.12D, 0.32D, -0.58D, -0.18D, 0.0D);
            case COLD_HIGHLANDS -> new Signals(-0.42D, 0.08D, -0.02D, -0.36D, 0.18D, 0.48D, -0.04D, -0.66D, 0.04D);
            case TROPICAL_MONSOON -> new Signals(0.8D, 0.92D, 0.38D, 0.82D, 0.72D, 0.1D, 0.36D, 0.08D, 0.08D);
            case RIPARIAN_WETLAND -> new Signals(0.08D, 0.96D, 0.92D, 0.18D, 0.46D, 0.04D, 0.92D, 0.68D, 0.08D);
            case BOREAL_WET -> new Signals(-0.2D, 0.62D, 0.45D, -0.18D, 0.42D, -0.08D, 0.22D, 0.02D, 0.02D);
            case TEMPERATE_HIGHLANDS -> new Signals(-0.08D, 0.18D, 0.1D, 0.05D, 0.18D, 0.42D, 0.08D, -0.62D, 0.04D);
            case SNOWMELT_ALPINE -> new Signals(-0.92D, 0.28D, 0.02D, -0.72D, 0.12D, 0.5D, 0.1D, -0.82D, 0.02D);
            case COASTAL_FLUVIAL -> new Signals(0.12D, 0.54D, 0.34D, 0.1D, 0.36D, 0.12D, 0.72D, 0.32D, 0.92D);
            case TUNDRA_STEPPE -> new Signals(-0.74D, -0.08D, -0.1D, -0.62D, 0.02D, 0.22D, -0.08D, -0.14D, 0.0D);
            case BALANCED_TEMPERATE -> new Signals(0.0D, 0.15D, 0.2D, 0.0D, 0.18D, 0.08D, 0.12D, 0.0D, 0.0D);
        };
    }

    private static double weightedSquare(double value, double target, double weight) {
        double delta = value - target;
        return delta * delta * weight;
    }

    private static BiomeEnvironmentProfile profileFor(Archetype archetype) {
        return switch (archetype) {
            case ARID_DROUGHT -> ARID_DROUGHT;
            case DRY_HIGHLANDS -> DRY_HIGHLANDS;
            case VOLCANIC -> VOLCANIC;
            case COLD_HIGHLANDS -> COLD_HIGHLANDS;
            case TROPICAL_MONSOON -> TROPICAL_MONSOON;
            case RIPARIAN_WETLAND -> RIPARIAN_WETLAND;
            case BOREAL_WET -> BOREAL_WET;
            case TEMPERATE_HIGHLANDS -> TEMPERATE_HIGHLANDS;
            case SNOWMELT_ALPINE -> SNOWMELT_ALPINE;
            case COASTAL_FLUVIAL -> COASTAL_FLUVIAL;
            case TUNDRA_STEPPE -> TUNDRA_STEPPE;
            case BALANCED_TEMPERATE -> BALANCED_TEMPERATE;
        };
    }

    private static BiomeEnvironmentProfile softenProfile(BiomeEnvironmentProfile profile, double confidence) {
        if (profile.archetype() == Archetype.BALANCED_TEMPERATE) {
            return new BiomeEnvironmentProfile(
                profile.archetype(),
                confidence,
                profile.rainChanceMultiplier(),
                profile.rainIntensityMultiplier(),
                profile.stormMultiplier(),
                profile.evaporationMultiplier(),
                profile.absorptionMultiplier(),
                profile.releaseMultiplier(),
                profile.snowmeltMultiplier(),
                profile.collectorMultiplier(),
                profile.retentionMultiplier(),
                profile.droughtSensitivity()
            );
        }
        double influence = 0.58D + confidence * 0.42D;
        return new BiomeEnvironmentProfile(
            profile.archetype(),
            confidence,
            Mth.lerp(influence, BALANCED_TEMPERATE.rainChanceMultiplier(), profile.rainChanceMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.rainIntensityMultiplier(), profile.rainIntensityMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.stormMultiplier(), profile.stormMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.evaporationMultiplier(), profile.evaporationMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.absorptionMultiplier(), profile.absorptionMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.releaseMultiplier(), profile.releaseMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.snowmeltMultiplier(), profile.snowmeltMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.collectorMultiplier(), profile.collectorMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.retentionMultiplier(), profile.retentionMultiplier()),
            Mth.lerp(influence, BALANCED_TEMPERATE.droughtSensitivity(), profile.droughtSensitivity())
        );
    }

    public static Signals createBaseSignals(ResourceLocation biomeId, Biome biome) {
        double heat = Mth.clamp((biome.getBaseTemperature() - 0.8D) * 1.85D, -1.6D, 1.6D);
        double downfall = biomeDownfall(biome);
        boolean hasPrecipitation = biomeHasPrecipitation(biome);
        double moisture = Mth.clamp((downfall - 0.45D) * 1.7D, -1.0D, 1.0D);
        double retention = 0.0D;
        double hydrology = Mth.clamp((downfall - 0.35D) * 1.25D, -0.5D, 1.0D);
        double lowlandness = 0.0D;
        double marineExposure = 0.0D;

        if (!hasPrecipitation) {
            moisture -= 0.45D;
            hydrology -= 0.25D;
        }
        if (!biome.warmEnoughToRain(new BlockPos(0, 64, 0))) {
            heat -= 0.35D;
            moisture += 0.3D;
        }

        String path = biomeId.getPath();
        if (containsAny(path, "desert", "badlands", "eroded", "mesa")) {
            heat += 1.0D;
            moisture -= 1.05D;
            retention -= 0.55D;
            hydrology -= 0.7D;
        }
        if (containsAny(path, "volcanic", "caldera", "crater", "lava", "magma", "basalt", "blackstone", "obsidian", "ash", "pumice")) {
            heat += 0.9D;
            moisture -= 0.65D;
            retention -= 0.45D;
            hydrology -= 0.45D;
            lowlandness -= 0.15D;
        }
        if (containsAny(path, "savanna")) {
            heat += 0.55D;
            moisture -= 0.45D;
            hydrology -= 0.22D;
        }
        if (containsAny(path, "jungle", "bamboo")) {
            heat += 0.55D;
            moisture += 0.8D;
            retention += 0.2D;
            hydrology += 0.25D;
        }
        if (containsAny(path, "rainforest") || path.contains("orchid")) {
            heat += 0.4D;
            moisture += 0.65D;
            retention += 0.15D;
            hydrology += 0.28D;
        }
        if (containsAny(path, "swamp", "mangrove")) {
            heat += 0.2D;
            moisture += 1.05D;
            retention += 0.9D;
            hydrology += 0.75D;
            lowlandness += 0.42D;
        }
        if (containsAny(path, "taiga", "spruce", "pine")) {
            heat -= 0.4D;
            moisture += 0.55D;
            retention += 0.35D;
        }
        if (containsAny(path, "snow", "frozen", "ice")) {
            heat -= 1.1D;
            moisture += 0.5D;
        }
        if (containsAny(path, "peak", "mountain", "slope", "grove", "windswept", "cliffs", "highlands")) {
            heat -= 0.45D;
            retention -= 0.1D;
            lowlandness -= 0.6D;
        }
        if (containsAny(path, "plateau", "spires", "canyon", "ridge", "cliff", "cliffs", "mountain", "highland", "highlands")) {
            lowlandness -= 0.35D;
        }
        if (containsAny(path, "valley", "lowlands", "marsh", "bog", "fen", "floodplain", "river")) {
            lowlandness += 0.45D;
        }
        if (containsAny(path, "river", "beach", "ocean", "coast", "shore")) {
            moisture += 0.45D;
            retention += 0.2D;
            hydrology += 0.55D;
        }
        if (containsAny(path, "river", "marsh", "bog", "fen", "wetland", "oasis", "delta")) {
            hydrology += 0.4D;
            lowlandness += 0.25D;
        }
        if (containsAny(path, "beach", "ocean", "coast", "shore", "sea")) {
            marineExposure += 0.95D;
            lowlandness += 0.4D;
        }
        if (containsAny(path, "isles", "islands", "archipelago")) {
            marineExposure += 0.5D;
            hydrology += 0.2D;
        }
        if (containsAny(path, "forest", "plains", "meadow", "cherry")) {
            moisture += 0.12D;
            retention += 0.2D;
        }
        if ((containsAny(path, "steppe") || containsAny(path, "brushland") || path.contains("shrubland"))
            && !containsAny(path, "snow", "frozen", "ice", "cold", "wintry", "glacial", "siberian")) {
            heat += 0.32D;
            moisture -= 0.48D;
            retention -= 0.12D;
            hydrology -= 0.25D;
        }
        double biomeTemp = biomeTemperatureSignal(biome);
        double biomeHumid = biomeHumiditySignal(biome, new BlockPos(0, 72, 0), downfall, hasPrecipitation);
        Signals signals = new Signals(
            heat,
            moisture,
            retention,
            biomeTemp,
            biomeHumid,
            0.0D,
            hydrology,
            Mth.clamp(lowlandness, -1.0D, 1.0D),
            Mth.clamp(marineExposure, 0.0D, 1.0D)
        );
        CuratedBiomePreset preset = CURATED_BIOME_PRESETS.get(biomeId.toString());
        if (preset == null) {
            return signals;
        }
        double presetWeight = Mth.clamp(0.42D + preset.confidence() * 0.4D, 0.45D, 0.82D);
        return blendSignals(signals, seedSignalsFor(preset.archetype()), presetWeight);
    }

    public static Signals observeEnvironment(ServerLevel level, BlockPos groundPos, BlockState groundState) {
        double[] blockScores = observeColumnBlockScores(level, groundPos, groundState);
        Biome biome = level.getBiome(groundPos).value();
        double biomeTemp = biomeTemperatureSignal(biome);
        double downfall = biomeDownfall(biome);
        boolean hasPrecipitation = biomeHasPrecipitation(biome);
        int probeY = Mth.clamp(level.getSeaLevel() + 8, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 2);
        double biomeHumid = biomeHumiditySignal(biome, new BlockPos(groundPos.getX(), probeY, groundPos.getZ()), downfall, hasPrecipitation);
        double openness = columnOpenness(level, groundPos);
        HydrologySample hydrologySample = sampleHydrology(level, groundPos);
        double hydrology = Mth.clamp(blockScores[3] * 0.55D + hydrologySample.hydrology() * 0.85D, -1.0D, 1.0D);
        double lowlandness = Mth.clamp(terrainLowlandness(level, groundPos) * 0.55D + hydrologySample.lowlandness() * 0.45D, -1.0D, 1.0D);
        return new Signals(
            blockScores[0],
            blockScores[1],
            blockScores[2],
            biomeTemp,
            biomeHumid,
            openness,
            hydrology,
            lowlandness,
            hydrologySample.marineExposure()
        );
    }

    public static Signals observeSurface(ServerLevel level, BlockPos groundPos, BlockState groundState) {
        return observeEnvironment(level, groundPos, groundState);
    }

    public static Signals observeSurface(ServerLevel level, BlockPos pos) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        if (groundY < level.getMinBuildHeight()) {
            return Signals.ZERO;
        }
        BlockPos groundPos = new BlockPos(pos.getX(), groundY, pos.getZ());
        return observeEnvironment(level, groundPos, level.getBlockState(groundPos));
    }

    private static double[] observeColumnBlockScores(ServerLevel level, BlockPos groundPos, BlockState groundState) {
        double[] scores = new double[]{0.0D, 0.0D, 0.0D, 0.0D};
        SurfaceSample center = new SurfaceSample(groundPos, groundState, level.getBlockState(groundPos.above()));
        analyzeSample(center, 1.0D, scores);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SurfaceSample sample = sampleSurface(level, groundPos, direction, center);
            analyzeSample(sample, 0.9D, scores);
        }
        accumulateSubsurface(level, groundPos, 0.52D, scores);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int x = groundPos.getX() + direction.getStepX();
            int z = groundPos.getZ() + direction.getStepZ();
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
                continue;
            }
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (gy < level.getMinBuildHeight()) {
                continue;
            }
            BlockPos neighborGround = new BlockPos(x, gy, z);
            accumulateSubsurface(level, neighborGround, 0.4D, scores);
        }
        return scores;
    }

    private static void accumulateSubsurface(ServerLevel level, BlockPos groundPos, double baseWeight, double[] scores) {
        for (int depth = 1; depth <= 3; depth++) {
            BlockPos p = groundPos.below(depth);
            if (p.getY() < level.getMinBuildHeight()) {
                break;
            }
            BlockState st = level.getBlockState(p);
            double w = baseWeight * (1.05D - depth * 0.12D);
            applyBlockSignature(st, w, true, scores);
        }
    }

    private static double biomeTemperatureSignal(Biome biome) {
        return Mth.clamp((biome.getBaseTemperature() - 0.5D) * 2.0D, -1.2D, 1.2D);
    }

    private static double biomeHumiditySignal(Biome biome, BlockPos climateProbe, double downfall, boolean hasPrecipitation) {
        boolean snow = biome.coldEnoughToSnow(climateProbe);
        boolean rain = biome.warmEnoughToRain(climateProbe);
        double humidity = Mth.clamp((downfall - 0.5D) * 2.0D, -1.0D, 1.0D);
        if (snow) {
            humidity += 0.18D;
        }
        if (rain && hasPrecipitation) {
            humidity += 0.08D;
        }
        if (!hasPrecipitation) {
            humidity -= 0.48D;
        }
        return Mth.clamp(humidity, -1.0D, 1.0D);
    }

    private static double columnOpenness(ServerLevel level, BlockPos groundPos) {
        int x = groundPos.getX();
        int z = groundPos.getZ();
        int airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos airPos = new BlockPos(x, airY, z);
        int sky = level.getBrightness(LightLayer.SKY, airPos);
        return Mth.clamp((sky / 15.0D) * 1.4D - 0.72D, -1.0D, 1.0D);
    }

    private static double terrainLowlandness(ServerLevel level, BlockPos groundPos) {
        double normalized = (level.getSeaLevel() + 20.0D - groundPos.getY()) / 40.0D;
        return Mth.clamp(normalized, -1.0D, 1.0D);
    }

    private static HydrologySample sampleHydrology(ServerLevel level, BlockPos groundPos) {
        int considered = 0;
        int waterColumns = 0;
        int wetColumns = 0;
        int shorelineColumns = 0;
        int marineColumns = 0;
        double lowlandness = 0.0D;

        for (int[] offset : HYDROLOGY_SAMPLE_OFFSETS) {
            int x = groundPos.getX() + offset[0];
            int z = groundPos.getZ() + offset[1];
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
                continue;
            }
            int airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int sampleY = airY - 1;
            if (sampleY < level.getMinBuildHeight()) {
                continue;
            }

            considered++;
            BlockPos samplePos = new BlockPos(x, sampleY, z);
            BlockState groundState = level.getBlockState(samplePos);
            BlockState coverState = level.getBlockState(samplePos.above());
            String groundPath = BuiltInRegistries.BLOCK.getKey(groundState.getBlock()).getPath();
            String coverPath = BuiltInRegistries.BLOCK.getKey(coverState.getBlock()).getPath();
            boolean openWater = !groundState.getFluidState().isEmpty() || !coverState.getFluidState().isEmpty();
            boolean wet = openWater || containsAny(groundPath, "mud", "clay", "moss", "mycelium", "silt")
                || containsAny(coverPath, "seagrass", "kelp", "lily", "reeds");

            if (openWater) {
                waterColumns++;
            }
            if (wet) {
                wetColumns++;
            }
            if (Math.abs(sampleY - level.getSeaLevel()) <= 3) {
                shorelineColumns++;
            }
            if (openWater && sampleY <= level.getSeaLevel() + 2) {
                marineColumns++;
            }
            lowlandness += terrainLowlandness(level, samplePos);
        }

        if (considered <= 0) {
            double localLowland = terrainLowlandness(level, groundPos);
            return new HydrologySample(0.0D, localLowland, 0.0D);
        }

        double waterRatio = waterColumns / (double) considered;
        double wetRatio = wetColumns / (double) considered;
        double shorelineRatio = shorelineColumns / (double) considered;
        double marineRatio = marineColumns / (double) considered;
        double hydrology = Mth.clamp(waterRatio * 1.2D + wetRatio * 0.7D + shorelineRatio * 0.12D - 0.18D, -1.0D, 1.0D);
        double averagedLowland = Mth.clamp(lowlandness / considered + shorelineRatio * 0.18D, -1.0D, 1.0D);
        double marineExposure = Mth.clamp(marineRatio * 1.25D + shorelineRatio * 0.08D, 0.0D, 1.0D);
        return new HydrologySample(hydrology, averagedLowland, marineExposure);
    }

    private static SurfaceSample sampleSurface(ServerLevel level, BlockPos center, Direction direction, SurfaceSample fallback) {
        int x = center.getX() + direction.getStepX();
        int z = center.getZ() + direction.getStepZ();
        if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return fallback;
        }
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (groundY < level.getMinBuildHeight()) {
            return fallback;
        }
        BlockPos groundPos = new BlockPos(x, groundY, z);
        return new SurfaceSample(groundPos, level.getBlockState(groundPos), level.getBlockState(groundPos.above()));
    }

    private static void analyzeSample(SurfaceSample sample, double weight, double[] scores) {
        applyBlockSignature(sample.surface(), weight, true, scores);
        applyBlockSignature(sample.cover(), weight * 0.6D, false, scores);
    }

    private static void applyBlockSignature(BlockState state, double weight, boolean surface, double[] scores) {
        if (state.isAir()) {
            return;
        }

        if (!state.getFluidState().isEmpty()) {
            scores[1] += 1.15D * weight;
            scores[2] += 0.85D * weight;
            scores[3] += 1.2D * weight;
        }

        Block block = state.getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();

        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || path.contains("powder_snow")) {
            scores[0] -= 1.3D * weight;
            scores[1] += 0.55D * weight;
            scores[3] += 0.18D * weight;
            return;
        }
        if (path.contains("ice")) {
            scores[0] -= 1.45D * weight;
            scores[1] += 0.7D * weight;
            scores[3] += 0.28D * weight;
            return;
        }
        if (path.contains("red_sand") || path.contains("terracotta") || path.contains("sandstone")) {
            scores[0] += 0.95D * weight;
            scores[1] -= 1.1D * weight;
            scores[2] -= 0.65D * weight;
            scores[3] -= 0.8D * weight;
            return;
        }
        if (state.is(Blocks.SAND) || path.contains("cactus") || path.contains("dead_bush")) {
            scores[0] += 0.85D * weight;
            scores[1] -= 1.0D * weight;
            scores[2] -= 0.55D * weight;
            scores[3] -= 0.65D * weight;
            return;
        }
        if (path.contains("mud") || path.contains("mangrove") || path.contains("moss") || path.contains("mycelium")) {
            scores[1] += 1.0D * weight;
            scores[2] += 1.0D * weight;
            scores[3] += 1.05D * weight;
            if (path.contains("mangrove")) {
                scores[0] += 0.45D * weight;
            }
            return;
        }
        if (path.contains("clay") || path.contains("lily") || path.contains("seagrass") || path.contains("kelp")) {
            scores[1] += 0.85D * weight;
            scores[2] += 0.65D * weight;
            scores[3] += 0.9D * weight;
            return;
        }
        if (path.contains("jungle") || path.contains("bamboo")) {
            scores[0] += 0.55D * weight;
            scores[1] += 0.85D * weight;
            scores[2] += 0.25D * weight;
            scores[3] += 0.35D * weight;
            return;
        }
        if (path.contains("spruce") || path.contains("podzol") || path.contains("fern")) {
            scores[0] -= 0.35D * weight;
            scores[1] += 0.55D * weight;
            scores[2] += 0.35D * weight;
            scores[3] += 0.2D * weight;
            return;
        }
        if (path.contains("acacia")) {
            scores[0] += 0.45D * weight;
            scores[1] -= 0.45D * weight;
            scores[3] -= 0.12D * weight;
            return;
        }
        if (path.contains("gravel") || path.contains("stone") || path.contains("calcite")) {
            scores[1] -= 0.15D * weight;
            scores[2] -= 0.35D * weight;
            scores[3] -= 0.18D * weight;
            return;
        }
        if (path.contains("basalt") || path.contains("blackstone") || path.contains("magma") || path.contains("obsidian") || path.contains("tuff") || path.contains("netherrack") || path.contains("scoria") || path.contains("pumice")) {
            scores[0] += 0.85D * weight;
            scores[1] -= 0.6D * weight;
            scores[2] -= 0.5D * weight;
            scores[3] -= 0.4D * weight;
            return;
        }
        if (path.contains("grass") || path.contains("dirt") || path.contains("farmland") || path.contains("flower") || path.contains("petal")) {
            scores[1] += 0.15D * weight;
            scores[2] += 0.25D * weight;
            scores[3] += 0.08D * weight;
            return;
        }
        if (surface && state.is(BlockTags.LEAVES)) {
            scores[1] += 0.2D * weight;
            scores[3] += 0.08D * weight;
        }
    }

    private static double biomeDownfall(Biome biome) {
        try {
            Object settings = biome.getClass().getMethod("getModifiedClimateSettings").invoke(biome);
            Object downfall = settings.getClass().getMethod("downfall").invoke(settings);
            if (downfall instanceof Number number) {
                return Mth.clamp(number.doubleValue(), 0.0D, 1.2D);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return biome.warmEnoughToRain(new BlockPos(0, 64, 0)) ? 0.65D : 0.3D;
    }

    private static boolean biomeHasPrecipitation(Biome biome) {
        try {
            Object value = biome.getClass().getMethod("hasPrecipitation").invoke(biome);
            if (value instanceof Boolean bool) {
                return bool;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return biome.warmEnoughToRain(new BlockPos(0, 64, 0)) || biome.coldEnoughToSnow(new BlockPos(0, 64, 0));
    }

    private static Signals blendSignals(Signals base, Signals target, double targetWeight) {
        double clampedWeight = Mth.clamp(targetWeight, 0.0D, 1.0D);
        double baseWeight = 1.0D - clampedWeight;
        return new Signals(
            base.heat() * baseWeight + target.heat() * clampedWeight,
            base.moisture() * baseWeight + target.moisture() * clampedWeight,
            base.retention() * baseWeight + target.retention() * clampedWeight,
            base.biomeTemp() * baseWeight + target.biomeTemp() * clampedWeight,
            base.biomeHumid() * baseWeight + target.biomeHumid() * clampedWeight,
            base.openness() * baseWeight + target.openness() * clampedWeight,
            base.hydrology() * baseWeight + target.hydrology() * clampedWeight,
            base.lowlandness() * baseWeight + target.lowlandness() * clampedWeight,
            Mth.clamp(base.marineExposure() * baseWeight + target.marineExposure() * clampedWeight, 0.0D, 1.0D)
        );
    }

    private static Map<String, CuratedBiomePreset> loadCuratedBiomePresets() {
        try (InputStream stream = BiomeEnvironmentProfile.class.getResourceAsStream(CURATED_BIOME_PRESETS_RESOURCE)) {
            if (stream == null) {
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject biomes = root.getAsJsonObject("biomes");
                if (biomes == null) {
                    return Map.of();
                }
                Map<String, CuratedBiomePreset> presets = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : biomes.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject value = entry.getValue().getAsJsonObject();
                    JsonElement archetypeElement = value.get("archetype");
                    if (archetypeElement == null) {
                        continue;
                    }
                    try {
                        Archetype archetype = Archetype.valueOf(archetypeElement.getAsString());
                        double confidence = value.has("confidence") ? Mth.clamp(value.get("confidence").getAsDouble(), 0.0D, 1.0D) : 0.75D;
                        presets.put(entry.getKey(), new CuratedBiomePreset(archetype, confidence));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                return Collections.unmodifiableMap(presets);
            }
        } catch (Exception e) {
            EnvironmentalExpansion.LOGGER.error("Failed to load curated biome archetype presets", e);
            return Map.of();
        }
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public enum Archetype {
        ARID_DROUGHT,
        DRY_HIGHLANDS,
        VOLCANIC,
        COLD_HIGHLANDS,
        TROPICAL_MONSOON,
        RIPARIAN_WETLAND,
        BOREAL_WET,
        TEMPERATE_HIGHLANDS,
        SNOWMELT_ALPINE,
        COASTAL_FLUVIAL,
        TUNDRA_STEPPE,
        BALANCED_TEMPERATE
    }

    public record Signals(double heat, double moisture, double retention, double biomeTemp, double biomeHumid,
                          double openness, double hydrology, double lowlandness, double marineExposure) {
        public static final Signals ZERO = new Signals(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

        public Signals add(Signals other) {
            return new Signals(
                heat + other.heat,
                moisture + other.moisture,
                retention + other.retention,
                biomeTemp + other.biomeTemp,
                biomeHumid + other.biomeHumid,
                openness + other.openness,
                hydrology + other.hydrology,
                lowlandness + other.lowlandness,
                marineExposure + other.marineExposure
            );
        }

        public Signals divide(double value) {
            if (value <= 0.0D) {
                return ZERO;
            }
            return new Signals(
                heat / value,
                moisture / value,
                retention / value,
                biomeTemp / value,
                biomeHumid / value,
                openness / value,
                hydrology / value,
                lowlandness / value,
                marineExposure / value
            );
        }
    }

    private record SurfaceSample(BlockPos pos, BlockState surface, BlockState cover) {
    }

    private record HydrologySample(double hydrology, double lowlandness, double marineExposure) {
    }

    private record ClassificationResult(Archetype archetype, double score, double confidence) {
    }

    private record CuratedBiomePreset(Archetype archetype, double confidence) {
    }
}
