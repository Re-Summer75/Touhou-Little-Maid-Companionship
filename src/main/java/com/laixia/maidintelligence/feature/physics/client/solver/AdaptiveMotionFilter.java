package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

/**
 * One-Euro-style nonlinear low-pass filter. Small changes use a low cutoff to
 * suppress network/tick noise; fast intentional motion raises the cutoff.
 */
public final class AdaptiveMotionFilter {
    private static final float MIN_CUTOFF = 1.8F;
    private static final float DERIVATIVE_CUTOFF = 1.0F;
    private static final float ACCELERATION_BETA = 0.65F;
    private static final float YAW_BETA = 3.0F;
    private static final float MAX_CUTOFF = 12.0F;
    private static final float TAU_FACTOR = 2.0F * (float) Math.PI;
    private static final float EPSILON = 1.0E-6F;

    private final AdaptiveVector acceleration = new AdaptiveVector();
    private final AdaptiveScalar yawRate = new AdaptiveScalar();

    public Output update(
            Vector3f rawAcceleration,
            float rawYawRate,
            float maximumAcceleration,
            float dt
    ) {
        Vector3f conditionedAcceleration = MotionNoiseGate.vector(
                rawAcceleration,
                maximumAcceleration,
                maximumAcceleration * 0.02F,
                maximumAcceleration * 0.08F
        );
        float maximumYawRate = (float) Math.toRadians(45.0D);
        float conditionedYaw = MotionNoiseGate.scalar(
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

    public void reset() {
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

    public record Output(Vector3f acceleration, float yawRate) {
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
