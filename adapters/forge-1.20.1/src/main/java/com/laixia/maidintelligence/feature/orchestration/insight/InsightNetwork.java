package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends one maid's insight to one player.
 *
 * <p>Addressed to a single player rather than tracked-by-entity: the panel is
 * a tool someone chose to hold, not a property of the maid, so nobody else is
 * paying for it.
 */
public final class InsightNetwork {
    private InsightNetwork() {
    }

    public static void sendInsight(
            ServerPlayer player,
            int maidEntityId,
            MaidInsight insight
    ) {
        ModNetwork.sendToPlayer(
                player,
                new ClientboundMaidInsightPacket(maidEntityId, insight)
        );
    }
}
