package com.laixia.maidintelligence.feature.physics.client.solver.collision;

import org.joml.Vector3f;

/**
 * Shared fixed-length projection for baked and frame-prepared proxies.
 */
public final class CollisionProjector {
    private static final int MAX_CAPSULE_ITERATIONS = 8;

    private CollisionProjector() {
    }

    public static boolean projectPlane(
            Vector3f direction,
            Vector3f pivot,
            Vector3f point,
            Vector3f normal,
            float hitRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        float pivotDistance = (pivot.x - point.x) * normal.x
                + (pivot.y - point.y) * normal.y
                + (pivot.z - point.z) * normal.z;
        return CollisionProjectionMath.projectMinimumDot(
                direction,
                normal,
                (hitRadius - pivotDistance) / leverArm,
                scratch.tangent
        );
    }

    public static float planeClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f point,
            Vector3f normal,
            float hitRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        scratch.tip.set(direction).mul(leverArm).add(pivot);
        return (scratch.tip.x - point.x) * normal.x
                + (scratch.tip.y - point.y) * normal.y
                + (scratch.tip.z - point.z) * normal.z
                - hitRadius;
    }

    public static boolean projectSphere(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        return CollisionProjectionMath.projectOutsideSphere(
                direction,
                pivot,
                center,
                combinedRadius,
                leverArm,
                scratch.normal,
                scratch.tangent
        );
    }

    public static float sphereClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        return CollisionProjectionMath.sphereClearance(
                direction,
                pivot,
                center,
                combinedRadius,
                leverArm,
                scratch.tip
        );
    }

    public static boolean projectCapsule(
            Vector3f direction,
            Vector3f pivot,
            Vector3f start,
            Vector3f end,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        scratch.segment.set(end).sub(start);
        if (scratch.segment.lengthSquared()
                <= CollisionProjectionMath.EPSILON) {
            return projectSphere(
                    direction,
                    pivot,
                    start,
                    combinedRadius,
                    leverArm,
                    scratch
            );
        }
        boolean corrected = false;
        for (int iteration = 0;
             iteration < MAX_CAPSULE_ITERATIONS;
             iteration++) {
            scratch.tip.set(direction).mul(leverArm).add(pivot);
            CollisionProjectionMath.closestPointOnSegment(
                    scratch.tip,
                    start,
                    end,
                    scratch.segment,
                    scratch.closest
            );
            scratch.normal.set(scratch.tip).sub(scratch.closest);
            if (scratch.normal.length() + CollisionProjectionMath.EPSILON
                    >= combinedRadius) {
                break;
            }
            boolean changed = projectSphere(
                    direction,
                    pivot,
                    scratch.closest,
                    combinedRadius,
                    leverArm,
                    scratch
            );
            if (!changed
                    && pivot.distanceSquared(scratch.closest)
                    <= CollisionProjectionMath.EPSILON) {
                CollisionProjectionMath.fallbackNormal(
                        scratch.segment,
                        scratch.normal
                );
                direction.set(scratch.normal);
                changed = true;
            }
            corrected |= changed;
            if (!changed) {
                break;
            }
        }
        return corrected;
    }

    public static float capsuleClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f start,
            Vector3f end,
            float combinedRadius,
            float leverArm,
            CollisionScratch scratch
    ) {
        scratch.tip.set(direction).mul(leverArm).add(pivot);
        CollisionProjectionMath.closestPointOnSegment(
                scratch.tip,
                start,
                end,
                scratch.segment,
                scratch.closest
        );
        return scratch.tip.distance(scratch.closest) - combinedRadius;
    }
}
