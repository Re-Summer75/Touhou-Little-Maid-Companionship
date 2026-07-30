package com.laixia.maidintelligence.platform.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import com.laixia.maidintelligence.platform.network.packet.ClientboundLevelUpPacket;
import com.laixia.maidintelligence.platform.network.packet.ClientboundMaidAdvancementsPacket;
import com.laixia.maidintelligence.platform.network.packet.ClientboundMaidEatingParticlesPacket;
import com.laixia.maidintelligence.platform.network.packet.ServerboundMouthFeedPacket;
import com.laixia.maidintelligence.platform.network.packet.ServerboundOpenMaidAdvancementPagePacket;
import com.laixia.maidintelligence.platform.network.packet.ServerboundRequestMaidAdvancementsPacket;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

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
        registrar.registerClientbound(
                ClientboundMaidEatingParticlesPacket.class,
                ClientboundMaidEatingParticlesPacket::encode,
                ClientboundMaidEatingParticlesPacket::decode,
                ClientboundMaidEatingParticlesPacket::handle
        );
        registrar.registerServerbound(
                ServerboundMouthFeedPacket.class,
                ServerboundMouthFeedPacket::encode,
                ServerboundMouthFeedPacket::decode,
                ServerboundMouthFeedPacket::handle
        );
        registrar.registerClientbound(
                ClientboundMaidAdvancementsPacket.class,
                ClientboundMaidAdvancementsPacket::encode,
                ClientboundMaidAdvancementsPacket::decode,
                ClientboundMaidAdvancementsPacket::handle
        );
        registrar.registerServerbound(
                ServerboundOpenMaidAdvancementPagePacket.class,
                ServerboundOpenMaidAdvancementPagePacket::encode,
                ServerboundOpenMaidAdvancementPagePacket::decode,
                ServerboundOpenMaidAdvancementPagePacket::handle
        );
        registrar.registerServerbound(
                ServerboundRequestMaidAdvancementsPacket.class,
                ServerboundRequestMaidAdvancementsPacket::encode,
                ServerboundRequestMaidAdvancementsPacket::decode,
                ServerboundRequestMaidAdvancementsPacket::handle
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

    public static void sendOpenMaidAdvancementPage(int maidEntityId) {
        CHANNEL.sendToServer(new ServerboundOpenMaidAdvancementPagePacket(maidEntityId));
    }

    public static void sendRequestMaidAdvancements(int maidEntityId) {
        CHANNEL.sendToServer(new ServerboundRequestMaidAdvancementsPacket(maidEntityId));
    }

    public static void sendMaidAdvancements(
            ServerPlayer player,
            int maidEntityId,
            MaidAdvancementSnapshot snapshot
    ) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                ClientboundMaidAdvancementsPacket.of(maidEntityId, snapshot)
        );
    }

    public static void sendMouthFeed(
            EntityMaid maid,
            float faceU,
            float faceV,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        CHANNEL.sendToServer(new ServerboundMouthFeedPacket(
                maid.getId(),
                faceU,
                faceV,
                worldCenter,
                worldNormal
        ));
    }

    public static void sendMaidEatingParticles(
            EntityMaid maid,
            ItemStack food,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> maid),
                new ClientboundMaidEatingParticlesPacket(
                        maid.getId(),
                        food,
                        false,
                        worldCenter,
                        worldNormal
                )
        );
    }

    public static void sendMaidEatingParticlesFromTrackedFace(
            EntityMaid maid,
            ItemStack food
    ) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> maid),
                new ClientboundMaidEatingParticlesPacket(
                        maid.getId(),
                        food,
                        true,
                        Vec3.ZERO,
                        Vec3.ZERO
                )
        );
    }
}
