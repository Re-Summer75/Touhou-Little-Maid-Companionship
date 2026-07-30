package com.laixia.maidintelligence.feature.physics.client.solver;

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
