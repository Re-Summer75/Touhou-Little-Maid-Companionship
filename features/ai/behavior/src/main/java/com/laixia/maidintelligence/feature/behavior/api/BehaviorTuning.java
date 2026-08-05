package com.laixia.maidintelligence.feature.behavior.api;

import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;
import com.laixia.maidintelligence.feature.orchestration.api.IntentRolloutMode;

/**
 * Engine and sensor safety tuning. Behavior semantics live in data packs.
 */
public record BehaviorTuning(
        boolean enabled,
        IntentRolloutMode rolloutMode,
        LearningMode learningMode,
        int evaluationIntervalTicks,
        int maxCandidateEvaluations,
        boolean diagnosticsEnabled,
        int gazeRecallHoldTicks,
        double gazeRecallRange
) {
    public BehaviorTuning {
        java.util.Objects.requireNonNull(rolloutMode, "rolloutMode");
        java.util.Objects.requireNonNull(learningMode, "learningMode");
        requireRange(
                evaluationIntervalTicks,
                1,
                100,
                "evaluationIntervalTicks"
        );
        requireRange(
                maxCandidateEvaluations,
                5,
                128,
                "maxCandidateEvaluations"
        );
        requireRange(gazeRecallHoldTicks, 1, 200, "gazeRecallHoldTicks");
        requireFiniteRange(
                gazeRecallRange,
                2.0D,
                32.0D,
                "gazeRecallRange"
        );
    }

    public static BehaviorTuning defaults() {
        return new BehaviorTuning(
                true,
                IntentRolloutMode.LIVE_ONLY,
                LearningMode.SHADOW,
                5,
                64,
                true,
                GazeRecallPolicy.DEFAULT_HOLD_TICKS,
                GazeRecallPolicy.DEFAULT_RANGE
        );
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

    private static void requireFiniteRange(
            double value,
            double minimum,
            double maximum,
            String name
    ) {
        if (!Double.isFinite(value)
                || value < minimum
                || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and in ["
                            + minimum + ", " + maximum + "]"
            );
        }
    }
}
