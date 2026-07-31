package com.laixia.maidintelligence.feature.physics.api;

import java.util.Optional;

final class PhysicsCollisionValidation {
    private PhysicsCollisionValidation() {
    }

    static void vector(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException(
                    "Collision vector components must be finite"
            );
        }
    }

    static void normal(PhysicsBoneSelectionPlan.CollisionVector normal) {
        if (normal.x() == 0.0F && normal.y() == 0.0F && normal.z() == 0.0F) {
            throw new IllegalArgumentException(
                    "Collision plane normal must be non-zero"
            );
        }
    }

    static void radius(float radius, String message) {
        if (!Float.isFinite(radius) || radius < 0.0F) {
            throw new IllegalArgumentException(message);
        }
    }

    static String reference(String reference) {
        String normalized = java.util.Objects.requireNonNull(
                reference,
                "reference"
        ).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Collision reference must not be blank"
            );
        }
        return normalized;
    }

    static Optional<Float> hitRadius(Optional<Float> hitRadius) {
        Optional<Float> normalized = hitRadius == null
                ? Optional.empty()
                : hitRadius;
        normalized.ifPresent(radius -> radius(
                radius,
                "Collision hit radius must be finite and non-negative"
        ));
        return normalized;
    }
}
