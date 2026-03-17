package net.skds.wpo.environmental.blockentity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.skds.wpo.api.WPOFluidAccess;
import net.skds.wpo.environmental.EnvironmentalContent;
import net.skds.wpo.environmental.EnvironmentalTicker;
import net.skds.wpo.environmental.block.CollectorBlock;

public class CollectorBlockEntity extends BlockEntity {

    private final FluidTank tank = new FluidTank(getConfiguredCapacity(), stack -> stack.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            sync();
        }
    };
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> tank);

    public CollectorBlockEntity(BlockPos pos, BlockState state) {
        super(EnvironmentalContent.COLLECTOR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CollectorBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if ((serverLevel.getGameTime() % 10L) != 0L) {
            return;
        }
        blockEntity.tickServer(serverLevel, pos, state);
    }

    private void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        CollectorBlock.CollectorProfile profile = getProfile();
        if (profile.collectsRainDirectly() && ((CollectorBlock) state.getBlock()).allowsPassiveRainCollection(state)) {
            collectRain(level, pos, profile);
        }
        int drainLevels = profile.getSurfaceDrainLevels();
        if (drainLevels > 0 && tank.getSpace() > 0) {
            drainSurfaceWater(level, pos, drainLevels);
        }
        int transferMb = profile.getTransferMbPerCycle();
        if (transferMb > 0 && !tank.isEmpty()) {
            pushDownward(level, pos.below(), transferMb);
        }
    }

    private void collectRain(ServerLevel level, BlockPos pos, CollectorBlock.CollectorProfile profile) {
        if (tank.getSpace() <= 0 || !level.isRainingAt(pos.above())) {
            return;
        }
        int mb = EnvironmentalTicker.getCollectorRainMb(level, pos, profile.getRainMbPerCycle());
        if (mb > 0) {
            tank.fill(new FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private void drainSurfaceWater(ServerLevel level, BlockPos pos, int drainLevels) {
        BlockPos[] samples = {
            pos.above(),
            pos.above().north(),
            pos.above().south(),
            pos.above().east(),
            pos.above().west()
        };
        int remaining = drainLevels;
        for (BlockPos samplePos : samples) {
            if (remaining <= 0 || tank.getSpace() < 125 || !WPOFluidAccess.isChunkLoaded(level, samplePos)) {
                continue;
            }
            int waterAmount = WPOFluidAccess.getWaterAmount(level, samplePos);
            if (waterAmount <= 0) {
                continue;
            }
            int removed = Math.min(remaining, waterAmount);
            int before = WPOFluidAccess.getWaterAmount(level, samplePos);
            int after = WPOFluidAccess.removeWater(level, samplePos, removed);
            int actualRemoved = Math.max(0, before - after);
            if (actualRemoved <= 0) {
                continue;
            }
            tank.fill(new FluidStack(Fluids.WATER, actualRemoved * 125), IFluidHandler.FluidAction.EXECUTE);
            remaining -= actualRemoved;
        }
    }

    private void pushDownward(ServerLevel level, BlockPos belowPos, int amountMb) {
        BlockEntity below = level.getBlockEntity(belowPos);
        if (below == null) {
            return;
        }
        below.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).ifPresent(target -> {
            if (!tank.isEmpty()) {
                FluidUtil.tryFluidTransfer(target, tank, amountMb, true);
            }
        });
    }

    public int getComparatorOutput() {
        int capacity = tank.getCapacity();
        if (capacity <= 0 || tank.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.round(15.0F * tank.getFluidAmount() / capacity));
    }

    private CollectorBlock.CollectorProfile getProfile() {
        return ((CollectorBlock) getBlockState().getBlock()).getProfile();
    }

    private int getConfiguredCapacity() {
        return getProfile().getCapacityMb();
    }

    private void sync() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("tank", tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("tank", CompoundTag.TAG_COMPOUND)) {
            tank.setCapacity(getConfiguredCapacity());
            tank.readFromNBT(tag.getCompound("tank"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        fluidCapability.invalidate();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        tank.setCapacity(getConfiguredCapacity());
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }
}
