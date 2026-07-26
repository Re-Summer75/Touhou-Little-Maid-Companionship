package com.laixia.maidintelligence.feature.physics.client.pose;

import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * Pluggable world-space signal for procedural secondary-motion animation.
 *
 * <p>Implementations overwrite {@code output}, advance only for positive
 * {@code dt}, and must not allocate on the per-frame path.
 */
public interface WorldPoseDriverSource {
    Vector3f sampleInto(
            LivingEntity entity,
            double animationTick,
            float dt,
            boolean paused,
            Vector3f output
    );

    void reset();
}
