package com.laixia.maidintelligence.platform.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 负责集中分配消息 ID，避免各特性直接操作通道。
 */
public final class PacketRegistrar {
    private final SimpleChannel channel;
    private int nextMessageId;

    public PacketRegistrar(SimpleChannel channel) {
        this.channel = channel;
    }

    public <T> void registerClientbound(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        channel.messageBuilder(type, nextMessageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
    }

    public <T> void registerServerbound(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        channel.messageBuilder(type, nextMessageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
    }
}
