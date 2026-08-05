package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.platform.network.FeaturePacketRegistrar;
import com.laixia.maidintelligence.platform.network.PacketRegistrar;

/**
 * Protocol ID 6 carries the soul lens insight.
 */
public final class InsightPacketRegistrar implements FeaturePacketRegistrar {
    public static final int MAID_INSIGHT_ID = 6;

    @Override
    public void registerPackets(PacketRegistrar registrar) {
        registrar.registerClientbound(
                MAID_INSIGHT_ID,
                ClientboundMaidInsightPacket.class,
                ClientboundMaidInsightPacket::encode,
                ClientboundMaidInsightPacket::decode,
                ClientboundMaidInsightPacket::handle
        );
    }
}
