package com.laixia.maidintelligence.platform.network.packet;

import com.laixia.maidintelligence.client.network.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundLevelUpPacket(int maidEntityId, int oldLevel, int newLevel) {
    public static void encode(ClientboundLevelUpPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidEntityId);
        buffer.writeVarInt(packet.oldLevel);
        buffer.writeVarInt(packet.newLevel);
    }

    public static ClientboundLevelUpPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundLevelUpPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(ClientboundLevelUpPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleLevelUp(packet));
        contextSupplier.get().setPacketHandled(true);
    }
}
