package com.laixia.maidintelligence.platform.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registers explicit protocol IDs while rejecting duplicates and gaps.
 */
public final class PacketRegistrar {
    private final SimpleChannel channel;
    private final Set<Integer> messageIds = new HashSet<>();

    public PacketRegistrar(SimpleChannel channel) {
        this.channel = channel;
    }

    public <T> void registerClientbound(
            int messageId,
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        claim(messageId);
        channel.messageBuilder(type, messageId, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
    }

    public <T> void registerServerbound(
            int messageId,
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        claim(messageId);
        channel.messageBuilder(type, messageId, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add();
    }

    public void verifyContiguousIds() {
        if (messageIds.isEmpty()) {
            throw new IllegalStateException("No packets were registered");
        }
        int maximum = messageIds.stream().mapToInt(Integer::intValue).max()
                .orElseThrow();
        for (int id = 0; id <= maximum; id++) {
            if (!messageIds.contains(id)) {
                throw new IllegalStateException(
                        "Missing packet protocol ID " + id
                );
            }
        }
    }

    private void claim(int messageId) {
        if (messageId < 0 || !messageIds.add(messageId)) {
            throw new IllegalStateException(
                    "Invalid or duplicate packet protocol ID " + messageId
            );
        }
    }
}
