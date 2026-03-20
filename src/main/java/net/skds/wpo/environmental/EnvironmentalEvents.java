package net.skds.wpo.environmental;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;
import net.skds.wpo.WPOConfig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EnvironmentalEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SeasonCommand.register(event.getDispatcher());
        EvaporationCommand.register(event.getDispatcher());
        AbsorptionCommand.register(event.getDispatcher());
        BiomeScoutCommand.register(event.getDispatcher());
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
            BiomeProfileManager.reset();
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent event) {
        if (event.phase != Phase.END || !(event.level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return;
        }
        EnvironmentalTicker.tick(serverLevel);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && !serverLevel.isClientSide()) {
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            
            // Check if the broken block has absorbed water
            int absorbed = EnvironmentalSavedData.get(serverLevel).getAbsorbed(pos);
            if (absorbed > 0) {
                // Spawn water matching the absorbed amount (not a full source block)
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

                // Clear the absorbed water data
                EnvironmentalSavedData.get(serverLevel).clearAbsorbed(pos);
            }
        }
    }
}
