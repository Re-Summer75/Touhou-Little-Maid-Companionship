package com.laixia.maidintelligence.feature.interaction.bridge;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Determines whether vanilla eating particles are replaced by an adapter.
 */
public interface EatingParticlePolicy {
    boolean suppressVanillaParticles(
            LivingEntity entity,
            ItemStack item
    );
}
