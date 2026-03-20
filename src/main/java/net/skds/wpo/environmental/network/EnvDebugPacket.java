package net.skds.wpo.environmental.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.skds.wpo.WPO;
import net.skds.wpo.api.WPOFluidAccess;

import java.util.function.Supplier;

/**
 * Server → Client packet carrying all WPO Environmental debug data.
 * Sent periodically (every ~10 ticks) to all players.
 */
public class EnvDebugPacket {

    // Season / time
    private final int subSeasonIndex;   // 0-11
    private final long worldDay;
    private final long dayTime;         // tick within current day
    private final boolean seasonsEnabled;
    private final boolean tropicalCycle;
    private final int tropicalPhaseIndex; // 0=WET, 1=DRY

    // Biome
    private final String biomeId;
    private final float biomeTemp;
    private final String archetype;

    // Local block
    private final int surfaceWaterLevels;
    private final int absorbedWater;
    private final int snowLayers;
    private final boolean isRaining;
    private final boolean isThundering;

    // System multipliers
    private final double rainMultiplier;
    private final double evaporationMultiplier;
    private final double absorptionMultiplier;
    private final double releaseMultiplier;
    private final double snowmeltMultiplier;
    private final double stormMultiplier;

    // System status
    private final boolean droughtActive;
    private final boolean absorptionEnabled;
    private final boolean evaporationEnabled;
    private final boolean snowmeltEnabled;
    private final boolean floodsEnabled;
    private final boolean distantRainCatchupEnabled;

    // Player look target
    private final BlockPos targetPos;
    private final String targetBlock;

    public EnvDebugPacket(FriendlyByteBuf buffer) {
        this.subSeasonIndex = buffer.readInt();
        this.worldDay = buffer.readLong();
        this.dayTime = buffer.readLong();
        this.seasonsEnabled = buffer.readBoolean();
        this.tropicalCycle = buffer.readBoolean();
        this.tropicalPhaseIndex = buffer.readInt();
        this.biomeId = buffer.readUtf(128);
        this.biomeTemp = buffer.readFloat();
        this.archetype = buffer.readUtf(32);
        this.surfaceWaterLevels = buffer.readInt();
        this.absorbedWater = buffer.readInt();
        this.snowLayers = buffer.readInt();
        this.isRaining = buffer.readBoolean();
        this.isThundering = buffer.readBoolean();
        this.rainMultiplier = buffer.readDouble();
        this.evaporationMultiplier = buffer.readDouble();
        this.absorptionMultiplier = buffer.readDouble();
        this.releaseMultiplier = buffer.readDouble();
        this.snowmeltMultiplier = buffer.readDouble();
        this.stormMultiplier = buffer.readDouble();
        this.droughtActive = buffer.readBoolean();
        this.absorptionEnabled = buffer.readBoolean();
        this.evaporationEnabled = buffer.readBoolean();
        this.snowmeltEnabled = buffer.readBoolean();
        this.floodsEnabled = buffer.readBoolean();
        this.distantRainCatchupEnabled = buffer.readBoolean();
        this.targetPos = buffer.readBlockPos();
        this.targetBlock = buffer.readUtf(128);
    }

    public EnvDebugPacket(
            int subSeasonIndex, long worldDay, long dayTime,
            boolean seasonsEnabled, boolean tropicalCycle, int tropicalPhaseIndex,
            String biomeId, float biomeTemp, String archetype,
            int surfaceWaterLevels, int absorbedWater, int snowLayers,
            boolean isRaining, boolean isThundering,
            double rainMultiplier, double evaporationMultiplier,
            double absorptionMultiplier, double releaseMultiplier,
            double snowmeltMultiplier, double stormMultiplier,
            boolean droughtActive,
            boolean absorptionEnabled, boolean evaporationEnabled,
            boolean snowmeltEnabled, boolean floodsEnabled,
            boolean distantRainCatchupEnabled,
            BlockPos targetPos, String targetBlock
    ) {
        this.subSeasonIndex = subSeasonIndex;
        this.worldDay = worldDay;
        this.dayTime = dayTime;
        this.seasonsEnabled = seasonsEnabled;
        this.tropicalCycle = tropicalCycle;
        this.tropicalPhaseIndex = tropicalPhaseIndex;
        this.biomeId = biomeId;
        this.biomeTemp = biomeTemp;
        this.archetype = archetype;
        this.surfaceWaterLevels = surfaceWaterLevels;
        this.absorbedWater = absorbedWater;
        this.snowLayers = snowLayers;
        this.isRaining = isRaining;
        this.isThundering = isThundering;
        this.rainMultiplier = rainMultiplier;
        this.evaporationMultiplier = evaporationMultiplier;
        this.absorptionMultiplier = absorptionMultiplier;
        this.releaseMultiplier = releaseMultiplier;
        this.snowmeltMultiplier = snowmeltMultiplier;
        this.stormMultiplier = stormMultiplier;
        this.droughtActive = droughtActive;
        this.absorptionEnabled = absorptionEnabled;
        this.evaporationEnabled = evaporationEnabled;
        this.snowmeltEnabled = snowmeltEnabled;
        this.floodsEnabled = floodsEnabled;
        this.distantRainCatchupEnabled = distantRainCatchupEnabled;
        this.targetPos = targetPos;
        this.targetBlock = targetBlock;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(subSeasonIndex);
        buffer.writeLong(worldDay);
        buffer.writeLong(dayTime);
        buffer.writeBoolean(seasonsEnabled);
        buffer.writeBoolean(tropicalCycle);
        buffer.writeInt(tropicalPhaseIndex);
        buffer.writeUtf(biomeId, 128);
        buffer.writeFloat(biomeTemp);
        buffer.writeUtf(archetype, 32);
        buffer.writeInt(surfaceWaterLevels);
        buffer.writeInt(absorbedWater);
        buffer.writeInt(snowLayers);
        buffer.writeBoolean(isRaining);
        buffer.writeBoolean(isThundering);
        buffer.writeDouble(rainMultiplier);
        buffer.writeDouble(evaporationMultiplier);
        buffer.writeDouble(absorptionMultiplier);
        buffer.writeDouble(releaseMultiplier);
        buffer.writeDouble(snowmeltMultiplier);
        buffer.writeDouble(stormMultiplier);
        buffer.writeBoolean(droughtActive);
        buffer.writeBoolean(absorptionEnabled);
        buffer.writeBoolean(evaporationEnabled);
        buffer.writeBoolean(snowmeltEnabled);
        buffer.writeBoolean(floodsEnabled);
        buffer.writeBoolean(distantRainCatchupEnabled);
        buffer.writeBlockPos(targetPos);
        buffer.writeUtf(targetBlock, 128);
    }

    public static EnvDebugPacket decode(FriendlyByteBuf buffer) {
        return new EnvDebugPacket(buffer);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> EnvDebugData.receive(this));
        ctx.setPacketHandled(true);
    }
}
