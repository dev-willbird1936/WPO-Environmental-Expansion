package net.skds.wpo.environmental.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.skds.wpo.environmental.EnvironmentalExpansion;

import java.util.Optional;

public class EnvPacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EnvironmentalExpansion.MOD_ID, "network"),
            () -> PROTOCOL_VERSION,
            v -> true,
            v -> true
    );

    private static int id = 0;

    public static SimpleChannel channel() {
        return CHANNEL;
    }

    public static void init() {
        CHANNEL.registerMessage(
                id++,
                EnvDebugPacket.class,
                EnvDebugPacket::encode,
                EnvDebugPacket::decode,
                EnvDebugPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
