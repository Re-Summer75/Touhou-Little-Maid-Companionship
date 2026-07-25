package com.laixia.maidintelligence.feature.physics.client.solver.collision.runtime;

import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySource;

/**
 * Preserves an automatic proxy's authored initial overlap without allowing
 * secondary motion to make that overlap deeper.
 */
final class PreparedCollisionRestAllowance {
    private static final float EPSILON = 1.0E-6F;

    private boolean calibrated;
    private float normalizedPenetration;
    private float runtimeScale = 1.0F;

    void prepare(
            float colliderScale,
            float endpointScale,
            float runtimeLeverArm,
            float fixedLeverArm
    ) {
        float leverScale = runtimeLeverArm / Math.max(EPSILON, fixedLeverArm);
        runtimeScale = Math.max(
                EPSILON,
                Math.max(
                        finiteScale(colliderScale),
                        Math.max(finiteScale(endpointScale), leverScale)
                )
        );
    }

    boolean needsCalibration(CollisionProxySource source) {
        return source == CollisionProxySource.AUTOMATIC && !calibrated;
    }

    void calibrate(float clearance) {
        normalizedPenetration = Math.max(0.0F, -clearance) / runtimeScale;
        calibrated = true;
    }

    float threshold(float unadjusted) {
        return unadjusted - normalizedPenetration * runtimeScale;
    }

    void reset() {
        calibrated = false;
        normalizedPenetration = 0.0F;
        runtimeScale = 1.0F;
    }

    private static float finiteScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.0F, scale) : 1.0F;
    }
}
