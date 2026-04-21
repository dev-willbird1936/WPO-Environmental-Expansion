package net.skds.wpo.environmental;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.api.WPOFluidAccess;
import net.skds.wpo.environmental.network.EnvDebugPacket;
import net.skds.wpo.environmental.network.EnvPacketHandler;

public final class EnvironmentalTicker {

    private EnvironmentalTicker() {
    }

    public static void tick(ServerLevel level) {
        sendDebugPackets(level);

        if ((level.getGameTime() % EnvironmentalConfig.COMMON.updateInterval.get()) != 0L) {
            return;
        }

        EnvironmentalSavedData data = EnvironmentalSavedData.get(level);
        sweepEviction(level, data);
        updateDrought(level, data);
        updateAmbientWetness(level, data);

        if (level.players().isEmpty()) {
            return;
        }

        for (Player player : level.players()) {
            long chunkKey = ChunkPos.asLong(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
            if (data.updatePlayerChunk(player.getUUID(), chunkKey)) {
                materializeArrivalArea(level, player, data);
            }
        }

        int budget = Math.max(1, level.players().size() * EnvironmentalConfig.COMMON.columnChecksPerPlayer.get());
        long cursor = data.nextCursor(budget);
        int radius = EnvironmentalConfig.COMMON.sampleRadius.get();
        for (int i = 0; i < budget; ++i) {
            Player player = level.players().get((int) Math.floorMod(cursor + i, level.players().size()));
            BlockPos sample = sampleColumn(player.blockPosition(), radius, cursor + i);
            processColumn(level, sample.getX(), sample.getZ(), data, false);
        }

        processFocusedEvaporation(level, data);
    }

    private static void sweepEviction(ServerLevel level, EnvironmentalSavedData data) {
        long dayTicks = 24000L;
        if ((level.getGameTime() % dayTicks) != 0L) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }
        int evicted = data.sweepStaleAbsorbedWater(level.getGameTime());
        if (evicted > 0 && EnvironmentalExpansion.LOGGER.isDebugEnabled()) {
            EnvironmentalExpansion.LOGGER.debug("Evicted {} stale absorbed-water entries", evicted);
        }
    }

    public static int getCollectorRainMb(ServerLevel level, BlockPos pos, int baseMb) {
        if (baseMb <= 0 || !level.isRainingAt(pos.above())) {
            return 0;
        }
        BiomeEnvironmentProfile profile = BiomeProfileManager.getProfile(level, pos);
        double multiplier = getRainIntensity(level, pos, EnvironmentalSavedData.get(level), profile) * profile.collectorMultiplier();
        return (int) Mth.clamp(baseMb * multiplier, 0.0D, 8000.0D);
    }

    private static void updateDrought(ServerLevel level, EnvironmentalSavedData data) {
        if (!EnvironmentalConfig.COMMON.droughts.get()) {
            return;
        }
        if (level.isRaining()) {
            data.adjustDrought(-EnvironmentalConfig.COMMON.droughtWetStep.get());
        } else {
            data.adjustDrought(EnvironmentalConfig.COMMON.droughtDryStep.get());
        }
    }

    private static void updateAmbientWetness(ServerLevel level, EnvironmentalSavedData data) {
        if (!EnvironmentalConfig.COMMON.distantRainCatchup.get()
            || !EnvironmentalConfig.COMMON.puddles.get()
            || !EnvironmentalConfig.COMMON.rainAccumulation.get()) {
            data.clearAmbientWetness();
            return;
        }

        int cap = EnvironmentalConfig.COMMON.ambientWetnessCap.get();
        if (cap <= 0) {
            data.clearAmbientWetness();
            return;
        }

        BiomeEnvironmentProfile activeProfile = BiomeProfileManager.getActiveProfile(level);
        if (level.isRaining()) {
            int gain = scaleStep(EnvironmentalConfig.COMMON.ambientWetnessRainGain.get(), getGlobalRainMemoryMultiplier(level, data, activeProfile));
            data.adjustAmbientWetness(gain, cap);
        } else {
            int decay = scaleStep(EnvironmentalConfig.COMMON.ambientWetnessDryDecay.get(), getGlobalDryDecayMultiplier(level, data, activeProfile));
            data.adjustAmbientWetness(-decay, cap);
        }
    }

    private static void materializeArrivalArea(ServerLevel level, Player player, EnvironmentalSavedData data) {
        int burstChecks = EnvironmentalConfig.COMMON.arrivalColumnChecks.get();
        if (burstChecks <= 0) {
            return;
        }
        int radius = EnvironmentalConfig.COMMON.sampleRadius.get();
        long base = mix(level.getGameTime() ^ ChunkPos.asLong(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4));
        for (int i = 0; i < burstChecks; ++i) {
            BlockPos sample = sampleColumn(player.blockPosition(), radius, base + i);
            processColumn(level, sample.getX(), sample.getZ(), data, true);
        }
    }

    private static void processColumn(ServerLevel level, int x, int z, EnvironmentalSavedData data, boolean burstMaterialization) {
        if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return;
        }

        int airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos airPos = new BlockPos(x, airY, z);
        BlockPos groundPos = airPos.below();
        if (groundPos.getY() < level.getMinBuildHeight()) {
            return;
        }

