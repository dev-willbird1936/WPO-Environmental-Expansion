package net.skds.wpo.environmental.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class EnvPacketHandler {

    private static final String PROTOCOL_VERSION = "1";

    private EnvPacketHandler() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(EnvPacketHandler::registerPayloads);
    }

    public static void send(ServerPlayer target, CustomPacketPayload message) {
        PacketDistributor.sendToPlayer(target, message);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(EnvDebugPacket.TYPE, EnvDebugPacket.STREAM_CODEC, EnvDebugPacket::handle);
    }
}
