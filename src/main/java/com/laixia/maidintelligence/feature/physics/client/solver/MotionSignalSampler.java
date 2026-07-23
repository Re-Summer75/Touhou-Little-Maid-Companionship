package com.laixia.maidintelligence.feature.physics.client.solver;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Samples tick-driven entity motion without injecting zero-valued render-frame
 * samples between game ticks.
 */
public final class MotionSignalSampler {
    private final AdaptiveMotionFilter filter = new AdaptiveMotionFilter();
    private final Vector3f sampledAcceleration = new Vector3f();
    private Vec3 previousVelocity = Vec3.ZERO;
    private float sampledYawRate;
    private int sampledTick = Integer.MIN_VALUE;

    public Sample update(
            Vec3 velocity,
            int currentTick,
            float bodyYaw,
            float previousBodyYaw,
            float maximumAcceleration,
            float dt
    ) {
        if (sampledTick == Integer.MIN_VALUE || currentTick < sampledTick) {
            reset(velocity, currentTick);
        } else if (currentTick > sampledTick) {
            float elapsedSeconds = (currentTick - sampledTick) / 20.0F;
            Vec3 acceleration = velocity.subtract(previousVelocity)
                    .scale(1.0D / elapsedSeconds);
            sampledAcceleration.set(
                    (float) acceleration.x,
                    (float) acceleration.y,
                    (float) acceleration.z
            );
            sampledYawRate = (float) Math.toRadians(
                    Mth.wrapDegrees(bodyYaw - previousBodyYaw)
            );
            previousVelocity = velocity;
            sampledTick = currentTick;
        }
        AdaptiveMotionFilter.Output output = filter.update(
                sampledAcceleration,
                sampledYawRate,
                maximumAcceleration,
                dt
        );
        return new Sample(output.acceleration(), output.yawRate());
    }

    public void reset() {
        previousVelocity = Vec3.ZERO;
        sampledAcceleration.zero();
        sampledYawRate = 0.0F;
        sampledTick = Integer.MIN_VALUE;
        filter.reset();
    }

    private void reset(Vec3 velocity, int currentTick) {
        previousVelocity = velocity;
        sampledAcceleration.zero();
        sampledYawRate = 0.0F;
        sampledTick = currentTick;
        filter.reset();
    }

    public record Sample(Vector3f worldAcceleration, float yawRate) {
    }
}
