package com.laixia.maidintelligence.feature.behavior.domain.learning;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationStatus;

import java.util.Objects;
import java.util.UUID;

public record LearningSignal(
        UUID signalId,
        OrchestrationId intent,
        OrchestrationId action,
        OperationStatus status,
        long completedAtTick,
        int dayBucket
) {
    public LearningSignal {
        Objects.requireNonNull(signalId, "signalId");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(status, "status");
        if (dayBucket < 0 || dayBucket >= 24) {
            throw new IllegalArgumentException("Invalid habit day bucket");
        }
    }

    public static LearningSignal from(OperationOutcome outcome) {
        long dayTime = Math.floorMod(
                outcome.completedAtTick(),
                24_000L
        );
        return new LearningSignal(
                outcome.identity().eventId(),
                outcome.intent(),
                outcome.action(),
                outcome.status(),
                outcome.completedAtTick(),
                (int) (dayTime / 1_000L)
        );
    }
}
