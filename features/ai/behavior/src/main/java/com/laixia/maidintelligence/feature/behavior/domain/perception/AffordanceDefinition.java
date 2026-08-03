package com.laixia.maidintelligence.feature.behavior.domain.perception;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;
import java.util.Set;

public record AffordanceDefinition(
        OrchestrationId id,
        Set<OrchestrationId> commodities,
        int defaultTtlTicks
) {
    public AffordanceDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(commodities, "commodities");
        if (defaultTtlTicks < 1 || defaultTtlTicks > 72_000) {
            throw new IllegalArgumentException(
                    "Affordance TTL must be in [1, 72000]"
            );
        }
        commodities = Set.copyOf(commodities);
    }
}
