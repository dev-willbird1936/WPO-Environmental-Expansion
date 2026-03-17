package net.skds.wpo.environmental;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.skds.wpo.WPOConfig;
import net.skds.wpo.api.WPOFluidAccess;

public final class EnvironmentalTicker {

    private EnvironmentalTicker() {
    }

    public static void tick(ServerLevel level) {
        if ((level.getGameTime() % EnvironmentalConfig.COMMON.updateInterval.get()) != 0L) {
            return;
        }

        EnvironmentalSavedData data = EnvironmentalSavedData.get(level);
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
    }

    public static int getCollectorRainMb(ServerLevel level, BlockPos pos, int baseMb) {
        if (baseMb <= 0 || !level.isRainingAt(pos.above())) {
            return 0;
        }
        double multiplier = getRainIntensity(level, pos, EnvironmentalSavedData.get(level));
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

        if (level.isRaining()) {
            int gain = scaleStep(EnvironmentalConfig.COMMON.ambientWetnessRainGain.get(), getGlobalRainMemoryMultiplier(level, data));
            data.adjustAmbientWetness(gain, cap);
        } else {
            int decay = scaleStep(EnvironmentalConfig.COMMON.ambientWetnessDryDecay.get(), getGlobalDryDecayMultiplier(level, data));
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
        if (EnvironmentalConfig.COMMON.snowmelt.get()) {
            processSnowmelt(level, groundPos, airPos, groundState, data);
            groundState = level.getBlockState(groundPos);
        }

        boolean rainingHere = EnvironmentalConfig.COMMON.rainAccumulation.get() && level.isRainingAt(airPos);
        if (rainingHere) {
            processRain(level, groundPos, airPos, groundState, data);
        }

        if (EnvironmentalConfig.COMMON.distantRainCatchup.get()) {
            applyAmbientCatchUp(level, groundPos, airPos, groundState, data, burstMaterialization);
        }

        if (EnvironmentalConfig.COMMON.absorption.get() || EnvironmentalConfig.COMMON.evaporation.get()) {
            processSurfaceWater(level, groundPos, airPos, groundState, data, rainingHere);
        }
    }

    private static void processRain(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data) {
        if (groundState.getBlock() instanceof LayeredCauldronBlock && groundState.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            if (groundState.getValue(BlockStateProperties.LEVEL_CAULDRON) < 3 && rollChance(level, groundPos, 0.10D * getRainIntensity(level, groundPos, data))) {
                level.setBlock(groundPos, groundState.cycle(BlockStateProperties.LEVEL_CAULDRON), 3);
            }
            return;
        }

        if (!EnvironmentalConfig.COMMON.puddles.get()) {
            return;
        }
        if (!WPOFluidAccess.isChunkLoaded(level, airPos)) {
            return;
        }

        double rainChance = EnvironmentalConfig.COMMON.rainChance.get() * getRainIntensity(level, groundPos, data);
        int steps = rollChance(level, airPos, rainChance) ? 1 : 0;
        if (EnvironmentalConfig.COMMON.floods.get() && level.isThundering() && rollChance(level, airPos.above(), rainChance * 0.5D)) {
            steps++;
        }
        if (steps <= 0) {
            return;
        }

        if (EnvironmentalConfig.COMMON.absorption.get()) {
            steps -= data.addAbsorbed(groundPos, steps, getAbsorptionCapacity(level.getBlockState(groundPos)));
        }
        if (steps > 0) {
            WPOFluidAccess.addWater(level, airPos, steps);
        }
    }

    private static void applyAmbientCatchUp(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, boolean burstMaterialization) {
        if (!EnvironmentalConfig.COMMON.puddles.get() || !WPOFluidAccess.isChunkLoaded(level, airPos)) {
            return;
        }
        if (!canReceiveAmbientRain(level, groundPos, airPos, groundState)) {
            return;
        }

        int targetLevels = getAmbientTargetWater(level, groundPos, airPos, data);
        if (targetLevels <= 0) {
            return;
        }

        int currentWater = WPOFluidAccess.getWaterAmount(level, airPos);
        if (currentWater >= targetLevels) {
            return;
        }

        int steps = burstMaterialization ? targetLevels - currentWater : 1;
        if (EnvironmentalConfig.COMMON.absorption.get()) {
            steps -= data.addAbsorbed(groundPos, steps, getAbsorptionCapacity(level.getBlockState(groundPos)));
        }
        if (steps > 0) {
            WPOFluidAccess.addWater(level, airPos, Math.min(targetLevels - currentWater, steps));
        }
    }

    private static void processSurfaceWater(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data, boolean rainingHere) {
        int waterAmount = WPOFluidAccess.getWaterAmount(level, airPos);
        if (EnvironmentalConfig.COMMON.absorption.get()) {
            int absorptionCapacity = getAbsorptionCapacity(groundState);
            if (absorptionCapacity <= 0) {
                data.clearAbsorbed(groundPos);
            } else {
                if (waterAmount > 0 && data.getAbsorbed(groundPos) < absorptionCapacity
                    && rollChance(level, groundPos.below(), EnvironmentalConfig.COMMON.absorptionChance.get())) {
                    int before = WPOFluidAccess.getWaterAmount(level, airPos);
                    int after = WPOFluidAccess.removeWater(level, airPos, 1);
                    if (before > after) {
                        data.addAbsorbed(groundPos, 1, absorptionCapacity);
                        waterAmount = after;
                    }
                }
                if (data.getAbsorbed(groundPos) > 0 && shouldRelease(level, groundPos, airPos, data, waterAmount, rainingHere)
                    && rollChance(level, groundPos.above(), EnvironmentalConfig.COMMON.releaseChance.get() * getReleaseMultiplier(level, groundPos))) {
                    int before = WPOFluidAccess.getWaterAmount(level, airPos);
                    int after = WPOFluidAccess.addWater(level, airPos, 1);
                    if (after > before) {
                        data.consumeAbsorbed(groundPos, 1);
                        waterAmount = after;
                    }
                }
            }
        }

        if (EnvironmentalConfig.COMMON.evaporation.get() && waterAmount > 0) {
            double evaporationChance = EnvironmentalConfig.COMMON.evaporationChance.get() * getEvaporationMultiplier(level, groundPos, airPos, data);
            if (rollChance(level, airPos.north(), evaporationChance)) {
                WPOFluidAccess.removeWater(level, airPos, 1);
            }
        }
    }

    private static void processSnowmelt(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState, EnvironmentalSavedData data) {
        Block groundBlock = groundState.getBlock();
        if (groundBlock != Blocks.SNOW && groundBlock != Blocks.SNOW_BLOCK) {
            return;
        }
        if (!level.getBiome(groundPos).value().warmEnoughToRain(groundPos) && level.getMaxLocalRawBrightness(airPos) < 12) {
            return;
        }
        double meltChance = EnvironmentalConfig.COMMON.snowmeltChance.get() * getReleaseMultiplier(level, groundPos);
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

    private static double getRainIntensity(ServerLevel level, BlockPos pos, EnvironmentalSavedData data) {
        double intensity = EnvironmentalConfig.COMMON.rainIntensity.get();
        if (EnvironmentalConfig.COMMON.floods.get() && level.isThundering()) {
            intensity *= EnvironmentalConfig.COMMON.stormIntensity.get();
        }
        Season season = getSeason(level);
        if (season == Season.SPRING) {
            intensity *= EnvironmentalConfig.COMMON.springRunoffMultiplier.get();
        } else if (season == Season.SUMMER) {
            intensity *= 0.85D;
        } else if (season == Season.WINTER) {
            intensity *= 0.75D;
        }
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            intensity *= 0.4D;
        }
        if (level.getBiome(pos).value().getBaseTemperature() < 0.15F) {
            intensity *= 0.75D;
        }
        return intensity;
    }

    private static double getGlobalRainMemoryMultiplier(ServerLevel level, EnvironmentalSavedData data) {
        double multiplier = EnvironmentalConfig.COMMON.rainIntensity.get();
        if (EnvironmentalConfig.COMMON.floods.get() && level.isThundering()) {
            multiplier *= EnvironmentalConfig.COMMON.stormIntensity.get();
        }
        Season season = getSeason(level);
        if (season == Season.SPRING) {
            multiplier *= EnvironmentalConfig.COMMON.springRunoffMultiplier.get();
        } else if (season == Season.SUMMER) {
            multiplier *= 0.85D;
        } else if (season == Season.WINTER) {
            multiplier *= 0.75D;
        }
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            multiplier *= 0.4D;
        }
        return multiplier;
    }

