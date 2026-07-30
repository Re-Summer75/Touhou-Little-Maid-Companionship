package com.laixia.maidintelligence.feature.physics.engine;


import com.laixia.maidintelligence.shared.geometry.Vec3d;
import org.joml.Vector3f;

/**
 * Samples tick-driven entity motion without injecting zero-valued render-frame
 * samples between game ticks.
 */
public final class MotionSignalSampler {
    private final AdaptiveMotionFilter filter = new AdaptiveMotionFilter();
    private final Vector3f sampledAcceleration = new Vector3f();
    private double previousVelocityX;
    private double previousVelocityY;
    private double previousVelocityZ;
    private float sampledYawRate;
    private int sampledTick = Integer.MIN_VALUE;

    public Sample update(
            Vec3d velocity,
            int currentTick,
            float bodyYaw,
            float previousBodyYaw,
            float maximumAcceleration,
            float dt
    ) {
        Vector3f accelerationOutput = new Vector3f();
        float yawRate = updateInto(
                velocity.x(),
                velocity.y(),
                velocity.z(),
                currentTick,
                bodyYaw,
                previousBodyYaw,
                maximumAcceleration,
                dt,
                accelerationOutput
        );
        return new Sample(accelerationOutput, yawRate);
    }

    public float updateInto(
            double velocityX,
            double velocityY,
            double velocityZ,
            int currentTick,
            float bodyYaw,
            float previousBodyYaw,
            float maximumAcceleration,
            float dt,
            Vector3f accelerationOutput
    ) {
        if (sampledTick == Integer.MIN_VALUE || currentTick < sampledTick) {
            reset(velocityX, velocityY, velocityZ, currentTick);
        } else if (currentTick > sampledTick) {
            float elapsedSeconds = (currentTick - sampledTick) / 20.0F;
            double inverseElapsed = 1.0D / elapsedSeconds;
            sampledAcceleration.set(
                    (float) ((velocityX - previousVelocityX) * inverseElapsed),
                    (float) ((velocityY - previousVelocityY) * inverseElapsed),
                    (float) ((velocityZ - previousVelocityZ) * inverseElapsed)
            );
            sampledYawRate = (float) Math.toRadians(
                    PhysicsMath.wrapDegrees(bodyYaw - previousBodyYaw)
            );
            previousVelocityX = velocityX;
            previousVelocityY = velocityY;
            previousVelocityZ = velocityZ;
            sampledTick = currentTick;
        }
        return filter.updateInto(
                sampledAcceleration,
                sampledYawRate,
                maximumAcceleration,
                dt,
                accelerationOutput
        );
    }

    public void reset() {
        previousVelocityX = 0.0D;
        previousVelocityY = 0.0D;
        previousVelocityZ = 0.0D;
        sampledAcceleration.zero();
        sampledYawRate = 0.0F;
        sampledTick = Integer.MIN_VALUE;
        filter.reset();
    }

    private void reset(
            double velocityX,
            double velocityY,
            double velocityZ,
            int currentTick
    ) {
        previousVelocityX = velocityX;
        previousVelocityY = velocityY;
        previousVelocityZ = velocityZ;
        sampledAcceleration.zero();
        sampledYawRate = 0.0F;
        sampledTick = currentTick;
        filter.reset();
    }

    public record Sample(Vector3f worldAcceleration, float yawRate) {
    }
}
