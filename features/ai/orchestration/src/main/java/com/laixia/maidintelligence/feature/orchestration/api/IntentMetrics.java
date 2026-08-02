package com.laixia.maidintelligence.feature.orchestration.api;

public record IntentMetrics(
        long catalogGeneration,
        int intentDefinitions,
        int planDefinitions,
        long evaluations,
        long activations,
        long switches,
        long interruptions,
        long completions,
        long failures,
        long cancellations
) {
}
