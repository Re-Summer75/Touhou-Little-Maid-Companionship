package com.laixia.maidintelligence.platform.network.packet;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.menu.MaidAdvancementContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * 顶部 Tab 请求打开进度页。校验方式对齐本体的 {@code ToggleTabMessage}：只有主人、
 * 女仆存活未睡着且在交互距离内才开界面。
 */
public record ServerboundOpenMaidAdvancementPagePacket(int maidEntityId) {
    private static final double MAX_INTERACTION_DISTANCE = 3.0D;

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
        if (player != null
                && player.level().getEntity(packet.maidEntityId()) instanceof EntityMaid maid
                && maid.isOwnedBy(player)
                && maid.isAlive()
                && !maid.isSleeping()
                && player.canReach(maid, MAX_INTERACTION_DISTANCE)) {
            maid.getNavigation().stop();
            NetworkHooks.openScreen(
                    player,
                    MaidAdvancementContainer.create(maid.getId()),
                    buffer -> buffer.writeInt(maid.getId())
            );
        }
        context.setPacketHandled(true);
    }
}
