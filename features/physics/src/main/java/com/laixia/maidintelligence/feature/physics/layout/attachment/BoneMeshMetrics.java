package com.laixia.maidintelligence.feature.physics.layout.attachment;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

record BoneMeshMetrics(
        Vector3f min,
        Vector3f max,
        Vector3f centroid,
        MeshSupportBox[] boxes,
        Vector3f[] corners,
        MeshPrincipalAxis principalAxis,
        float totalWeight,
        int cubeCount,
        boolean empty
) {
    private static final float EPSILON = 1.0E-6F;
    private static final float NOMINAL_SURFACE_THICKNESS = 1.0F / 16.0F;

    static BoneMeshMetrics measure(BoneModelSnapshot.Mesh mesh) {
        return measure(mesh, null);
    }

    static BoneMeshMetrics measure(BoneModelSnapshot.Mesh mesh, int[] cubeIndices) {
        Vector3f min = new Vector3f(
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY
        );
        Vector3f max = new Vector3f(
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        );
        Vector3f centroid = new Vector3f();
        List<Vector3f> points = new ArrayList<>();
        List<MeshSupportBox> boxes = new ArrayList<>();
        float totalWeight = 0.0F;
        int count = cubeIndices == null
                ? mesh.getCubeCount()
                : cubeIndices.length;
        int includedCount = 0;
        for (int cursor = 0; cursor < count; cursor++) {
            int cube = cubeIndices == null ? cursor : cubeIndices[cursor];
            Vector3f position = mesh.position(cube);
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            if (visibleRank(dx, dy, dz) < 2) {
                continue;
            }
            includedCount++;
            float weight = cubeWeight(dx, dy, dz);
            centroid.add(
                    new Vector3f(position)
                            .fma(0.5F, dx)
                            .fma(0.5F, dy)
                            .fma(0.5F, dz)
                            .mul(weight)
            );
            totalWeight += weight;
            boxes.add(new MeshSupportBox(position, dx, dy, dz));
            includeCorners(position, dx, dy, dz, min, max, points);
        }
        if (totalWeight <= 0.0F) {
            return new BoneMeshMetrics(
                    min,
                    max,
                    centroid,
                    new MeshSupportBox[0],
                    new Vector3f[0],
                    MeshPrincipalAxis.measure(List.of(), centroid),
                    0.0F,
                    0,
                    true
            );
        }
        centroid.div(totalWeight);
        return new BoneMeshMetrics(
                min,
                max,
                centroid,
                boxes.toArray(MeshSupportBox[]::new),
                points.toArray(Vector3f[]::new),
                MeshPrincipalAxis.measure(points, centroid),
                totalWeight,
                includedCount,
                false
        );
    }

    private static float cubeWeight(
            Vector3f dx,
            Vector3f dy,
            Vector3f dz
    ) {
        float x = dx.length();
        float y = dy.length();
        float z = dz.length();
        float volume = x * y * z;
        float area = Math.max(x * y, Math.max(x * z, y * z));
        return Math.max(
                EPSILON,
                Math.max(volume, area * NOMINAL_SURFACE_THICKNESS)
        );
    }

    private static int visibleRank(
            Vector3f dx,
            Vector3f dy,
            Vector3f dz
    ) {
        int rank = dx.lengthSquared() > EPSILON ? 1 : 0;
        if (dy.lengthSquared() > EPSILON) {
            rank++;
        }
        if (dz.lengthSquared() > EPSILON) {
            rank++;
        }
        return rank;
    }

    private static void includeCorners(
            Vector3f position,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz,
            Vector3f min,
            Vector3f max,
            List<Vector3f> points
    ) {
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = new Vector3f(position);
            if ((corner & 1) != 0) {
                point.add(dx);
            }
            if ((corner & 2) != 0) {
                point.add(dy);
            }
            if ((corner & 4) != 0) {
                point.add(dz);
            }
            min.min(point);
            max.max(point);
            points.add(point);
        }
    }

    float diagonal() {
        return min.distance(max);
    }

    float distanceTo(Vector3f point) {
        float minimumSquared = Float.POSITIVE_INFINITY;
        for (MeshSupportBox box : boxes) {
            minimumSquared = Math.min(
                    minimumSquared,
                    box.distanceSquared(point)
            );
        }
        return minimumSquared == Float.POSITIVE_INFINITY
                ? point.distance(closestPointInBounds(point))
                : (float) Math.sqrt(minimumSquared);
    }

    Vector3f closestPoint(Vector3f point) {
        Vector3f closest = null;
        float minimumSquared = Float.POSITIVE_INFINITY;
        for (MeshSupportBox box : boxes) {
            Vector3f candidate = box.closestPoint(point);
            float distanceSquared = candidate.distanceSquared(point);
            if (distanceSquared < minimumSquared) {
                minimumSquared = distanceSquared;
                closest = candidate;
            }
        }
        return closest == null ? closestPointInBounds(point) : closest;
    }

    private Vector3f closestPointInBounds(Vector3f point) {
        return new Vector3f(
                clamp(point.x, min.x, max.x),
                clamp(point.y, min.y, max.y),
                clamp(point.z, min.z, max.z)
        );
    }

    Vector3f topCenter() {
        return new Vector3f(
                (min.x + max.x) * 0.5F,
                max.y,
                (min.z + max.z) * 0.5F
        );
    }

    Vector3f bottomCenter() {
        return new Vector3f(
                (min.x + max.x) * 0.5F,
                min.y,
                (min.z + max.z) * 0.5F
        );
    }

    float maximumDistanceTo(Vector3f point) {
        float maximum = 0.0F;
        for (Vector3f corner : corners) {
            maximum = Math.max(maximum, point.distance(corner));
        }
        return maximum;
    }

    float maximumProjectionFrom(Vector3f point, Vector3f axis) {
        float maximum = 0.0F;
        for (Vector3f corner : corners) {
            maximum = Math.max(
                    maximum,
                    (corner.x - point.x) * axis.x
                            + (corner.y - point.y) * axis.y
                            + (corner.z - point.z) * axis.z
            );
        }
        return maximum;
    }

    MeshAttachmentAxis attachmentAxis(AttachmentSupport support) {
        MeshAttachmentAxis principal = orient(
                principalAxis.endA(),
                principalAxis.endB(),
                principalAxis.confidence(),
                principalAxis.length(),
                support
        );
        Vector3f top = topCenter();
        Vector3f bottom = bottomCenter();
        float verticalLength = Math.max(0.0F, max.y - min.y);
        float verticalConfidence = verticalLength
                / Math.max(EPSILON, diagonal());
        if (support.gravity() != AttachmentSupport.GravityPreference.NONE) {
            verticalConfidence = Math.max(0.55F, verticalConfidence);
        }
        MeshAttachmentAxis vertical = orient(
                top,
                bottom,
                Math.min(1.0F, verticalConfidence),
                verticalLength,
                support
        );
        if (principal.length() < EPSILON) {
            return vertical;
        }
        if (vertical.length() < EPSILON) {
            return principal;
        }
        return axisScore(vertical, support) < axisScore(principal, support)
                ? vertical
                : principal;
    }

    private MeshAttachmentAxis orient(
            Vector3f first,
            Vector3f second,
            float confidence,
            float length,
            AttachmentSupport support
    ) {
        float firstScore = support.score(first, centroid);
        float secondScore = support.score(second, centroid);
        boolean firstIsProximal = firstScore <= secondScore;
        float supportConfidence = Math.min(
                1.0F,
                Math.abs(firstScore - secondScore) / 0.45F
        );
        return new MeshAttachmentAxis(
                new Vector3f(firstIsProximal ? first : second),
                new Vector3f(firstIsProximal ? second : first),
                confidence,
                length,
                supportConfidence
        );
    }

    private float axisScore(
            MeshAttachmentAxis axis,
            AttachmentSupport support
    ) {
        return support.score(axis.proximal(), centroid)
                + (1.0F - axis.confidence()) * 0.30F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

record MeshAttachmentAxis(
        Vector3f proximal,
        Vector3f distal,
        float confidence,
        float length,
        float supportConfidence
) {
}
