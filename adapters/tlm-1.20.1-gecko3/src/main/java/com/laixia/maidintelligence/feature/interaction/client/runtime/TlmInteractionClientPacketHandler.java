package com.laixia.maidintelligence.feature.interaction.client.runtime;

import com.laixia.maidintelligence.feature.interaction.client.feed.MaidEatingParticleEffect;
import com.laixia.maidintelligence.feature.interaction.network.InteractionClientPacketHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TlmInteractionClientPacketHandler
        implements InteractionClientPacketHandler {
    @Override
    public void showEatingParticles(
            int maidEntityId,
            ItemStack food,
            boolean useTrackedFace,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        if (useTrackedFace) {
            MaidEatingParticleEffect.spawnFromTrackedFace(
                    maidEntityId,
                    food
            );
        } else {
            MaidEatingParticleEffect.spawn(
                    food,
                    worldCenter,
                    worldNormal
            );
        }
    }
}
