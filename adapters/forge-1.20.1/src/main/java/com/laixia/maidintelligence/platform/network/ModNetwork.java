package com.laixia.maidintelligence.platform.network;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

/**
 * Owns the Forge channel and coordinates feature-owned packet registrars.
 * Domain packets and handlers never enter this infrastructure class.
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "13";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ModResources.id("main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();
    private static boolean initialized;

    private ModNetwork() {
    }

    public static void initialize(
            List<? extends FeaturePacketRegistrar> featureRegistrars
    ) {
        if (initialized) {
            return;
        }
        initialized = true;

        PacketRegistrar registrar = new PacketRegistrar(CHANNEL);
        featureRegistrars.forEach(feature -> feature.registerPackets(registrar));
        registrar.verifyContiguousIds();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                message
        );
    }

    public static void sendTrackingEntityAndSelf(
            Entity entity,
            Object message
    ) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                message
        );
    }
}
