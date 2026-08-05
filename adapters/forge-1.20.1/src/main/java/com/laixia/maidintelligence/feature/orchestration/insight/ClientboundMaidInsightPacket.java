package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One maid's decision summary, sent only while a player holds the soul lens.
 */
public record ClientboundMaidInsightPacket(
        int maidEntityId,
        MaidInsight insight
) {
    public static void encode(
            ClientboundMaidInsightPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(packet.maidEntityId());
        MaidInsightCodec.write(buffer, packet.insight());
    }

    public static ClientboundMaidInsightPacket decode(FriendlyByteBuf buffer) {
        int maidEntityId = buffer.readVarInt();
        return new ClientboundMaidInsightPacket(
                maidEntityId,
                MaidInsightCodec.read(buffer)
        );
    }

    public static void handle(
            ClientboundMaidInsightPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientMaidInsights.accept(
                        packet.maidEntityId(),
                        packet.insight()
                )
        );
        contextSupplier.get().setPacketHandled(true);
    }
}
