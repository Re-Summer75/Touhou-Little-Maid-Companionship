package com.laixia.maidintelligence.feature.behavior.domain.perception;

import java.util.Objects;

public record AffordancePosition(
        String dimension,
        double x,
        double y,
        double z
) {
    public AffordancePosition {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "Invalid affordance position"
            );
        }
    }

    public double distanceSquared(AffordancePosition other) {
        if (!dimension.equals(other.dimension)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
