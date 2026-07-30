package com.laixia.maidintelligence.platform.network;

/**
 * Feature-owned contribution to the shared Forge channel.
 */
@FunctionalInterface
public interface FeaturePacketRegistrar {
    void registerPackets(PacketRegistrar registrar);
}
