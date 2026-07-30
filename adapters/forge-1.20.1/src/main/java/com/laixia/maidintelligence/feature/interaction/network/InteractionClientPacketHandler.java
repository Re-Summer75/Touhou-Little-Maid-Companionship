package com.laixia.maidintelligence.feature.interaction.network;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only TLM effect boundary used by the Forge packet adapter.
 */
public interface InteractionClientPacketHandler {
    void showEatingParticles(
            int maidEntityId,
            ItemStack food,
            boolean useTrackedFace,
            Vec3 worldCenter,
            Vec3 worldNormal
    );
}
