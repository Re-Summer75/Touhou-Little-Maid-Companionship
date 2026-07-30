package com.laixia.maidintelligence.feature.advancement.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * TLM-aware server behavior behind Forge-only advancement packets.
 */
public interface MaidAdvancementServerPacketHandler {
    void openPage(ServerPlayer player, int maidEntityId);

    void sendSnapshot(ServerPlayer player, int maidEntityId);
}
