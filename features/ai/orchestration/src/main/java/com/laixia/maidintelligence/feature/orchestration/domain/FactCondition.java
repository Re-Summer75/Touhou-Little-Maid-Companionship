package com.laixia.maidintelligence.feature.orchestration.domain;

import java.util.Objects;

public record FactCondition(
        OrchestrationId fact,
        FactComparison comparison,
        double expected
) {
    public FactCondition {
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(comparison, "comparison");
        if (!Double.isFinite(expected)) {
            throw new IllegalArgumentException("expected must be finite");
        }
    }

    public boolean test(double actual) {
        return Double.isFinite(actual) && comparison.test(actual, expected);
    }
}
