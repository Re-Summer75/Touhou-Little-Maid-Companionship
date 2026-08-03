package com.laixia.maidintelligence.feature.behavior.domain.learning;

public record AffordanceReliability(
        int successes,
        int failures,
        long lastUpdatedTick
) {
    private static final int MAX_EVIDENCE = 1_000;

    public AffordanceReliability {
        if (successes < 0
                || failures < 0
                || successes + failures > MAX_EVIDENCE) {
            throw new IllegalArgumentException(
                    "Invalid reliability evidence"
            );
        }
    }

    public static AffordanceReliability initial() {
        return new AffordanceReliability(0, 0, 0L);
    }

    public AffordanceReliability observe(
            boolean succeeded,
            long gameTime
    ) {
        int nextSuccesses = successes;
        int nextFailures = failures;
        if (nextSuccesses + nextFailures >= MAX_EVIDENCE) {
            nextSuccesses /= 2;
            nextFailures /= 2;
        }
        if (succeeded) {
            nextSuccesses++;
        } else {
            nextFailures++;
        }
        return new AffordanceReliability(
                nextSuccesses,
                nextFailures,
                gameTime
        );
    }

    public double betaMean() {
        return (successes + 1.0D)
                / (successes + failures + 2.0D);
    }
}
