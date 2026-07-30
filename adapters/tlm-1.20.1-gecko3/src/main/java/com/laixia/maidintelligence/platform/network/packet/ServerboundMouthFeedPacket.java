package com.laixia.maidintelligence.platform.network.packet;

import com.laixia.maidintelligence.feature.interaction.service.MaidMouthFeedRequestHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundMouthFeedPacket(
        int maidEntityId,
        float faceU,
        float faceV,
        Vec3 worldCenter,
        Vec3 worldNormal
) {
    public static void encode(ServerboundMouthFeedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidEntityId());
        buffer.writeFloat(packet.faceU());
        buffer.writeFloat(packet.faceV());
        buffer.writeDouble(packet.worldCenter().x);
        buffer.writeDouble(packet.worldCenter().y);
        buffer.writeDouble(packet.worldCenter().z);
        buffer.writeFloat((float) packet.worldNormal().x);
        buffer.writeFloat((float) packet.worldNormal().y);
        buffer.writeFloat((float) packet.worldNormal().z);
    }

    public static ServerboundMouthFeedPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundMouthFeedPacket(
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                new Vec3(
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()
                ),
                new Vec3(
                        buffer.readFloat(),
                        buffer.readFloat(),
                        buffer.readFloat()
                )
        );
    }

    public static void handle(
            ServerboundMouthFeedPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            MaidMouthFeedRequestHandler.handle(
                    player,
                    packet.maidEntityId(),
                    packet.faceU(),
                    packet.faceV(),
                    packet.worldCenter(),
                    packet.worldNormal()
            );
        }
        context.setPacketHandled(true);
    }
}
