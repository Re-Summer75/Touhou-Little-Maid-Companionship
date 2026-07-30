package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import com.laixia.maidintelligence.feature.physics.client.solver.MotionNoiseGate;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Persistent derivative history and conditioned output for one driven bone.
 */
final class AnimationMotionSample {
    private static final float MIN_DELTA_SECONDS = 1.0E-4F;
    private static final float MAX_DELTA_SECONDS = 0.10F;
    private static final float MIN_PIVOT_CUT_DISTANCE = 0.50F;
    private static final float PIVOT_CUT_SEGMENT_SCALE = 3.0F;
    private static final float MAX_ACCELERATION = 0.50F;
    private static final float ACCELERATION_NOISE_FLOOR = 0.0015F;
    private static final float ACCELERATION_NOISE_FADE = 0.010F;
    private static final float FILTER_TIME_CONSTANT = 0.040F;
    private static final float MAX_ACCELERATION_HOLD_SECONDS = 0.125F;
    private static final float MAX_POSE_SAMPLE_INTERVAL_SECONDS = 0.25F;
    private static final float POSITION_CHANGE_EPSILON_SQUARED = 1.0E-10F;
    private static final float ANGULAR_CHANGE_EPSILON = 1.0E-5F;
    private static final float SCALE_CHANGE_EPSILON = 1.0E-6F;
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final Vector3f previousPivot = new Vector3f();
    private final Vector3f previousLinearVelocity = new Vector3f();
    private final Quaternionf previousMountOrientation = new Quaternionf();
    private final Vector3f previousAngularVelocity = new Vector3f();
    private final Vector3f targetAcceleration = new Vector3f();
    private final Vector3f acceleration = new Vector3f();
    private float previousLeverLength;
    private float previousScaleVelocity;
    private float sampleElapsedSeconds;
    private boolean initialized;

    void update(
            PhysicsSolverLayout.Node node,
            Vector3f currentPivot,
            Vector3f currentTip,
            Quaternionf mountOrientation,
            float dt,
            boolean paused,
            AnimationMotionScratch scratch
    ) {
        scratch.currentLever.set(currentTip).sub(currentPivot);
        float leverLength = scratch.currentLever.length();
        if (!AnimationMotionScratch.finite(currentPivot)
                || !AnimationMotionScratch.finite(currentTip)
                || !AnimationMotionScratch.finite(mountOrientation)
                || !Float.isFinite(leverLength)) {
            reset();
            return;
        }
        if (paused
                || !Float.isFinite(dt)
                || dt > MAX_DELTA_SECONDS
                || !initialized) {
            initialize(currentPivot, mountOrientation, leverLength);
            return;
        }
        if (dt < MIN_DELTA_SECONDS) {
            return;
        }

        sampleElapsedSeconds += dt;
        float angularStep = scratch.angularVelocityInto(
                previousMountOrientation,
                mountOrientation,
                sampleElapsedSeconds
        );
        if (!poseChanged(currentPivot, leverLength, angularStep)) {
            if (sampleElapsedSeconds
                    >= MAX_ACCELERATION_HOLD_SECONDS) {
                targetAcceleration.zero();
            }
            if (sampleElapsedSeconds
                    >= MAX_POSE_SAMPLE_INTERVAL_SECONDS) {
                settleHeldPose(
                        currentPivot,
                        mountOrientation,
                        leverLength
                );
            }
            filterOutput(dt);
            return;
        }
        if (discontinuous(
                node,
                currentPivot,
                leverLength,
                angularStep
        )) {
            initialize(currentPivot, mountOrientation, leverLength);
            return;
        }

        float inverseSeconds = 1.0F / sampleElapsedSeconds;
        float inverseTicks =
                inverseSeconds / AnimationMotionScratch.TICKS_PER_SECOND;
        scratch.linearVelocity.set(currentPivot)
                .sub(previousPivot)
                .mul(inverseTicks);
        scratch.linearAcceleration.set(scratch.linearVelocity)
                .sub(previousLinearVelocity)
                .mul(inverseSeconds);
        scratch.angularAcceleration.set(scratch.angularVelocity)
                .sub(previousAngularVelocity)
                .mul(inverseSeconds);
        float scaleVelocity =
                (leverLength - previousLeverLength) * inverseTicks;
        float scaleAcceleration =
                (scaleVelocity - previousScaleVelocity) * inverseSeconds;
        scratch.deriveAngularAcceleration(
                scaleVelocity,
                scaleAcceleration,
                leverLength
        );

        scratch.rawAcceleration.set(scratch.linearAcceleration)
                .mul(AnimationInertiaPolicy.linearGain(node))
                .fma(
                        AnimationInertiaPolicy.angularGain(node),
                        scratch.angularKinematicAcceleration
                )
                .fma(
                        AnimationInertiaPolicy.scaleGain(node),
                        scratch.radialAcceleration
                );
        if (!AnimationMotionScratch.finite(scratch.rawAcceleration)) {
            initialize(currentPivot, mountOrientation, leverLength);
            return;
        }
        MotionNoiseGate.vectorInto(
                scratch.rawAcceleration,
                MAX_ACCELERATION,
                ACCELERATION_NOISE_FLOOR,
                ACCELERATION_NOISE_FADE,
                targetAcceleration
        );
        filterOutput(dt);
        record(
                currentPivot,
                mountOrientation,
                leverLength,
                scaleVelocity,
                scratch
        );
    }

