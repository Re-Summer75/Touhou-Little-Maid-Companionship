package com.laixia.maidintelligence.feature.level.event;

import com.laixia.maidintelligence.kernel.event.DomainEvent;

import java.util.Objects;

/**
 * Synchronous fact emitted after a maid reaches a higher level.
 */
public record MaidLevelChangedEvent<S>(
        S subject,
        int oldLevel,
        int newLevel
) implements DomainEvent {
    public MaidLevelChangedEvent {
        Objects.requireNonNull(subject, "subject");
        if (newLevel <= oldLevel) {
            throw new IllegalArgumentException("newLevel must be higher than oldLevel");
        }
    }
}
