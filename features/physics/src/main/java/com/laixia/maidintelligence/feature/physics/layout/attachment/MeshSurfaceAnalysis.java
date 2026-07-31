package com.laixia.maidintelligence.feature.physics.layout.attachment;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/**
 * Fixed model-load-time surface samples for a union of baked OBB cubes.
 */
record MeshSurfaceSamples(
        Vector3f[] points,
        float[] weights,
        int[] boxIndices,
        float totalWeight
) {
    private static final float EPSILON = 1.0E-6F;
    private static final float INTERIOR_MARGIN = 1.0E-4F;
    private static final float[][] FACE_COORDINATES = {
            {0.5F, 0.5F},
            {0.2F, 0.2F},
            {0.2F, 0.8F},
            {0.8F, 0.2F},
            {0.8F, 0.8F}
    };

    static MeshSurfaceSamples sample(BoneMeshMetrics mesh) {
        List<Vector3f> points = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        List<Integer> boxIndices = new ArrayList<>();
        MeshSupportBox[] boxes = mesh.boxes();
        float totalWeight = 0.0F;
        for (int boxIndex = 0; boxIndex < boxes.length; boxIndex++) {
            MeshSupportBox box = boxes[boxIndex];
            for (int fixedAxis = 0; fixedAxis < 3; fixedAxis++) {
                float faceArea = box.faceArea(fixedAxis);
                if (faceArea <= EPSILON) {
                    continue;
                }
                float sampleWeight =
                        faceArea / FACE_COORDINATES.length;
                for (int side = 0; side < 2; side++) {
                    for (float[] coordinates : FACE_COORDINATES) {
                        Vector3f point = facePoint(
                                box,
                                fixedAxis,
                                side,
                                coordinates[0],
                                coordinates[1]
                        );
                        if (insideAnotherBox(
                                point,
                                boxIndex,
                                boxes
                        )) {
                            continue;
                        }
                        points.add(point);
                        weights.add(sampleWeight);
                        boxIndices.add(boxIndex);
                        totalWeight += sampleWeight;
                    }
                }
            }
        }
        float[] packedWeights = new float[weights.size()];
        int[] packedIndices = new int[boxIndices.size()];
        for (int index = 0; index < weights.size(); index++) {
            packedWeights[index] = weights.get(index);
            packedIndices[index] = boxIndices.get(index);
        }
        return new MeshSurfaceSamples(
                points.toArray(Vector3f[]::new),
                packedWeights,
                packedIndices,
                totalWeight
        );
    }

    private static Vector3f facePoint(
            MeshSupportBox box,
            int fixedAxis,
            int side,
            float first,
            float second
    ) {
        float fixed = side;
        return switch (fixedAxis) {
            case 0 -> box.point(fixed, first, second);
            case 1 -> box.point(first, fixed, second);
            default -> box.point(first, second, fixed);
        };
    }

    private static boolean insideAnotherBox(
            Vector3f point,
            int ownIndex,
            MeshSupportBox[] boxes
    ) {
        for (int index = 0; index < boxes.length; index++) {
            if (index != ownIndex
                    && boxes[index].strictlyContains(
                    point,
                    INTERIOR_MARGIN
            )) {
                return true;
            }
        }
        return false;
    }
}

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
