package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;

import java.util.List;

public record CompanionObservationSnapshot(
        List<EpisodicEvent> events,
        List<Belief> beliefs,
        List<OperationOutcome> outcomes
) {
    public CompanionObservationSnapshot {
        events = List.copyOf(events);
        beliefs = List.copyOf(beliefs);
        outcomes = List.copyOf(outcomes);
    }

    public static CompanionObservationSnapshot empty() {
        return new CompanionObservationSnapshot(
                List.of(),
                List.of(),
                List.of()
        );
    }
}
