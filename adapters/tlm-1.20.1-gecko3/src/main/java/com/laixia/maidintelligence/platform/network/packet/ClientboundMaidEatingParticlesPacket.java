package com.laixia.maidintelligence.platform.network.packet;

import com.laixia.maidintelligence.client.network.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundMaidEatingParticlesPacket(
        int maidEntityId,
        ItemStack food,
        boolean useTrackedFace,
        Vec3 worldCenter,
        Vec3 worldNormal
) {
    public static void encode(
            ClientboundMaidEatingParticlesPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(packet.maidEntityId());
        buffer.writeItem(packet.food());
        buffer.writeBoolean(packet.useTrackedFace());
        buffer.writeDouble(packet.worldCenter().x);
        buffer.writeDouble(packet.worldCenter().y);
        buffer.writeDouble(packet.worldCenter().z);
        buffer.writeFloat((float) packet.worldNormal().x);
        buffer.writeFloat((float) packet.worldNormal().y);
        buffer.writeFloat((float) packet.worldNormal().z);
    }

    public static ClientboundMaidEatingParticlesPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundMaidEatingParticlesPacket(
                buffer.readVarInt(),
                buffer.readItem(),
                buffer.readBoolean(),
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
            ClientboundMaidEatingParticlesPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPacketHandlers.handleMaidEatingParticles(packet)
        );
        contextSupplier.get().setPacketHandled(true);
    }
}
