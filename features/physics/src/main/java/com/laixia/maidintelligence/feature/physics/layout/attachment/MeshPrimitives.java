package com.laixia.maidintelligence.feature.physics.layout.attachment;

import java.util.List;
import org.joml.Vector3f;

/**
 * One baked cube represented by its three (possibly rotated) edge vectors.
 * Used only during discovery to measure actual support-surface distance.
 */
record MeshSupportBox(
        Vector3f origin,
        Vector3f edgeX,
        Vector3f edgeY,
        Vector3f edgeZ
) {
    private static final float EPSILON = 1.0E-8F;

    MeshSupportBox {
        origin = new Vector3f(origin);
        edgeX = new Vector3f(edgeX);
        edgeY = new Vector3f(edgeY);
        edgeZ = new Vector3f(edgeZ);
    }

    Vector3f closestPoint(Vector3f point) {
        Vector3f relative = new Vector3f(point).sub(origin);
        return new Vector3f(origin)
                .fma(coordinate(relative, edgeX), edgeX)
                .fma(coordinate(relative, edgeY), edgeY)
                .fma(coordinate(relative, edgeZ), edgeZ);
    }

    Vector3f closestSurfacePoint(Vector3f point) {
        Vector3f relative = new Vector3f(point).sub(origin);
        float x = rawCoordinate(relative, edgeX);
        float y = rawCoordinate(relative, edgeY);
        float z = rawCoordinate(relative, edgeZ);
        if (!inside(x) || !inside(y) || !inside(z)) {
            return point(
                    clamp01(x),
                    clamp01(y),
                    clamp01(z)
            );
        }
        float[] distances = {
                faceDistance(x, edgeX),
                faceDistance(y, edgeY),
                faceDistance(z, edgeZ)
        };
        int nearestAxis = distances[1] < distances[0] ? 1 : 0;
        if (distances[2] < distances[nearestAxis]) {
            nearestAxis = 2;
        }
        if (nearestAxis == 0) {
            x = x <= 0.5F ? 0.0F : 1.0F;
        } else if (nearestAxis == 1) {
            y = y <= 0.5F ? 0.0F : 1.0F;
        } else {
            z = z <= 0.5F ? 0.0F : 1.0F;
        }
        return point(x, y, z);
    }

    Vector3f point(float x, float y, float z) {
        return new Vector3f(origin)
                .fma(x, edgeX)
                .fma(y, edgeY)
                .fma(z, edgeZ);
    }

    float faceArea(int fixedAxis) {
        return switch (fixedAxis) {
            case 0 -> edgeY.cross(edgeZ, new Vector3f()).length();
            case 1 -> edgeX.cross(edgeZ, new Vector3f()).length();
            default -> edgeX.cross(edgeY, new Vector3f()).length();
        };
    }

    float signedDistance(Vector3f point) {
        Vector3f center = point(0.5F, 0.5F, 0.5F);
        Vector3f relative = new Vector3f(point).sub(center);
        float qx = axisDistance(relative, edgeX);
        float qy = axisDistance(relative, edgeY);
        float qz = axisDistance(relative, edgeZ);
        float ox = Math.max(qx, 0.0F);
        float oy = Math.max(qy, 0.0F);
        float oz = Math.max(qz, 0.0F);
        float outside = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        float inside = Math.min(Math.max(qx, Math.max(qy, qz)), 0.0F);
        return outside + inside;
    }

    boolean strictlyContains(Vector3f point, float margin) {
        return signedDistance(point) < -margin;
    }

    float distanceSquared(Vector3f point) {
        return closestPoint(point).distanceSquared(point);
    }

    private static float coordinate(Vector3f point, Vector3f edge) {
        return clamp01(rawCoordinate(point, edge));
    }

    private static float rawCoordinate(Vector3f point, Vector3f edge) {
        float lengthSquared = edge.lengthSquared();
        if (lengthSquared < EPSILON) {
            return 0.0F;
        }
        return point.dot(edge) / lengthSquared;
    }

    private static float axisDistance(Vector3f point, Vector3f edge) {
        float length = edge.length();
        if (length < EPSILON) {
            return 0.0F;
        }
        return Math.abs(point.dot(edge) / length) - length * 0.5F;
    }

    private static float faceDistance(float coordinate, Vector3f edge) {
        float length = edge.length();
        if (length < EPSILON) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.min(coordinate, 1.0F - coordinate) * length;
    }

    private static boolean inside(float value) {
        return value >= 0.0F && value <= 1.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

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
