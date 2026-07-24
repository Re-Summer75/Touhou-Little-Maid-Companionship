package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import org.joml.Vector3f;

/**
 * Shared fixed-length projection primitives.
 */
final class CollisionProjectionMath {
    static final float EPSILON = 1.0E-6F;

    private CollisionProjectionMath() {
    }

    static boolean projectMinimumDot(
            Vector3f direction,
            Vector3f normal,
            float minimumDot,
            Vector3f tangent
    ) {
        if (minimumDot <= -1.0F) {
            return false;
        }
        minimumDot = Math.min(minimumDot, 1.0F);
        float currentDot = direction.dot(normal);
        if (currentDot + EPSILON >= minimumDot) {
            return false;
        }
        tangent.set(direction).add(
                -normal.x * currentDot,
                -normal.y * currentDot,
                -normal.z * currentDot
        );
        if (tangent.lengthSquared() < EPSILON) {
            fallbackNormal(normal, tangent);
        }
        tangent.normalize().mul(
                (float) Math.sqrt(
                        Math.max(
                                0.0F,
                                1.0F - minimumDot * minimumDot
                        )
                )
        );
        direction.set(normal).mul(minimumDot).add(tangent).normalize();
        return true;
    }

    static boolean projectOutsideSphere(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            Vector3f normal,
            Vector3f tangent
    ) {
        normal.set(pivot).sub(center);
        float pivotLength = normal.length();
        if (pivotLength <= EPSILON) {
            return false;
        }
        normal.div(pivotLength);
        float minimumDot = (
                combinedRadius * combinedRadius
                        - pivotLength * pivotLength
                        - leverArm * leverArm
        ) / (2.0F * pivotLength * leverArm);
        return projectMinimumDot(
                direction,
                normal,
                minimumDot,
                tangent
        );
    }

    static float sphereClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            Vector3f tip
    ) {
        tip.set(direction).mul(leverArm).add(pivot);
        return tip.distance(center) - combinedRadius;
    }

    static boolean closestPointOnSegment(
            Vector3f point,
            Vector3f start,
            Vector3f end,
            Vector3f segment,
            Vector3f output
    ) {
        segment.set(end).sub(start);
        float lengthSquared = segment.lengthSquared();
        if (lengthSquared <= EPSILON) {
            output.set(start);
            return false;
        }
        float t = (
                (point.x - start.x) * segment.x
                        + (point.y - start.y) * segment.y
                        + (point.z - start.z) * segment.z
        ) / lengthSquared;
        t = Math.max(0.0F, Math.min(1.0F, t));
        output.set(segment).mul(t).add(start);
        return true;
    }

    static void fallbackNormal(Vector3f axis, Vector3f output) {
        if (Math.abs(axis.y) < 0.90F) {
            output.set(0.0F, 1.0F, 0.0F).cross(axis);
        } else {
            output.set(1.0F, 0.0F, 0.0F).cross(axis);
        }
        if (output.lengthSquared() <= EPSILON) {
            output.set(0.0F, 0.0F, 1.0F);
        } else {
            output.normalize();
        }
    }
}
