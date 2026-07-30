package com.laixia.maidintelligence.feature.advancement.network;

import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 顶部 Tab 请求打开进度页。校验方式对齐本体的 {@code ToggleTabMessage}：只有主人、
 * 女仆存活未睡着且在交互距离内才开界面。
 */
public record ServerboundOpenMaidAdvancementPagePacket(int maidEntityId) {
    public static void encode(ServerboundOpenMaidAdvancementPagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidEntityId());
    }

    public static ServerboundOpenMaidAdvancementPagePacket decode(FriendlyByteBuf buffer) {
        return new ServerboundOpenMaidAdvancementPagePacket(buffer.readVarInt());
    }

    public static void handle(
            ServerboundOpenMaidAdvancementPagePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            AdapterRuntime.require(MaidAdvancementServerPacketHandler.class)
                    .openPage(player, packet.maidEntityId());
        }
        context.setPacketHandled(true);
    }
}
