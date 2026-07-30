package com.laixia.maidintelligence.feature.level.network;

import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;

public final class LevelNetwork {
    private LevelNetwork() {
    }

    public static void sendLevelUp(
            ServerPlayer player,
            int maidEntityId,
            int oldLevel,
            int newLevel
    ) {
        ModNetwork.sendToPlayer(
                player,
                new ClientboundLevelUpPacket(
                        maidEntityId,
                        oldLevel,
                        newLevel
                )
        );
    }
}
