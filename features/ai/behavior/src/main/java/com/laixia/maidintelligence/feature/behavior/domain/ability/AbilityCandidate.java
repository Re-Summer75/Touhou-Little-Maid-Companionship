package com.laixia.maidintelligence.feature.behavior.domain.ability;

import java.util.Objects;

public record AbilityCandidate(
        AbilityDefinition definition,
        AbilityGrant grant,
        AbilityActivationRequest request,
        double score
) {
    public AbilityCandidate {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(request, "request");
        if (!definition.id().equals(grant.ability())
                || !definition.id().equals(request.ability())
                || !Double.isFinite(score)) {
            throw new IllegalArgumentException(
                    "Inconsistent ability candidate"
            );
        }
    }
}
