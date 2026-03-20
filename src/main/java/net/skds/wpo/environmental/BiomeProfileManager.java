package net.skds.wpo.environmental;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BiomeProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PROFILE_VERSION = 6;
    public static final int MAX_OBSERVED_SAMPLES = 64;
    private static final long MIN_TICKS_BETWEEN_BIOME_SAMPLES = 100L;
    private static final int SAVE_EVERY_OBSERVATIONS = 8;

    private static final int[][] SURFACE_PATCH_OFFSETS = {
        {0, 0},
        {6, 0}, {-6, 0}, {0, 6}, {0, -6},
        {11, 0}, {-11, 0}, {0, 11}, {0, -11},
        {8, 8}, {8, -8}, {-8, 8}, {-8, -8},
        {16, 0}, {-16, 0}, {0, 16}, {0, -16}
    };

    private static int loadedProfileFileVersion;

    private static final Map<ResourceLocation, StoredProfile> PROFILES = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_OBSERVED_TICK = new HashMap<>();

    private static boolean initialized;
    private static boolean dirty;
    private static int pendingObservationSaves;

    private BiomeProfileManager() {
    }

    public static synchronized void ensureInitialized(ServerLevel level) {
        if (initialized) {
            return;
        }

        load();

        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        if (loadedProfileFileVersion < PROFILE_VERSION) {
            upgradeProfilesToCurrentVersion(biomeRegistry);
            dirty = true;
        }

        boolean generatedMissing = false;
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomeRegistry.entrySet()) {
            ResourceLocation biomeId = entry.getKey().location();
            if (PROFILES.containsKey(biomeId)) {
                continue;
            }
            BiomeEnvironmentProfile vanillaProfile = BiomeEnvironmentProfile.getVanillaProfile(biomeId);
            if (vanillaProfile != null) {
                PROFILES.put(biomeId, StoredProfile.vanilla(vanillaProfile));
            } else if (BiomeEnvironmentProfile.hasCuratedPreset(biomeId)) {
                PROFILES.put(biomeId, StoredProfile.curated(BiomeEnvironmentProfile.createBaseSignals(biomeId, entry.getValue())));
            } else {
                PROFILES.put(biomeId, StoredProfile.generated(BiomeEnvironmentProfile.createBaseSignals(biomeId, entry.getValue())));
                generatedMissing = true;
            }
        }

        initialized = true;
        if (dirty || generatedMissing) {
            save();
        }
    }

    private static void upgradeProfilesToCurrentVersion(Registry<Biome> biomeRegistry) {
        Map<ResourceLocation, StoredProfile> next = new HashMap<>();
        for (Map.Entry<ResourceLocation, StoredProfile> entry : PROFILES.entrySet()) {
            ResourceLocation id = entry.getKey();
            StoredProfile sp = entry.getValue();
            if (sp.isVanilla()) {
                BiomeEnvironmentProfile vanilla = BiomeEnvironmentProfile.getVanillaProfile(id);
                if (vanilla != null) {
                    next.put(id, StoredProfile.vanilla(vanilla));
                } else {
                    next.put(id, sp);
                }
                continue;
            }
            Optional<Holder.Reference<Biome>> holder = biomeRegistry.getHolder(ResourceKey.create(Registries.BIOME, id));
            if (holder.isEmpty()) {
                next.put(id, sp);
                continue;
            }
            BiomeEnvironmentProfile.Signals newBase = BiomeEnvironmentProfile.createBaseSignals(id, holder.get().value());
            if (BiomeEnvironmentProfile.hasCuratedPreset(id)) {
                next.put(id, StoredProfile.curated(newBase));
            } else {
                next.put(id, StoredProfile.generated(newBase));
            }
        }
        PROFILES.clear();
        PROFILES.putAll(next);
        loadedProfileFileVersion = PROFILE_VERSION;
    }

    public static synchronized BiomeEnvironmentProfile getProfile(ServerLevel level, BlockPos pos) {
        ensureInitialized(level);
        ResourceLocation biomeId = getBiomeId(level, pos);
        StoredProfile profile = PROFILES.get(biomeId);
        if (profile == null) {
            Biome biome = level.getBiome(pos).value();
            BiomeEnvironmentProfile vanillaProfile = BiomeEnvironmentProfile.getVanillaProfile(biomeId);
            if (vanillaProfile != null) {
                profile = StoredProfile.vanilla(vanillaProfile);
            } else if (BiomeEnvironmentProfile.hasCuratedPreset(biomeId)) {
                profile = StoredProfile.curated(BiomeEnvironmentProfile.createBaseSignals(biomeId, biome));
            } else {
                profile = StoredProfile.generated(BiomeEnvironmentProfile.createBaseSignals(biomeId, biome));
            }
            PROFILES.put(biomeId, profile);
            dirty = true;
            save();
        }
        return profile.resolve(biomeId);
    }

    public static synchronized void observe(ServerLevel level, BlockPos groundPos, BlockState groundState) {
        ensureInitialized(level);
        ResourceLocation biomeId = getBiomeId(level, groundPos);
        StoredProfile profile = PROFILES.get(biomeId);
        if (profile == null || profile.isSamplingLocked() || profile.observedSamples >= MAX_OBSERVED_SAMPLES) {
            return;
        }

        long now = level.getGameTime();
        long lastObserved = LAST_OBSERVED_TICK.getOrDefault(biomeId, Long.MIN_VALUE);
        if (lastObserved != Long.MIN_VALUE && now - lastObserved < MIN_TICKS_BETWEEN_BIOME_SAMPLES) {
            return;
        }

        profile.observe(buildPatchObservation(level, groundPos, groundState));
        LAST_OBSERVED_TICK.put(biomeId, now);
        dirty = true;
        pendingObservationSaves++;
        if (profile.observedSamples >= MAX_OBSERVED_SAMPLES || pendingObservationSaves >= SAVE_EVERY_OBSERVATIONS) {
            save();
        }
    }

    /**
     * Extra wide patch sample for automated biome scouting; ignores the per-biome tick throttle
     * so several snapshots can land during one visit.
     */
    public static synchronized void scoutObserve(ServerLevel level, int blockX, int blockZ) {
        ensureInitialized(level);
        BlockPos groundPos = resolveColumnGround(level, blockX, blockZ);
        if (groundPos == null) {
            return;
        }
        ResourceLocation biomeId = getBiomeId(level, groundPos);
        StoredProfile profile = PROFILES.get(biomeId);
        if (profile == null || profile.isSamplingLocked() || profile.observedSamples >= MAX_OBSERVED_SAMPLES) {
            return;
        }
        BlockState groundState = level.getBlockState(groundPos);
        profile.observe(buildPatchObservation(level, groundPos, groundState));
        LAST_OBSERVED_TICK.put(biomeId, level.getGameTime());
        dirty = true;
        pendingObservationSaves++;
        if (profile.observedSamples >= MAX_OBSERVED_SAMPLES || pendingObservationSaves >= SAVE_EVERY_OBSERVATIONS) {
            save();
        }
    }

    public static synchronized boolean hasInPersonSamplingData(ServerLevel level, ResourceLocation biomeId) {
        ensureInitialized(level);
        StoredProfile profile = PROFILES.get(biomeId);
        return profile != null && profile.hasInPersonSamplingData();
    }

    public static synchronized boolean hasSamplingCoverage(ServerLevel level, ResourceLocation biomeId) {
        ensureInitialized(level);
        StoredProfile profile = PROFILES.get(biomeId);
        return profile != null && profile.hasSamplingCoverage();
    }

    public static synchronized BiomeEnvironmentProfile getActiveProfile(ServerLevel level) {
        ensureInitialized(level);
        if (level.players().isEmpty()) {
            return BiomeEnvironmentProfile.BALANCED_TEMPERATE;
        }
        List<BiomeEnvironmentProfile> profiles = new ArrayList<>();
        for (Player player : level.players()) {
            profiles.add(getProfile(level, player.blockPosition()));
        }
        return BiomeEnvironmentProfile.blend(profiles);
    }

    public static synchronized void reset() {
        if (dirty) {
            save();
        }
        PROFILES.clear();
        LAST_OBSERVED_TICK.clear();
        initialized = false;
        dirty = false;
        pendingObservationSaves = 0;
        loadedProfileFileVersion = PROFILE_VERSION;
    }

    public static synchronized int clearModdedSamplingData() {
        load();
        int cleared = 0;
        for (Map.Entry<ResourceLocation, StoredProfile> entry : PROFILES.entrySet()) {
            StoredProfile profile = entry.getValue();
            if (profile.clearObservedData()) {
                cleared++;
            }
        }
        dirty = true;
        save();
        return cleared;
    }

    private static ResourceLocation getBiomeId(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(ResourceKey::location).orElse(new ResourceLocation("minecraft", "unknown"));
    }

    private static BlockPos resolveColumnGround(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return null;
        }
        int airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos airPos = new BlockPos(x, airY, z);
        BlockPos groundPos = airPos.below();
        if (groundPos.getY() < level.getMinBuildHeight()) {
            return null;
        }
        return groundPos;
    }

    private static BiomeEnvironmentProfile.Signals buildPatchObservation(ServerLevel level, BlockPos centerGround, BlockState centerState) {
        ResourceLocation targetBiome = getBiomeId(level, centerGround);
        double heat = 0.0D;
        double moisture = 0.0D;
        double retention = 0.0D;
        double biomeTemp = 0.0D;
        double biomeHumid = 0.0D;
        double openness = 0.0D;
        double hydrology = 0.0D;
        double lowlandness = 0.0D;
        double marineExposure = 0.0D;
        int count = 0;
        int cx = centerGround.getX();
        int cz = centerGround.getZ();
        for (int[] offset : SURFACE_PATCH_OFFSETS) {
            BlockPos groundPos = resolveColumnGround(level, cx + offset[0], cz + offset[1]);
            if (groundPos == null) {
                continue;
            }
            if (!getBiomeId(level, groundPos).equals(targetBiome)) {
                continue;
            }
            BlockState state = level.getBlockState(groundPos);
            BiomeEnvironmentProfile.Signals sample = BiomeEnvironmentProfile.observeEnvironment(level, groundPos, state);
            heat += sample.heat();
            moisture += sample.moisture();
            retention += sample.retention();
            biomeTemp += sample.biomeTemp();
            biomeHumid += sample.biomeHumid();
            openness += sample.openness();
            hydrology += sample.hydrology();
            lowlandness += sample.lowlandness();
            marineExposure += sample.marineExposure();
            count++;
        }
        if (count == 0) {
            return BiomeEnvironmentProfile.observeEnvironment(level, centerGround, centerState);
        }
        return new BiomeEnvironmentProfile.Signals(
            heat / count,
            moisture / count,
            retention / count,
            biomeTemp / count,
            biomeHumid / count,
            openness / count,
            hydrology / count,
            lowlandness / count,
            marineExposure / count
        );
    }

    private static Path getConfigDir() {
        return Paths.get(System.getProperty("user.dir"), "config", EnvironmentalExpansion.MOD_ID);
    }

    private static Path getProfilesPath() {
        return getConfigDir().resolve("biome-profiles.json");
    }

    private static void load() {
        PROFILES.clear();
        LAST_OBSERVED_TICK.clear();
        pendingObservationSaves = 0;
        dirty = false;
        loadedProfileFileVersion = PROFILE_VERSION;

        try {
            Files.createDirectories(getConfigDir());
        } catch (IOException e) {
            EnvironmentalExpansion.LOGGER.error("Failed to create biome profile config directory", e);
            return;
        }

        Path path = getProfilesPath();
        if (!Files.exists(path)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ProfileFile file = GSON.fromJson(reader, ProfileFile.class);
            if (file == null || file.biomes == null) {
                return;
            }
            loadedProfileFileVersion = file.version > 0 ? file.version : 1;
            for (Map.Entry<String, StoredProfileData> entry : file.biomes.entrySet()) {
                ResourceLocation biomeId = ResourceLocation.tryParse(entry.getKey());
                if (biomeId == null || entry.getValue() == null) {
                    continue;
                }
                PROFILES.put(biomeId, StoredProfile.fromData(entry.getValue()));
            }
        } catch (Exception e) {
            EnvironmentalExpansion.LOGGER.error("Failed to load biome profiles", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(getConfigDir());
            ProfileFile file = new ProfileFile();
            file.version = PROFILE_VERSION;
            file.maxObservedSamples = MAX_OBSERVED_SAMPLES;
            file.biomes = new LinkedHashMap<>();
            PROFILES.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> file.biomes.put(entry.getKey().toString(), entry.getValue().toData()));

            try (BufferedWriter writer = Files.newBufferedWriter(getProfilesPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(file, writer);
            }
            dirty = false;
            pendingObservationSaves = 0;
        } catch (IOException e) {
            EnvironmentalExpansion.LOGGER.error("Failed to save biome profiles", e);
        }
    }

    private static final class StoredProfile {
        private final BiomeEnvironmentProfile.Signals baseSignals;
        private BiomeEnvironmentProfile.Signals observedTotals;
        private int observedSamples;
        private final boolean isVanilla;
        private final boolean isCurated;

        private StoredProfile(BiomeEnvironmentProfile.Signals baseSignals, BiomeEnvironmentProfile.Signals observedTotals, int observedSamples, boolean isVanilla, boolean isCurated) {
            this.baseSignals = baseSignals;
            this.observedTotals = observedTotals;
            this.observedSamples = observedSamples;
            this.isVanilla = isVanilla;
            this.isCurated = isCurated;
        }

        private static StoredProfile generated(BiomeEnvironmentProfile.Signals baseSignals) {
            return new StoredProfile(baseSignals, BiomeEnvironmentProfile.Signals.ZERO, 0, false, false);
        }

        private static StoredProfile curated(BiomeEnvironmentProfile.Signals baseSignals) {
            return new StoredProfile(baseSignals, BiomeEnvironmentProfile.Signals.ZERO, 0, false, true);
        }

        private static StoredProfile vanilla(BiomeEnvironmentProfile profile) {
            return new StoredProfile(BiomeEnvironmentProfile.seedSignalsFor(profile.archetype()), BiomeEnvironmentProfile.Signals.ZERO, 0, true, false);
        }

        private StoredProfile withRebasedNonVanilla(BiomeEnvironmentProfile.Signals newBase) {
            if (isSamplingLocked()) {
                return this;
            }
            int n = observedSamples;
            if (n <= 0) {
                return new StoredProfile(newBase, BiomeEnvironmentProfile.Signals.ZERO, 0, false, false);
            }
            BiomeEnvironmentProfile.Signals ot = observedTotals;
            BiomeEnvironmentProfile.Signals nt = new BiomeEnvironmentProfile.Signals(
                ot.heat(),
                ot.moisture(),
                ot.retention(),
                newBase.biomeTemp() * n,
                newBase.biomeHumid() * n,
                ot.openness(),
                newBase.hydrology() * n,
                newBase.lowlandness() * n,
                newBase.marineExposure() * n
            );
            return new StoredProfile(newBase, nt, n, false, false);
        }

        private static StoredProfile fromData(StoredProfileData data) {
            boolean isVanilla = data.isVanilla;
            boolean isCurated = data.isCurated;
            return new StoredProfile(
                new BiomeEnvironmentProfile.Signals(
                    data.baseHeat,
                    data.baseMoisture,
                    data.baseRetention,
                    data.baseBiomeTemp,
                    data.baseBiomeHumid,
                    data.baseOpenness,
                    data.baseHydrology,
                    data.baseLowlandness,
                    data.baseMarineExposure
                ),
                new BiomeEnvironmentProfile.Signals(
                    data.observedHeatTotal,
                    data.observedMoistureTotal,
                    data.observedRetentionTotal,
                    data.observedBiomeTempTotal,
                    data.observedBiomeHumidTotal,
                    data.observedOpennessTotal,
                    data.observedHydrologyTotal,
                    data.observedLowlandnessTotal,
                    data.observedMarineExposureTotal
                ),
                Math.max(0, Math.min(MAX_OBSERVED_SAMPLES, data.observedSamples)),
                isVanilla,
                isCurated
            );
        }

        private void observe(BiomeEnvironmentProfile.Signals observation) {
            if (isSamplingLocked() || observedSamples >= MAX_OBSERVED_SAMPLES) {
                return;
            }
            observedTotals = observedTotals.add(observation);
            observedSamples++;
        }

        private boolean hasInPersonSamplingData() {
            return !isSamplingLocked() && observedSamples > 0;
        }

        private boolean hasSamplingCoverage() {
            return isCurated || hasInPersonSamplingData();
        }

        private boolean isVanilla() {
            return isVanilla;
        }

        private boolean isSamplingLocked() {
            return isVanilla || isCurated;
        }

        private boolean clearObservedData() {
            if (observedSamples <= 0) {
                return false;
            }
            observedTotals = BiomeEnvironmentProfile.Signals.ZERO;
            observedSamples = 0;
            return true;
        }

        private BiomeEnvironmentProfile resolve(ResourceLocation biomeId) {
            if (isSamplingLocked()) {
                return BiomeEnvironmentProfile.resolve(biomeId, baseSignals, getObservedAverage(), 0, 1);
            }
            return BiomeEnvironmentProfile.resolve(biomeId, baseSignals, getObservedAverage(), observedSamples, MAX_OBSERVED_SAMPLES);
        }

        private BiomeEnvironmentProfile.Signals getObservedAverage() {
            return observedTotals.divide(Math.max(1, observedSamples));
        }

        private StoredProfileData toData() {
            StoredProfileData data = new StoredProfileData();
            data.baseHeat = baseSignals.heat();
            data.baseMoisture = baseSignals.moisture();
            data.baseRetention = baseSignals.retention();
            data.baseBiomeTemp = baseSignals.biomeTemp();
            data.baseBiomeHumid = baseSignals.biomeHumid();
            data.baseOpenness = baseSignals.openness();
            data.baseHydrology = baseSignals.hydrology();
            data.baseLowlandness = baseSignals.lowlandness();
            data.baseMarineExposure = baseSignals.marineExposure();
            data.observedHeatTotal = observedTotals.heat();
            data.observedMoistureTotal = observedTotals.moisture();
            data.observedRetentionTotal = observedTotals.retention();
            data.observedBiomeTempTotal = observedTotals.biomeTemp();
            data.observedBiomeHumidTotal = observedTotals.biomeHumid();
            data.observedOpennessTotal = observedTotals.openness();
            data.observedHydrologyTotal = observedTotals.hydrology();
            data.observedLowlandnessTotal = observedTotals.lowlandness();
            data.observedMarineExposureTotal = observedTotals.marineExposure();
            data.observedSamples = observedSamples;
            data.isVanilla = isVanilla;
            data.isCurated = isCurated;
            return data;
        }
    }

    private static final class ProfileFile {
        private int version;
        private int maxObservedSamples;
        private Map<String, StoredProfileData> biomes;
    }

    private static final class StoredProfileData {
        private double baseHeat;
        private double baseMoisture;
        private double baseRetention;
        private double baseBiomeTemp;
        private double baseBiomeHumid;
        private double baseOpenness;
        private double baseHydrology;
        private double baseLowlandness;
        private double baseMarineExposure;
        private double observedHeatTotal;
        private double observedMoistureTotal;
        private double observedRetentionTotal;
        private double observedBiomeTempTotal;
        private double observedBiomeHumidTotal;
        private double observedOpennessTotal;
        private double observedHydrologyTotal;
        private double observedLowlandnessTotal;
        private double observedMarineExposureTotal;
        private int observedSamples;
        private boolean isVanilla;
        private boolean isCurated;
    }
}
