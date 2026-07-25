package com.laixia.maidintelligence.platform.network.packet;

import com.laixia.maidintelligence.client.network.ClientPacketHandlers;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 把一只女仆当前可见的进度下发给正在看进度页的玩家：**条目定义与完成情况一起送**。
 * <p>
 * 定义不能省。客户端本来那份进度表是服务端按玩家可见性下发的，女仆专属的进度玩家永远不会完成，
 * 所以客户端压根没有它们；女仆在别的树里做到的地方也常常还没对玩家开放。
 * 载荷直接借原版的 {@link ClientboundUpdateAdvancementsPacket} 编解码——格式与原版给玩家发的
 * 完全一样，只是发到我们自己的通道、进我们自己那份表，不碰玩家的进度界面。
 */
public record ClientboundMaidAdvancementsPacket(
        int maidEntityId,
        ClientboundUpdateAdvancementsPacket update
) {
    public static ClientboundMaidAdvancementsPacket of(int maidEntityId, MaidAdvancementSnapshot snapshot) {
        return new ClientboundMaidAdvancementsPacket(
                maidEntityId,
                new ClientboundUpdateAdvancementsPacket(true, snapshot.visible(), Set.of(), snapshot.progress())
        );
    }

    public static void encode(ClientboundMaidAdvancementsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidEntityId());
        packet.update().write(buffer);
    }

    public static ClientboundMaidAdvancementsPacket decode(FriendlyByteBuf buffer) {
        int maidEntityId = buffer.readVarInt();
        return new ClientboundMaidAdvancementsPacket(
                maidEntityId,
                new ClientboundUpdateAdvancementsPacket(buffer)
        );
    }

    public static void handle(
            ClientboundMaidAdvancementsPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        // 收到的表归渲染线程读，解析父子关系也得挑个稳定时机，所以回主线程再动。
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandlers.handleMaidAdvancements(packet)
        ));
        context.setPacketHandled(true);
    }
}
