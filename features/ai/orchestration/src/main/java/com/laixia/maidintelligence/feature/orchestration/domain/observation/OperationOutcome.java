package com.laixia.maidintelligence.feature.orchestration.domain.observation;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;

/**
 * The exactly-once terminal record for one action operation.
 */
public record OperationOutcome(
        EventIdentity identity,
        OrchestrationId intent,
        OrchestrationId plan,
        String state,
        OrchestrationId action,
        OperationStatus status,
        long startedAtTick,
        long completedAtTick,
        String detail
) {
    public OperationOutcome {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
        if (state.isBlank()) {
            throw new IllegalArgumentException("Outcome state is blank");
        }
        if (completedAtTick < startedAtTick) {
            throw new IllegalArgumentException(
                    "Outcome completion precedes its start"
            );
        }
    }
}
