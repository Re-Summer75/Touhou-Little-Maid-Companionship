package com.laixia.maidintelligence.feature.physics.client.solver.spring;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Solver-owned temporary values for animation derivative sampling.
 */
final class AnimationMotionScratch {
    static final float TICKS_PER_SECOND = 20.0F;
    static final float MAX_SCALE_STEP_RATIO = 2.50F;

    final Quaternionf orientationInverse = new Quaternionf();
    final Quaternionf orientationDelta = new Quaternionf();
    final Vector3f currentLever = new Vector3f();
    final Vector3f linearVelocity = new Vector3f();
    final Vector3f linearAcceleration = new Vector3f();
    final Vector3f angularVelocity = new Vector3f();
    final Vector3f angularAcceleration = new Vector3f();
    final Vector3f angularKinematicAcceleration = new Vector3f();
    final Vector3f radialAcceleration = new Vector3f();
    final Vector3f crossScratch = new Vector3f();
    final Vector3f rawAcceleration = new Vector3f();

    float angularVelocityInto(
            Quaternionf previous,
            Quaternionf current,
            float dt
    ) {
        float dot = previous.x() * current.x()
                + previous.y() * current.y()
                + previous.z() * current.z()
                + previous.w() * current.w();
        if (dot < 0.0F) {
            orientationDelta.set(
                    -current.x(),
                    -current.y(),
                    -current.z(),
                    -current.w()
            );
        } else {
            orientationDelta.set(current);
        }
        orientationInverse.set(previous).conjugate();
        orientationDelta.mul(orientationInverse).normalize();
        float vectorLength = (float) Math.sqrt(
                orientationDelta.x() * orientationDelta.x()
                        + orientationDelta.y() * orientationDelta.y()
                        + orientationDelta.z() * orientationDelta.z()
        );
        float angle = 2.0F * (float) Math.atan2(
                vectorLength,
                Math.max(0.0F, orientationDelta.w())
        );
        float gain = vectorLength <= SpringBoneMath.EPSILON
                ? 2.0F / (dt * TICKS_PER_SECOND)
                : angle / (vectorLength * dt * TICKS_PER_SECOND);
        angularVelocity.set(
                orientationDelta.x(),
                orientationDelta.y(),
                orientationDelta.z()
        ).mul(gain);
        return angle;
    }

    void deriveAngularAcceleration(
            float scaleVelocity,
            float scaleAcceleration,
            float leverLength
    ) {
        angularAcceleration.cross(
                currentLever,
                angularKinematicAcceleration
        );
        angularVelocity.cross(currentLever, crossScratch);
        angularVelocity.cross(crossScratch, crossScratch)
                .mul(TICKS_PER_SECOND);
        angularKinematicAcceleration.add(crossScratch);

        if (leverLength <= SpringBoneMath.EPSILON) {
            radialAcceleration.zero();
            return;
        }
        radialAcceleration.set(currentLever)
                .mul(scaleAcceleration / leverLength);
        crossScratch.set(currentLever)
                .mul(scaleVelocity / leverLength);
        angularVelocity.cross(crossScratch, crossScratch)
                .mul(2.0F * TICKS_PER_SECOND);
        angularKinematicAcceleration.add(crossScratch);
    }

    static boolean finite(Vector3f value) {
        return Float.isFinite(value.x())
                && Float.isFinite(value.y())
                && Float.isFinite(value.z());
    }

    static boolean finite(Quaternionf value) {
        return Float.isFinite(value.x())
                && Float.isFinite(value.y())
                && Float.isFinite(value.z())
                && Float.isFinite(value.w());
    }

    static boolean scaleDiscontinuous(
            float previousLength,
            float currentLength
    ) {
        if (previousLength <= SpringBoneMath.EPSILON
                || currentLength <= SpringBoneMath.EPSILON) {
            return previousLength > SpringBoneMath.EPSILON
                    || currentLength > SpringBoneMath.EPSILON;
        }
        float ratio = currentLength / previousLength;
        return ratio > MAX_SCALE_STEP_RATIO
                || ratio < 1.0F / MAX_SCALE_STEP_RATIO;
    }
}
