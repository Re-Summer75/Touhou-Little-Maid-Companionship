package com.laixia.maidintelligence.feature.orchestration.api.insight;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;

/**
 * When a raw fact is worth telling the player about.
 *
 * <p>Orchestration knows that {@code fact/hunger} is a number and nothing else
 * — not that low means hungry, nor that hungry is interesting. So the rules are
 * supplied from outside, by the layer that does know what the facts mean, and
 * the narrator only applies them.
 *
 * @param topic a stable key the display turns into a sentence, never the
 *              sentence itself
 */
public record NoteRule(
        OrchestrationId fact,
        FactComparison comparison,
        double threshold,
        String topic
) {
    public NoteRule {
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(topic, "topic");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Note topic must not be blank");
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Note threshold must be finite");
        }
    }

    /**
     * A fact the reader could not supply reads as not firing rather than as
     * firing: a panel that invents "she is starving" from a missing number is
     * worse than one that stays quiet.
     */
    public boolean firesOn(double actual) {
        return Double.isFinite(actual)
                && comparison.test(actual, threshold);
    }
}
