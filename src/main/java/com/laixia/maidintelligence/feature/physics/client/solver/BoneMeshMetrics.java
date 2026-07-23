package com.laixia.maidintelligence.feature.physics.client.solver;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import org.joml.Vector3f;

record BoneMeshMetrics(
        Vector3f min,
        Vector3f max,
        Vector3f centroid,
        Vector3f[] corners,
        boolean empty
) {
    private static final float EPSILON = 1.0E-6F;

    static BoneMeshMetrics measure(GeoMesh mesh) {
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
        float totalWeight = 0.0F;
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f position = mesh.position(cube);
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            float weight = Math.max(
                    dx.length() * dy.length() * dz.length(),
                    EPSILON
            );
            centroid.add(
                    new Vector3f(position)
                            .fma(0.5F, dx)
                            .fma(0.5F, dy)
                            .fma(0.5F, dz)
                            .mul(weight)
            );
            totalWeight += weight;
            includeCorners(position, dx, dy, dz, min, max);
        }
        if (totalWeight <= 0.0F) {
            return new BoneMeshMetrics(
                    min,
                    max,
                    centroid,
                    new Vector3f[0],
                    true
            );
        }
        centroid.div(totalWeight);
        return new BoneMeshMetrics(
                min,
                max,
                centroid,
                boundsCorners(min, max),
                false
        );
    }

    private static void includeCorners(
            Vector3f position,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz,
            Vector3f min,
            Vector3f max
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
        }
    }

    private static Vector3f[] boundsCorners(Vector3f min, Vector3f max) {
        Vector3f[] result = new Vector3f[8];
        for (int corner = 0; corner < result.length; corner++) {
            result[corner] = new Vector3f(
                    (corner & 1) == 0 ? min.x : max.x,
                    (corner & 2) == 0 ? min.y : max.y,
                    (corner & 4) == 0 ? min.z : max.z
            );
        }
        return result;
    }

    float diagonal() {
        return min.distance(max);
    }

    float distanceTo(Vector3f point) {
        return point.distance(closestPoint(point));
    }

    Vector3f closestPoint(Vector3f point) {
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

    float maximumDistanceTo(Vector3f point) {
        float maximum = 0.0F;
        for (Vector3f corner : corners) {
            maximum = Math.max(maximum, point.distance(corner));
        }
        return maximum;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
