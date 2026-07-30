package com.laixia.maidintelligence.feature.physics.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Frozen allocating implementation from before the hot-path rewrite.
 */
final class ReferenceMotionSignalSampler {
    private final ReferenceAdaptiveMotionFilter filter =
            new ReferenceAdaptiveMotionFilter();
    private final Vector3f sampledAcceleration = new Vector3f();
    private Vec3 previousVelocity = Vec3.ZERO;
    private float sampledYawRate;
    private int sampledTick = Integer.MIN_VALUE;

    Output update(
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
        ReferenceAdaptiveMotionFilter.Output output = filter.update(
                sampledAcceleration,
                sampledYawRate,
                maximumAcceleration,
                dt
        );
        return new Output(output.acceleration(), output.yawRate());
    }

    private void reset(Vec3 velocity, int currentTick) {
        previousVelocity = velocity;
        sampledAcceleration.zero();
        sampledYawRate = 0.0F;
        sampledTick = currentTick;
        filter.reset();
    }

    record Output(Vector3f worldAcceleration, float yawRate) {
    }
}

final class ReferenceAdaptiveMotionFilter {
    private static final float MIN_CUTOFF = 1.8F;
    private static final float DERIVATIVE_CUTOFF = 1.0F;
    private static final float ACCELERATION_BETA = 0.65F;
    private static final float YAW_BETA = 3.0F;
    private static final float MAX_CUTOFF = 12.0F;
    private static final float TAU_FACTOR = 2.0F * (float) Math.PI;
    private static final float EPSILON = 1.0E-6F;

    private final AdaptiveVector acceleration = new AdaptiveVector();
    private final AdaptiveScalar yawRate = new AdaptiveScalar();

    Output update(
            Vector3f rawAcceleration,
            float rawYawRate,
            float maximumAcceleration,
            float dt
    ) {
        Vector3f conditionedAcceleration = ReferenceMotionNoiseGate.vector(
                rawAcceleration,
                maximumAcceleration,
                maximumAcceleration * 0.02F,
                maximumAcceleration * 0.08F
        );
        float maximumYawRate = (float) Math.toRadians(45.0D);
        float conditionedYaw = ReferenceMotionNoiseGate.scalar(
                rawYawRate,
                maximumYawRate,
                (float) Math.toRadians(0.15D),
                (float) Math.toRadians(0.60D)
        );
        return new Output(
                acceleration.update(
                        conditionedAcceleration,
                        dt,
                        ACCELERATION_BETA
                ),
                yawRate.update(conditionedYaw, dt, YAW_BETA)
        );
    }

    void reset() {
        acceleration.reset();
        yawRate.reset();
    }

    private static float smoothingAlpha(float cutoff, float dt) {
        if (dt <= EPSILON) {
            return 0.0F;
        }
        float tau = 1.0F / (TAU_FACTOR * cutoff);
        return dt / (dt + tau);
    }

    private static float adaptiveCutoff(float speed, float beta) {
        return Math.max(
                MIN_CUTOFF,
                Math.min(MAX_CUTOFF, MIN_CUTOFF + beta * speed)
        );
    }

    record Output(Vector3f acceleration, float yawRate) {
    }

    private static final class AdaptiveVector {
        private final Vector3f previousInput = new Vector3f();
        private final Vector3f filtered = new Vector3f();
        private final Vector3f derivative = new Vector3f();
        private boolean initialized;

        private Vector3f update(Vector3f input, float dt, float beta) {
            if (!initialized) {
                previousInput.set(input);
                filtered.set(input);
                derivative.zero();
                initialized = true;
                return new Vector3f(filtered);
            }
            if (dt <= EPSILON) {
                return new Vector3f(filtered);
            }
            Vector3f rawDerivative = new Vector3f(input)
                    .sub(previousInput)
                    .div(dt);
            derivative.lerp(
                    rawDerivative,
                    smoothingAlpha(DERIVATIVE_CUTOFF, dt)
            );
            filtered.lerp(
                    input,
                    smoothingAlpha(
                            adaptiveCutoff(derivative.length(), beta),
                            dt
                    )
            );
            previousInput.set(input);
            return new Vector3f(filtered);
        }

        private void reset() {
            previousInput.zero();
            filtered.zero();
            derivative.zero();
            initialized = false;
        }
    }

    private static final class AdaptiveScalar {
        private float previousInput;
        private float filtered;
        private float derivative;
        private boolean initialized;

        private float update(float input, float dt, float beta) {
            if (!initialized) {
                previousInput = input;
                filtered = input;
                derivative = 0.0F;
                initialized = true;
                return filtered;
            }
            if (dt <= EPSILON) {
                return filtered;
            }
            float rawDerivative = (input - previousInput) / dt;
            float derivativeAlpha =
                    smoothingAlpha(DERIVATIVE_CUTOFF, dt);
            derivative += derivativeAlpha * (rawDerivative - derivative);
            float valueAlpha = smoothingAlpha(
                    adaptiveCutoff(Math.abs(derivative), beta),
                    dt
            );
            filtered += valueAlpha * (input - filtered);
            previousInput = input;
            return filtered;
        }

        private void reset() {
            previousInput = 0.0F;
            filtered = 0.0F;
            derivative = 0.0F;
            initialized = false;
        }
    }
}

final class ReferenceMotionNoiseGate {
    private static final float EPSILON = 1.0E-6F;

    private ReferenceMotionNoiseGate() {
    }

    static Vector3f vector(
            Vector3f input,
            float maximum,
            float noiseFloor,
            float noiseFade
    ) {
        float length = input.length();
        if (length < EPSILON || maximum <= EPSILON) {
            return new Vector3f();
        }
        float limited = softLimit(length, maximum);
        float gain = deadZoneGain(limited, noiseFloor, noiseFade);
        return new Vector3f(input).mul(limited * gain / length);
    }

    static float scalar(
            float input,
            float maximum,
            float noiseFloor,
            float noiseFade
    ) {
        float magnitude = Math.abs(input);
        if (magnitude < EPSILON || maximum <= EPSILON) {
            return 0.0F;
        }
        float limited = softLimit(magnitude, maximum);
        return Math.copySign(
                limited * deadZoneGain(limited, noiseFloor, noiseFade),
                input
        );
    }

    private static float softLimit(float value, float maximum) {
        float knee = maximum * 0.70F;
        if (value <= knee) {
            return value;
        }
        float range = maximum - knee;
        return knee + range * (float) Math.tanh((value - knee) / range);
    }

    private static float deadZoneGain(
            float value,
            float noiseFloor,
            float noiseFade
    ) {
        if (value <= noiseFloor) {
            return 0.0F;
        }
        if (noiseFade <= EPSILON || value >= noiseFloor + noiseFade) {
            return 1.0F;
        }
        float ratio = (value - noiseFloor) / noiseFade;
        return ratio * ratio * (3.0F - 2.0F * ratio);
    }
}
