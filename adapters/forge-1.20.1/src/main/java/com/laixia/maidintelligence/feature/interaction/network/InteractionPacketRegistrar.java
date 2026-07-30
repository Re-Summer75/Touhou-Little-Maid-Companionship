package com.laixia.maidintelligence.feature.interaction.network;

import com.laixia.maidintelligence.platform.network.FeaturePacketRegistrar;
import com.laixia.maidintelligence.platform.network.PacketRegistrar;

/**
 * Retains interaction protocol IDs 1 and 2.
 */
public final class InteractionPacketRegistrar
        implements FeaturePacketRegistrar {
    public static final int EATING_PARTICLES_ID = 1;
    public static final int MOUTH_FEED_ID = 2;

    @Override
    public void registerPackets(PacketRegistrar registrar) {
        registrar.registerClientbound(
                EATING_PARTICLES_ID,
                ClientboundMaidEatingParticlesPacket.class,
                ClientboundMaidEatingParticlesPacket::encode,
                ClientboundMaidEatingParticlesPacket::decode,
                ClientboundMaidEatingParticlesPacket::handle
        );
        registrar.registerServerbound(
                MOUTH_FEED_ID,
                ServerboundMouthFeedPacket.class,
                ServerboundMouthFeedPacket::encode,
                ServerboundMouthFeedPacket::decode,
                ServerboundMouthFeedPacket::handle
        );
    }
}
