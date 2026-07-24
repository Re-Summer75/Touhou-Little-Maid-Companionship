package com.laixia.maidintelligence.feature.physics.client.solver.collision.build;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneGeometry;
import org.joml.Vector3f;

/**
 * Derives a conservative endpoint radius from driven-bone geometry.
 */
final class CollisionHitRadius {
    private static final float EPSILON = 1.0E-6F;

    private CollisionHitRadius() {
    }

    static float derive(
            PhysicsBoneGeometry.Node node,
            float colliderRadius
    ) {
        if (node == null || !node.hasGeometry()) {
            return 0.0F;
        }
        Vector3f size = node.size();
        float smallest = smallestPositive(size.x, size.y, size.z);
        if (smallest <= EPSILON) {
            return 0.0F;
        }
        float derived = smallest * 0.5F;
        return colliderRadius > EPSILON
                ? Math.min(derived, colliderRadius * 0.15F)
                : derived;
    }

    private static float smallestPositive(float x, float y, float z) {
        float result = Float.POSITIVE_INFINITY;
        if (x > EPSILON) {
            result = x;
        }
        if (y > EPSILON) {
            result = Math.min(result, y);
        }
        if (z > EPSILON) {
            result = Math.min(result, z);
        }
        return Float.isFinite(result) ? result : 0.0F;
    }
}
