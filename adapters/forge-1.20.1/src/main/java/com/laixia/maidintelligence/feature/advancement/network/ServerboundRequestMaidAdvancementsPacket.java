package com.laixia.maidintelligence.feature.advancement.network;

import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 打开进度页时向服务端要一份完整进度。只有能摸到这只女仆的玩家才拿得到。
 */
public record ServerboundRequestMaidAdvancementsPacket(int maidEntityId) {
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
        if (player != null) {
            AdapterRuntime.require(MaidAdvancementServerPacketHandler.class)
                    .sendSnapshot(player, packet.maidEntityId());
        }
        context.setPacketHandled(true);
    }
}
