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

    float distanceSquared(Vector3f point) {
        return closestPoint(point).distanceSquared(point);
    }

    private static float coordinate(Vector3f point, Vector3f edge) {
        float lengthSquared = edge.lengthSquared();
        if (lengthSquared < EPSILON) {
            return 0.0F;
        }
        return Math.max(
                0.0F,
                Math.min(1.0F, point.dot(edge) / lengthSquared)
        );
    }
}
