package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Side-by-side diagnostics for the authoritative and dry-run pipelines.
 */
public record IntentTraceComparison(
        IntentTrace live,
        IntentTrace shadow,
        boolean shadowAvailable,
        boolean activeIntentMatches,
        int matchingCandidates,
        int comparedCandidates
) {
    private static final double SCORE_EPSILON = 1.0E-9D;

    public IntentTraceComparison {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(shadow, "shadow");
        if (matchingCandidates < 0
                || comparedCandidates < matchingCandidates) {
            throw new IllegalArgumentException(
                    "Invalid candidate comparison counts"
            );
        }
    }

    public static IntentTraceComparison unavailable(IntentTrace live) {
        return new IntentTraceComparison(
                live,
                IntentTrace.idle(),
                false,
                false,
                0,
                0
        );
    }

    public static IntentTraceComparison compare(
            IntentTrace live,
            IntentTrace shadow
    ) {
        Map<OrchestrationId, IntentTrace.Candidate> liveCandidates =
                index(live);
        Map<OrchestrationId, IntentTrace.Candidate> shadowCandidates =
                index(shadow);
        int matches = 0;
        for (Map.Entry<OrchestrationId, IntentTrace.Candidate> entry
                : liveCandidates.entrySet()) {
            IntentTrace.Candidate other = shadowCandidates.get(entry.getKey());
            if (other != null && same(entry.getValue(), other)) {
                matches++;
            }
        }
        int compared = Math.max(
                liveCandidates.size(),
                shadowCandidates.size()
        );
        return new IntentTraceComparison(
                live,
                shadow,
                true,
                Objects.equals(
                        live.activeIntent(),
                        shadow.activeIntent()
                ),
                matches,
                compared
        );
    }

    public boolean candidatesMatch() {
        return shadowAvailable && matchingCandidates == comparedCandidates;
    }

    public boolean matches() {
        return shadowAvailable
                && activeIntentMatches
                && candidatesMatch();
    }

    private static Map<OrchestrationId, IntentTrace.Candidate> index(
            IntentTrace trace
    ) {
        Map<OrchestrationId, IntentTrace.Candidate> indexed =
                new LinkedHashMap<>();
        for (IntentTrace.Candidate candidate : trace.candidates()) {
            indexed.put(candidate.intent(), candidate);
        }
        return indexed;
    }

    private static boolean same(
            IntentTrace.Candidate left,
            IntentTrace.Candidate right
    ) {
        return left.status().equals(right.status())
                && Math.abs(left.score() - right.score()) <= SCORE_EPSILON;
    }
}
