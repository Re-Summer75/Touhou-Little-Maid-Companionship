package com.laixia.maidintelligence.feature.physics.engine.collision.bake;


import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Conservative Y-axis capsule derived from an AABB. Values stay in the
 * block-based model units supplied by {@link PhysicsBoneGeometry.Analysis}.
 */
public record CapsuleFit(
        Vector3f start,
        Vector3f end,
        float radius,
        boolean sphereFallback
) {
    private static final float EPSILON = 1.0E-6F;

    public CapsuleFit {
        start = new Vector3f(Objects.requireNonNull(start, "start"));
        end = new Vector3f(Objects.requireNonNull(end, "end"));
        if (!start.isFinite()
                || !end.isFinite()
                || !Float.isFinite(radius)
                || radius < 0.0F) {
            throw new IllegalArgumentException("Capsule values must be finite");
        }
    }

    @Override
    public Vector3f start() {
        return new Vector3f(start);
    }

    @Override
    public Vector3f end() {
        return new Vector3f(end);
    }

    public static CapsuleFit fromBounds(
            PhysicsBoneGeometry.Bounds bounds
    ) {
        Objects.requireNonNull(bounds, "bounds");
        if (bounds.isEmpty() || !finite(bounds)) {
            return zero();
        }

        float centerX = (float) ((bounds.minX() + bounds.maxX()) * 0.5D);
        float centerY = (float) ((bounds.minY() + bounds.maxY()) * 0.5D);
        float centerZ = (float) ((bounds.minZ() + bounds.maxZ()) * 0.5D);
        float halfX = (float) ((bounds.maxX() - bounds.minX()) * 0.5D);
        float halfY = (float) ((bounds.maxY() - bounds.minY()) * 0.5D);
        float halfZ = (float) ((bounds.maxZ() - bounds.minZ()) * 0.5D);
        if (!Float.isFinite(centerX)
                || !Float.isFinite(centerY)
                || !Float.isFinite(centerZ)
                || !Float.isFinite(halfX)
                || !Float.isFinite(halfY)
                || !Float.isFinite(halfZ)) {
            return zero();
        }

        float cylinderRadius = Math.min(halfX, halfZ) * 0.90F;
        if (cylinderRadius <= EPSILON
                || halfY <= cylinderRadius * 1.05F) {
            float sphereRadius = Math.max(
                    cylinderRadius,
                    halfY * 0.90F
            );
            Vector3f center = new Vector3f(centerX, centerY, centerZ);
            return new CapsuleFit(center, center, sphereRadius, true);
        }

        Vector3f start = new Vector3f(
                centerX,
                (float) bounds.minY() + cylinderRadius,
                centerZ
        );
        Vector3f end = new Vector3f(
                centerX,
                (float) bounds.maxY() - cylinderRadius,
                centerZ
        );
        return new CapsuleFit(start, end, cylinderRadius, false);
    }

    public static CapsuleFit fit(PhysicsBoneGeometry.Bounds bounds) {
        return fromBounds(bounds);
    }

    public boolean isFinite() {
        return start.isFinite() && end.isFinite() && Float.isFinite(radius);
    }

    private static CapsuleFit zero() {
        return new CapsuleFit(
                new Vector3f(),
                new Vector3f(),
                0.0F,
                true
        );
    }

    private static boolean finite(PhysicsBoneGeometry.Bounds bounds) {
        return Double.isFinite(bounds.minX())
                && Double.isFinite(bounds.minY())
                && Double.isFinite(bounds.minZ())
                && Double.isFinite(bounds.maxX())
                && Double.isFinite(bounds.maxY())
                && Double.isFinite(bounds.maxZ());
    }
}