        processColumnAt(level, groundPos, airPos, data, burstMaterialization);
    }

    static void processBenchmarkColumn(ServerLevel level, BlockPos groundPos, boolean burstMaterialization) {
        if (groundPos.getY() < level.getMinBuildHeight()) {
            return;
        }
        BlockPos airPos = groundPos.above();
        if (!level.hasChunkAt(groundPos)) {
            return;
        }
        processColumnAt(level, groundPos, airPos, EnvironmentalSavedData.get(level), burstMaterialization);
    }

    private static void processColumnAt(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, boolean burstMaterialization) {
        if (groundPos.getY() < level.getMinBuildHeight()) {
            return;
        }

        BlockState groundState = level.getBlockState(groundPos);
        BiomeEnvironmentProfile profile = BiomeProfileManager.getProfile(level, groundPos);
        BiomeProfileManager.observe(level, groundPos, groundState);
        if (EnvironmentalConfig.COMMON.snowmelt.get()) {
            processSnowmelt(level, groundPos, airPos, groundState, data, profile);
            groundState = level.getBlockState(groundPos);
        }

        if (EnvironmentalConfig.COMMON.surfaceIce.get()) {
            ClimateEstimate climate = sampleClimateEstimate(level, groundPos, airPos, data, profile);
            processSurfaceIce(level, groundPos, airPos, groundState, data, profile, climate);
            groundState = level.getBlockState(groundPos);
        }

        boolean rainingHere = EnvironmentalConfig.COMMON.rainAccumulation.get() && level.isRainingAt(airPos);
        if (rainingHere) {
            processRain(level, groundPos, airPos, groundState, data, profile);
        }

        if (EnvironmentalConfig.COMMON.distantRainCatchup.get()) {
            applyAmbientCatchUp(level, groundPos, airPos, groundState, data, burstMaterialization, profile);
        }

        if (EnvironmentalConfig.COMMON.absorption.get() || EnvironmentalConfig.COMMON.evaporation.get()) {
            processSurfaceWater(level, groundPos, airPos, groundState, data, rainingHere, profile);
        }

        if (EnvironmentalConfig.COMMON.condensation.get()) {
            ClimateEstimate climate = sampleClimateEstimate(level, groundPos, airPos, data, profile);
            processCondensation(level, groundPos, airPos, groundState, data, profile, climate);
        }

        if (EnvironmentalConfig.COMMON.agriculture.get()) {
            processAgriculture(level, groundPos, groundState, data);
        }
    }

    static int getAbsorptionCapacityForBenchmark(BlockState state, BiomeEnvironmentProfile profile, ServerLevel level) {
        return getAbsorptionCapacity(state, profile, level);
    }

    static boolean canHoldSurfacePuddleForBenchmark(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState) {
        return canHoldSurfacePuddle(level, groundPos, airPos, groundState);
    }

    static boolean hasNearbyHeatSourceForBenchmark(ServerLevel level, BlockPos pos) {
        return hasNearbyHeatSource(level, pos);
    }

    static boolean hasNearbyHydratingWaterForBenchmark(ServerLevel level, BlockPos farmlandPos) {
        return hasNearbyHydratingWater(level, farmlandPos);
    }

    private static void processFocusedEvaporation(ServerLevel level, EnvironmentalSavedData data) {
        if (!EnvironmentalConfig.COMMON.evaporation.get() || level.players().isEmpty()) {
            return;
        }

        double override = EnvironmentalConfig.COMMON.evaporationMultiplierOverride.get();
        if (override <= 1.0D) {
            return;
        }

        int focusedChecks = Mth.clamp(Mth.ceil((float) Math.min(override, 96.0D)), 1, 96);
        int focusedRadius = Math.min(8, EnvironmentalConfig.COMMON.sampleRadius.get());

        for (Player player : level.players()) {
            long base = mix(level.getGameTime() ^ player.blockPosition().asLong() ^ 0x5A17D3E4C2B19F61L);
            for (int i = 0; i < focusedChecks; ++i) {
                BlockPos sample = sampleColumn(player.blockPosition(), focusedRadius, base + i);
                processEvaporationColumn(level, sample.getX(), sample.getZ(), data);
            }
        }
    }

    private static void processEvaporationColumn(ServerLevel level, int x, int z, EnvironmentalSavedData data) {
        if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return;
        }

        int airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos airPos = new BlockPos(x, airY, z);
        BlockPos groundPos = airPos.below();
        if (groundPos.getY() < level.getMinBuildHeight()) {
            return;
        }

        BiomeEnvironmentProfile profile = BiomeProfileManager.getProfile(level, groundPos);
        evaporateSurfaceWater(level, groundPos, airPos, data, profile);
    }

    private static void processRain(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        if (groundState.getBlock() instanceof LayeredCauldronBlock && groundState.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            if (groundState.getValue(BlockStateProperties.LEVEL_CAULDRON) < 3) {
                SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
                double rainChance = 0.10D * getRainIntensity(level, groundPos, data, profile) * profile.rainChanceMultiplier() * SeasonalModifiers.getRainChanceMultiplier(subSeason);
                if (rollChance(level, groundPos, rainChance)) {
                    level.setBlock(groundPos, groundState.cycle(BlockStateProperties.LEVEL_CAULDRON), 3);
                }
            }
            return;
        }

        if (!EnvironmentalConfig.COMMON.puddles.get()) {
            return;
        }
        if (!WPOFluidAccess.isChunkLoaded(level, airPos)) {
            return;
        }
        if (!canHoldSurfacePuddle(level, groundPos, airPos, groundState)) {
            return;
        }

        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        double rainChance = EnvironmentalConfig.COMMON.rainChance.get() * getRainIntensity(level, groundPos, data, profile) * profile.rainChanceMultiplier() * SeasonalModifiers.getRainChanceMultiplier(subSeason);
        rainChance *= DayNightModifiers.getRainChanceMultiplier(level);
        int steps = rollChance(level, airPos, rainChance) ? 1 : 0;
        if (EnvironmentalConfig.COMMON.floods.get() && level.isThundering()
            && rollChance(level, airPos.above(), rainChance * 0.5D * SeasonalModifiers.getStormMultiplier(subSeason))) {
            steps++;
        }
        if (steps <= 0) {
            return;
        }

        if (EnvironmentalConfig.COMMON.absorption.get()) {
            steps -= data.addAbsorbed(groundPos, steps, getAbsorptionCapacity(level.getBlockState(groundPos), profile, level));
        }
        if (steps > 0) {
            WPOFluidAccess.addWater(level, airPos, steps);
        }
    }

    private static void applyAmbientCatchUp(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, boolean burstMaterialization, BiomeEnvironmentProfile profile) {
        if (!EnvironmentalConfig.COMMON.puddles.get() || !WPOFluidAccess.isChunkLoaded(level, airPos)) {
            return;
        }
        if (!level.isRainingAt(airPos) || !canHoldSurfacePuddle(level, groundPos, airPos, groundState)) {
            return;
        }

        int targetLevels = getAmbientTargetWater(level, groundPos, airPos, data, profile);
        if (targetLevels <= 0) {
            return;
        }

        int currentWater = WPOFluidAccess.getWaterAmount(level, airPos);
        if (currentWater >= targetLevels) {
            return;
        }

        int steps = burstMaterialization ? targetLevels - currentWater : 1;
        if (EnvironmentalConfig.COMMON.absorption.get()) {
            steps -= data.addAbsorbed(groundPos, steps, getAbsorptionCapacity(level.getBlockState(groundPos), profile, level));
        }
        if (steps > 0) {
            WPOFluidAccess.addWater(level, airPos, Math.min(targetLevels - currentWater, steps));
        }
    }

    private static void processSurfaceWater(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, boolean rainingHere, BiomeEnvironmentProfile profile) {
        int waterAmount = WPOFluidAccess.getWaterAmount(level, airPos);
        if (EnvironmentalConfig.COMMON.absorption.get()) {
            int absorptionCapacity = getAbsorptionCapacity(groundState, profile, level);
            if (absorptionCapacity <= 0) {
                data.clearAbsorbed(groundPos);
            } else {
                if (waterAmount > 0 && data.getAbsorbed(groundPos) < absorptionCapacity
                    && rollChance(level, groundPos.below(), EnvironmentalConfig.COMMON.absorptionChance.get() * profile.absorptionMultiplier() * EnvironmentalConfig.COMMON.absorptionMultiplierOverride.get())) {
                    int before = WPOFluidAccess.getWaterAmount(level, airPos);
                    int after = WPOFluidAccess.removeWater(level, airPos, 1);
                    if (before > after) {
                        data.addAbsorbed(groundPos, 1, absorptionCapacity);
                        waterAmount = after;
                    }
                }
                if (data.getAbsorbed(groundPos) > 0 && shouldRelease(level, groundPos, airPos, data, waterAmount, rainingHere)
                    && rollChance(level, groundPos.above(), EnvironmentalConfig.COMMON.releaseChance.get() * getReleaseMultiplier(level, groundPos, profile))) {
                    int before = WPOFluidAccess.getWaterAmount(level, airPos);
                    int after = WPOFluidAccess.addWater(level, airPos, 1);
                    if (after > before) {
                        data.consumeAbsorbed(groundPos, 1);
                        waterAmount = after;
                    }
                }
            }
        }

        if (waterAmount > 0) {
            spawnDripParticles(level, groundPos, airPos);
        }

        if (EnvironmentalConfig.COMMON.evaporation.get()) {
            evaporateSurfaceWater(level, groundPos, airPos, data, profile);
        }
    }

    private static void spawnDripParticles(ServerLevel level, BlockPos groundPos, BlockPos airPos) {
        if (!level.isRainingAt(airPos) && level.canSeeSky(airPos)) {
            BlockPos belowAir = airPos.below();
            if (level.getBlockState(belowAir).isAir()) {
                if (rollChance(level, airPos, 0.015D)) {
                    double x = airPos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.6D;
                    double y = airPos.getY() + 0.05D;
                    double z = airPos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.6D;
                    level.sendParticles(ParticleTypes.DRIPPING_WATER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private static void evaporateSurfaceWater(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        BlockPos evaporationPos = getExposedWaterPos(level, groundPos, airPos);
        if (evaporationPos == null) {
            return;
        }

        int waterAmount = WPOFluidAccess.getWaterAmount(level, evaporationPos);
        if (waterAmount <= 0) {
            return;
        }

        double baseMultiplier = getEvaporationMultiplier(level, groundPos, airPos, data, profile);
        double evaporationChance = EnvironmentalConfig.COMMON.evaporationChance.get() * baseMultiplier;
        evaporationChance *= EnvironmentalConfig.COMMON.evaporationMultiplierOverride.get();
        int evaporated = resolveScaledSteps(level, evaporationPos.north(), evaporationChance, waterAmount);
        if (evaporated > 0) {
            WPOFluidAccess.removeWater(level, evaporationPos, evaporated);
            double visualChance = getEvaporationVisualChance(profile, evaporationChance);
            if (visualChance > 0.0D && rollChance(level, evaporationPos, visualChance)) {
                int particleCount = Mth.clamp(Math.max(1, evaporated) + (int) Math.floor(visualChance * 2.0D), 1, 4);
                for (int i = 0; i < particleCount; ++i) {
                    double x = evaporationPos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                    double y = evaporationPos.getY() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.3D;
                    double z = evaporationPos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                    level.sendParticles(ParticleTypes.POOF, x, y, z, 1, 0.0D, 0.05D, 0.0D, 0.02D);
                }
            }
        }
    }

    private static void processSnowmelt(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        Block groundBlock = groundState.getBlock();
        if (groundBlock != Blocks.SNOW && groundBlock != Blocks.SNOW_BLOCK) {
            return;
        }
        if (!isWarmEnoughToRain(level, groundPos) && level.getMaxLocalRawBrightness(airPos) < 12) {
            return;
        }
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        double meltChance = EnvironmentalConfig.COMMON.snowmeltChance.get() * getReleaseMultiplier(level, groundPos, profile) * profile.snowmeltMultiplier() * SeasonalModifiers.getSnowmeltMultiplier(subSeason);
        
        meltChance *= DayNightModifiers.getSnowmeltMultiplier(level, profile);
        
        if (!rollChance(level, groundPos, meltChance)) {
            return;
        }

        if (groundBlock == Blocks.SNOW && groundState.hasProperty(SnowLayerBlock.LAYERS)) {
            int layers = groundState.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) {
                level.setBlock(groundPos, groundState.setValue(SnowLayerBlock.LAYERS, layers - 1), 3);
            } else {
                level.setBlock(groundPos, Blocks.AIR.defaultBlockState(), 3);
            }
        } else {
            level.setBlock(groundPos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7), 3);
        }
        WPOFluidAccess.addWater(level, airPos, 1);
    }

    private static void processSurfaceIce(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, BiomeEnvironmentProfile profile, ClimateEstimate climate) {
        BlockPos icePos = getSurfaceIcePos(level, groundPos, airPos);
        if (icePos != null) {
            int iceLevels = getSurfaceIceLevels(level.getBlockState(icePos));
            double thawChance = getSurfaceIceThawChance(level, groundPos, airPos, data, profile, climate);
            if (thawChance > 0.0D && rollChance(level, groundPos, thawChance)) {
                meltSurfaceIce(level, icePos, iceLevels, data, profile);
            }
            return;
        }

        BlockPos waterPos = getExposedWaterPos(level, groundPos, airPos);
        if (waterPos == null) {
            return;
        }

        int waterAmount = WPOFluidAccess.getWaterAmount(level, waterPos);
        BlockPos supportPos = waterPos.below();
        if (supportPos.getY() < level.getMinBuildHeight()) {
            return;
        }

        BlockState supportState = level.getBlockState(supportPos);
        if (waterAmount <= 0 || !canFreezeSurface(level, waterPos, airPos, supportPos, supportState)) {
            return;
        }

        double freezeChance = getSurfaceFreezeChance(level, groundPos, airPos, data, profile, climate);
        if (freezeChance <= 0.0D || !rollChance(level, waterPos, freezeChance)) {
            return;
        }

        int remaining = WPOFluidAccess.removeWater(level, waterPos, waterAmount);
        int frozen = Math.max(0, waterAmount - remaining);
        if (frozen > 0) {
            setSurfaceIceLevels(level, waterPos, frozen);
        }
    }

    private static void processCondensation(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, BiomeEnvironmentProfile profile, ClimateEstimate climate) {
        if (level.isRainingAt(airPos) || climate.realTempC() <= 0.75F) {
            return;
        }

        double condensationChance = getCondensationChance(level, groundPos, airPos, data, profile, climate);
        if (condensationChance <= 0.0D) {
            return;
        }

        if (groundState.getBlock() instanceof LayeredCauldronBlock && groundState.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            if (groundState.getValue(BlockStateProperties.LEVEL_CAULDRON) < 3 && rollChance(level, groundPos, condensationChance * 0.65D)) {
                level.setBlock(groundPos, groundState.cycle(BlockStateProperties.LEVEL_CAULDRON), 3);
            }
            return;
        }

        if (!EnvironmentalConfig.COMMON.puddles.get() || !WPOFluidAccess.isChunkLoaded(level, airPos) || !canHoldSurfacePuddle(level, groundPos, airPos, groundState)) {
            return;
        }

        if (!rollChance(level, airPos.above(), condensationChance)) {
            return;
        }

        int steps = 1;
        if (EnvironmentalConfig.COMMON.absorption.get()) {
            steps -= data.addAbsorbed(groundPos, steps, getAbsorptionCapacity(level.getBlockState(groundPos), profile, level));
        }
        if (steps > 0) {
            WPOFluidAccess.addWater(level, airPos, steps);
        }
    }

    private static void processAgriculture(ServerLevel level, BlockPos groundPos, BlockState groundState, EnvironmentalSavedData data) {
        if (!groundState.is(Blocks.FARMLAND) || !groundState.hasProperty(FarmBlock.MOISTURE)) {
            return;
        }

        int absorbed = data.getAbsorbed(groundPos);
        if (absorbed <= 0 || hasNearbyHydratingWater(level, groundPos)) {
            return;
        }

        int moisture = groundState.getValue(FarmBlock.MOISTURE);
        if (moisture >= 7) {
            return;
        }

        level.setBlock(groundPos, groundState.setValue(FarmBlock.MOISTURE, 7), 3);
        data.consumeAbsorbed(groundPos, 1);
    }

    private static BlockPos sampleColumn(BlockPos center, int radius, long cursor) {
        int width = (radius * 2) + 1;
        long mixed = mix(cursor);
        int offsetX = (int) Math.floorMod(mixed, width) - radius;
        int offsetZ = (int) Math.floorMod(mixed >>> 32, width) - radius;
        return center.offset(offsetX, 0, offsetZ);
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return mixed;
    }

    private static boolean rollChance(ServerLevel level, BlockPos pos, double chance) {
        if (chance <= 0.0D) {
            return false;
        }
        if (chance >= 1.0D) {
            return true;
        }
        long seed = mix(level.getGameTime() ^ pos.asLong());
        double normalized = (seed & 0xFFFFFFL) / (double) 0x1000000L;
        return normalized < chance;
    }

    private static int scaleStep(int base, double multiplier) {
        if (base <= 0 || multiplier <= 0.0D) {
            return 0;
        }
        return Math.max(1, Mth.floor((float) (base * multiplier)));
    }

    private static int resolveScaledSteps(ServerLevel level, BlockPos pos, double scaledValue, int maxSteps) {
        if (scaledValue <= 0.0D || maxSteps <= 0) {
            return 0;
        }

        int guaranteed = Mth.floor((float) scaledValue);
        double fractional = scaledValue - guaranteed;
        int steps = Math.min(maxSteps, guaranteed);
        if (steps < maxSteps && fractional > 0.0D && rollChance(level, pos, fractional)) {
            steps++;
        }
        return steps;
    }

    private static double getEvaporationVisualChance(BiomeEnvironmentProfile profile, double evaporationChance) {
        if (evaporationChance <= 0.0D) {
            return 0.0D;
        }

        double activity = Mth.clamp((evaporationChance - 0.02D) / 0.18D, 0.0D, 1.0D);
        double heatBias = Mth.clamp((profile.evaporationMultiplier() - 0.65D) / 0.85D, 0.0D, 1.0D);

        // Keep cold and wet biomes visually restrained, and let hot/dry biomes ramp harder.
        double visualChance = activity * (0.15D + heatBias * 0.35D);
        visualChance += activity * activity * (0.10D + heatBias * 0.20D);

        if (heatBias < 0.25D) {
            visualChance *= 0.75D + (heatBias * 0.25D);
        }

        return Mth.clamp(visualChance, 0.0D, 0.9D);
    }

    private static double getRainIntensity(ServerLevel level, BlockPos pos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        double intensity = EnvironmentalConfig.COMMON.rainIntensity.get() * profile.rainIntensityMultiplier();
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        intensity *= SeasonalModifiers.getRainIntensityMultiplier(subSeason);
        
        if (EnvironmentalConfig.COMMON.floods.get() && level.isThundering()) {
            intensity *= EnvironmentalConfig.COMMON.stormIntensity.get() * SeasonalModifiers.getStormMultiplier(subSeason);
        }
        
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            intensity *= Mth.clamp(1.0D - (0.6D * profile.droughtSensitivity() * SeasonalModifiers.getDroughtSensitivity(subSeason)), 0.2D, 1.0D);
        }
        
        if (getEffectiveBiomeTemperature(level, pos) < 0.15F) {
            intensity *= 0.75D;
        }
        
        intensity *= SeasonalModifiers.getCollectorMultiplier(subSeason);
        intensity *= DayNightModifiers.getCollectorMultiplier(level, profile);
        
        return intensity;
    }

    private static double getGlobalRainMemoryMultiplier(ServerLevel level, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        double multiplier = EnvironmentalConfig.COMMON.rainIntensity.get() * profile.rainIntensityMultiplier() * profile.retentionMultiplier();
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        multiplier *= SeasonalModifiers.getRainIntensityMultiplier(subSeason);
        multiplier *= SeasonalModifiers.getRetentionMultiplier(subSeason);
        
        if (EnvironmentalConfig.COMMON.floods.get() && level.isThundering()) {
            multiplier *= EnvironmentalConfig.COMMON.stormIntensity.get() * SeasonalModifiers.getStormMultiplier(subSeason);
        }
        
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            multiplier *= Mth.clamp(1.0D - (0.6D * profile.droughtSensitivity() * SeasonalModifiers.getDroughtSensitivity(subSeason)), 0.2D, 1.0D);
        }
        
        return multiplier;
    }

    private static double getGlobalDryDecayMultiplier(ServerLevel level, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        double multiplier = profile.evaporationMultiplier() / Math.max(0.45D, profile.retentionMultiplier());
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        multiplier *= SeasonalModifiers.getEvaporationMultiplier(subSeason);
        
        multiplier *= DayNightModifiers.getEvaporationMultiplier(level, profile);
        
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            multiplier += EnvironmentalConfig.COMMON.droughtEvaporationBonus.get() * 0.35D * profile.droughtSensitivity() * SeasonalModifiers.getDroughtSensitivity(subSeason);
        }
        
        multiplier *= EnvironmentalConfig.COMMON.evaporationMultiplierOverride.get();
        
        return multiplier;
    }

    private static double getEvaporationMultiplier(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        double multiplier = profile.evaporationMultiplier();
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        multiplier *= SeasonalModifiers.getEvaporationMultiplier(subSeason);
        
        multiplier *= DayNightModifiers.getEvaporationMultiplier(level, profile);
        
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            multiplier += EnvironmentalConfig.COMMON.droughtEvaporationBonus.get() * profile.droughtSensitivity() * SeasonalModifiers.getDroughtSensitivity(subSeason);
        }
        
        if (level.canSeeSky(airPos) && level.getMaxLocalRawBrightness(airPos) >= 13) {
            multiplier += EnvironmentalConfig.COMMON.sunlightEvaporationBonus.get();
        }
        double biomeTemp = getEffectiveBiomeTemperature(level, groundPos);
        if (biomeTemp >= 1.0F) {
            double heatFactor = Mth.clamp((biomeTemp - 1.0F) / 0.5F, 0.0D, 1.0D);
            double hotBonus = EnvironmentalConfig.COMMON.hotBiomeEvaporationBonus.get();
            multiplier += hotBonus * (0.5D + heatFactor * 0.5D);
        }
        if (hasNearbyHeatSource(level, groundPos)) {
            multiplier += EnvironmentalConfig.COMMON.lavaEvaporationBonus.get();
        }
        if (level.isRainingAt(airPos)) {
            multiplier *= 0.35D;
        }
        
        return multiplier;
    }

    private static boolean hasNearbyHeatSource(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState state = level.getBlockState(pos.relative(direction));
            if (state.is(Blocks.MAGMA_BLOCK) || state.getFluidState().getType().isSame(Blocks.LAVA.defaultBlockState().getFluidState().getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldRelease(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, int waterAmount, boolean rainingHere) {
        if (waterAmount >= WPOConfig.MAX_FLUID_LEVEL) {
            return false;
        }
        // Release when it's DRY (not raining) - simulates drying out
        if (rainingHere) {
            return false;
        }
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            return false;
        }
        return true;
    }

    private static BlockPos getExposedWaterPos(ServerLevel level, BlockPos groundPos, BlockPos airPos) {
        if (!level.canSeeSky(airPos)) {
            return null;
        }

        if (WPOFluidAccess.getWaterAmount(level, airPos) > 0) {
            return airPos;
        }

        if (WPOFluidAccess.getWaterAmount(level, groundPos) > 0) {
            return groundPos;
        }

        return null;
    }

    private static boolean canHoldSurfacePuddle(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState) {
        if (!level.canSeeSky(airPos)) {
            return false;
        }
        if (!isWarmEnoughToRain(level, groundPos)) {
            return false;
        }
        if (!groundState.getFluidState().isEmpty()) {
            return false;
        }
        if (groundState.is(BlockTags.LEAVES)) {
            return false;
        }
        if (!groundState.isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }
        Block groundBlock = groundState.getBlock();
        return groundBlock != Blocks.SNOW
            && groundBlock != Blocks.SNOW_BLOCK
            && groundBlock != Blocks.ICE
            && groundBlock != EnvironmentalContent.SURFACE_ICE.get();
    }

    private static int getAmbientTargetWater(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        int cap = EnvironmentalConfig.COMMON.ambientWetnessCap.get();
        int maxLevels = Math.min(WPOConfig.MAX_FLUID_LEVEL, EnvironmentalConfig.COMMON.ambientMaxPuddleLevels.get());
        if (cap <= 0 || maxLevels <= 0 || data.getAmbientWetness() <= 0) {
            return 0;
        }

        double wetness = data.getAmbientWetness() / (double) cap;
        double terrainFactor = getTerrainRetentionFactor(level, groundPos) * profile.retentionMultiplier();
        double noiseFactor = getColumnNoise(level, airPos);
        int target = Mth.clamp(Mth.floor((float) (wetness * terrainFactor * noiseFactor * maxLevels)), 0, maxLevels);
        if (target <= 0 && level.isRainingAt(airPos) && wetness >= 0.25D && terrainFactor >= 0.95D) {
            return 1;
        }
        return target;
    }

    private static double getTerrainRetentionFactor(ServerLevel level, BlockPos groundPos) {
        int basinBias = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int neighborSurfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                groundPos.getX() + direction.getStepX(),
                groundPos.getZ() + direction.getStepZ()
            ) - 1;
            basinBias += Mth.clamp(neighborSurfaceY - groundPos.getY(), -2, 2);
        }
        return Mth.clamp(1.0D + (basinBias * 0.12D), 0.55D, 1.55D);
    }

    private static double getColumnNoise(ServerLevel level, BlockPos pos) {
        long mixed = mix(level.getSeed() ^ pos.asLong());
        double normalized = ((mixed >>> 16) & 0xFFFFL) / 65535.0D;
        return 0.65D + (normalized * 0.7D);
    }

    private static double getReleaseMultiplier(ServerLevel level, BlockPos pos, BiomeEnvironmentProfile profile) {
        double multiplier = profile.releaseMultiplier();
        
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        multiplier *= SeasonalModifiers.getReleaseMultiplier(subSeason);
        
        multiplier *= DayNightModifiers.getReleaseMultiplier(level);
        
        if (isWarmEnoughToRain(level, pos) && level.getMaxLocalRawBrightness(pos.above()) >= 12) {
            multiplier *= 1.15D;
        }
        return multiplier;
    }

    private static int getAbsorptionCapacity(BlockState state, BiomeEnvironmentProfile profile, ServerLevel level) {
        int baseCapacity = getBaseAbsorptionCapacity(state);
        
        double multiplier = profile.absorptionMultiplier();
        
        if (SeasonManager.isSeasonsEnabled()) {
            SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
            multiplier *= SeasonalModifiers.getAbsorptionMultiplier(subSeason);
            multiplier *= DayNightModifiers.getAbsorptionMultiplier(level);
        }

        multiplier *= EnvironmentalConfig.COMMON.absorptionMultiplierOverride.get();
        
        return Math.max(0, Mth.floor((float) (baseCapacity * multiplier)));
    }

    private static int getBaseAbsorptionCapacity(BlockState state) {
        Block block = state.getBlock();
        
        // Very high absorption - spongy, organic materials
        if (block == Blocks.MUD) return 6;
        if (block == Blocks.MYCELIUM) return 4;
        if (block == Blocks.PODZOL) return 4;
        if (block == Blocks.MOSS_BLOCK) return 4;
        if (block == Blocks.MUDDY_MANGROVE_ROOTS) return 4;
        
        // High absorption - farmland and grassy earth
        if (block == Blocks.FARMLAND) return 5;
        if (block == Blocks.GRASS_BLOCK) return 3;
        if (block == Blocks.DIRT) return 3;
        if (block == Blocks.ROOTED_DIRT) return 3;
        
        // Medium absorption - sandy materials
        if (block == Blocks.SAND) return 2;
        if (block == Blocks.RED_SAND) return 2;
        
        // Low absorption - coarse/gravel
        if (block == Blocks.COARSE_DIRT) return 1;
        if (block == Blocks.GRAVEL) return 1;
        
        // Cave dripstone - water passes through slowly
        if (block == Blocks.DRIPSTONE_BLOCK) return 1;
        if (block == Blocks.POINTED_DRIPSTONE) return 1;
        
        // Snow - handled by snowmelt system
        if (block == Blocks.SNOW_BLOCK) return 0;
        if (block == Blocks.SNOW) return 0;
        
        return 0;
    }

    static double getAgricultureGrowthChance(ServerLevel level, BlockPos farmlandPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        if (!EnvironmentalConfig.COMMON.agriculture.get() || data.getAbsorbed(farmlandPos) <= 0) {
            return 0.0D;
        }
        double multiplier = getAgricultureMultiplier(level, farmlandPos, data, profile);
        return EnvironmentalConfig.COMMON.agricultureGrowthBoostChance.get() * multiplier;
    }

    static ColumnMetrics captureColumnMetrics(ServerLevel level, BlockPos groundPos, BlockPos airPos) {
        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        BiomeEnvironmentProfile profile = BiomeProfileManager.getProfile(level, groundPos);
        EnvironmentalSavedData data = EnvironmentalSavedData.get(level);

        double evapMult = profile.evaporationMultiplier() * SeasonalModifiers.getEvaporationMultiplier(subSeason) * DayNightModifiers.getEvaporationMultiplier(level, profile);
        double rainMult = profile.rainChanceMultiplier() * profile.rainIntensityMultiplier() * SeasonalModifiers.getRainIntensityMultiplier(subSeason);
        double absorbMult = profile.absorptionMultiplier() * SeasonalModifiers.getAbsorptionMultiplier(subSeason) * DayNightModifiers.getAbsorptionMultiplier(level);
        double releaseMult = profile.releaseMultiplier() * SeasonalModifiers.getReleaseMultiplier(subSeason) * DayNightModifiers.getReleaseMultiplier(level);
        double snowmeltMult = profile.snowmeltMultiplier() * SeasonalModifiers.getSnowmeltMultiplier(subSeason) * DayNightModifiers.getSnowmeltMultiplier(level, profile);
        double stormMult = profile.stormMultiplier() * SeasonalModifiers.getStormMultiplier(subSeason);

        int tropicalPhase = SeasonManager.isTropicalCycle() ? (SeasonManager.getTropicalPhase(level) == SeasonManager.TropicalPhase.WET ? 0 : 1) : 0;
        int surfaceWater = getLocalSurfaceWaterLevels(level, groundPos, airPos);
        int absorbed = data.getAbsorbed(groundPos);
        int snowLayers = countSnowLayers(level, groundPos);
        int surfaceIceLevels = getLocalSurfaceIceLevels(level, groundPos, airPos);
        int farmlandMoisture = getVisibleFarmlandMoisture(level, groundPos);
        ClimateEstimate realWorld = computeRealWorldConditions(
            level,
            groundPos,
            airPos,
            data,
            profile,
            subSeason,
            level.isRainingAt(airPos),
            level.isThundering() && level.canSeeSky(airPos),
            surfaceWater,
            absorbed,
            snowLayers,
            surfaceIceLevels
        );
        float condensationChancePct = (float) (getCondensationChance(level, groundPos, airPos, data, profile, realWorld) * 100.0D);
        float freezeChancePct = (float) (getSurfaceFreezeChance(level, groundPos, airPos, data, profile, realWorld) * 100.0D);
        float thawChancePct = (float) (getSurfaceIceThawChance(level, groundPos, airPos, data, profile, realWorld) * 100.0D);
        float agricultureGrowthChancePct = (float) (getAgricultureGrowthChance(level, groundPos, data, profile) * 100.0D);

        return new ColumnMetrics(
            subSeason.index(),
            SeasonManager.getWorldDay(level),
            level.getDayTime(),
            SeasonManager.isSeasonsEnabled(),
            SeasonManager.isTropicalCycle(),
            tropicalPhase,
            level.getBiome(groundPos).unwrapKey().map(Object::toString).orElse("unknown"),
            getEffectiveBiomeTemperature(level, groundPos),
            profile.archetype().name(),
            surfaceWater,
            absorbed,
            snowLayers,
            surfaceIceLevels,
            farmlandMoisture,
            level.isRaining(),
            level.isThundering(),
            rainMult,
            evapMult,
            absorbMult,
            releaseMult,
            snowmeltMult,
            stormMult,
            data.isDroughtActive(),
            EnvironmentalConfig.COMMON.absorption.get(),
            EnvironmentalConfig.COMMON.evaporation.get(),
            EnvironmentalConfig.COMMON.snowmelt.get(),
            EnvironmentalConfig.COMMON.condensation.get(),
            EnvironmentalConfig.COMMON.surfaceIce.get(),
            EnvironmentalConfig.COMMON.agriculture.get(),
            EnvironmentalConfig.COMMON.floods.get(),
            EnvironmentalConfig.COMMON.distantRainCatchup.get(),
            realWorld.realTempC(),
            realWorld.realHumidityPct(),
            realWorld.realWindMs(),
            realWorld.precipChancePct(),
            realWorld.precipMmHr(),
            condensationChancePct,
            freezeChancePct,
            thawChancePct,
            agricultureGrowthChancePct
        );
    }

    private static void sendDebugPackets(ServerLevel level) {
        // Send debug data to all players every 10 ticks
        if (level.getGameTime() % 10L != 0L || level.players().isEmpty()) {
            return;
        }

        Player player = level.players().get(0);
        BlockPos playerPos = player.blockPosition();
        BlockPos groundPos = new BlockPos(playerPos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos.getX(), playerPos.getZ()) - 1, playerPos.getZ());
        BlockPos airPos = groundPos.above();
        ColumnMetrics metrics = captureColumnMetrics(level, groundPos, airPos);

        // Block player is looking at
        BlockPos targetPos = BlockPos.ZERO;
        String targetBlock = "";
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            targetPos = blockHit.getBlockPos();
            targetBlock = level.getBlockState(targetPos).getBlock().toString();
        }

        EnvDebugPacket packet = new EnvDebugPacket(
                metrics.subSeasonIndex(),
                metrics.worldDay(),
                metrics.dayTime(),
                metrics.seasonsEnabled(),
                metrics.tropicalCycle(),
                metrics.tropicalPhaseIndex(),
                metrics.biomeId(),
                metrics.biomeTemp(),
                metrics.archetype(),
                metrics.surfaceWaterLevels(),
                metrics.absorbedWater(),
                metrics.snowLayers(),
                metrics.surfaceIceLevels(),
                metrics.farmlandMoisture(),
                metrics.isRaining(),
                metrics.isThundering(),
                metrics.rainMultiplier(),
                metrics.evaporationMultiplier(),
                metrics.absorptionMultiplier(),
                metrics.releaseMultiplier(),
                metrics.snowmeltMultiplier(),
                metrics.stormMultiplier(),
                metrics.droughtActive(),
                metrics.absorptionEnabled(),
                metrics.evaporationEnabled(),
                metrics.snowmeltEnabled(),
                metrics.condensationEnabled(),
                metrics.surfaceIceEnabled(),
                metrics.agricultureEnabled(),
                metrics.floodsEnabled(),
                metrics.distantRainCatchupEnabled(),
                targetPos,
                targetBlock,
                metrics.realTempC(),
                metrics.realHumidityPct(),
                metrics.realWindMs(),
                metrics.precipChancePct(),
                metrics.precipMmHr(),
                metrics.condensationChancePct(),
                metrics.freezingChancePct(),
                metrics.thawChancePct(),
                metrics.agricultureGrowthChancePct()
        );

        EnvPacketHandler.send((net.minecraft.server.level.ServerPlayer) player, packet);
    }

    private static ClimateEstimate computeRealWorldConditions(
            ServerLevel level,
            BlockPos groundPos,
            BlockPos airPos,
            EnvironmentalSavedData data,
            BiomeEnvironmentProfile profile,
            SeasonManager.SubSeason subSeason,
            boolean isRaining,
            boolean isThundering,
            int surfaceWater,
            int absorbedWater,
            int snowLayers,
            int surfaceIceLevels
    ) {
        BiomeEnvironmentProfile.Signals signals = BiomeEnvironmentProfile.seedSignalsFor(profile.archetype());
        double signalScale = 0.55D + (profile.confidence() * 0.45D);

        double heatSignal = signals.heat() * signalScale;
        double moistureSignal = signals.moisture() * signalScale;
        double retentionSignal = signals.retention() * signalScale;
        double biomeTempSignal = signals.biomeTemp() * signalScale;
        double biomeHumidSignal = signals.biomeHumid() * signalScale;
        double opennessSignal = signals.openness() * signalScale;
        double hydrologySignal = signals.hydrology() * signalScale;
        double lowlandSignal = signals.lowlandness() * signalScale;
        double marineSignal = signals.marineExposure() * signalScale;

        double exposure = getSurfaceExposure(level, airPos);
        double wetnessRatio = getWetnessRatio(data);
        double solarFactor = getSolarCycleFactor(level);
        double evaporationPulse = profile.evaporationMultiplier() * SeasonalModifiers.getEvaporationMultiplier(subSeason) * DayNightModifiers.getEvaporationMultiplier(level, profile);
        double rainChanceBias = profile.rainChanceMultiplier() * SeasonalModifiers.getRainChanceMultiplier(subSeason) * DayNightModifiers.getRainChanceMultiplier(level);
        double rainIntensityBias = profile.rainIntensityMultiplier() * SeasonalModifiers.getRainIntensityMultiplier(subSeason);
        double stormBias = profile.stormMultiplier() * SeasonalModifiers.getStormMultiplier(subSeason);
        double snowmeltPulse = profile.snowmeltMultiplier() * SeasonalModifiers.getSnowmeltMultiplier(subSeason) * DayNightModifiers.getSnowmeltMultiplier(level, profile);

        double biomeClimateC = (getEffectiveBiomeTemperature(level, groundPos) - 0.8D) * 18.0D;
        double archetypeClimateC = 14.0D + (heatSignal * 16.5D) + (biomeTempSignal * 8.5D);
        double blend = Mth.clamp(0.55D + profile.confidence() * 0.25D, 0.45D, 0.85D);
        double baseTemp = Mth.lerp(blend, biomeClimateC, archetypeClimateC);
        baseTemp += lowlandSignal * 0.9D;

        double seasonFactor = SeasonManager.getSeasonTemperatureModifier(level);
        double seasonalVariance = 10.0D + (1.0D - profile.retentionMultiplier()) * 3.0D + (1.0D - exposure) * 2.5D;
        seasonalVariance *= switch (subSeason.season()) {
            case SUMMER, WINTER -> 1.1D;
            case SPRING, AUTUMN -> 0.95D;
        };
        seasonalVariance *= switch (subSeason.phase()) {
            case EARLY, LATE -> 1.05D;
            case MID -> 0.97D;
        };
        double seasonalHeating = seasonFactor * seasonalVariance;

        double diurnalAmplitude = 7.0D + Math.max(0.0D, opennessSignal) * 4.5D + Math.max(0.0D, heatSignal) * 2.0D;
        diurnalAmplitude *= 1.0D - (wetnessRatio * 0.28D) - (marineSignal * 0.10D);
        diurnalAmplitude *= 0.40D + (exposure * 0.60D);
        diurnalAmplitude = Mth.clamp(diurnalAmplitude, 3.0D, 14.0D);
        double solarHeating = solarFactor * diurnalAmplitude;

        double altitudeCooling = Math.max(0.0D, groundPos.getY() - level.getSeaLevel()) * 0.0065D;
        altitudeCooling *= 1.0D + (1.0D - exposure) * 0.15D;

        double weatherCooling = 0.0D;
        if (isRaining) {
            weatherCooling -= 1.8D + profile.evaporationMultiplier() * 0.7D + wetnessRatio * 1.3D;
        }
        if (isThundering) {
            weatherCooling -= 1.2D + profile.stormMultiplier() * 1.4D;
        }

        double wetSurfaceCooling = (surfaceWater * 0.55D) + (absorbedWater * 0.22D) + (surfaceIceLevels * 0.38D) + (wetnessRatio * 3.0D) + (Math.max(0.0D, retentionSignal) * 0.9D);
        wetSurfaceCooling *= 1.0D + (Math.max(0.0D, opennessSignal) * 0.25D);

        double snowCooling = snowLayers * (0.9D + (1.0D - profile.snowmeltMultiplier()) * 0.35D);

        double windPreview = estimateWindSpeed(
            level,
            profile,
            signals,
            data,
            surfaceWater,
            absorbedWater,
            snowLayers,
            surfaceIceLevels,
            isRaining,
            isThundering,
            solarFactor,
            wetnessRatio,
            evaporationPulse,
            rainChanceBias,
            stormBias,
            subSeason
        );

        double realTemp = baseTemp + seasonalHeating + solarHeating - altitudeCooling + weatherCooling - wetSurfaceCooling - snowCooling - (windPreview * 0.12D);
        realTemp = Mth.clamp(realTemp, -45.0D, 58.0D);

        double dewGap = 23.0D
            - (moistureSignal * 2.5D)
            - (biomeHumidSignal * 3.0D)
            - (Math.max(0.0D, hydrologySignal) * 2.1D)
            - (Math.max(0.0D, marineSignal) * 2.4D)
            - (Math.max(0.0D, retentionSignal) * 1.8D)
            - (wetnessRatio * 6.0D)
            - (surfaceWater * 1.1D)
            - (absorbedWater * 0.5D)
            - (snowLayers * 0.8D)
            - (Math.max(0.0D, evaporationPulse - 0.55D) * 2.2D)
            - (Math.max(0.0D, rainChanceBias - 0.9D) * 0.7D)
            - (Math.max(0.0D, snowmeltPulse - 0.65D) * 0.6D)
            + (Math.max(0.0D, heatSignal) * 2.3D)
            + (Math.max(0.0D, opennessSignal) * 1.2D)
            + Math.max(0.0D, windPreview - 2.0D) * 0.35D;
        if (data.isDroughtActive()) {
            dewGap += 1.8D + (profile.droughtSensitivity() * 2.5D);
        }
        if (isRaining) {
            dewGap -= 5.0D + rainIntensityBias * 1.5D;
        }
        if (isThundering) {
            dewGap -= 2.5D + stormBias * 0.8D;
        }
        dewGap = Mth.clamp(dewGap, 0.3D, 28.0D);

        double dewPointCandidate = realTemp - dewGap;
        double humidityPct = relativeHumidity(realTemp, dewPointCandidate);
        double cloudCoverPct = estimateCloudCover(realTemp, humidityPct, dewPointCandidate, windPreview, wetnessRatio, solarFactor, evaporationPulse, rainChanceBias, snowmeltPulse, signals, profile, isRaining, isThundering, data.isDroughtActive());
        double precipChancePct = estimatePrecipChance(realTemp, humidityPct, dewPointCandidate, cloudCoverPct, windPreview, solarFactor, signals, profile, subSeason, wetnessRatio, rainChanceBias, rainIntensityBias, stormBias, snowmeltPulse, isRaining, isThundering, data.isDroughtActive());
        double precipMmHr = estimatePrecipitationRate(realTemp, humidityPct, cloudCoverPct, precipChancePct, windPreview, solarFactor, signals, profile, rainIntensityBias, stormBias, snowmeltPulse, isRaining, isThundering);

        return new ClimateEstimate(
            (float) realTemp,
            (float) humidityPct,
            (float) windPreview,
            (float) precipChancePct,
            (float) precipMmHr
        );
    }

    private static double getSolarCycleFactor(ServerLevel level) {
        double ticks = level.getDayTime() % 24000L;
        double angle = ((ticks - 6000.0D) / 24000.0D) * Math.PI * 2.0D;
        return Mth.clamp(Math.cos(angle), -1.0D, 1.0D);
    }

    private static double getSurfaceExposure(ServerLevel level, BlockPos airPos) {
        double sky = level.canSeeSky(airPos) ? 1.0D : 0.25D;
        double light = Mth.clamp((level.getMaxLocalRawBrightness(airPos) - 4.0D) / 11.0D, 0.0D, 1.0D);
        return Mth.clamp((sky * 0.7D) + (light * 0.3D), 0.2D, 1.0D);
    }

    private static double getWetnessRatio(EnvironmentalSavedData data) {
        int cap = EnvironmentalConfig.COMMON.ambientWetnessCap.get();
        if (cap <= 0) {
            return 0.0D;
        }
        return Mth.clamp(data.getAmbientWetness() / (double) cap, 0.0D, 1.0D);
    }

    private static double estimateWindSpeed(
            ServerLevel level,
            BiomeEnvironmentProfile profile,
            BiomeEnvironmentProfile.Signals signals,
            EnvironmentalSavedData data,
            int surfaceWater,
            int absorbedWater,
            int snowLayers,
            int surfaceIceLevels,
            boolean isRaining,
            boolean isThundering,
            double solarFactor,
            double wetnessRatio,
            double evaporationPulse,
            double rainChanceBias,
            double stormBias,
            SeasonManager.SubSeason subSeason
    ) {
        double openness = Mth.clamp((signals.openness() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double marineExposure = Mth.clamp(signals.marineExposure(), 0.0D, 1.0D);
        double dryExposure = Mth.clamp(-signals.moisture(), 0.0D, 1.0D);
        double terrainRelease = Mth.clamp((signals.lowlandness() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double seasonalInstability = 1.0D - SeasonManager.getTransitionFactor(level);
        seasonalInstability += switch (subSeason.phase()) {
            case EARLY, LATE -> 0.10D;
            case MID -> 0.0D;
        };

        double wind = 0.85D
            + openness * 4.4D
            + marineExposure * 2.8D
            + dryExposure * 1.2D
            + terrainRelease * 0.7D
            + seasonalInstability * 0.8D
            + Math.abs(solarFactor) * 0.45D;
        wind += Math.max(0.0D, evaporationPulse - 0.55D) * 0.7D;
        wind += Math.max(0.0D, rainChanceBias - 0.9D) * 0.35D;
        wind += wetnessRatio * 0.3D;
        wind += Math.min(2.0D, surfaceWater * 0.16D + absorbedWater * 0.08D + snowLayers * 0.20D + surfaceIceLevels * 0.14D);
        wind *= 1.0D + Math.max(0.0D, profile.stormMultiplier() - 1.0D) * 0.20D + Math.max(0.0D, stormBias - 1.0D) * 0.08D;
        if (isRaining) {
            wind += 0.75D + wetnessRatio * 0.55D + profile.rainIntensityMultiplier() * 0.15D + rainChanceBias * 0.05D;
        }
        if (isThundering) {
            wind += 2.1D + profile.stormMultiplier() * 0.95D + stormBias * 0.12D;
        }
        if (data.isDroughtActive()) {
            wind += 0.35D + dryExposure * 0.65D;
        }
        return Mth.clamp(wind, 0.3D, 28.0D);
    }

    private static double estimateCloudCover(
            double tempC,
            double humidityPct,
            double dewPointC,
            double windMs,
            double wetnessRatio,
            double solarFactor,
            double evaporationPulse,
            double rainChanceBias,
            double snowmeltPulse,
            BiomeEnvironmentProfile.Signals signals,
            BiomeEnvironmentProfile profile,
            boolean isRaining,
            boolean isThundering,
            boolean droughtActive
    ) {
        double dewSpread = Math.max(0.0D, tempC - dewPointC);
        double saturation = Mth.clamp(humidityPct / 100.0D, 0.0D, 1.0D);
        double moisture = Mth.clamp((signals.moisture() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double hydrology = Mth.clamp((signals.hydrology() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double marine = Mth.clamp(signals.marineExposure(), 0.0D, 1.0D);
        double lowland = Mth.clamp((signals.lowlandness() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double openness = Mth.clamp((signals.openness() + 1.0D) * 0.5D, 0.0D, 1.0D);

        double cloud = 0.12D
            + saturation * 0.34D
            + Mth.clamp(1.0D - (dewSpread / 16.0D), 0.0D, 1.0D) * 0.22D
            + moisture * 0.08D
            + hydrology * 0.10D
            + marine * 0.09D
            + lowland * 0.05D
            + wetnessRatio * 0.08D
            + Math.max(0.0D, solarFactor) * Math.max(0.0D, signals.heat()) * 0.05D
            + Math.max(0.0D, evaporationPulse - 0.55D) * 0.05D
            + Math.max(0.0D, snowmeltPulse - 0.55D) * 0.03D
            + Math.max(0.0D, rainChanceBias - 0.9D) * 0.04D
            - Math.max(0.0D, windMs - 3.0D) * 0.015D
            - openness * 0.05D;
        if (isRaining) {
            cloud += 0.20D + profile.rainChanceMultiplier() * 0.05D;
        }
        if (isThundering) {
            cloud += 0.14D + profile.stormMultiplier() * 0.05D;
        }
        if (droughtActive) {
            cloud -= 0.14D + profile.droughtSensitivity() * 0.05D;
        }
        return Mth.clamp(cloud * 100.0D, 0.0D, 100.0D);
    }

    private static double estimatePrecipChance(
            double tempC,
            double humidityPct,
            double dewPointC,
            double cloudCoverPct,
            double windMs,
            double solarFactor,
            BiomeEnvironmentProfile.Signals signals,
            BiomeEnvironmentProfile profile,
            SeasonManager.SubSeason subSeason,
            double wetnessRatio,
            double rainChanceBias,
            double rainIntensityBias,
            double stormBias,
            double snowmeltPulse,
            boolean isRaining,
            boolean isThundering,
            boolean droughtActive
    ) {
        double dewSpread = Math.max(0.0D, tempC - dewPointC);
        double saturation = Mth.clamp(humidityPct / 100.0D, 0.0D, 1.0D);
        double cloudSignal = Mth.clamp((cloudCoverPct - 20.0D) / 80.0D, 0.0D, 1.0D);
        double moisture = Mth.clamp((signals.moisture() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double hydrology = Mth.clamp((signals.hydrology() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double marine = Mth.clamp(signals.marineExposure(), 0.0D, 1.0D);
        double lowland = Mth.clamp((signals.lowlandness() + 1.0D) * 0.5D, 0.0D, 1.0D);
        double coldLift = tempC < 2.0D ? (2.0D - tempC) * 0.12D : 0.0D;
        double warmConv = Math.max(0.0D, solarFactor) * Math.max(0.0D, signals.heat()) * 0.12D;
        double seasonalSupport = switch (subSeason.season()) {
            case SPRING -> 0.14D;
            case SUMMER -> 0.02D;
            case AUTUMN -> 0.09D;
            case WINTER -> -0.04D;
        };

        double score = -2.15D
            + cloudSignal * 2.9D
            + saturation * 1.45D
            + Mth.clamp(1.0D - (dewSpread / 18.0D), 0.0D, 1.0D) * 1.35D
            + moisture * 0.35D
            + hydrology * 0.35D
            + marine * 0.22D
            + lowland * 0.18D
            + wetnessRatio * 0.24D
            + warmConv
            + rainChanceBias * 0.28D
            + rainIntensityBias * 0.08D
            + stormBias * 0.10D
            + snowmeltPulse * 0.05D
            + seasonalSupport
            + coldLift
            + (isRaining ? 1.35D : 0.0D)
            + (isThundering ? 0.85D : 0.0D)
            - (droughtActive ? 1.35D + profile.droughtSensitivity() * 0.55D : 0.0D)
            - Math.max(0.0D, tempC - 28.0D) * 0.04D
            - Math.max(0.0D, windMs - 18.0D) * 0.03D;
        return Mth.clamp(logistic(score) * 100.0D, 0.0D, 100.0D);
    }

    private static double estimatePrecipitationRate(
            double tempC,
            double humidityPct,
            double cloudCoverPct,
            double precipChancePct,
            double windMs,
            double solarFactor,
            BiomeEnvironmentProfile.Signals signals,
            BiomeEnvironmentProfile profile,
            double rainIntensityBias,
            double stormBias,
            double snowmeltPulse,
            boolean isRaining,
            boolean isThundering
    ) {
        double saturation = Mth.clamp(humidityPct / 100.0D, 0.0D, 1.0D);
        double cloudWeight = Mth.clamp((cloudCoverPct - 20.0D) / 80.0D, 0.0D, 1.0D);
        double chanceWeight = Mth.clamp(precipChancePct / 100.0D, 0.0D, 1.0D);
        double marine = Mth.clamp(signals.marineExposure(), 0.0D, 1.0D);
        double hydrology = Mth.clamp((signals.hydrology() + 1.0D) * 0.5D, 0.0D, 1.0D);

        double intensity = chanceWeight * (0.12D + cloudWeight * 0.55D)
            + saturation * 0.25D
            + cloudWeight * 0.35D
            + marine * 0.08D
            + hydrology * 0.06D
            + rainIntensityBias * 0.15D
            + stormBias * 0.10D
            + snowmeltPulse * 0.04D
            + (isRaining ? 0.55D : 0.0D)
            + (isThundering ? 0.9D : 0.0D);
        intensity += profile.rainIntensityMultiplier() * 0.04D;

        double tempModifier = tempC <= 0.0D ? 0.85D : tempC < 3.0D ? 0.9D : tempC > 28.0D ? 0.85D : 1.0D;
        double windModifier = 1.0D + Mth.clamp((windMs - 5.0D) / 20.0D, 0.0D, 0.35D);
        double solarModifier = 1.0D + Math.max(0.0D, solarFactor) * 0.08D;
        double rate = intensity * tempModifier * windModifier * solarModifier * 8.0D;
        if (isRaining) {
            rate = Math.max(rate, 0.35D + rainIntensityBias * 0.75D);
        }
        if (isThundering) {
            rate = Math.max(rate, 1.4D + stormBias * 1.1D);
        }
        return Mth.clamp(rate, 0.0D, 18.0D);
    }

    private static double relativeHumidity(double tempC, double dewPointC) {
        double a = 17.625D;
        double b = 243.04D;
        double dewTerm = (a * dewPointC) / (b + dewPointC);
        double tempTerm = (a * tempC) / (b + tempC);
        return Mth.clamp(Math.exp(dewTerm - tempTerm) * 100.0D, 0.0D, 100.0D);
    }

    private static double logistic(double value) {
        double clamped = Mth.clamp(value, -12.0D, 12.0D);
        return 1.0D / (1.0D + Math.exp(-clamped));
    }

    static float getEffectiveBiomeTemperature(ServerLevel level, BlockPos pos) {
        Float override = EnvironmentalBenchmarkManager.getForcedBiomeTemperature(level, pos);
        return override != null ? override : level.getBiome(pos).value().getBaseTemperature();
    }

    private static boolean isWarmEnoughToRain(ServerLevel level, BlockPos pos) {
        Float override = EnvironmentalBenchmarkManager.getForcedBiomeTemperature(level, pos);
        if (override != null) {
            return override >= 0.15F;
        }
        return level.getBiome(pos).value().warmEnoughToRain(pos);
    }

    static record ColumnMetrics(
            int subSeasonIndex,
            long worldDay,
            long dayTime,
            boolean seasonsEnabled,
            boolean tropicalCycle,
            int tropicalPhaseIndex,
            String biomeId,
            float biomeTemp,
            String archetype,
            int surfaceWaterLevels,
            int absorbedWater,
            int snowLayers,
            int surfaceIceLevels,
            int farmlandMoisture,
            boolean isRaining,
            boolean isThundering,
            double rainMultiplier,
            double evaporationMultiplier,
            double absorptionMultiplier,
            double releaseMultiplier,
            double snowmeltMultiplier,
            double stormMultiplier,
            boolean droughtActive,
            boolean absorptionEnabled,
            boolean evaporationEnabled,
            boolean snowmeltEnabled,
            boolean condensationEnabled,
            boolean surfaceIceEnabled,
            boolean agricultureEnabled,
            boolean floodsEnabled,
            boolean distantRainCatchupEnabled,
            float realTempC,
            float realHumidityPct,
            float realWindMs,
            float precipChancePct,
            float precipMmHr,
            float condensationChancePct,
            float freezingChancePct,
            float thawChancePct,
            float agricultureGrowthChancePct
    ) {
    }

    private record ClimateEstimate(
            float realTempC,
            float realHumidityPct,
            float realWindMs,
            float precipChancePct,
            float precipMmHr
    ) {
    }

    private static int countSnowLayers(ServerLevel level, BlockPos groundPos) {
        BlockState groundState = level.getBlockState(groundPos);
        if (groundState.is(Blocks.SNOW) && groundState.hasProperty(SnowLayerBlock.LAYERS)) {
            return groundState.getValue(SnowLayerBlock.LAYERS);
        }
        return groundState.is(Blocks.SNOW_BLOCK) ? 8 : 0;
    }

    static int getSurfaceIceLevels(BlockState state) {
        if (state.is(Blocks.ICE)) {
            return WPOConfig.MAX_FLUID_LEVEL;
        }
        if (state.is(EnvironmentalContent.SURFACE_ICE.get()) && state.hasProperty(BlockStateProperties.LAYERS)) {
            return state.getValue(BlockStateProperties.LAYERS);
        }
        return 0;
    }

    private static int getFarmlandMoisture(BlockState state) {
        if (state.is(Blocks.FARMLAND) && state.hasProperty(FarmBlock.MOISTURE)) {
            return state.getValue(FarmBlock.MOISTURE);
        }
        return -1;
    }

    private static int getVisibleFarmlandMoisture(ServerLevel level, BlockPos groundPos) {
        int moisture = getFarmlandMoisture(level.getBlockState(groundPos));
        if (moisture >= 0) {
            return moisture;
        }

        BlockState coverState = level.getBlockState(groundPos);
        if (coverState.getFluidState().isEmpty()
            && getSurfaceIceLevels(coverState) <= 0
            && !coverState.is(Blocks.SNOW)
            && !coverState.is(Blocks.SNOW_BLOCK)) {
            return -1;
        }

        BlockPos belowPos = groundPos.below();
        if (belowPos.getY() < level.getMinBuildHeight()) {
            return -1;
        }
        return getFarmlandMoisture(level.getBlockState(belowPos));
    }

    private static ClimateEstimate sampleClimateEstimate(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        return computeRealWorldConditions(
            level,
            groundPos,
            airPos,
            data,
            profile,
            SeasonManager.getSubSeason(level),
            level.isRainingAt(airPos),
            level.isThundering() && level.canSeeSky(airPos),
            getLocalSurfaceWaterLevels(level, groundPos, airPos),
            data.getAbsorbed(groundPos),
            countSnowLayers(level, groundPos),
            getLocalSurfaceIceLevels(level, groundPos, airPos)
        );
    }

    private static boolean canFreezeSurface(ServerLevel level, BlockPos surfacePos, BlockPos airPos, BlockPos supportPos, BlockState supportState) {
        if (!level.canSeeSky(airPos)) {
            return false;
        }
        if (!WPOFluidAccess.isChunkLoaded(level, surfacePos)) {
            return false;
        }
        if (!supportState.isFaceSturdy(level, supportPos, Direction.UP) || supportState.is(BlockTags.LEAVES)) {
            return false;
        }
        return !hasNearbyHeatSource(level, supportPos);
    }

    private static void setSurfaceIceLevels(ServerLevel level, BlockPos pos, int levels) {
        BlockState nextState;
        if (levels > 0) {
            nextState = EnvironmentalContent.SURFACE_ICE.get().defaultBlockState().setValue(BlockStateProperties.LAYERS, levels);
        } else {
            nextState = Blocks.AIR.defaultBlockState();
        }
        level.setBlock(pos, nextState, 3);
    }

    private static void meltSurfaceIce(ServerLevel level, BlockPos icePos, int iceLevels, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        int nextIceLevels = Math.max(0, iceLevels - 1);
        setSurfaceIceLevels(level, icePos, nextIceLevels);

        BlockPos meltPos = nextIceLevels > 0 ? icePos.above() : icePos;
        int before = WPOFluidAccess.getWaterAmount(level, meltPos);
        int after = WPOFluidAccess.addWater(level, meltPos, 1);
        if (after > before) {
            return;
        }

        if (!EnvironmentalConfig.COMMON.absorption.get()) {
            return;
        }

        BlockPos supportPos = icePos.below();
        if (supportPos.getY() < level.getMinBuildHeight()) {
            return;
        }

        int capacity = getAbsorptionCapacity(level.getBlockState(supportPos), profile, level);
        if (capacity > 0) {
            data.addAbsorbed(supportPos, 1, capacity);
        }
    }

    private static int getLocalSurfaceWaterLevels(ServerLevel level, BlockPos groundPos, BlockPos airPos) {
        BlockPos waterPos = getExposedWaterPos(level, groundPos, airPos);
        return waterPos == null ? 0 : WPOFluidAccess.getWaterAmount(level, waterPos);
    }

    private static int getLocalSurfaceIceLevels(ServerLevel level, BlockPos groundPos, BlockPos airPos) {
        BlockPos icePos = getSurfaceIcePos(level, groundPos, airPos);
        return icePos == null ? 0 : getSurfaceIceLevels(level.getBlockState(icePos));
    }

    private static BlockPos getSurfaceIcePos(ServerLevel level, BlockPos groundPos, BlockPos airPos) {
        if (getSurfaceIceLevels(level.getBlockState(airPos)) > 0) {
            return airPos;
        }
        if (getSurfaceIceLevels(level.getBlockState(groundPos)) > 0) {
            return groundPos;
        }
        return null;
    }

    private static double getCondensationChance(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile, ClimateEstimate climate) {
        if (!EnvironmentalConfig.COMMON.condensation.get() || !level.canSeeSky(airPos) || climate.realTempC() <= 0.75F) {
            return 0.0D;
        }

        double humidityFactor = Mth.clamp((climate.realHumidityPct() - 60.0D) / 35.0D, 0.0D, 1.4D);
        if (humidityFactor <= 0.0D) {
            return 0.0D;
        }

        double temp = climate.realTempC();
        double tempFactor = temp <= 18.0D ? 1.0D : Mth.clamp(1.0D - ((temp - 18.0D) / 18.0D), 0.0D, 1.0D);
        double timeFactor = switch (DayNightModifiers.getTimeOfDay(level)) {
            case NIGHT -> 1.15D;
            case DAWN -> 1.35D;
            case DUSK -> 0.65D;
            case DAY -> 0.25D;
        };
        double windFactor = Mth.clamp(1.15D - (climate.realWindMs() / 10.0D), 0.15D, 1.1D);
        double wetnessFactor = 0.75D + (getWetnessRatio(data) * 0.55D) + Math.max(0.0D, profile.retentionMultiplier() - 1.0D) * 0.35D;

        return EnvironmentalConfig.COMMON.condensationChance.get()
            * humidityFactor
            * tempFactor
            * timeFactor
            * windFactor
            * wetnessFactor
            * EnvironmentalConfig.COMMON.condensationMultiplierOverride.get();
    }

    private static double getSurfaceFreezeChance(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile, ClimateEstimate climate) {
        if (!EnvironmentalConfig.COMMON.surfaceIce.get() || climate.realTempC() > -0.25F) {
            return 0.0D;
        }

        double tempFactor = Mth.clamp((-climate.realTempC() + 0.25D) / 9.0D, 0.0D, 1.75D);
        double humidityFactor = 0.6D + Mth.clamp(climate.realHumidityPct() / 100.0D, 0.0D, 1.0D) * 0.4D;
        double windFactor = 0.8D + Mth.clamp(climate.realWindMs() / 16.0D, 0.0D, 0.35D);
        double coldBias = 1.0D + Math.max(0.0D, profile.snowmeltMultiplier() - 1.0D) * 0.2D;
        double droughtPenalty = data.isDroughtActive() ? Mth.clamp(1.0D - profile.droughtSensitivity() * 0.18D, 0.55D, 1.0D) : 1.0D;

        return EnvironmentalConfig.COMMON.surfaceFreezeChance.get()
            * tempFactor
            * humidityFactor
            * windFactor
            * coldBias
            * droughtPenalty
            * EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.get();
    }

    private static double getSurfaceIceThawChance(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile, ClimateEstimate climate) {
        if (!EnvironmentalConfig.COMMON.surfaceIce.get()) {
            return 0.0D;
        }

        double temp = climate.realTempC();
        double temperatureFactor = temp <= 0.5D ? 0.0D : Mth.clamp((temp - 0.5D) / 12.0D, 0.0D, 1.6D);
        if (temperatureFactor <= 0.0D) {
            return 0.0D;
        }

        SeasonManager.SubSeason subSeason = SeasonManager.getSubSeason(level);
        double meltChance = EnvironmentalConfig.COMMON.snowmeltChance.get()
            * getReleaseMultiplier(level, groundPos, profile)
            * profile.snowmeltMultiplier()
            * SeasonalModifiers.getSnowmeltMultiplier(subSeason)
            * DayNightModifiers.getSnowmeltMultiplier(level, profile)
            * EnvironmentalConfig.COMMON.surfaceIceMultiplierOverride.get();

        if (level.isRainingAt(airPos)) {
            meltChance *= 0.6D;
        }
        if (data.isDroughtActive()) {
            meltChance *= 0.9D;
        }
        return meltChance * temperatureFactor;
    }

    private static double getAgricultureMultiplier(ServerLevel level, BlockPos farmlandPos, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        if (data.getAbsorbed(farmlandPos) <= 0) {
            return 0.0D;
        }

        double multiplier = EnvironmentalConfig.COMMON.agricultureMultiplierOverride.get();
        multiplier *= 0.8D + Math.max(0.0D, profile.retentionMultiplier()) * 0.2D;
        multiplier *= 0.9D + Math.max(0.0D, profile.absorptionMultiplier()) * 0.18D;
        multiplier *= 1.0D + Math.min(0.35D, data.getAbsorbed(farmlandPos) * 0.06D);
        return multiplier;
    }

    private static boolean hasNearbyHydratingWater(ServerLevel level, BlockPos farmlandPos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; ++dx) {
            for (int dz = -4; dz <= 4; ++dz) {
                for (int dy = 0; dy <= 1; ++dy) {
                    cursor.set(farmlandPos.getX() + dx, farmlandPos.getY() + dy, farmlandPos.getZ() + dz);
                    if (WPOFluidAccess.getWaterAmount(level, cursor) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
