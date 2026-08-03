package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;

public record AbilityGrant(
        OrchestrationId ability,
        long grantedAtTick,
        String source
) {
    public AbilityGrant {
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(source, "source");
        if (source.isBlank() || source.length() > 64) {
            throw new IllegalArgumentException(
                    "Grant source must contain 1-64 characters"
            );
        }
    }
}
