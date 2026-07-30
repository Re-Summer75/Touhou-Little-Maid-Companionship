package com.laixia.maidintelligence.feature.advancement.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * TLM-sensitive behavior behind the pure Minecraft HoneyBlock Mixin.
 */
public interface HoneySlideHandler {
    void onHoneySlide(Entity entity, BlockPos blockPosition);
}
