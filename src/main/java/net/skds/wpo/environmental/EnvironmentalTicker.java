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
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.PacketDistributor;
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

        BlockState groundState = level.getBlockState(groundPos);
        BiomeEnvironmentProfile profile = BiomeProfileManager.getProfile(level, groundPos);
        BiomeProfileManager.observe(level, groundPos, groundState);
        if (EnvironmentalConfig.COMMON.snowmelt.get()) {
            processSnowmelt(level, groundPos, airPos, groundState, data, profile);
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
            if (rollChance(level, evaporationPos, 0.3D)) {
                double x = evaporationPos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                double y = evaporationPos.getY() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.3D;
                double z = evaporationPos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                level.sendParticles(ParticleTypes.POOF, x, y, z, 1, 0.0D, 0.05D, 0.0D, 0.02D);
            }
        }
    }

    private static void processSnowmelt(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, BiomeEnvironmentProfile profile) {
        Block groundBlock = groundState.getBlock();
        if (groundBlock != Blocks.SNOW && groundBlock != Blocks.SNOW_BLOCK) {
            return;
        }
        if (!level.getBiome(groundPos).value().warmEnoughToRain(groundPos) && level.getMaxLocalRawBrightness(airPos) < 12) {
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
        
        if (level.getBiome(pos).value().getBaseTemperature() < 0.15F) {
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
        double biomeTemp = level.getBiome(groundPos).value().getBaseTemperature();
        if (biomeTemp >= 1.0F) {
            double heatFactor = Mth.clamp((biomeTemp - 1.0F) / 0.5F, 0.0D, 1.0D);
            double hotBonus = EnvironmentalConfig.COMMON.hotBiomeEvaporationBonus.get();
            multiplier += hotBonus * (0.5D + heatFactor * 0.5D);
        }
        if (hasNearbyLava(level, groundPos)) {
            multiplier += EnvironmentalConfig.COMMON.lavaEvaporationBonus.get();
        }
        if (level.isRainingAt(airPos)) {
            multiplier *= 0.35D;
        }
        
        return multiplier;
    }

    private static boolean hasNearbyLava(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getFluidState().getType().isSame(Blocks.LAVA.defaultBlockState().getFluidState().getType())) {
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
        if (!level.getBiome(groundPos).value().warmEnoughToRain(groundPos)) {
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
        return groundBlock != Blocks.SNOW && groundBlock != Blocks.SNOW_BLOCK;
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
        
        if (level.getBiome(pos).value().warmEnoughToRain(pos) && level.getMaxLocalRawBrightness(pos.above()) >= 12) {
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

    private static void sendDebugPackets(ServerLevel level) {
        // Send debug data to all players every 10 ticks
        if (level.getGameTime() % 10L != 0L || level.players().isEmpty()) {
            return;
        }

        Player player = level.players().get(0);
        BlockPos playerPos = player.blockPosition();
        BlockPos groundPos = new BlockPos(playerPos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos.getX(), playerPos.getZ()) - 1, playerPos.getZ());

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

        int surfaceWater = WPOFluidAccess.getWaterAmount(level, groundPos.above());
        int absorbed = data.getAbsorbed(groundPos);
        int snowLayers = countSnowLayers(level, groundPos);

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
                subSeason.index(),
                SeasonManager.getWorldDay(level),
                level.getDayTime(),
                SeasonManager.isSeasonsEnabled(),
                SeasonManager.isTropicalCycle(),
                tropicalPhase,
                level.getBiome(groundPos).unwrapKey().map(Object::toString).orElse("unknown"),
                level.getBiome(groundPos).value().getBaseTemperature(),
                profile.archetype().name(),
                surfaceWater,
                absorbed,
                snowLayers,
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
                EnvironmentalConfig.COMMON.floods.get(),
                EnvironmentalConfig.COMMON.distantRainCatchup.get(),
                targetPos,
                targetBlock
        );

        EnvPacketHandler.channel().send(PacketDistributor.PLAYER.with(() -> (net.minecraft.server.level.ServerPlayer) player), packet);
    }

    private static int countSnowLayers(ServerLevel level, BlockPos groundPos) {
        int layers = 0;
        BlockPos pos = groundPos.above();
        for (int i = 0; i < 8; i++) {
            if (level.getBlockState(pos).is(Blocks.SNOW)) {
                layers++;
                pos = pos.above();
            } else {
                break;
            }
        }
        return layers;
    }
}
