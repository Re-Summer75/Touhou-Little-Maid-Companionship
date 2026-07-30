package com.laixia.maidintelligence.platform.network.packet;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.MaidAdvancementFeature;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 打开进度页时向服务端要一份完整进度。只有能摸到这只女仆的玩家才拿得到。
 */
public record ServerboundRequestMaidAdvancementsPacket(int maidEntityId) {
    private static final double MAX_INTERACTION_DISTANCE = 8.0D;

    public static void encode(ServerboundRequestMaidAdvancementsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidEntityId());
    }

    public static ServerboundRequestMaidAdvancementsPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundRequestMaidAdvancementsPacket(buffer.readVarInt());
    }

    public static void handle(
            ServerboundRequestMaidAdvancementsPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        MinecraftServer server = player == null ? null : player.getServer();
        if (server != null
                && player.level().getEntity(packet.maidEntityId()) instanceof EntityMaid maid
                && player.canReach(maid, MAX_INTERACTION_DISTANCE)) {
            MaidAdvancementFeature.INSTANCE.manager().tracker(maid).ifPresent(tracker -> ModNetwork.sendMaidAdvancements(
                    player,
                    maid.getId(),
                    tracker.snapshot(server.getAdvancements())
            ));
        }
        context.setPacketHandled(true);
    }
}
