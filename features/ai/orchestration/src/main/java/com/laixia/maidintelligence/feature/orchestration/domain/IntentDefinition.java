package com.laixia.maidintelligence.feature.orchestration.domain;

import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
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
        int cooldownTicks,
        UtilityAggregation aggregation
) {
    public IntentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(aggregation, "aggregation");
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
        if (aggregation == UtilityAggregation.PRODUCT) {
            requireProductBounds(baseScore, considerations);
        }
    }

    /**
     * Legacy shape, kept because every {@code format_version: 1} definition and
     * every existing construction site means a summed intent. Routing them
     * through here rather than defaulting inside the codec keeps the old
     * semantics attached to the type instead of to one parser.
     */
    public IntentDefinition(
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
        this(
                id,
                plan,
                conditions,
                considerations,
                baseScore,
                minimumScore,
                activationChance,
                evaluationIntervalTicks,
                minimumCommitTicks,
                switchMargin,
                interruptPriority,
                cooldownTicks,
                UtilityAggregation.SUM
        );
    }

    /**
     * A product intent only keeps its {@code [0, 1]} score and its veto if
     * every input already lives there. A base above one could never be pulled
     * back under it by factors that are themselves at most one, and a weight
     * outside the interval breaks
     * {@link UtilityConsideration#factor(double)}'s bound. Rejecting at
     * construction keeps that guarantee out of the hot path, where the
     * selection engine relies on it to stop scoring early.
     */
    private static void requireProductBounds(
            double baseScore,
            List<UtilityConsideration> considerations
    ) {
        requireRange(baseScore, 0.0D, 1.0D, "baseScore (product aggregation)");
        for (UtilityConsideration consideration : considerations) {
            requireRange(
                    consideration.weight(),
                    0.0D,
                    1.0D,
                    "weight of " + consideration.fact()
                            + " (product aggregation)"
            );
        }
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
