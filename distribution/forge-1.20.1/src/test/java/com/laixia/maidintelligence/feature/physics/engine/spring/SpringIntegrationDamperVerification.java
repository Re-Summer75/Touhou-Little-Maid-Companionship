package com.laixia.maidintelligence.feature.physics.engine.spring;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Focused checks for rest-frame transport used during integration cycles. */
final class SpringIntegrationDamperVerification {
    private static final float TOLERANCE = 1.0E-5F;

    private SpringIntegrationDamperVerification() {
    }

    static void run() {
        preservesDeflectionAcrossMovingRest();
        leavesAStationaryRestUntouched();
    }

    private static void preservesDeflectionAcrossMovingRest() {
        Vector3f previousRest = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f rest = new Quaternionf()
                .rotateZ(0.47F)
                .transform(new Vector3f(previousRest));
        Vector3f direction = new Quaternionf()
                .rotateX(0.31F)
                .transform(new Vector3f(previousRest));
        Vector3f transported = new Vector3f();

        SpringIntegrationDamper.transportInto(
                previousRest,
                rest,
                direction,
                transported
        );

        requireNear(
                previousRest.dot(direction),
                rest.dot(transported),
                "Rest transport changed physical deflection"
        );
        requireNear(
                1.0F,
                transported.length(),
                "Rest transport changed direction length"
        );
    }

    private static void leavesAStationaryRestUntouched() {
        Vector3f rest = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f direction = new Vector3f(0.2F, 0.96F, -0.1F).normalize();
        Vector3f transported = new Vector3f();

        SpringIntegrationDamper.transportInto(
                rest,
                rest,
                direction,
                transported
        );
        requireNear(
                0.0F,
                direction.distance(transported),
                "Stationary rest moved the direction"
        );
    }

    private static void requireNear(
            float expected,
            float actual,
            String message
    ) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(
                    message + ": expected " + expected + ", got " + actual
            );
        }
    }
}
