package com.laixia.maidintelligence.feature.orchestration.domain.observation;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable causal identity shared by events and operation outcomes.
 */
public record EventIdentity(
        UUID eventId,
        UUID operationId,
        UUID correlationId,
        UUID causationId
) {
    public EventIdentity {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
    }

    public static EventIdentity root(UUID id) {
        Objects.requireNonNull(id, "id");
        return new EventIdentity(id, id, id, id);
    }
}
