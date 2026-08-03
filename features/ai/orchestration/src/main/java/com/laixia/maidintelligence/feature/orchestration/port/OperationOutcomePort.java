package com.laixia.maidintelligence.feature.orchestration.port;

import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;

@FunctionalInterface
public interface OperationOutcomePort<M> {
    void record(M subject, OperationOutcome outcome);

    static <M> OperationOutcomePort<M> noop() {
        return (subject, outcome) -> {
        };
    }
}
