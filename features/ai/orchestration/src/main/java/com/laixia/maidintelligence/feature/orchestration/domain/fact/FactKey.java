package com.laixia.maidintelligence.feature.orchestration.domain.fact;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;

public record FactKey(
        OrchestrationId id,
        FactType type
) {
    public FactKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
