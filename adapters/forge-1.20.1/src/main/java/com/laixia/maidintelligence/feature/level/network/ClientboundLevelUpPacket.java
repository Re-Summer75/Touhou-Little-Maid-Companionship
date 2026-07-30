package com.laixia.maidintelligence.feature.level.network;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
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

    public static void handle(
            ClientboundLevelUpPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientHandler.handle(packet)
        );
        contextSupplier.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        private ClientHandler() {
        }

        private static void handle(ClientboundLevelUpPacket packet) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }
            Entity entity = minecraft.level.getEntity(packet.maidEntityId());
            if (entity != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        ModResources.translationKey("message", "level_up"),
                        entity.getDisplayName(),
                        packet.oldLevel(),
                        packet.newLevel()
                ), false);
            }
        }
    }
}
