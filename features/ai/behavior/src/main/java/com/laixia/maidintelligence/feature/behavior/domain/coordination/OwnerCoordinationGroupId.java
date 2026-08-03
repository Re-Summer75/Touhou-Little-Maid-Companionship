package com.laixia.maidintelligence.feature.behavior.domain.coordination;

import java.util.Objects;
import java.util.UUID;

/**
 * Partitions an owner's loaded companions by dimension.
 */
public record OwnerCoordinationGroupId(
        UUID ownerId,
        String dimension
) implements Comparable<OwnerCoordinationGroupId> {
    public OwnerCoordinationGroupId {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > 256) {
            throw new IllegalArgumentException(
                    "Dimension must contain 1..256 characters"
            );
        }
    }

    @Override
    public int compareTo(OwnerCoordinationGroupId other) {
        int dimensionOrder = dimension.compareTo(other.dimension);
        return dimensionOrder != 0
                ? dimensionOrder
                : ownerId.compareTo(other.ownerId);
    }
}
