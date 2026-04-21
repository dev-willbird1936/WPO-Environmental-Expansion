package net.skds.wpo.environmental;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.skds.wpo.api.WPOFluidAccess;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class EnvironmentalBenchmarkManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Path OUTPUT_DIR = Paths.get(System.getProperty("user.dir"), "config", EnvironmentalExpansion.MOD_ID, "benchmarks");
    private static final int LANE_SPACING = 8;
    private static final int LANE_RADIUS = 1;
    private static final int LANE_CLEAR_RADIUS = 2;
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int CHAT_PROGRESS_INTERVAL_TICKS = 200;
    private static final int WEATHER_LOCK_DURATION = 1_000_000;
    private static final int TRIAL_DURATION_TICKS = 600;
    private static final int PREPARATION_TIMEOUT_TICKS = 10;
    private static final int PREPARATION_STABLE_TICKS = 2;
    private static final int MAX_PREPARATION_RETRIES = 2;

    private static final Deque<RunRequest> QUEUE = new ArrayDeque<>();
    private static ActiveRun activeRun;
    private static Path lastOutputPath;
    private static int sessionTotalTrials;
    private static int sessionCompletedTrials;

    private EnvironmentalBenchmarkManager() {
    }

    static boolean hasOverride(ServerLevel level, BlockPos pos) {
        return activeRun != null && activeRun.appliesTo(level, pos);
    }

    static BiomeEnvironmentProfile getForcedProfile(ServerLevel level, BlockPos pos) {
        if (activeRun == null || !activeRun.appliesTo(level, pos)) {
            return null;
        }
        return activeRun.activeProfile();
    }

    static Float getForcedBiomeTemperature(ServerLevel level, BlockPos pos) {
        if (activeRun == null || !activeRun.appliesTo(level, pos)) {
            return null;
        }
        return activeRun.activeBiomeTemperature();
    }

    public static String replaceQueue(ServerPlayer player, List<Suite> suites) {
        Objects.requireNonNull(player, "player");
        stopActive(player.serverLevel(), "replaced");
        QUEUE.clear();
        return queueInternal(player, suites, true);
    }

    public static String queue(ServerPlayer player, List<Suite> suites) {
        Objects.requireNonNull(player, "player");
        return queueInternal(player, suites, false);
    }

    public static String stop(ServerLevel level) {
        boolean stoppedActive = stopActive(level, "stopped");
        int queued = QUEUE.size();
        QUEUE.clear();
        resetSessionProgressIfIdle();
        if (!stoppedActive && queued == 0) {
            return "No environmental benchmark is running or queued.";
        }
        if (stoppedActive && queued > 0) {
            return "Stopped the active environmental benchmark and cleared " + queued + " queued suite(s).";
        }
        if (stoppedActive) {
            return "Stopped the active environmental benchmark.";
        }
        return "Cleared " + queued + " queued environmental benchmark suite(s).";
    }

    public static String status(ServerLevel level) {
        if (activeRun == null && QUEUE.isEmpty()) {
            return lastOutputPath == null
                ? "No environmental benchmark is running. Queue is empty."
                : "No environmental benchmark is running. Last output: " + lastOutputPath;
        }

        StringBuilder builder = new StringBuilder();
        if (activeRun != null) {
            builder.append("Running ")
                .append(activeRun.request.suite.id)
                .append(" trial ")
                .append(activeRun.trialIndex + 1)
                .append("/")
                .append(activeRun.trials.size());
            if (activeRun.trialState != null) {
                builder.append(" [").append(activeRun.trialState.definition.id).append("]");
            }
        } else {
            builder.append("No active benchmark.");
        }

        if (!QUEUE.isEmpty()) {
            builder.append(" Queue: ");
            boolean first = true;
            for (RunRequest request : QUEUE) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder.append(request.suite.id);
            }
        }
        if (lastOutputPath != null) {
            builder.append(" Last output: ").append(lastOutputPath);
        }
        return builder.toString();
    }

    public static void tick(ServerLevel level) {
        try {
            if (activeRun != null) {
                if (activeRun.dimension() != level.dimension()) {
                    return;
                }
                if (activeRun.tick(level)) {
                    activeRun = null;
                }
            }

            if (activeRun == null) {
                RunRequest next = QUEUE.peekFirst();
                if (next != null && next.dimension.equals(level.dimension())) {
                    QUEUE.removeFirst();
                    activeRun = new ActiveRun(next);
                    activeRun.start(level);
                }
            }
        } catch (Exception e) {
            EnvironmentalExpansion.LOGGER.error("Environmental benchmark runner crashed", e);
            if (activeRun != null && activeRun.dimension().equals(level.dimension())) {
                activeRun.abort(level, "error", e.getMessage());
                activeRun = null;
            }
        }
    }

    public static void onAgricultureGrowthBoost(ServerLevel level, BlockPos farmlandPos) {
        if (activeRun != null && activeRun.dimension().equals(level.dimension())) {
            activeRun.recordCropBoost(farmlandPos);
        }
    }

    public static void onLevelUnload(ServerLevel level) {
        if (activeRun != null && activeRun.dimension().equals(level.dimension())) {
            activeRun = null;
        }
        QUEUE.removeIf(request -> request.dimension.equals(level.dimension()));
        resetSessionProgressIfIdle();
    }

    private static void sendBenchmarkChat(ServerLevel level, UUID playerId, String message) {
        sendBenchmarkChat(level, playerId, message, null);
    }

    private static void sendBenchmarkChat(ServerLevel level, UUID playerId, String message, Integer percent) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        String prefix = percent == null ? "[WPO Bench] " : "[WPO Bench " + percent + "%] ";
        Component component = Component.literal(prefix + message);
        if (player != null) {
            player.sendSystemMessage(component);
            return;
        }
        EnvironmentalExpansion.LOGGER.info("{}{}", prefix, message);
    }

    private static String queueInternal(ServerPlayer player, List<Suite> suites, boolean replaced) {
        if (suites.isEmpty()) {
            return "No environmental benchmark suites were selected.";
        }
        if (activeRun == null && QUEUE.isEmpty()) {
            sessionCompletedTrials = 0;
            sessionTotalTrials = 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos anchor = player.blockPosition();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        int floorY = Mth.clamp(Math.max(surfaceY + 6, level.getSeaLevel() + 12), level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 8);
        for (Suite suite : suites) {
            QUEUE.addLast(new RunRequest(level.dimension(), anchor.immutable(), floorY, player.getUUID(), player.getScoreboardName(), suite));
            sessionTotalTrials += createTrials(suite).size();
        }
        String prefix = replaced ? "Replaced the queue and scheduled " : "Queued ";
        return prefix + suites.size() + " environmental benchmark suite(s): " + suites.stream().map(suite -> suite.id).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static boolean stopActive(ServerLevel level, String reason) {
        if (activeRun == null || !activeRun.dimension().equals(level.dimension())) {
            return false;
        }
        activeRun.abort(level, reason, null);
        activeRun = null;
        return true;
    }

    private static String prettyKind(TrialKind kind) {
        return switch (kind) {
            case EVAPORATION -> "evaporation";
            case ABSORPTION -> "absorption";
            case CONDENSATION -> "condensation";
            case FREEZE -> "freezing";
            case THAW -> "thaw";
            case AGRICULTURE_HYDRATION -> "agriculture hydration";
            case AGRICULTURE_GROWTH -> "agriculture growth";
        };
    }

    private static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0L, (ticks + 19L) / 20L);
        if (totalSeconds < 60L) {
            return totalSeconds + "s (" + ticks + " ticks)";
        }
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s (" + ticks + " ticks)";
    }

    private static void resetSessionProgressIfIdle() {
        if (activeRun == null && QUEUE.isEmpty()) {
            sessionCompletedTrials = 0;
            sessionTotalTrials = 0;
        }
    }

    public enum Suite {
        EVAPORATION("evaporation"),
        ABSORPTION("absorption"),
        CONDENSATION("condensation"),
        FREEZE("freeze"),
        THAW("thaw"),
        AGRICULTURE_HYDRATION("agriculture_hydration"),
        AGRICULTURE_GROWTH("agriculture_growth");

        private final String id;

        Suite(String id) {
            this.id = id;
        }

        public static Suite fromId(String id) {
            return Arrays.stream(values())
                .filter(value -> value.id.equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown benchmark suite: " + id));
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(value -> value.id).toList();
        }
    }

    private enum TrialKind {
        EVAPORATION,
        ABSORPTION,
        CONDENSATION,
        FREEZE,
        THAW,
        AGRICULTURE_HYDRATION,
        AGRICULTURE_GROWTH
    }

    private enum WeatherSetting {
        CLEAR,
        RAIN,
        THUNDER
    }

    private record RunRequest(
        net.minecraft.resources.ResourceKey<Level> dimension,
        BlockPos anchor,
        int floorY,
        UUID playerId,
        String requestedBy,
        Suite suite
    ) {
    }

    private record TrialDefinition(
        String id,
        TrialKind kind,
        BiomeEnvironmentProfile.Archetype archetype,
        float biomeTemperature,
        SeasonManager.SubSeason subSeason,
        long timeOfDay,
        WeatherSetting weather,
        double multiplier,
        int timeoutTicks,
        int fixedDurationTicks
    ) {
    }

    private record LanePlan(
        String name,
        BlockState supportState,
        BlockState topState,
        int surfaceWater,
        int absorbed,
        int surfaceIce
    ) {
    }

    private record ArenaLane(
        String name,
        int index,
        BlockPos supportPos,
        LanePlan plan
    ) {
    }

    private record ArenaLayout(
        BlockPos origin,
        int floorY,
        int minX,
        int maxX,
        int minZ,
        int maxZ,
        List<ArenaLane> lanes
    ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getZ() >= minZ && pos.getZ() <= maxZ
                && pos.getY() >= floorY && pos.getY() <= floorY + 4;
        }
    }

    private record LaneDiagnostics(
        String supportBlock,
        String topBlock,
        String waterColumn,
        boolean canSeeSky,
        boolean canHoldSurfacePuddle,
        int absorptionCapacity,
        boolean nearbyHydratingWater,
        boolean nearbyHeatSource,
        double targetChancePct,
        boolean eligible,
        String reason
    ) {
    }

    private static List<TrialDefinition> createTrials(Suite suite) {
        return switch (suite) {
            case EVAPORATION -> List.of(
                evaporationTrial("arid_drought_x1", BiomeEnvironmentProfile.Archetype.ARID_DROUGHT, 2.10F, SeasonManager.SubSeason.MID_SUMMER, 6000L, 1.0D),
                evaporationTrial("arid_drought_x2", BiomeEnvironmentProfile.Archetype.ARID_DROUGHT, 2.10F, SeasonManager.SubSeason.MID_SUMMER, 6000L, 2.0D),
                evaporationTrial("arid_drought_x4", BiomeEnvironmentProfile.Archetype.ARID_DROUGHT, 2.10F, SeasonManager.SubSeason.MID_SUMMER, 6000L, 4.0D),
                evaporationTrial("arid_drought_x8", BiomeEnvironmentProfile.Archetype.ARID_DROUGHT, 2.10F, SeasonManager.SubSeason.MID_SUMMER, 6000L, 8.0D)
            );
            case ABSORPTION -> List.of(
                absorptionTrial("absorption_x1", 1.0D),
                absorptionTrial("absorption_x2", 2.0D),
                absorptionTrial("absorption_x4", 4.0D),
                absorptionTrial("absorption_x8", 8.0D)
            );
            case CONDENSATION -> List.of(
                condensationTrial("riparian_dawn_x1", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 0.95F, 1.0D, 23000L),
                condensationTrial("riparian_dawn_x2", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 0.95F, 2.0D, 23000L),
                condensationTrial("riparian_dawn_x4", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 0.95F, 4.0D, 23000L),
                condensationTrial("riparian_dawn_x8", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 0.95F, 8.0D, 23000L)
            );
            case FREEZE -> List.of(
                freezeTrial("snowmelt_alpine_x1", BiomeEnvironmentProfile.Archetype.SNOWMELT_ALPINE, -0.45F, 1.0D),
                freezeTrial("snowmelt_alpine_x2", BiomeEnvironmentProfile.Archetype.SNOWMELT_ALPINE, -0.45F, 2.0D),
                freezeTrial("snowmelt_alpine_x4", BiomeEnvironmentProfile.Archetype.SNOWMELT_ALPINE, -0.45F, 4.0D),
                freezeTrial("snowmelt_alpine_x8", BiomeEnvironmentProfile.Archetype.SNOWMELT_ALPINE, -0.45F, 8.0D)
            );
            case THAW -> List.of(
                thawTrial("riparian_wetland_x1", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 2.20F, 1.0D),
                thawTrial("riparian_wetland_x2", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 2.20F, 2.0D),
                thawTrial("riparian_wetland_x4", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 2.20F, 4.0D),
                thawTrial("riparian_wetland_x8", BiomeEnvironmentProfile.Archetype.RIPARIAN_WETLAND, 2.20F, 8.0D)
            );
            case AGRICULTURE_HYDRATION -> List.of(
                agricultureHydrationTrial("agriculture_hydration_x1", 1.0D),
                agricultureHydrationTrial("agriculture_hydration_x2", 2.0D),
                agricultureHydrationTrial("agriculture_hydration_x4", 4.0D)
            );
            case AGRICULTURE_GROWTH -> List.of(
                agricultureGrowthTrial("agriculture_growth_x1", 1.0D),
                agricultureGrowthTrial("agriculture_growth_x2", 2.0D),
                agricultureGrowthTrial("agriculture_growth_x4", 4.0D)
            );
        };
    }

    private static TrialDefinition evaporationTrial(String id, BiomeEnvironmentProfile.Archetype archetype, float biomeTemperature, SeasonManager.SubSeason subSeason, long timeOfDay, double multiplier) {
        return new TrialDefinition(id, TrialKind.EVAPORATION, archetype, biomeTemperature, subSeason, timeOfDay, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static TrialDefinition absorptionTrial(String id, double multiplier) {
        return new TrialDefinition(id, TrialKind.ABSORPTION, BiomeEnvironmentProfile.Archetype.BALANCED_TEMPERATE, 0.80F, SeasonManager.SubSeason.MID_SPRING, 6000L, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static TrialDefinition condensationTrial(String id, BiomeEnvironmentProfile.Archetype archetype, float biomeTemperature, double multiplier, long timeOfDay) {
        return new TrialDefinition(id, TrialKind.CONDENSATION, archetype, biomeTemperature, SeasonManager.SubSeason.EARLY_AUTUMN, timeOfDay, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static TrialDefinition freezeTrial(String id, BiomeEnvironmentProfile.Archetype archetype, float biomeTemperature, double multiplier) {
        return new TrialDefinition(id, TrialKind.FREEZE, archetype, biomeTemperature, SeasonManager.SubSeason.MID_WINTER, 18000L, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static TrialDefinition thawTrial(String id, BiomeEnvironmentProfile.Archetype archetype, float biomeTemperature, double multiplier) {
        return new TrialDefinition(id, TrialKind.THAW, archetype, biomeTemperature, SeasonManager.SubSeason.MID_SUMMER, 6000L, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static TrialDefinition agricultureHydrationTrial(String id, double multiplier) {
        return new TrialDefinition(id, TrialKind.AGRICULTURE_HYDRATION, BiomeEnvironmentProfile.Archetype.BALANCED_TEMPERATE, 0.80F, SeasonManager.SubSeason.MID_SUMMER, 6000L, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static TrialDefinition agricultureGrowthTrial(String id, double multiplier) {
        return new TrialDefinition(id, TrialKind.AGRICULTURE_GROWTH, BiomeEnvironmentProfile.Archetype.BALANCED_TEMPERATE, 0.80F, SeasonManager.SubSeason.MID_SUMMER, 6000L, WeatherSetting.CLEAR, multiplier, TRIAL_DURATION_TICKS, TRIAL_DURATION_TICKS);
    }

    private static List<LanePlan> createLanePlans(TrialDefinition definition) {
        return switch (definition.kind) {
            case EVAPORATION -> List.of(
                simpleLane("water_a", Blocks.STONE.defaultBlockState(), 4, 0, 0),
                simpleLane("water_b", Blocks.STONE.defaultBlockState(), 4, 0, 0),
                simpleLane("water_c", Blocks.STONE.defaultBlockState(), 4, 0, 0),
                simpleLane("water_d", Blocks.STONE.defaultBlockState(), 4, 0, 0),
                simpleLane("water_e", Blocks.STONE.defaultBlockState(), 4, 0, 0),
                simpleLane("water_f", Blocks.STONE.defaultBlockState(), 4, 0, 0)
            );
            case ABSORPTION -> List.of(
                simpleLane("mud_a", Blocks.MUD.defaultBlockState(), 4, 0, 0),
                simpleLane("mud_b", Blocks.MUD.defaultBlockState(), 4, 0, 0),
                simpleLane("mud_c", Blocks.MUD.defaultBlockState(), 4, 0, 0),
                simpleLane("mud_d", Blocks.MUD.defaultBlockState(), 4, 0, 0),
                simpleLane("mud_e", Blocks.MUD.defaultBlockState(), 4, 0, 0),
                simpleLane("mud_f", Blocks.MUD.defaultBlockState(), 4, 0, 0)
            );
            case CONDENSATION -> List.of(
                simpleLane("stone_a", Blocks.STONE.defaultBlockState(), 0, 0, 0),
                simpleLane("stone_b", Blocks.STONE.defaultBlockState(), 0, 0, 0),
                simpleLane("stone_c", Blocks.STONE.defaultBlockState(), 0, 0, 0),
                simpleLane("stone_d", Blocks.STONE.defaultBlockState(), 0, 0, 0),
                cauldronLane("water_cauldron_a", 1),
                cauldronLane("water_cauldron_b", 1)
            );
            case FREEZE -> buildSteppedIceWaterLanes(false);
            case THAW -> buildSteppedIceWaterLanes(true);
            case AGRICULTURE_HYDRATION -> List.of(
                hydrationLane("absorbed_1", 1),
                hydrationLane("absorbed_2", 2),
                hydrationLane("absorbed_3", 3),
                hydrationLane("absorbed_4", 4),
                hydrationLane("absorbed_5", 5)
            );
            case AGRICULTURE_GROWTH -> List.of(
                cropLane("crop_absorbed_1", 1),
                cropLane("crop_absorbed_2", 2),
                cropLane("crop_absorbed_3", 3),
                cropLane("crop_absorbed_4", 4),
                cropLane("crop_absorbed_5", 5)
            );
        };
    }

    private static List<LanePlan> buildSteppedIceWaterLanes(boolean thaw) {
        List<LanePlan> plans = new ArrayList<>();
        for (int level = 1; level <= 8; level++) {
            plans.add(new LanePlan((thaw ? "ice_" : "water_") + level, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), thaw ? 0 : level, 0, thaw ? level : 0));
        }
        return plans;
    }

    private static LanePlan simpleLane(String name, BlockState supportState, int surfaceWater, int absorbed, int surfaceIce) {
        return new LanePlan(name, supportState, Blocks.AIR.defaultBlockState(), surfaceWater, absorbed, surfaceIce);
    }

    private static LanePlan cauldronLane(String name, int level) {
        return new LanePlan(name, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, Mth.clamp(level, 1, 3)), Blocks.AIR.defaultBlockState(), 0, 0, 0);
    }

    private static LanePlan hydrationLane(String name, int absorbed) {
        return new LanePlan(name, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), Blocks.AIR.defaultBlockState(), 0, absorbed, 0);
    }

    private static LanePlan cropLane(String name, int absorbed) {
        return new LanePlan(name, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 0), 0, absorbed, 0);
    }

    private static void resetLaneArea(ServerLevel level, EnvironmentalSavedData data, BlockPos center) {
        int topY = level.getMaxBuildHeight() - 1;
        for (int dx = -LANE_RADIUS; dx <= LANE_RADIUS; dx++) {
            for (int dz = -LANE_RADIUS; dz <= LANE_RADIUS; dz++) {
                for (int y = center.getY(); y <= topY; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    data.clearAbsorbed(pos);
                    WPOFluidAccess.setWaterAmount(level, pos, 0);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void seedLaneArea(ServerLevel level, EnvironmentalSavedData data, BlockPos center, LanePlan plan) {
        level.getChunk(center);
        for (int dx = -LANE_RADIUS; dx <= LANE_RADIUS; dx++) {
            for (int dz = -LANE_RADIUS; dz <= LANE_RADIUS; dz++) {
                BlockPos floorPos = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (dx == 0 && dz == 0) {
                    level.setBlock(floorPos, plan.supportState, 3);
                } else {
                    level.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);
                }
                BlockPos topPos = floorPos.above();
                if (dx == 0 && dz == 0) {
                    if (plan.surfaceWater > 0) {
                        WPOFluidAccess.setWaterAmount(level, topPos, plan.surfaceWater);
                        WPOFluidAccess.wakeWater(level, topPos);
                    } else if (plan.surfaceIce > 0) {
                        level.setBlock(topPos, EnvironmentalContent.SURFACE_ICE.get().defaultBlockState().setValue(BlockStateProperties.LAYERS, plan.surfaceIce), 3);
                    } else if (!plan.topState.isAir()) {
                        level.setBlock(topPos, plan.topState, 3);
                    } else {
                        level.setBlock(topPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                } else {
                    level.setBlock(topPos, Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        if (plan.absorbed > 0) {
            data.setAbsorbed(center, plan.absorbed);
        }
    }

    private static long absoluteDayTime(SeasonManager.SubSeason subSeason, long timeOfDay) {
        long phaseLength = SeasonManager.getPhaseLengthDays();
        long subSeasonIndex = ((long) subSeason.season().ordinal() * 3L) + subSeason.phase().ordinal();
        return (subSeasonIndex * phaseLength * 24000L) + Math.floorMod(timeOfDay, 24000L);
    }

    private static int captureLaneWater(ServerLevel level, BlockPos supportPos) {
        return WPOFluidAccess.getWaterAmount(level, supportPos)
            + WPOFluidAccess.getWaterAmount(level, supportPos.above())
            + WPOFluidAccess.getWaterAmount(level, supportPos.above(2));
    }

    private static int captureLaneIce(ServerLevel level, BlockPos supportPos) {
        int topIce = EnvironmentalTicker.getSurfaceIceLevels(level.getBlockState(supportPos.above()));
        if (topIce > 0) {
            return topIce;
        }
        return EnvironmentalTicker.getSurfaceIceLevels(level.getBlockState(supportPos));
    }

    private static JsonObject posJson(BlockPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    private static JsonObject stateJson(LaneState state) {
        JsonObject json = new JsonObject();
        json.addProperty("surfaceWater", state.surfaceWater);
        json.addProperty("absorbedWater", state.absorbedWater);
        json.addProperty("surfaceIce", state.surfaceIceLevels);
        json.addProperty("snowLayers", state.snowLayers);
        json.addProperty("farmlandMoisture", state.farmlandMoisture);
        json.addProperty("cropAge", state.cropAge);
        json.addProperty("cauldronLevel", state.cauldronLevel);
        return json;
    }

    private static JsonObject diagnosticsJson(LaneDiagnostics diagnostics) {
        JsonObject json = new JsonObject();
        json.addProperty("supportBlock", diagnostics.supportBlock);
        json.addProperty("topBlock", diagnostics.topBlock);
        json.addProperty("waterColumn", diagnostics.waterColumn);
        json.addProperty("canSeeSky", diagnostics.canSeeSky);
        json.addProperty("canHoldSurfacePuddle", diagnostics.canHoldSurfacePuddle);
        json.addProperty("absorptionCapacity", diagnostics.absorptionCapacity);
        json.addProperty("nearbyHydratingWater", diagnostics.nearbyHydratingWater);
        json.addProperty("nearbyHeatSource", diagnostics.nearbyHeatSource);
        json.addProperty("targetChancePct", diagnostics.targetChancePct);
        json.addProperty("eligible", diagnostics.eligible);
        json.addProperty("reason", diagnostics.reason);
        return json;
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static double benchmarkTargetChancePct(TrialDefinition definition, EnvironmentalTicker.ColumnMetrics metrics) {
        return switch (definition.kind) {
            case EVAPORATION -> EnvironmentalConfig.COMMON.evaporationChance.get()
                * metrics.evaporationMultiplier()
                * EnvironmentalConfig.COMMON.evaporationMultiplierOverride.get()
                * 100.0D;
            case ABSORPTION -> EnvironmentalConfig.COMMON.absorptionChance.get()
                * metrics.absorptionMultiplier()
                * EnvironmentalConfig.COMMON.absorptionMultiplierOverride.get()
                * 100.0D;
            case CONDENSATION -> metrics.condensationChancePct();
            case FREEZE -> metrics.freezingChancePct();
            case THAW -> metrics.thawChancePct();
            case AGRICULTURE_HYDRATION -> 0.0D;
            case AGRICULTURE_GROWTH -> metrics.agricultureGrowthChancePct();
        };
    }

    private static LaneDiagnostics captureDiagnostics(ServerLevel level, TrialDefinition definition, ArenaLane lane, LaneState state, EnvironmentalTicker.ColumnMetrics metrics) {
        BlockPos groundPos = lane.supportPos;
        BlockPos airPos = groundPos.above();
        BlockState groundState = level.getBlockState(groundPos);
        BlockState topState = level.getBlockState(airPos);
        BiomeEnvironmentProfile profile = BiomeEnvironmentProfile.forArchetype(definition.archetype);
        int absorptionCapacity = EnvironmentalTicker.getAbsorptionCapacityForBenchmark(groundState, profile, level);
        boolean canSeeSky = level.canSeeSky(airPos);
        boolean canHoldSurfacePuddle = EnvironmentalTicker.canHoldSurfacePuddleForBenchmark(level, groundPos, airPos, groundState);
        boolean nearbyHydratingWater = EnvironmentalTicker.hasNearbyHydratingWaterForBenchmark(level, groundPos);
        boolean nearbyHeatSource = EnvironmentalTicker.hasNearbyHeatSourceForBenchmark(level, groundPos);
        double targetChancePct = benchmarkTargetChancePct(definition, metrics);

        String waterColumn = "none";
        if (WPOFluidAccess.getWaterAmount(level, airPos.above()) > 0) {
            waterColumn = "air+1";
        } else if (WPOFluidAccess.getWaterAmount(level, airPos) > 0) {
            waterColumn = "air";
        } else if (WPOFluidAccess.getWaterAmount(level, groundPos) > 0) {
            waterColumn = "ground";
        }

        boolean eligible;
        String reason;
        switch (definition.kind) {
            case EVAPORATION -> {
                if (!canSeeSky) {
                    eligible = false;
                    reason = "no_sky";
                } else if (state.surfaceWater <= 0) {
                    eligible = false;
                    reason = "no_exposed_water";
                } else if (targetChancePct <= 0.0D) {
                    eligible = false;
                    reason = "zero_evaporation_chance";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            case ABSORPTION -> {
                if (state.surfaceWater <= 0) {
                    eligible = false;
                    reason = "no_surface_water";
                } else if (absorptionCapacity <= 0) {
                    eligible = false;
                    reason = "non_absorbent_support";
                } else if (state.absorbedWater >= absorptionCapacity) {
                    eligible = false;
                    reason = "already_at_capacity";
                } else if (targetChancePct <= 0.0D) {
                    eligible = false;
                    reason = "zero_absorption_chance";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            case CONDENSATION -> {
                if (!canSeeSky) {
                    eligible = false;
                    reason = "no_sky";
                } else if (groundState.getBlock() instanceof LayeredCauldronBlock) {
                    eligible = state.cauldronLevel >= 0 && state.cauldronLevel < 3 && targetChancePct > 0.0D;
                    reason = eligible ? "ok" : (state.cauldronLevel >= 3 ? "cauldron_full" : "zero_condensation_chance");
                } else if (!canHoldSurfacePuddle) {
                    eligible = false;
                    reason = "cannot_hold_puddle";
                } else if (targetChancePct <= 0.0D) {
                    eligible = false;
                    reason = "zero_condensation_chance";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            case FREEZE -> {
                if (state.surfaceWater <= 0) {
                    eligible = false;
                    reason = "no_surface_water";
                } else if (!canSeeSky) {
                    eligible = false;
                    reason = "no_sky";
                } else if (nearbyHeatSource) {
                    eligible = false;
                    reason = "nearby_heat_source";
                } else if (targetChancePct <= 0.0D) {
                    eligible = false;
                    reason = "zero_freeze_chance";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            case THAW -> {
                if (state.surfaceIceLevels <= 0) {
                    eligible = false;
                    reason = "no_surface_ice";
                } else if (targetChancePct <= 0.0D) {
                    eligible = false;
                    reason = "zero_thaw_chance";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            case AGRICULTURE_HYDRATION -> {
                targetChancePct = state.absorbedWater > 0 ? 100.0D : 0.0D;
                if (state.absorbedWater <= 0) {
                    eligible = false;
                    reason = "no_absorbed_water";
                } else if (state.farmlandMoisture >= 7) {
                    eligible = false;
                    reason = "already_hydrated";
                } else if (nearbyHydratingWater) {
                    eligible = false;
                    reason = "nearby_water_source";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            case AGRICULTURE_GROWTH -> {
                if (state.absorbedWater <= 0) {
                    eligible = false;
                    reason = "no_absorbed_water";
                } else if (state.cropAge < 0) {
                    eligible = false;
                    reason = "no_crop";
                } else if (state.cropAge >= 7) {
                    eligible = false;
                    reason = "crop_mature";
                } else if (targetChancePct <= 0.0D) {
                    eligible = false;
                    reason = "zero_growth_chance";
                } else {
                    eligible = true;
                    reason = "ok";
                }
            }
            default -> {
                eligible = false;
                reason = "unsupported";
            }
        }

        return new LaneDiagnostics(
            blockId(groundState),
            blockId(topState),
            waterColumn,
            canSeeSky,
            canHoldSurfacePuddle,
            absorptionCapacity,
            nearbyHydratingWater,
            nearbyHeatSource,
            targetChancePct,
            eligible,
            reason
        );
    }

    private static JsonObject metricsJson(EnvironmentalTicker.ColumnMetrics metrics) {
        JsonObject json = new JsonObject();
        json.addProperty("subSeasonIndex", metrics.subSeasonIndex());
        json.addProperty("worldDay", metrics.worldDay());
        json.addProperty("dayTime", metrics.dayTime());
        json.addProperty("seasonsEnabled", metrics.seasonsEnabled());
        json.addProperty("tropicalCycle", metrics.tropicalCycle());
        json.addProperty("tropicalPhaseIndex", metrics.tropicalPhaseIndex());
        json.addProperty("biomeId", metrics.biomeId());
        json.addProperty("biomeTemp", metrics.biomeTemp());
        json.addProperty("archetype", metrics.archetype());
        json.addProperty("surfaceWaterLevels", metrics.surfaceWaterLevels());
        json.addProperty("absorbedWater", metrics.absorbedWater());
        json.addProperty("snowLayers", metrics.snowLayers());
        json.addProperty("surfaceIceLevels", metrics.surfaceIceLevels());
        json.addProperty("farmlandMoisture", metrics.farmlandMoisture());
        json.addProperty("isRaining", metrics.isRaining());
        json.addProperty("isThundering", metrics.isThundering());
        json.addProperty("rainMultiplier", metrics.rainMultiplier());
        json.addProperty("evaporationMultiplier", metrics.evaporationMultiplier());
        json.addProperty("absorptionMultiplier", metrics.absorptionMultiplier());
        json.addProperty("releaseMultiplier", metrics.releaseMultiplier());
        json.addProperty("snowmeltMultiplier", metrics.snowmeltMultiplier());
        json.addProperty("stormMultiplier", metrics.stormMultiplier());
        json.addProperty("droughtActive", metrics.droughtActive());
        json.addProperty("absorptionEnabled", metrics.absorptionEnabled());
        json.addProperty("evaporationEnabled", metrics.evaporationEnabled());
        json.addProperty("snowmeltEnabled", metrics.snowmeltEnabled());
        json.addProperty("condensationEnabled", metrics.condensationEnabled());
        json.addProperty("surfaceIceEnabled", metrics.surfaceIceEnabled());
        json.addProperty("agricultureEnabled", metrics.agricultureEnabled());
        json.addProperty("floodsEnabled", metrics.floodsEnabled());
        json.addProperty("distantRainCatchupEnabled", metrics.distantRainCatchupEnabled());
        json.addProperty("realTempC", metrics.realTempC());
        json.addProperty("realHumidityPct", metrics.realHumidityPct());
        json.addProperty("realWindMs", metrics.realWindMs());
        json.addProperty("precipChancePct", metrics.precipChancePct());
        json.addProperty("precipMmHr", metrics.precipMmHr());
        json.addProperty("condensationChancePct", metrics.condensationChancePct());
        json.addProperty("freezingChancePct", metrics.freezingChancePct());
        json.addProperty("thawChancePct", metrics.thawChancePct());
        json.addProperty("agricultureGrowthChancePct", metrics.agricultureGrowthChancePct());
        return json;
    }

    private static final class ActiveRun {
        private final RunRequest request;
        private final List<TrialDefinition> trials;
        private final JsonArray trialResults = new JsonArray();
        private final SavedEnvironment savedEnvironment = new SavedEnvironment();
        private final Path outputPath;
        private final int completedTrialsBeforeRun;

        private TrialState trialState;
        private int trialIndex;
        private boolean progressCredited;

        private ActiveRun(RunRequest request) {
            this.request = request;
            this.trials = createTrials(request.suite);
            this.outputPath = OUTPUT_DIR.resolve(FILE_STAMP.format(LocalDateTime.now()) + "-" + request.suite.id + ".json");
            this.completedTrialsBeforeRun = sessionCompletedTrials;
        }

        private net.minecraft.resources.ResourceKey<Level> dimension() {
            return request.dimension;
        }

        private void start(ServerLevel level) {
            try {
                Files.createDirectories(OUTPUT_DIR);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create benchmark output directory", e);
            }
            savedEnvironment.capture(level);
            announce(level, 0.0D, "Starting benchmark suite " + request.suite.id + " with " + trials.size() + " trial(s).");
            beginNextTrial(level);
            EnvironmentalExpansion.LOGGER.info("Started environmental benchmark suite {}", request.suite.id);
        }

        private boolean tick(ServerLevel level) {
            if (trialState == null) {
                finish(level, "completed", "No benchmark trials were defined.");
                return true;
            }
            if (!trialState.tick(level)) {
                return false;
            }
            announce(level, trialIndex + 1.0D, trialState.completionMessage());
            trialResults.add(trialState.toJson());
            trialIndex++;
            if (trialIndex >= trials.size()) {
                finish(level, "completed", "All benchmark trials completed.");
                return true;
            }
            beginNextTrial(level);
            return false;
        }

        private void beginNextTrial(ServerLevel level) {
            savedEnvironment.restore(level);
            TrialDefinition definition = trials.get(trialIndex);
            configureTrial(level, definition);
            ArenaLayout arena = buildArena(level, request, definition);
            trialState = new TrialState(this, definition, arena, request.playerId);
            trialState.initialize(level);
            announce(level, trialIndex, "Testing " + prettyKind(definition.kind) + " trial " + (trialIndex + 1) + "/" + trials.size() + ": " + definition.id + ".");
            announce(level, trialIndex, "Waiting " + formatTicks(definition.fixedDurationTicks > 0 ? definition.fixedDurationTicks : definition.timeoutTicks) + " for " + prettyKind(definition.kind) + ".");
            EnvironmentalExpansion.LOGGER.info("Running environmental benchmark trial {} [{}]", definition.id, request.suite.id);
        }

        private void configureTrial(ServerLevel level, TrialDefinition definition) {
            EnvironmentalSavedData data = EnvironmentalSavedData.get(level);
            data.setDroughtScore(0);
            int ambientWetness = switch (definition.kind) {
                case CONDENSATION -> Math.max(1, (int) Math.round(EnvironmentalConfig.COMMON.ambientWetnessCap.get() * 0.80D));
                case EVAPORATION -> 0;
                case FREEZE -> Math.max(0, (int) Math.round(EnvironmentalConfig.COMMON.ambientWetnessCap.get() * 0.15D));
                case THAW -> Math.max(0, (int) Math.round(EnvironmentalConfig.COMMON.ambientWetnessCap.get() * 0.25D));
                default -> 0;
            };
            data.setAmbientWetness(ambientWetness, EnvironmentalConfig.COMMON.ambientWetnessCap.get());

            level.setDayTime(absoluteDayTime(definition.subSeason, definition.timeOfDay));
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(definition.kind == TrialKind.AGRICULTURE_GROWTH ? 16 : savedEnvironment.randomTickSpeed, level.getServer());
            switch (definition.weather) {
                case CLEAR -> level.setWeatherParameters(WEATHER_LOCK_DURATION, 0, false, false);
                case RAIN -> level.setWeatherParameters(0, WEATHER_LOCK_DURATION, true, false);
                case THUNDER -> level.setWeatherParameters(0, WEATHER_LOCK_DURATION, true, true);
            }

            EnvironmentalConfig.COMMON.seasons.set(true);
            EnvironmentalConfig.COMMON.tropicalSeasons.set(false);
            EnvironmentalConfig.COMMON.rainAccumulation.set(false);
            EnvironmentalConfig.COMMON.puddles.set(definition.kind != TrialKind.AGRICULTURE_HYDRATION && definition.kind != TrialKind.AGRICULTURE_GROWTH);
            EnvironmentalConfig.COMMON.distantRainCatchup.set(false);
            EnvironmentalConfig.COMMON.droughts.set(false);
            EnvironmentalConfig.COMMON.floods.set(false);
            EnvironmentalConfig.COMMON.snowmelt.set(false);
            EnvironmentalConfig.COMMON.absorption.set(definition.kind == TrialKind.ABSORPTION);
            EnvironmentalConfig.COMMON.evaporation.set(definition.kind == TrialKind.EVAPORATION);
            EnvironmentalConfig.COMMON.condensation.set(definition.kind == TrialKind.CONDENSATION);
            EnvironmentalConfig.COMMON.surfaceIce.set(definition.kind == TrialKind.FREEZE || definition.kind == TrialKind.THAW);
            EnvironmentalConfig.COMMON.agriculture.set(definition.kind == TrialKind.AGRICULTURE_HYDRATION || definition.kind == TrialKind.AGRICULTURE_GROWTH);
            EnvironmentalConfig.COMMON.releaseChance.set(0.0D);
            EnvironmentalConfig.COMMON.evaporationMultiplierOverride.set(definition.kind == TrialKind.EVAPORATION ? definition.multiplier : 1.0D);
            EnvironmentalConfig.COMMON.absorptionMultiplierOverride.set(definition.kind == TrialKind.ABSORPTION ? definition.multiplier : 1.0D);
            EnvironmentalConfig.COMMON.condensationMultiplierOverride.set(definition.kind == TrialKind.CONDENSATION ? definition.multiplier : 1.0D);
            EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.set(definition.kind == TrialKind.FREEZE || definition.kind == TrialKind.THAW ? definition.multiplier : 1.0D);
            EnvironmentalConfig.COMMON.agricultureMultiplierOverride.set(definition.kind == TrialKind.AGRICULTURE_HYDRATION || definition.kind == TrialKind.AGRICULTURE_GROWTH ? definition.multiplier : 1.0D);
        }

        private ArenaLayout buildArena(ServerLevel level, RunRequest request, TrialDefinition definition) {
            List<LanePlan> plans = createLanePlans(definition);
            int laneSpan = Math.max(0, (plans.size() - 1) * LANE_SPACING);
            int originX = request.anchor.getX() - (laneSpan / 2);
            int originZ = request.anchor.getZ();
            int minX = originX - LANE_CLEAR_RADIUS;
            int maxX = originX + laneSpan + LANE_CLEAR_RADIUS;
            int minZ = originZ - LANE_CLEAR_RADIUS;
            int maxZ = originZ + LANE_CLEAR_RADIUS;
            int floorY = request.floorY;
            EnvironmentalSavedData data = EnvironmentalSavedData.get(level);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos floorPos = new BlockPos(x, floorY - 1, z);
                    BlockPos surfacePos = new BlockPos(x, floorY, z);
                    data.clearAbsorbed(surfacePos);
                    WPOFluidAccess.setWaterAmount(level, surfacePos, 0);
                    WPOFluidAccess.setWaterAmount(level, floorPos, 0);
                    level.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(surfacePos, Blocks.STONE.defaultBlockState(), 3);
                }
            }

            List<ArenaLane> lanes = new ArrayList<>();
            for (int index = 0; index < plans.size(); index++) {
                LanePlan plan = plans.get(index);
                BlockPos supportPos = new BlockPos(originX + (index * LANE_SPACING), floorY, originZ);
                clearLaneArea(level, data, supportPos);
                placeLaneBasin(level, data, supportPos, plan);
                lanes.add(new ArenaLane(plan.name, index, supportPos, plan));
            }
            return new ArenaLayout(new BlockPos(originX, floorY, originZ), floorY, minX, maxX, minZ, maxZ, lanes);
        }

        private static void clearLaneArea(ServerLevel level, EnvironmentalSavedData data, BlockPos center) {
            int topY = level.getMaxBuildHeight() - 1;
            for (int dx = -LANE_RADIUS; dx <= LANE_RADIUS; dx++) {
                for (int dz = -LANE_RADIUS; dz <= LANE_RADIUS; dz++) {
                    for (int y = center.getY(); y <= topY; y++) {
                        BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        data.clearAbsorbed(pos);
                        WPOFluidAccess.setWaterAmount(level, pos, 0);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        private static void placeLaneBasin(ServerLevel level, EnvironmentalSavedData data, BlockPos center, LanePlan plan) {
            for (int dx = -LANE_RADIUS; dx <= LANE_RADIUS; dx++) {
                for (int dz = -LANE_RADIUS; dz <= LANE_RADIUS; dz++) {
                    BlockPos floorPos = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
                    if (dx == 0 && dz == 0) {
                        level.setBlock(floorPos, plan.supportState, 3);
                    } else {
                        level.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);
                    }
                    BlockPos topPos = floorPos.above();
                    if (dx == 0 && dz == 0) {
                        if (plan.surfaceWater > 0) {
                            WPOFluidAccess.setWaterAmount(level, topPos, plan.surfaceWater);
                            WPOFluidAccess.wakeWater(level, topPos);
                        } else if (plan.surfaceIce > 0) {
                            level.setBlock(topPos, EnvironmentalContent.SURFACE_ICE.get().defaultBlockState().setValue(BlockStateProperties.LAYERS, plan.surfaceIce), 3);
                        } else if (!plan.topState.isAir()) {
                            level.setBlock(topPos, plan.topState, 3);
                        } else {
                            level.setBlock(topPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    } else {
                        level.setBlock(topPos, Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
            if (plan.absorbed > 0) {
                data.setAbsorbed(center, plan.absorbed);
            }
        }

        private void recordCropBoost(BlockPos farmlandPos) {
            if (trialState != null) {
                trialState.recordCropBoost(farmlandPos);
            }
        }

        private boolean appliesTo(ServerLevel level, BlockPos pos) {
            return trialState != null && level.dimension().equals(request.dimension) && trialState.arena.contains(pos);
        }

        private BiomeEnvironmentProfile activeProfile() {
            return trialState == null ? null : BiomeEnvironmentProfile.forArchetype(trialState.definition.archetype);
        }

        private Float activeBiomeTemperature() {
            return trialState == null ? null : trialState.definition.biomeTemperature;
        }

        private void abort(ServerLevel level, String status, String message) {
            if (trialState != null && "running".equals(trialState.status)) {
                trialState.markStopped(level, status);
                trialResults.add(trialState.toJson());
            }
            finish(level, status, message == null ? "Benchmark aborted." : message);
        }

        private void finish(ServerLevel level, String status, String message) {
            savedEnvironment.restore(level);
            JsonObject root = new JsonObject();
            root.addProperty("suite", request.suite.id);
            root.addProperty("requestedBy", request.requestedBy);
            root.addProperty("status", status);
            root.addProperty("message", message == null ? "" : message);
            root.add("anchor", posJson(request.anchor));
            root.addProperty("floorY", request.floorY);
            root.addProperty("trialCount", trials.size());
            root.addProperty("completedTrials", trialResults.size());
            root.addProperty("generatedAt", LocalDateTime.now().toString());
            root.add("trials", trialResults);

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
                lastOutputPath = outputPath;
                if (!progressCredited) {
                    sessionCompletedTrials += trialResults.size();
                    progressCredited = true;
                }
                announce(level, trials.size(), "Benchmark suite " + request.suite.id + " finished with status " + status + ". Output: " + outputPath);
            } catch (IOException e) {
                EnvironmentalExpansion.LOGGER.error("Failed to write environmental benchmark output {}", outputPath, e);
            }
        }

        private void announce(ServerLevel level, double completedTrialProgress, String message) {
            int totalTrials = Math.max(1, sessionTotalTrials);
            int percent = Mth.clamp((int) Math.round(((completedTrialsBeforeRun + completedTrialProgress) * 100.0D) / totalTrials), 0, 100);
            sendBenchmarkChat(level, request.playerId, message, percent);
        }
    }

    private static final class TrialState {
        private final ActiveRun owner;
        private final TrialDefinition definition;
        private final ArenaLayout arena;
        private final List<LaneTracker> lanes;
        private final UUID playerId;
        private final JsonArray snapshots = new JsonArray();
        private final JsonObject configuration = new JsonObject();
        private long preparationStartedAtGameTime;
        private long startedAtGameTime;

        private long lastSnapshotElapsed = Long.MIN_VALUE;
        private long nextChatProgressTick = CHAT_PROGRESS_INTERVAL_TICKS;
        private long finishedElapsedTicks = -1L;
        private int stablePreparationTicks;
        private int reseedAttempts;
        private String status = "running";

        private TrialState(ActiveRun owner, TrialDefinition definition, ArenaLayout arena, UUID playerId) {
            this.owner = owner;
            this.definition = definition;
            this.arena = arena;
            this.lanes = arena.lanes.stream().map(LaneTracker::new).toList();
            this.playerId = playerId;
            this.startedAtGameTime = -1L;
        }

        private void initialize(ServerLevel level) {
            this.preparationStartedAtGameTime = level.getGameTime();
            configuration.addProperty("kind", definition.kind.name().toLowerCase(Locale.ROOT));
            configuration.addProperty("archetype", definition.archetype.name());
            configuration.addProperty("benchmarkBiomeTemperature", definition.biomeTemperature);
            configuration.addProperty("season", definition.subSeason.season().name());
            configuration.addProperty("phase", definition.subSeason.phase().name());
            configuration.addProperty("timeOfDay", definition.timeOfDay);
            configuration.addProperty("weather", definition.weather.name());
            configuration.addProperty("multiplier", definition.multiplier);
            configuration.addProperty("timeoutTicks", definition.timeoutTicks);
            configuration.addProperty("fixedDurationTicks", definition.fixedDurationTicks);
            configuration.addProperty("randomTickSpeed", level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).get());
            configuration.addProperty("benchmarkUpdateIntervalTicks", Math.max(1, EnvironmentalConfig.COMMON.updateInterval.get()));
            owner.announce(level, owner.trialIndex, "Validating seeded " + prettyKind(definition.kind) + " state before timing starts.");
        }

        private boolean tick(ServerLevel level) {
            if (startedAtGameTime < 0L) {
                return tickPreparation(level);
            }
            long elapsed = Math.max(0L, level.getGameTime() - startedAtGameTime);
            driveBenchmarkColumns(level, elapsed);
            sample(level, elapsed, elapsed % SAMPLE_INTERVAL_TICKS == 0L);
            maybeAnnounceProgress(level, elapsed);
            if (!isComplete(elapsed)) {
                return false;
            }
            finishedElapsedTicks = elapsed;
            if ("running".equals(status)) {
                if (definition.fixedDurationTicks > 0 && elapsed >= definition.fixedDurationTicks) {
                    status = "completed";
                } else if (elapsed >= definition.timeoutTicks) {
                    status = "timeout";
                } else {
                    status = "completed";
                }
            }
            sample(level, elapsed, true);
            return true;
        }

        private boolean tickPreparation(ServerLevel level) {
            boolean seeded = lanes.stream().allMatch(lane -> lane.matchesSeed(level, definition));
            if (seeded) {
                stablePreparationTicks++;
                if (stablePreparationTicks >= PREPARATION_STABLE_TICKS) {
                    startedAtGameTime = level.getGameTime();
                    sample(level, 0L, true);
                    owner.announce(level, owner.trialIndex, "Seed verified. Measuring " + prettyKind(definition.kind) + ".");
                }
                return false;
            }

            stablePreparationTicks = 0;
            long preparationElapsed = Math.max(0L, level.getGameTime() - preparationStartedAtGameTime);
            if (preparationElapsed < PREPARATION_TIMEOUT_TICKS) {
                return false;
            }

            if (reseedAttempts >= MAX_PREPARATION_RETRIES) {
                startedAtGameTime = level.getGameTime();
                finishedElapsedTicks = 0L;
                status = "invalid_setup";
                sample(level, 0L, true);
                owner.announce(level, owner.trialIndex, "Seed validation failed for " + definition.id + ". Trial marked invalid.");
                return true;
            }

            reseedAttempts++;
            preparationStartedAtGameTime = level.getGameTime();
            resendSeed(level);
            owner.announce(level, owner.trialIndex, "Seed mismatch detected. Reseeding " + definition.id + " (" + reseedAttempts + "/" + MAX_PREPARATION_RETRIES + ").");
            return false;
        }

        private void resendSeed(ServerLevel level) {
            EnvironmentalSavedData data = EnvironmentalSavedData.get(level);
            for (LaneTracker lane : lanes) {
                resetLaneArea(level, data, lane.lane.supportPos);
                seedLaneArea(level, data, lane.lane.supportPos, lane.lane.plan);
            }
        }

        private void markStopped(ServerLevel level, String nextStatus) {
            if (!"running".equals(status)) {
                return;
            }
            long baseline = startedAtGameTime >= 0L ? startedAtGameTime : preparationStartedAtGameTime;
            finishedElapsedTicks = Math.max(0L, level.getGameTime() - baseline);
            status = nextStatus;
            sample(level, startedAtGameTime < 0L ? 0L : finishedElapsedTicks, true);
        }

        private void maybeAnnounceProgress(ServerLevel level, long elapsedTicks) {
            long targetTicks = definition.fixedDurationTicks > 0 ? definition.fixedDurationTicks : definition.timeoutTicks;
            if (targetTicks <= 0L || elapsedTicks < nextChatProgressTick || elapsedTicks >= targetTicks) {
                return;
            }

            while (nextChatProgressTick <= elapsedTicks && nextChatProgressTick < targetTicks) {
                owner.announce(level, owner.trialIndex + Math.min(1.0D, nextChatProgressTick / (double) targetTicks), "Waiting " + formatTicks(nextChatProgressTick) + " for " + prettyKind(definition.kind) + ".");
                nextChatProgressTick += CHAT_PROGRESS_INTERVAL_TICKS;
            }
        }

        private void driveBenchmarkColumns(ServerLevel level, long elapsedTicks) {
            int updateInterval = Math.max(1, EnvironmentalConfig.COMMON.updateInterval.get());
            if (elapsedTicks <= 0L || elapsedTicks % updateInterval != 0L) {
                return;
            }
            for (LaneTracker lane : lanes) {
                EnvironmentalTicker.processBenchmarkColumn(level, lane.lane.supportPos, false);
            }
        }

        private void sample(ServerLevel level, long elapsedTicks, boolean forceSnapshot) {
            for (LaneTracker lane : lanes) {
                lane.capture(level, definition, elapsedTicks);
            }
            if (!forceSnapshot && lastSnapshotElapsed != Long.MIN_VALUE && elapsedTicks - lastSnapshotElapsed < SAMPLE_INTERVAL_TICKS) {
                return;
            }
            JsonArray laneJson = new JsonArray();
            for (LaneTracker lane : lanes) {
                JsonObject laneObject = new JsonObject();
                laneObject.addProperty("name", lane.lane.name);
                laneObject.add("state", stateJson(lane.latest));
                laneObject.add("metrics", metricsJson(lane.latestMetrics));
                laneObject.add("diagnostics", diagnosticsJson(lane.latestDiagnostics));
                laneJson.add(laneObject);
            }
            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("elapsedTicks", elapsedTicks);
            snapshot.add("lanes", laneJson);
            snapshots.add(snapshot);
            lastSnapshotElapsed = elapsedTicks;
        }

        private boolean isComplete(long elapsedTicks) {
            if (definition.fixedDurationTicks > 0 && elapsedTicks >= definition.fixedDurationTicks) {
                return true;
            }
            if (definition.timeoutTicks > 0 && elapsedTicks >= definition.timeoutTicks) {
                return true;
            }
            return definition.fixedDurationTicks <= 0 && definition.timeoutTicks <= 0
                && lanes.stream().allMatch(lane -> lane.completionTick >= 0L);
        }

        private void recordCropBoost(BlockPos farmlandPos) {
            for (LaneTracker lane : lanes) {
                if (lane.lane.supportPos.equals(farmlandPos)) {
                    lane.cropBoosts++;
                    return;
                }
            }
        }

        private String completionMessage() {
            return prettyKind(definition.kind) + " trial " + definition.id + " " + status + " after " + formatTicks(Math.max(0L, finishedElapsedTicks)) + ".";
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", definition.id);
            json.addProperty("status", status);
            json.add("configuration", configuration);
            json.add("arenaOrigin", posJson(arena.origin));
            json.addProperty("elapsedTicks", finishedElapsedTicks < 0L ? 0L : finishedElapsedTicks);
            json.addProperty("reseedAttempts", reseedAttempts);
            json.add("summary", buildSummary());
            JsonArray laneResults = new JsonArray();
            for (LaneTracker lane : lanes) {
                laneResults.add(lane.toJson());
            }
            json.add("lanes", laneResults);
            json.add("snapshots", snapshots);
            return json;
        }

        private JsonObject buildSummary() {
            JsonObject summary = new JsonObject();
            summary.addProperty("laneCount", lanes.size());
            summary.addProperty("completedLanes", lanes.stream().filter(lane -> lane.completionTick >= 0L).count());
            summary.addProperty("cropBoostCount", lanes.stream().mapToInt(lane -> lane.cropBoosts).sum());
            summary.addProperty("eligibleSamples", lanes.stream().mapToInt(lane -> lane.eligibleSamples).sum());
            summary.addProperty("ineligibleSamples", lanes.stream().mapToInt(lane -> lane.ineligibleSamples).sum());
            summary.addProperty("averageInitialTargetChancePct", lanes.stream().mapToDouble(lane -> lane.initialDiagnostics == null ? 0.0D : lane.initialDiagnostics.targetChancePct).average().orElse(0.0D));
            summary.addProperty("averageFinalTargetChancePct", lanes.stream().mapToDouble(lane -> lane.latestDiagnostics == null ? 0.0D : lane.latestDiagnostics.targetChancePct).average().orElse(0.0D));

            List<Long> firstChanges = lanes.stream().map(lane -> lane.firstChangeTick).filter(value -> value >= 0L).sorted().toList();
            List<Long> completions = lanes.stream().map(lane -> lane.completionTick).filter(value -> value >= 0L).sorted().toList();
            addStats(summary, "firstChange", firstChanges);
            addStats(summary, "completion", completions);

            int startSurface = lanes.stream().mapToInt(lane -> lane.initial == null ? 0 : lane.initial.surfaceWater).sum();
            int endSurface = lanes.stream().mapToInt(lane -> lane.latest == null ? 0 : lane.latest.surfaceWater).sum();
            int startAbsorbed = lanes.stream().mapToInt(lane -> lane.initial == null ? 0 : lane.initial.absorbedWater).sum();
            int endAbsorbed = lanes.stream().mapToInt(lane -> lane.latest == null ? 0 : lane.latest.absorbedWater).sum();
            int startIce = lanes.stream().mapToInt(lane -> lane.initial == null ? 0 : lane.initial.surfaceIceLevels).sum();
            int endIce = lanes.stream().mapToInt(lane -> lane.latest == null ? 0 : lane.latest.surfaceIceLevels).sum();
            int startCrop = lanes.stream().mapToInt(lane -> lane.initial == null ? 0 : Math.max(0, lane.initial.cropAge)).sum();
            int endCrop = lanes.stream().mapToInt(lane -> lane.latest == null ? 0 : Math.max(0, lane.latest.cropAge)).sum();
            int startCauldron = lanes.stream().mapToInt(lane -> lane.initial == null ? 0 : Math.max(0, lane.initial.cauldronLevel)).sum();
            int endCauldron = lanes.stream().mapToInt(lane -> lane.latest == null ? 0 : Math.max(0, lane.latest.cauldronLevel)).sum();

            long elapsed = Math.max(1L, finishedElapsedTicks < 0L ? definition.timeoutTicks : finishedElapsedTicks);
            summary.addProperty("surfaceWaterStart", startSurface);
            summary.addProperty("surfaceWaterEnd", endSurface);
            summary.addProperty("surfaceWaterDelta", endSurface - startSurface);
            summary.addProperty("absorbedWaterStart", startAbsorbed);
            summary.addProperty("absorbedWaterEnd", endAbsorbed);
            summary.addProperty("absorbedWaterDelta", endAbsorbed - startAbsorbed);
            summary.addProperty("surfaceIceStart", startIce);
            summary.addProperty("surfaceIceEnd", endIce);
            summary.addProperty("surfaceIceDelta", endIce - startIce);
            summary.addProperty("cropAgeStart", startCrop);
            summary.addProperty("cropAgeEnd", endCrop);
            summary.addProperty("cropAgeDelta", endCrop - startCrop);
            summary.addProperty("cauldronLevelStart", startCauldron);
            summary.addProperty("cauldronLevelEnd", endCauldron);
            summary.addProperty("cauldronLevelDelta", endCauldron - startCauldron);
            summary.addProperty("surfaceWaterRatePer100Ticks", ((endSurface - startSurface) * 100.0D) / elapsed);
            summary.addProperty("absorbedWaterRatePer100Ticks", ((endAbsorbed - startAbsorbed) * 100.0D) / elapsed);
            summary.addProperty("surfaceIceRatePer100Ticks", ((endIce - startIce) * 100.0D) / elapsed);
            summary.addProperty("cropAgeRatePer100Ticks", ((endCrop - startCrop) * 100.0D) / elapsed);
            summary.addProperty("cauldronRatePer100Ticks", ((endCauldron - startCauldron) * 100.0D) / elapsed);
            return summary;
        }

        private void addStats(JsonObject summary, String prefix, List<Long> values) {
            if (values.isEmpty()) {
                summary.addProperty(prefix + "Count", 0);
                return;
            }
            summary.addProperty(prefix + "Count", values.size());
            summary.addProperty(prefix + "MinTicks", values.get(0));
            summary.addProperty(prefix + "MedianTicks", values.get(values.size() / 2));
            summary.addProperty(prefix + "MaxTicks", values.get(values.size() - 1));
            summary.addProperty(prefix + "AverageTicks", values.stream().mapToLong(Long::longValue).average().orElse(0.0D));
        }
    }

    private static final class LaneTracker {
        private final ArenaLane lane;
        private LaneState initial;
        private LaneState latest;
        private LaneDiagnostics initialDiagnostics;
        private LaneDiagnostics latestDiagnostics;
        private EnvironmentalTicker.ColumnMetrics initialMetrics;
        private EnvironmentalTicker.ColumnMetrics latestMetrics;
        private long firstChangeTick = -1L;
        private long completionTick = -1L;
        private int cropBoosts;
        private int eligibleSamples;
        private int ineligibleSamples;
        private String firstIneligibleReason = "";
        private String lastIneligibleReason = "";

        private LaneTracker(ArenaLane lane) {
            this.lane = lane;
        }

        private void capture(ServerLevel level, TrialDefinition definition, long elapsedTicks) {
            EnvironmentalTicker.ColumnMetrics metrics = EnvironmentalTicker.captureColumnMetrics(level, lane.supportPos, lane.supportPos.above());
            LaneState state = LaneState.capture(level, lane.supportPos, metrics);
            LaneDiagnostics diagnostics = captureDiagnostics(level, definition, lane, state, metrics);
            if (initial == null) {
                initial = state;
                latest = state;
                initialDiagnostics = diagnostics;
                latestDiagnostics = diagnostics;
                initialMetrics = metrics;
                latestMetrics = metrics;
                updateEligibility(diagnostics);
                if (isComplete(definition.kind, state)) {
                    completionTick = 0L;
                }
                return;
            }

            if (firstChangeTick < 0L && !latest.equals(state)) {
                firstChangeTick = elapsedTicks;
            }
            latest = state;
            latestDiagnostics = diagnostics;
            latestMetrics = metrics;
            updateEligibility(diagnostics);
            if (completionTick < 0L && isComplete(definition.kind, state)) {
                completionTick = elapsedTicks;
            }
        }

        private void updateEligibility(LaneDiagnostics diagnostics) {
            if (diagnostics.eligible) {
                eligibleSamples++;
                return;
            }
            ineligibleSamples++;
            if (firstIneligibleReason.isEmpty()) {
                firstIneligibleReason = diagnostics.reason;
            }
            lastIneligibleReason = diagnostics.reason;
        }

        private boolean matchesSeed(ServerLevel level, TrialDefinition definition) {
            EnvironmentalTicker.ColumnMetrics metrics = EnvironmentalTicker.captureColumnMetrics(level, lane.supportPos, lane.supportPos.above());
            LaneState state = LaneState.capture(level, lane.supportPos, metrics);
            return switch (definition.kind) {
                case EVAPORATION, ABSORPTION -> state.surfaceWater > 0;
                case CONDENSATION -> state.surfaceWater == lane.plan.surfaceWater
                    && state.absorbedWater == lane.plan.absorbed
                    && state.surfaceIceLevels == lane.plan.surfaceIce
                    && (lane.plan.supportState.getBlock() instanceof LayeredCauldronBlock
                        ? state.cauldronLevel == lane.plan.supportState.getValue(BlockStateProperties.LEVEL_CAULDRON)
                        : true);
                case FREEZE -> state.surfaceWater == lane.plan.surfaceWater && state.surfaceIceLevels == lane.plan.surfaceIce;
                case THAW -> state.surfaceWater == lane.plan.surfaceWater && state.surfaceIceLevels == lane.plan.surfaceIce;
                case AGRICULTURE_HYDRATION -> state.absorbedWater == lane.plan.absorbed && state.farmlandMoisture == 0;
                case AGRICULTURE_GROWTH -> state.absorbedWater == lane.plan.absorbed && state.farmlandMoisture == 7 && state.cropAge == 0;
            };
        }

        private boolean isComplete(TrialKind kind, LaneState state) {
            return switch (kind) {
                case EVAPORATION -> state.surfaceWater <= 0;
                case ABSORPTION -> state.surfaceWater <= 0;
                case CONDENSATION -> false;
                case FREEZE -> state.surfaceWater <= 0 && state.surfaceIceLevels >= lane.plan.surfaceWater;
                case THAW -> state.surfaceIceLevels <= 0 && state.surfaceWater >= lane.plan.surfaceIce;
                case AGRICULTURE_HYDRATION -> state.farmlandMoisture >= 7;
                case AGRICULTURE_GROWTH -> state.cropAge >= 7;
            };
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", lane.name);
            json.add("supportPos", posJson(lane.supportPos));
            json.add("initialState", stateJson(initial));
            json.add("finalState", stateJson(latest));
            json.add("initialDiagnostics", diagnosticsJson(initialDiagnostics));
            json.add("finalDiagnostics", diagnosticsJson(latestDiagnostics));
            json.add("initialMetrics", metricsJson(initialMetrics));
            json.add("finalMetrics", metricsJson(latestMetrics));
            json.addProperty("firstChangeTick", firstChangeTick);
            json.addProperty("completionTick", completionTick);
            json.addProperty("cropBoosts", cropBoosts);
            json.addProperty("eligibleSamples", eligibleSamples);
            json.addProperty("ineligibleSamples", ineligibleSamples);
            json.addProperty("firstIneligibleReason", firstIneligibleReason);
            json.addProperty("lastIneligibleReason", lastIneligibleReason);
            return json;
        }
    }

    private record LaneState(
        int surfaceWater,
        int absorbedWater,
        int surfaceIceLevels,
        int snowLayers,
        int farmlandMoisture,
        int cropAge,
        int cauldronLevel
    ) {
        private static LaneState capture(ServerLevel level, BlockPos supportPos, EnvironmentalTicker.ColumnMetrics metrics) {
            BlockState supportState = level.getBlockState(supportPos);
            BlockState topState = level.getBlockState(supportPos.above());
            int cropAge = topState.getBlock() instanceof CropBlock cropBlock ? cropBlock.getAge(topState) : -1;
            int cauldronLevel = supportState.getBlock() instanceof LayeredCauldronBlock && supportState.hasProperty(BlockStateProperties.LEVEL_CAULDRON)
                ? supportState.getValue(BlockStateProperties.LEVEL_CAULDRON)
                : -1;
            return new LaneState(
                captureLaneWater(level, supportPos),
                EnvironmentalSavedData.get(level).getAbsorbed(supportPos),
                captureLaneIce(level, supportPos),
                metrics.snowLayers(),
                metrics.farmlandMoisture(),
                cropAge,
                cauldronLevel
            );
        }
    }

    private static final class SavedEnvironment {
        private boolean captured;
        private long dayTime;
        private boolean daylightCycle;
        private boolean weatherCycle;
        private int randomTickSpeed;
        private boolean rainAccumulation;
        private boolean puddles;
        private boolean distantRainCatchup;
        private boolean evaporation;
        private boolean droughts;
        private boolean floods;
        private boolean snowmelt;
        private boolean absorption;
        private boolean condensation;
        private boolean surfaceIce;
        private boolean agriculture;
        private boolean seasons;
        private boolean tropicalSeasons;
        private double releaseChance;
        private double evaporationMultiplierOverride;
        private double absorptionMultiplierOverride;
        private double condensationMultiplierOverride;
        private double surfaceIceMultiplierOverride;
        private double agricultureMultiplierOverride;
        private boolean wasRaining;
        private boolean wasThundering;
        private int droughtScore;
        private int ambientWetness;

        private void capture(ServerLevel level) {
            if (captured) {
                return;
            }
            captured = true;
            dayTime = level.getDayTime();
            daylightCycle = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).get();
            weatherCycle = level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).get();
            randomTickSpeed = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).get();
            rainAccumulation = EnvironmentalConfig.COMMON.rainAccumulation.get();
            puddles = EnvironmentalConfig.COMMON.puddles.get();
            distantRainCatchup = EnvironmentalConfig.COMMON.distantRainCatchup.get();
            evaporation = EnvironmentalConfig.COMMON.evaporation.get();
            droughts = EnvironmentalConfig.COMMON.droughts.get();
            floods = EnvironmentalConfig.COMMON.floods.get();
            snowmelt = EnvironmentalConfig.COMMON.snowmelt.get();
            absorption = EnvironmentalConfig.COMMON.absorption.get();
            condensation = EnvironmentalConfig.COMMON.condensation.get();
            surfaceIce = EnvironmentalConfig.COMMON.surfaceIce.get();
            agriculture = EnvironmentalConfig.COMMON.agriculture.get();
            seasons = EnvironmentalConfig.COMMON.seasons.get();
            tropicalSeasons = EnvironmentalConfig.COMMON.tropicalSeasons.get();
            releaseChance = EnvironmentalConfig.COMMON.releaseChance.get();
            evaporationMultiplierOverride = EnvironmentalConfig.COMMON.evaporationMultiplierOverride.get();
            absorptionMultiplierOverride = EnvironmentalConfig.COMMON.absorptionMultiplierOverride.get();
            condensationMultiplierOverride = EnvironmentalConfig.COMMON.condensationMultiplierOverride.get();
            surfaceIceMultiplierOverride = EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.get();
            agricultureMultiplierOverride = EnvironmentalConfig.COMMON.agricultureMultiplierOverride.get();
            wasRaining = level.isRaining();
            wasThundering = level.isThundering();
            EnvironmentalSavedData data = EnvironmentalSavedData.get(level);
            droughtScore = data.getDroughtScore();
            ambientWetness = data.getAmbientWetness();
        }

        private void restore(ServerLevel level) {
            if (!captured) {
                return;
            }
            level.setDayTime(dayTime);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylightCycle, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(weatherCycle, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(randomTickSpeed, level.getServer());
            level.setWeatherParameters(wasRaining ? 0 : WEATHER_LOCK_DURATION, wasRaining ? WEATHER_LOCK_DURATION : 0, wasRaining, wasThundering);

            EnvironmentalConfig.COMMON.rainAccumulation.set(rainAccumulation);
            EnvironmentalConfig.COMMON.puddles.set(puddles);
            EnvironmentalConfig.COMMON.distantRainCatchup.set(distantRainCatchup);
            EnvironmentalConfig.COMMON.evaporation.set(evaporation);
            EnvironmentalConfig.COMMON.droughts.set(droughts);
            EnvironmentalConfig.COMMON.floods.set(floods);
            EnvironmentalConfig.COMMON.snowmelt.set(snowmelt);
            EnvironmentalConfig.COMMON.absorption.set(absorption);
            EnvironmentalConfig.COMMON.condensation.set(condensation);
            EnvironmentalConfig.COMMON.surfaceIce.set(surfaceIce);
            EnvironmentalConfig.COMMON.agriculture.set(agriculture);
            EnvironmentalConfig.COMMON.seasons.set(seasons);
            EnvironmentalConfig.COMMON.tropicalSeasons.set(tropicalSeasons);
            EnvironmentalConfig.COMMON.releaseChance.set(releaseChance);
            EnvironmentalConfig.COMMON.evaporationMultiplierOverride.set(evaporationMultiplierOverride);
            EnvironmentalConfig.COMMON.absorptionMultiplierOverride.set(absorptionMultiplierOverride);
            EnvironmentalConfig.COMMON.condensationMultiplierOverride.set(condensationMultiplierOverride);
            EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.set(surfaceIceMultiplierOverride);
            EnvironmentalConfig.COMMON.agricultureMultiplierOverride.set(agricultureMultiplierOverride);

            EnvironmentalSavedData data = EnvironmentalSavedData.get(level);
            data.setDroughtScore(droughtScore);
            data.setAmbientWetness(ambientWetness, EnvironmentalConfig.COMMON.ambientWetnessCap.get());
        }
    }
}
