package com.laixia.maidintelligence.feature.ai.api;

/**
 * Aggregate diagnostics for server-side movement intent coordination.
 */
public record MovementCoordinationSnapshot(
        long claims,
        long renewals,
        long retargets,
        long preemptions,
        long suppressions,
        long observedConflicts,
        long failOpenTransitions
) {
}
