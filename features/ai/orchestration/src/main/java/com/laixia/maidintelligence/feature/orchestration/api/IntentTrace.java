package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.List;

public record IntentTrace(
        OrchestrationId activeIntent,
        String activeState,
        long activeSinceTick,
        List<Candidate> candidates,
        String lastTransition
) {
    public IntentTrace {
        candidates = List.copyOf(candidates);
    }

    public static IntentTrace idle() {
        return new IntentTrace(
                null,
                "",
                -1L,
                List.of(),
                "idle"
        );
    }

    public record Candidate(
            OrchestrationId intent,
            double score,
            String status
    ) {
    }
}
