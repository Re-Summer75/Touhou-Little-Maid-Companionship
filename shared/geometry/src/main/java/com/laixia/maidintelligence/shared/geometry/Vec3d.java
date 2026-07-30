package com.laixia.maidintelligence.shared.geometry;

import java.util.Objects;

/**
 * Immutable double-precision vector shared by model-facing features.
 */
public final class Vec3d {
    public static final Vec3d ZERO = new Vec3d(0.0D, 0.0D, 0.0D);

    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Vec3d add(Vec3d other) {
        Objects.requireNonNull(other, "other");
        return add(other.x, other.y, other.z);
    }

    public Vec3d add(double x, double y, double z) {
        return new Vec3d(this.x + x, this.y + y, this.z + z);
    }

    public Vec3d subtract(Vec3d other) {
        Objects.requireNonNull(other, "other");
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    public Vec3d scale(double factor) {
        return new Vec3d(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3d other) {
        Objects.requireNonNull(other, "other");
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3d cross(Vec3d other) {
        Objects.requireNonNull(other, "other");
        return new Vec3d(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    /**
     * Matches the zero-vector behavior used by Minecraft's immutable vector.
     */
    public Vec3d normalize() {
        double length = length();
        return length < 1.0E-4D ? ZERO : scale(1.0D / length);
    }

    public double distanceToSqr(Vec3d other) {
        Objects.requireNonNull(other, "other");
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(Vec3d other) {
        return Math.sqrt(distanceToSqr(other));
    }

    public boolean isFinite() {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3d vector)) {
            return false;
        }
        return Double.compare(x, vector.x) == 0
                && Double.compare(y, vector.y) == 0
                && Double.compare(z, vector.z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "Vec3d[" + x + ", " + y + ", " + z + "]";
    }
}
