package com.laixia.maidintelligence.feature.advancement.event;

import com.laixia.maidintelligence.kernel.event.DomainEvent;

import java.util.Objects;

/**
 * Synchronous fact emitted when an advancement grants subject experience.
 */
public record MaidAdvancementExperienceRewardedEvent<S>(
        S subject,
        int points
) implements DomainEvent {
    public MaidAdvancementExperienceRewardedEvent {
        Objects.requireNonNull(subject, "subject");
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive");
        }
    }
}
