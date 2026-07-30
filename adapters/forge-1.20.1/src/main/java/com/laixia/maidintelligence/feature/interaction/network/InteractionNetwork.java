package com.laixia.maidintelligence.feature.interaction.network;

import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class InteractionNetwork {
    private InteractionNetwork() {
    }

    public static void sendMouthFeed(
            int maidEntityId,
            float faceU,
            float faceV,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        ModNetwork.sendToServer(new ServerboundMouthFeedPacket(
                maidEntityId,
                faceU,
                faceV,
                worldCenter,
                worldNormal
        ));
    }

    public static void sendEatingParticles(
            Entity trackedEntity,
            ItemStack food,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        ModNetwork.sendTrackingEntityAndSelf(
                trackedEntity,
                new ClientboundMaidEatingParticlesPacket(
                        trackedEntity.getId(),
                        food,
                        false,
                        worldCenter,
                        worldNormal
                )
        );
    }

    public static void sendTrackedFaceEatingParticles(
            Entity trackedEntity,
            ItemStack food
    ) {
        ModNetwork.sendTrackingEntityAndSelf(
                trackedEntity,
                new ClientboundMaidEatingParticlesPacket(
                        trackedEntity.getId(),
                        food,
                        true,
                        Vec3.ZERO,
                        Vec3.ZERO
                )
        );
    }
}
