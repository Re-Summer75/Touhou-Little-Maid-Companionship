package com.laixia.maidintelligence.feature.interaction.event;

import com.laixia.maidintelligence.kernel.event.DomainEvent;

import java.util.Objects;

/**
 * Synchronous fact emitted after a subject was successfully fed.
 *
 * <p>The publishing adapter owns any mutable-item snapshot semantics.</p>
 */
public record MaidFedEvent<S, I>(S subject, I item)
        implements DomainEvent {
    public MaidFedEvent {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(item, "item");
    }
}
