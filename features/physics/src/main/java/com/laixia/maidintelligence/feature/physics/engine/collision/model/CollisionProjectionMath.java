package com.laixia.maidintelligence.feature.physics.engine.collision.model;


import org.joml.Vector3f;

/**
 * Shared fixed-length projection primitives.
 */
final class CollisionProjectionMath {
    static final float EPSILON = 1.0E-6F;
    /**
     * Closest a projection may ask the direction to come to a contact normal.
     *
     * <p>Cosine of about twenty degrees, which still lets a projection lift an
     * endpoint most of the way onto a surface it is pressed against; the excluded
     * remainder is the band where the arithmetic stops paying for itself.
     */
    private static final float MAX_MINIMUM_DOT = 0.94F;

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
        /*
         * Held short of pointing straight along the normal. As the bound
         * approaches one the cone it names closes onto the normal itself, and the
         * turn needed to reach it grows without limit while the clearance it buys
         * approaches nothing: the endpoint moves along the normal by the sine of
         * its angle to it, so near the axis it barely moves at all. Enforcing such
         * a bound swings the segment far for almost no gain — measured on
         * winefox_magical's hatsidefront2, 0.373 rad in one frame to gain 1.13 px,
         * while its lever arm is 126 px. Whatever cannot be cleared this frame is
         * left for the next, which is how every other bound here behaves.
         */
        minimumDot = Math.min(minimumDot, MAX_MINIMUM_DOT);
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
