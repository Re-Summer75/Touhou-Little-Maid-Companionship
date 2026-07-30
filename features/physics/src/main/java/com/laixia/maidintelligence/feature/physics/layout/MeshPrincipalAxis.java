package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

import java.util.List;

/**
 * Discovery-time principal axis and its two geometric endpoints.
 */
record MeshPrincipalAxis(
        Vector3f endA,
        Vector3f endB,
        float confidence,
        float length
) {
    private static final float EPSILON = 1.0E-6F;
    private static final int POWER_ITERATIONS = 12;

    static MeshPrincipalAxis measure(
            List<Vector3f> points,
            Vector3f massCenter
    ) {
        if (points.size() < 2) {
            return new MeshPrincipalAxis(
                    new Vector3f(massCenter),
                    new Vector3f(massCenter),
                    0.0F,
                    0.0F
            );
        }
        Vector3f mean = new Vector3f();
        for (Vector3f point : points) {
            mean.add(point);
        }
        mean.div(points.size());

        float xx = 0.0F;
        float xy = 0.0F;
        float xz = 0.0F;
        float yy = 0.0F;
        float yz = 0.0F;
        float zz = 0.0F;
        for (Vector3f point : points) {
            float x = point.x - mean.x;
            float y = point.y - mean.y;
            float z = point.z - mean.z;
            xx += x * x;
            xy += x * y;
            xz += x * z;
            yy += y * y;
            yz += y * z;
            zz += z * z;
        }
        Vector3f axis = initialAxis(xx, yy, zz);
        Vector3f next = new Vector3f();
        for (int iteration = 0; iteration < POWER_ITERATIONS; iteration++) {
            next.set(
                    xx * axis.x + xy * axis.y + xz * axis.z,
                    xy * axis.x + yy * axis.y + yz * axis.z,
                    xz * axis.x + yz * axis.y + zz * axis.z
            );
            if (next.lengthSquared() < EPSILON) {
                break;
            }
            axis.set(next).normalize();
        }

        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (Vector3f point : points) {
            float projection = new Vector3f(point)
                    .sub(massCenter)
                    .dot(axis);
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        float eigenvalue = axis.x * (
                xx * axis.x + xy * axis.y + xz * axis.z
        ) + axis.y * (
                xy * axis.x + yy * axis.y + yz * axis.z
        ) + axis.z * (
                xz * axis.x + yz * axis.y + zz * axis.z
        );
        float trace = xx + yy + zz;
        return new MeshPrincipalAxis(
                new Vector3f(massCenter).fma(minimum, axis),
                new Vector3f(massCenter).fma(maximum, axis),
                trace > EPSILON
                        ? Math.max(0.0F, Math.min(1.0F, eigenvalue / trace))
                        : 0.0F,
                Math.max(0.0F, maximum - minimum)
        );
    }

    Vector3f endpointToward(Vector3f target, boolean preferUpper) {
        return new Vector3f(towardA(target, preferUpper) ? endA : endB);
    }

    Vector3f endpointAway(Vector3f target, boolean preferUpper) {
        return new Vector3f(towardA(target, preferUpper) ? endB : endA);
    }

    private boolean towardA(Vector3f target, boolean preferUpper) {
        if (target == null) {
            return !preferUpper || endA.y >= endB.y;
        }
        float distanceA = endA.distance(target);
        float distanceB = endB.distance(target);
        float tieTolerance = Math.max(0.03F, length * 0.08F);
        if (preferUpper
                && Math.abs(distanceA - distanceB) <= tieTolerance
                && Math.abs(endA.y - endB.y) > EPSILON) {
            return endA.y > endB.y;
        }
        return distanceA <= distanceB;
    }

    private static Vector3f initialAxis(float xx, float yy, float zz) {
        if (xx >= yy && xx >= zz) {
            return new Vector3f(1.0F, 0.0F, 0.0F);
        }
        if (yy >= zz) {
            return new Vector3f(0.0F, 1.0F, 0.0F);
        }
        return new Vector3f(0.0F, 0.0F, 1.0F);
    }
}
