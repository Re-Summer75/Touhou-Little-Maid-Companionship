package com.laixia.maidintelligence.platform.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.network.packet.ClientboundLevelUpPacket;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ModResources.id("main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();
    private static boolean initialized;

    private ModNetwork() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        PacketRegistrar registrar = new PacketRegistrar(CHANNEL);
        registrar.registerClientbound(
                ClientboundLevelUpPacket.class,
                ClientboundLevelUpPacket::encode,
                ClientboundLevelUpPacket::decode,
                ClientboundLevelUpPacket::handle
        );
    }

    public static void sendLevelUp(EntityMaid maid, int oldLevel, int newLevel) {
        if (maid.getOwner() instanceof ServerPlayer owner) {
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> owner),
                    new ClientboundLevelUpPacket(maid.getId(), oldLevel, newLevel)
            );
        }
    }
}
