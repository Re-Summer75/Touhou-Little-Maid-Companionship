package com.laixia.maidintelligence.feature.level.network;

import com.laixia.maidintelligence.platform.network.FeaturePacketRegistrar;
import com.laixia.maidintelligence.platform.network.PacketRegistrar;

/**
 * Protocol ID 0 is retained for the level-up notification.
 */
public final class LevelPacketRegistrar implements FeaturePacketRegistrar {
    public static final int LEVEL_UP_ID = 0;

    @Override
    public void registerPackets(PacketRegistrar registrar) {
        registrar.registerClientbound(
                LEVEL_UP_ID,
                ClientboundLevelUpPacket.class,
                ClientboundLevelUpPacket::encode,
                ClientboundLevelUpPacket::decode,
                ClientboundLevelUpPacket::handle
        );
    }
}
