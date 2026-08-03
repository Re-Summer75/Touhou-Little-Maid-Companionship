package com.laixia.maidintelligence.feature.orchestration.domain.observation;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;

/**
 * A sourced projection with explicit confidence and validity.
 */
public record Belief(
        OrchestrationId key,
        double value,
        OrchestrationId source,
        double confidence,
        long observedAtTick,
        long expiresAtTick,
        boolean persistent
) {
    public Belief {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Belief value must be finite");
        }
        if (!Double.isFinite(confidence)
                || confidence < 0.0D
                || confidence > 1.0D) {
            throw new IllegalArgumentException(
                    "Belief confidence must be in [0, 1]"
            );
        }
        if (expiresAtTick <= observedAtTick) {
            throw new IllegalArgumentException(
                    "Belief expiry must follow observation"
            );
        }
    }

    public boolean activeAt(long gameTime) {
        return gameTime >= observedAtTick && gameTime < expiresAtTick;
    }
}