    private static double getGlobalDryDecayMultiplier(ServerLevel level, EnvironmentalSavedData data) {
        double multiplier = 1.0D;
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            multiplier += EnvironmentalConfig.COMMON.droughtEvaporationBonus.get() * 0.35D;
        }
        Season season = getSeason(level);
        if (season == Season.SUMMER) {
            multiplier *= EnvironmentalConfig.COMMON.summerEvaporationMultiplier.get();
        } else if (season == Season.WINTER) {
            multiplier *= 0.75D;
        }
        if (level.isDay()) {
            multiplier += 0.2D;
        }
        return multiplier;
    }

    private static double getEvaporationMultiplier(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data) {
        double multiplier = 1.0D;
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            multiplier += EnvironmentalConfig.COMMON.droughtEvaporationBonus.get();
        }
        Season season = getSeason(level);
        if (season == Season.SUMMER) {
            multiplier *= EnvironmentalConfig.COMMON.summerEvaporationMultiplier.get();
        }
        if (level.canSeeSky(airPos) && level.isDay() && level.getMaxLocalRawBrightness(airPos) >= 13) {
            multiplier += EnvironmentalConfig.COMMON.sunlightEvaporationBonus.get();
        }
        if (level.getBiome(groundPos).value().getBaseTemperature() >= 1.0F) {
            multiplier += EnvironmentalConfig.COMMON.hotBiomeEvaporationBonus.get();
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
        if (rainingHere) {
            return true;
        }
        if (EnvironmentalConfig.COMMON.droughts.get() && data.isDroughtActive()) {
            return false;
        }
        Season season = getSeason(level);
        return season == Season.SPRING || !level.canSeeSky(airPos) || level.getMaxLocalRawBrightness(groundPos) < 10;
    }

    private static boolean canReceiveAmbientRain(ServerLevel level, BlockPos groundPos, BlockPos airPos, BlockState groundState) {
        if (!level.canSeeSky(airPos)) {
            return false;
        }
        if (!level.getBiome(groundPos).value().warmEnoughToRain(groundPos)) {
            return false;
        }
        Block groundBlock = groundState.getBlock();
        return groundBlock != Blocks.SNOW && groundBlock != Blocks.SNOW_BLOCK;
    }

    private static int getAmbientTargetWater(ServerLevel level, BlockPos groundPos, BlockPos airPos, EnvironmentalSavedData data) {
        int cap = EnvironmentalConfig.COMMON.ambientWetnessCap.get();
        int maxLevels = Math.min(WPOConfig.MAX_FLUID_LEVEL, EnvironmentalConfig.COMMON.ambientMaxPuddleLevels.get());
        if (cap <= 0 || maxLevels <= 0 || data.getAmbientWetness() <= 0) {
            return 0;
        }

        double wetness = data.getAmbientWetness() / (double) cap;
        double terrainFactor = getTerrainRetentionFactor(level, groundPos);
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

    private static double getReleaseMultiplier(ServerLevel level, BlockPos pos) {
        double multiplier = 1.0D;
        Season season = getSeason(level);
        if (season == Season.SPRING) {
            multiplier *= EnvironmentalConfig.COMMON.springRunoffMultiplier.get();
        }
        if (level.getBiome(pos).value().warmEnoughToRain(pos) && level.getMaxLocalRawBrightness(pos.above()) >= 12) {
            multiplier *= 1.15D;
        }
        return multiplier;
    }

    private static int getAbsorptionCapacity(BlockState state) {
        if (state.is(Blocks.DIRT)) {
            return 3;
        }
        if (state.is(Blocks.COARSE_DIRT)) {
            return 2;
        }
        if (state.is(Blocks.FARMLAND)) {
            return 5;
        }
        if (state.is(Blocks.SAND)) {
            return 2;
        }
        if (state.is(Blocks.GRAVEL)) {
            return 1;
        }
        if (state.is(Blocks.MUD)) {
            return 6;
        }
        return 0;
    }

    private static Season getSeason(ServerLevel level) {
        if (!EnvironmentalConfig.COMMON.seasons.get()) {
            return Season.NONE;
        }
        long day = level.getDayTime() / 24000L;
        int seasonLength = Math.max(1, EnvironmentalConfig.COMMON.seasonLengthDays.get());
        return switch ((int) Math.floorMod(day / seasonLength, 4L)) {
            case 0 -> Season.SPRING;
            case 1 -> Season.SUMMER;
            case 2 -> Season.AUTUMN;
            default -> Season.WINTER;
        };
    }

    private enum Season {
        NONE,
        SPRING,
        SUMMER,
        AUTUMN,
        WINTER
    }
}
