package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

/**
 * Deterministic signed-distance queries over the union of baked OBB cubes.
 */
final class MeshDistanceField {
    private MeshDistanceField() {
    }

    static float signedDistance(BoneMeshMetrics mesh, Vector3f point) {
        float distance = Float.POSITIVE_INFINITY;
        for (MeshSupportBox box : mesh.boxes()) {
            distance = Math.min(distance, box.signedDistance(point));
        }
        return distance;
    }

    static Nearest nearestSurface(BoneMeshMetrics mesh, Vector3f point) {
        Vector3f nearest = null;
        float nearestMagnitude = Float.POSITIVE_INFINITY;
        float unionDistance = Float.POSITIVE_INFINITY;
        for (MeshSupportBox box : mesh.boxes()) {
            float signedDistance = box.signedDistance(point);
            unionDistance = Math.min(unionDistance, signedDistance);
            float magnitude = Math.abs(signedDistance);
            if (magnitude < nearestMagnitude) {
                nearestMagnitude = magnitude;
                nearest = box.closestSurfacePoint(point);
            }
        }
        if (nearest == null) {
            nearest = new Vector3f(point);
        }
        return new Nearest(nearest, unionDistance, nearestMagnitude);
    }

    record Nearest(
            Vector3f point,
            float signedDistance,
            float surfaceDistance
    ) {
        Nearest {
            point = new Vector3f(point);
        }
    }
}
