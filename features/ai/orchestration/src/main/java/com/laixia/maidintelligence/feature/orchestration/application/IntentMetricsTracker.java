package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;

final class IntentMetricsTracker {
    private long evaluations;
    private long activations;
    private long switches;
    private long interruptions;
    private long completions;
    private long failures;
    private long cancellations;

    void evaluated() {
        evaluations++;
    }

    void activated() {
        activations++;
    }

    void switched() {
        switches++;
    }

    void interrupted() {
        interruptions++;
    }

    void completed() {
        completions++;
    }

    void failed() {
        failures++;
    }

    void cancelled() {
        cancellations++;
    }

    IntentMetrics snapshot(IntentCatalog catalog) {
        return new IntentMetrics(
                catalog.generation(),
                catalog.intents().size(),
                catalog.planCount(),
                evaluations,
                activations,
                switches,
                interruptions,
                completions,
                failures,
                cancellations
        );
    }

    void reset() {
        evaluations = 0L;
        activations = 0L;
        switches = 0L;
        interruptions = 0L;
        completions = 0L;
        failures = 0L;
        cancellations = 0L;
    }
}
