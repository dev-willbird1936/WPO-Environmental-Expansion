package net.skds.wpo.environmental;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.api.WPOFluidAccess;

public class EnvironmentalEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SeasonCommand.register(event.getDispatcher());
        EvaporationCommand.register(event.getDispatcher());
        AbsorptionCommand.register(event.getDispatcher());
        CondensationCommand.register(event.getDispatcher());
        FreezingCommand.register(event.getDispatcher());
        AgricultureCommand.register(event.getDispatcher());
        BiomeScoutCommand.register(event.getDispatcher());
        EnvironmentalBenchmarkCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && !serverLevel.isClientSide() && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            BiomeProfileManager.ensureInitialized(serverLevel);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && !serverLevel.isClientSide() && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            EnvironmentalBenchmarkManager.onLevelUnload(serverLevel);
            BiomeProfileManager.reset();
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return;
        }
        EnvironmentalTicker.tick(serverLevel);
        EnvironmentalBenchmarkManager.tick(serverLevel);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && !serverLevel.isClientSide()) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            int surfaceIceLevels = state.is(EnvironmentalContent.SURFACE_ICE.get()) ? EnvironmentalTicker.getSurfaceIceLevels(state) : 0;
            if (surfaceIceLevels > 0) {
                WPOFluidAccess.setWaterAmount(serverLevel, pos, surfaceIceLevels);
            }

            int absorbed = EnvironmentalSavedData.get(serverLevel).getAbsorbed(pos);
            if (absorbed > 0) {
                if (!state.getFluidState().isSource()) {
                    FlowingFluid water = (FlowingFluid) Fluids.WATER;
                    BlockState waterBlock;
                    if (absorbed >= WPOConfig.MAX_FLUID_LEVEL) {
                        waterBlock = water.getSource(false).createLegacyBlock();
                    } else {
                        waterBlock = water.getFlowing(absorbed, false).createLegacyBlock();
                    }
                    serverLevel.setBlock(pos, waterBlock, 3);
                }

                EnvironmentalSavedData.get(serverLevel).clearAbsorbed(pos);
            }
        }
    }

    @SubscribeEvent
    public void onCropGrowPre(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.isClientSide() || event.getResult() == CropGrowEvent.Pre.Result.DO_NOT_GROW) {
            return;
        }
        if (!EnvironmentalConfig.COMMON.agriculture.get()) {
            return;
        }
        if (!(event.getState().getBlock() instanceof CropBlock)) {
            return;
        }

        BlockPos farmlandPos = event.getPos().below();
        BlockState farmlandState = serverLevel.getBlockState(farmlandPos);
        if (!farmlandState.is(Blocks.FARMLAND) || !farmlandState.hasProperty(FarmBlock.MOISTURE)) {
            return;
        }

        EnvironmentalSavedData data = EnvironmentalSavedData.get(serverLevel);
        if (data.getAbsorbed(farmlandPos) <= 0) {
            return;
        }

        if (farmlandState.getValue(FarmBlock.MOISTURE) < 7) {
            serverLevel.setBlock(farmlandPos, farmlandState.setValue(FarmBlock.MOISTURE, 7), 3);
        }

        BiomeEnvironmentProfile profile = BiomeProfileManager.getProfile(serverLevel, farmlandPos);
        double chance = Mth.clamp(EnvironmentalTicker.getAgricultureGrowthChance(serverLevel, farmlandPos, data, profile), 0.0D, 0.95D);
        if (chance <= 0.0D || serverLevel.random.nextDouble() >= chance) {
            return;
        }

        data.consumeAbsorbed(farmlandPos, 1);
        EnvironmentalBenchmarkManager.onAgricultureGrowthBoost(serverLevel, farmlandPos);
        event.setResult(CropGrowEvent.Pre.Result.GROW);
    }
}