    private void filterOutput(float dt) {
        float filterGain = 1.0F - (float) Math.exp(
                -dt / FILTER_TIME_CONSTANT
        );
        acceleration.lerp(targetAcceleration, filterGain);
        if (acceleration.lengthSquared() < 1.0E-12F) {
            acceleration.zero();
        }
    }

    Vector3f acceleration() {
        return acceleration;
    }

    boolean copyAcceleration(Vector3f output) {
        if (!initialized) {
            output.zero();
            return false;
        }
        output.set(acceleration);
        return true;
    }

    void reset() {
        previousPivot.zero();
        previousLinearVelocity.zero();
        previousMountOrientation.identity();
        previousAngularVelocity.zero();
        targetAcceleration.zero();
        acceleration.zero();
        previousLeverLength = 0.0F;
        previousScaleVelocity = 0.0F;
        sampleElapsedSeconds = 0.0F;
        initialized = false;
    }

    private boolean poseChanged(
            Vector3f currentPivot,
            float leverLength,
            float angularStep
    ) {
        return currentPivot.distanceSquared(previousPivot)
                > POSITION_CHANGE_EPSILON_SQUARED
                || angularStep > ANGULAR_CHANGE_EPSILON
                || Math.abs(leverLength - previousLeverLength)
                > SCALE_CHANGE_EPSILON;
    }

    private boolean discontinuous(
            PhysicsSolverLayout.Node node,
            Vector3f currentPivot,
            float leverLength,
            float angularStep
    ) {
        float pivotCutDistance = Math.max(
                MIN_PIVOT_CUT_DISTANCE,
                node.kinematics().segmentLength()
                        / PIXELS_PER_BLOCK
                        * PIVOT_CUT_SEGMENT_SCALE
        );
        return !Float.isFinite(angularStep)
                || angularStep > SpringBoneMath.MAX_REFERENCE_DELTA
                || currentPivot.distanceSquared(previousPivot)
                > pivotCutDistance * pivotCutDistance
                || AnimationMotionScratch.scaleDiscontinuous(
                previousLeverLength,
                leverLength
        );
    }

    private void initialize(
            Vector3f pivot,
            Quaternionf orientation,
            float leverLength
    ) {
        previousPivot.set(pivot);
        previousLinearVelocity.zero();
        previousMountOrientation.set(orientation);
        previousAngularVelocity.zero();
        targetAcceleration.zero();
        acceleration.zero();
        previousLeverLength = leverLength;
        previousScaleVelocity = 0.0F;
        sampleElapsedSeconds = 0.0F;
        initialized = true;
    }

    private void settleHeldPose(
            Vector3f pivot,
            Quaternionf orientation,
            float leverLength
    ) {
        previousPivot.set(pivot);
        previousLinearVelocity.zero();
        previousMountOrientation.set(orientation);
        previousAngularVelocity.zero();
        targetAcceleration.zero();
        previousLeverLength = leverLength;
        previousScaleVelocity = 0.0F;
        sampleElapsedSeconds = 0.0F;
    }

    private void record(
            Vector3f pivot,
            Quaternionf orientation,
            float leverLength,
            float scaleVelocity,
            AnimationMotionScratch scratch
    ) {
        previousPivot.set(pivot);
        previousLinearVelocity.set(scratch.linearVelocity);
        previousMountOrientation.set(orientation);
        previousAngularVelocity.set(scratch.angularVelocity);
        previousLeverLength = leverLength;
        previousScaleVelocity = scaleVelocity;
        sampleElapsedSeconds = 0.0F;
    }
}
