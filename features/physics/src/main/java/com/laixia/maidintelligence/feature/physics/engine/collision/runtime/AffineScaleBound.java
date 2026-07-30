package com.laixia.maidintelligence.feature.physics.engine.collision.runtime;

import org.joml.Matrix4f;

/**
 * Conservative spectral-scale bound, exact for rotation plus axis scaling.
 */
final class AffineScaleBound {
    private static final float ORTHOGONAL_EPSILON = 1.0E-5F;

    private AffineScaleBound() {
    }

    static float upperBound(Matrix4f matrix) {
        float x2 = squared(matrix.m00(), matrix.m01(), matrix.m02());
        float y2 = squared(matrix.m10(), matrix.m11(), matrix.m12());
        float z2 = squared(matrix.m20(), matrix.m21(), matrix.m22());
        float maximumSquared = Math.max(x2, Math.max(y2, z2));
        if (!Float.isFinite(maximumSquared)) {
            return 1.0F;
        }
        float maximum = (float) Math.sqrt(maximumSquared);
        if (orthogonal(dot(
                matrix.m00(), matrix.m01(), matrix.m02(),
                matrix.m10(), matrix.m11(), matrix.m12()
        ), x2, y2)
                && orthogonal(dot(
                matrix.m00(), matrix.m01(), matrix.m02(),
                matrix.m20(), matrix.m21(), matrix.m22()
        ), x2, z2)
                && orthogonal(dot(
                matrix.m10(), matrix.m11(), matrix.m12(),
                matrix.m20(), matrix.m21(), matrix.m22()
        ), y2, z2)) {
            return maximum;
        }
        float oneNorm = Math.max(
                absSum(matrix.m00(), matrix.m01(), matrix.m02()),
                Math.max(
                        absSum(matrix.m10(), matrix.m11(), matrix.m12()),
                        absSum(matrix.m20(), matrix.m21(), matrix.m22())
                )
        );
        float infinityNorm = Math.max(
                absSum(matrix.m00(), matrix.m10(), matrix.m20()),
                Math.max(
                        absSum(matrix.m01(), matrix.m11(), matrix.m21()),
                        absSum(matrix.m02(), matrix.m12(), matrix.m22())
                )
        );
        float inducedBound = (float) Math.sqrt(oneNorm * infinityNorm);
        float frobeniusBound = (float) Math.sqrt(x2 + y2 + z2);
        return Math.max(maximum, Math.min(inducedBound, frobeniusBound));
    }

    private static float squared(float x, float y, float z) {
        return x * x + y * y + z * z;
    }

    private static float dot(
            float ax, float ay, float az,
            float bx, float by, float bz
    ) {
        return ax * bx + ay * by + az * bz;
    }

    private static boolean orthogonal(
            float dot,
            float firstSquared,
            float secondSquared
    ) {
        float relative = (float) Math.sqrt(firstSquared * secondSquared);
        return Math.abs(dot) <= relative * ORTHOGONAL_EPSILON;
    }

    private static float absSum(float x, float y, float z) {
        return Math.abs(x) + Math.abs(y) + Math.abs(z);
    }
}
