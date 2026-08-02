package com.laixia.maidintelligence.feature.orchestration.domain;

import java.util.List;
import java.util.Objects;

public record IntentDefinition(
        OrchestrationId id,
        OrchestrationId plan,
        List<FactCondition> conditions,
        List<UtilityConsideration> considerations,
        double baseScore,
        double minimumScore,
        double activationChance,
        int evaluationIntervalTicks,
        int minimumCommitTicks,
        double switchMargin,
        int interruptPriority,
        int cooldownTicks
) {
    public IntentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(plan, "plan");
        conditions = List.copyOf(conditions);
        considerations = List.copyOf(considerations);
        requireFinite(baseScore, "baseScore");
        requireFinite(minimumScore, "minimumScore");
        requireRange(activationChance, 0.0D, 1.0D, "activationChance");
        requireRange(evaluationIntervalTicks, 1, 12_000,
                "evaluationIntervalTicks");
        requireRange(minimumCommitTicks, 0, 12_000, "minimumCommitTicks");
        requireRange(switchMargin, 0.0D, 1000.0D, "switchMargin");
        requireRange(interruptPriority, 0, 1_000, "interruptPriority");
        requireRange(cooldownTicks, 0, 72_000, "cooldownTicks");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireRange(
            double value,
            double minimum,
            double maximum,
            String name
    ) {
        if (!Double.isFinite(value)
                || value < minimum
                || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minimum + ", " + maximum + "]"
            );
        }
    }

    private static void requireRange(
            int value,
            int minimum,
            int maximum,
            String name
    ) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minimum + ", " + maximum + "]"
            );
        }
    }
}
