package com.laixia.maidintelligence.feature.behavior.domain.perception;

import java.util.Objects;

public record AffordanceTargetId(String provider, String value)
        implements Comparable<AffordanceTargetId> {
    public AffordanceTargetId {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(value, "value");
        if (provider.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Affordance target identity is blank"
            );
        }
    }

    @Override
    public int compareTo(AffordanceTargetId other) {
        int providerOrder = provider.compareTo(other.provider);
        return providerOrder != 0
                ? providerOrder
                : value.compareTo(other.value);
    }
}
