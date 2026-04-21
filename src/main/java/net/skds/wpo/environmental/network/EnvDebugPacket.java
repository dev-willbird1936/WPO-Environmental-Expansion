package net.skds.wpo.environmental.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.skds.wpo.environmental.EnvironmentalExpansion;

public record EnvDebugPacket(
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
    BlockPos targetPos,
    String targetBlock,
    float realTempC,
    float realHumidityPct,
    float realWindMs,
    float precipChancePct,
    float precipMmHr,
    float condensationChancePct,
    float freezingChancePct,
    float thawChancePct,
    float agricultureGrowthChancePct
) implements CustomPacketPayload {

    public static final Type<EnvDebugPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(EnvironmentalExpansion.MOD_ID, "debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnvDebugPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> packet.write(buffer), EnvDebugPacket::new);

    private EnvDebugPacket(RegistryFriendlyByteBuf buffer) {
        this(
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readInt(),
            buffer.readUtf(128),
            buffer.readFloat(),
            buffer.readUtf(32),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBlockPos(),
            buffer.readUtf(128),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
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
        buffer.writeInt(surfaceIceLevels);
        buffer.writeInt(farmlandMoisture);
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
        buffer.writeBoolean(condensationEnabled);
        buffer.writeBoolean(surfaceIceEnabled);
        buffer.writeBoolean(agricultureEnabled);
        buffer.writeBoolean(floodsEnabled);
        buffer.writeBoolean(distantRainCatchupEnabled);
        buffer.writeBlockPos(targetPos);
        buffer.writeUtf(targetBlock, 128);
        buffer.writeFloat(realTempC);
        buffer.writeFloat(realHumidityPct);
        buffer.writeFloat(realWindMs);
        buffer.writeFloat(precipChancePct);
        buffer.writeFloat(precipMmHr);
        buffer.writeFloat(condensationChancePct);
        buffer.writeFloat(freezingChancePct);
        buffer.writeFloat(thawChancePct);
        buffer.writeFloat(agricultureGrowthChancePct);
    }

    @Override
    public Type<EnvDebugPacket> type() {
        return TYPE;
    }

    public static void handle(EnvDebugPacket packet, IPayloadContext context) {
        EnvDebugData.receive(packet);
    }
}
