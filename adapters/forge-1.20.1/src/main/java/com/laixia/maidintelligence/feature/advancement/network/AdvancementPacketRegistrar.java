package com.laixia.maidintelligence.feature.advancement.network;

import com.laixia.maidintelligence.platform.network.FeaturePacketRegistrar;
import com.laixia.maidintelligence.platform.network.PacketRegistrar;

/**
 * Retains advancement protocol IDs 3 through 5.
 */
public final class AdvancementPacketRegistrar
        implements FeaturePacketRegistrar {
    public static final int SNAPSHOT_ID = 3;
    public static final int OPEN_PAGE_ID = 4;
    public static final int REQUEST_SNAPSHOT_ID = 5;

    @Override
    public void registerPackets(PacketRegistrar registrar) {
        registrar.registerClientbound(
                SNAPSHOT_ID,
                ClientboundMaidAdvancementsPacket.class,
                ClientboundMaidAdvancementsPacket::encode,
                ClientboundMaidAdvancementsPacket::decode,
                ClientboundMaidAdvancementsPacket::handle
        );
        registrar.registerServerbound(
                OPEN_PAGE_ID,
                ServerboundOpenMaidAdvancementPagePacket.class,
                ServerboundOpenMaidAdvancementPagePacket::encode,
                ServerboundOpenMaidAdvancementPagePacket::decode,
                ServerboundOpenMaidAdvancementPagePacket::handle
        );
        registrar.registerServerbound(
                REQUEST_SNAPSHOT_ID,
                ServerboundRequestMaidAdvancementsPacket.class,
                ServerboundRequestMaidAdvancementsPacket::encode,
                ServerboundRequestMaidAdvancementsPacket::decode,
                ServerboundRequestMaidAdvancementsPacket::handle
        );
    }
}
