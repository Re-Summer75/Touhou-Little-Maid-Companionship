package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Explain snapshot spanning selection inputs, execution and recent outcomes.
 */
public record DecisionTrace(
        UUID decisionId,
        long evaluatedAtTick,
        long catalogGeneration,
        IntentTrace intent,
        Map<OrchestrationId, Double> facts,
        CompanionObservationSnapshot observations,
        UUID activeOperationId,
        UUID correlationId
) {
    private static final UUID NONE = new UUID(0L, 0L);

    public DecisionTrace {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(activeOperationId, "activeOperationId");
        Objects.requireNonNull(correlationId, "correlationId");
        facts = Map.copyOf(facts);
    }

    public static DecisionTrace idle() {
        return new DecisionTrace(
                NONE,
                -1L,
                -1L,
                IntentTrace.idle(),
                Map.of(),
                CompanionObservationSnapshot.empty(),
                NONE,
                NONE
        );
    }

    public boolean hasActiveOperation() {
        return !NONE.equals(activeOperationId);
    }
}
