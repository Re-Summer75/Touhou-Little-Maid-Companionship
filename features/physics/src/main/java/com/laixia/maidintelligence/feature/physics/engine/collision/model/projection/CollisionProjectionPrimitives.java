package com.laixia.maidintelligence.feature.physics.engine.collision.model.projection;

import org.joml.Vector3f;

/**
 * Narrow facade over package-private projection arithmetic.
 */
public final class CollisionProjectionPrimitives {
    public static final float EPSILON = CollisionProjectionMath.EPSILON;

    private CollisionProjectionPrimitives() {
    }

    public static boolean projectMinimumDot(
            Vector3f direction,
            Vector3f normal,
            float minimumDot,
            Vector3f tangent
    ) {
        return CollisionProjectionMath.projectMinimumDot(
                direction,
                normal,
                minimumDot,
                tangent
        );
    }

    public static boolean projectOutsideSphere(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            Vector3f normal,
            Vector3f tangent
    ) {
        return CollisionProjectionMath.projectOutsideSphere(
                direction,
                pivot,
                center,
                combinedRadius,
                leverArm,
                normal,
                tangent
        );
    }

    public static float sphereClearance(
            Vector3f direction,
            Vector3f pivot,
            Vector3f center,
            float combinedRadius,
            float leverArm,
            Vector3f tip
    ) {
        return CollisionProjectionMath.sphereClearance(
                direction,
                pivot,
                center,
                combinedRadius,
                leverArm,
                tip
        );
    }

    public static boolean closestPointOnSegment(
            Vector3f point,
            Vector3f start,
            Vector3f end,
            Vector3f segment,
            Vector3f output
    ) {
        return CollisionProjectionMath.closestPointOnSegment(
                point,
                start,
                end,
                segment,
                output
        );
    }

    public static void fallbackNormal(Vector3f axis, Vector3f output) {
        CollisionProjectionMath.fallbackNormal(axis, output);
    }
}
