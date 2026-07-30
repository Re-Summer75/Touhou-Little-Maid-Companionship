package com.laixia.maidintelligence.feature.advancement.network;

import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;

public final class AdvancementNetwork {
    private AdvancementNetwork() {
    }

    public static void openPage(int maidEntityId) {
        ModNetwork.sendToServer(
                new ServerboundOpenMaidAdvancementPagePacket(maidEntityId)
        );
    }

    public static void requestSnapshot(int maidEntityId) {
        ModNetwork.sendToServer(
                new ServerboundRequestMaidAdvancementsPacket(maidEntityId)
        );
    }

    public static void sendSnapshot(
            ServerPlayer player,
            int maidEntityId,
            MaidAdvancementSnapshot snapshot
    ) {
        ModNetwork.sendToPlayer(
                player,
                ClientboundMaidAdvancementsPacket.of(
                        maidEntityId,
                        snapshot
                )
        );
    }
}
