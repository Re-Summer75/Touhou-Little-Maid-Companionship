package com.laixia.maidintelligence.shared.geometry;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Axis-aligned bounds for already-transformed model geometry.
 */
public record Bounds3d(Vec3d minimum, Vec3d maximum) {
    public Bounds3d {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (!minimum.isFinite()
                || !maximum.isFinite()
                || minimum.x > maximum.x
                || minimum.y > maximum.y
                || minimum.z > maximum.z) {
            throw new IllegalArgumentException("bounds must be finite and ordered");
        }
    }

    public static Optional<Bounds3d> enclosing(Collection<Vec3d> positions) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) {
            return Optional.empty();
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d position : positions) {
            Objects.requireNonNull(position, "position");
            if (!position.isFinite()) {
                return Optional.empty();
            }
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            minZ = Math.min(minZ, position.z);
            maxX = Math.max(maxX, position.x);
            maxY = Math.max(maxY, position.y);
            maxZ = Math.max(maxZ, position.z);
        }
        return Optional.of(new Bounds3d(
                new Vec3d(minX, minY, minZ),
                new Vec3d(maxX, maxY, maxZ)
        ));
    }

    public Vec3d center() {
        return minimum.add(maximum).scale(0.5D);
    }

    public Vec3d size() {
        return maximum.subtract(minimum);
    }

    public Bounds3d include(Vec3d position) {
        Objects.requireNonNull(position, "position");
        if (!position.isFinite()) {
            throw new IllegalArgumentException("position must be finite");
        }
        return new Bounds3d(
                new Vec3d(
                        Math.min(minimum.x, position.x),
                        Math.min(minimum.y, position.y),
                        Math.min(minimum.z, position.z)
                ),
                new Vec3d(
                        Math.max(maximum.x, position.x),
                        Math.max(maximum.y, position.y),
                        Math.max(maximum.z, position.z)
                )
        );
    }

    public Bounds3d union(Bounds3d other) {
        Objects.requireNonNull(other, "other");
        return new Bounds3d(
                new Vec3d(
                        Math.min(minimum.x, other.minimum.x),
                        Math.min(minimum.y, other.minimum.y),
                        Math.min(minimum.z, other.minimum.z)
                ),
                new Vec3d(
                        Math.max(maximum.x, other.maximum.x),
                        Math.max(maximum.y, other.maximum.y),
                        Math.max(maximum.z, other.maximum.z)
                )
        );
    }

    public double volume() {
        Vec3d size = size();
        return size.x * size.y * size.z;
    }

    public boolean contains(Vec3d position) {
        Objects.requireNonNull(position, "position");
        return position.x >= minimum.x
                && position.x <= maximum.x
                && position.y >= minimum.y
                && position.y <= maximum.y
                && position.z >= minimum.z
                && position.z <= maximum.z;
    }

    public boolean contains(Bounds3d other, double margin) {
        Objects.requireNonNull(other, "other");
        return minimum.x <= other.minimum.x + margin
                && minimum.y <= other.minimum.y + margin
                && minimum.z <= other.minimum.z + margin
                && maximum.x >= other.maximum.x - margin
                && maximum.y >= other.maximum.y - margin
                && maximum.z >= other.maximum.z - margin;
    }
}
