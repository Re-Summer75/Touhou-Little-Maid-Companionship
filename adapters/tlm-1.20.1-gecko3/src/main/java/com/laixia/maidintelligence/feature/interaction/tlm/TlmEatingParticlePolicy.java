package com.laixia.maidintelligence.feature.interaction.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.bridge.EatingParticlePolicy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public final class TlmEatingParticlePolicy
        implements EatingParticlePolicy {
    @Override
    public boolean suppressVanillaParticles(
            LivingEntity entity,
            ItemStack item
    ) {
        return entity instanceof EntityMaid
                && item.getUseAnimation() == UseAnim.EAT;
    }
}
