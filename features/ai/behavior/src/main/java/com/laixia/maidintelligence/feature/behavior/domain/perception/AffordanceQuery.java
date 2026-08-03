package com.laixia.maidintelligence.feature.behavior.domain.perception;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;
import java.util.Set;

public record AffordanceQuery(
        Set<OrchestrationId> requiredAffordances,
        OrchestrationId commodity,
        AffordancePosition origin,
        double maximumDistance,
        int topK,
        long gameTime
) {
    public AffordanceQuery {
        Objects.requireNonNull(requiredAffordances, "requiredAffordances");
        Objects.requireNonNull(origin, "origin");
        if (requiredAffordances.isEmpty() && commodity == null) {
            throw new IllegalArgumentException(
                    "Affordance query has no selector"
            );
        }
        if (!Double.isFinite(maximumDistance)
                || maximumDistance <= 0.0D
                || maximumDistance > 256.0D
                || topK < 1
                || topK > 32) {
            throw new IllegalArgumentException(
                    "Invalid affordance query budget"
            );
        }
        requiredAffordances = Set.copyOf(requiredAffordances);
    }
}
