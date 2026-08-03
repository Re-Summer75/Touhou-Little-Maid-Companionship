package com.laixia.maidintelligence.feature.orchestration.domain.claim;

import java.util.Objects;

public record CoordinationResourceKey(
        String dimension,
        CoordinationResourceType type,
        String value
) implements Comparable<CoordinationResourceKey> {
    public CoordinationResourceKey {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        if (dimension.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Coordination resource identity is blank"
            );
        }
    }

    @Override
    public int compareTo(CoordinationResourceKey other) {
        int dimensionOrder = dimension.compareTo(other.dimension);
        if (dimensionOrder != 0) {
            return dimensionOrder;
        }
        int typeOrder = type.compareTo(other.type);
        return typeOrder != 0
                ? typeOrder
                : value.compareTo(other.value);
    }
}
