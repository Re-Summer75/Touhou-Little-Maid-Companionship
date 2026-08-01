package com.laixia.maidintelligence.feature.physics.engine.spring;

/**
 * Verifies that projection smoothing is normalized by visible tip travel.
 */
final class SpringLinearCorrectionVerification {
    private static final float EPSILON = 1.0E-5F;

    private SpringLinearCorrectionVerification() {
    }

    static void run() {
        float dt = 1.0F / 60.0F;
        float shortLength = 0.125F;
        float longLength = 0.5F;
        float shortTravel = shortLength
                * SpringConstraintProjector.maximumCorrection(
                dt, shortLength
        );
        float longTravel = longLength
                * SpringConstraintProjector.maximumCorrection(
                dt, longLength
        );
        requireNear(shortTravel, longTravel,
                "Correction budget still depends on bone length");
        requireNear(
                SpringConstraintProjector.maximumCorrection(dt, 0.25F),
                40.0F * dt,
                "Reference-length correction rate changed"
        );
        requireNear(
                SpringConstraintProjector.maximumCorrection(0.0F, longLength),
                0.0F,
                "Repeated render frame received correction budget"
        );
    }

    private static void requireNear(
            float actual,
            float expected,
            String message
    ) {
        if (Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(
                    message + ": " + actual + " against " + expected
            );
        }
    }
}
