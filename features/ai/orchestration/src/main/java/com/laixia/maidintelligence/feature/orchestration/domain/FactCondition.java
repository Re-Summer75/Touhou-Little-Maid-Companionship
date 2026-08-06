package com.laixia.maidintelligence.feature.orchestration.domain;

import java.util.Objects;

/**
 * One test an intent makes of the world.
 *
 * @param entryOnly whether this decides only whether she may begin.
 *                  Conditions are otherwise re-tested every tick that the
 *                  intent is active and cancel it the moment one fails, which
 *                  is right for "the cupboard still has food in it" and wrong
 *                  for "she was free when asked" — the second cancelled a maid
 *                  halfway across the room because something brushed her aside
 *                  for a tick.
 */
public record FactCondition(
        OrchestrationId fact,
        FactComparison comparison,
        double expected,
        boolean entryOnly
) {
    /** A condition that also has to keep holding while she works. */
    public FactCondition(
            OrchestrationId fact,
            FactComparison comparison,
            double expected
    ) {
        this(fact, comparison, expected, false);
    }

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
