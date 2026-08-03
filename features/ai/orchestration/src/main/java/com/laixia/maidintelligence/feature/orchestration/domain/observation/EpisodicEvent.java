package com.laixia.maidintelligence.feature.orchestration.domain.observation;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Map;
import java.util.Objects;

/**
 * A bounded, expiring fact about something that happened to one maid.
 */
public record EpisodicEvent(
        EventIdentity identity,
        OrchestrationId type,
        long occurredAtTick,
        long expiresAtTick,
        Map<String, String> attributes
) {
    private static final int MAX_ATTRIBUTES = 16;

    public EpisodicEvent {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(attributes, "attributes");
        if (expiresAtTick <= occurredAtTick) {
            throw new IllegalArgumentException(
                    "Event expiry must follow occurrence"
            );
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("Too many event attributes");
        }
        attributes = Map.copyOf(attributes);
    }

    public boolean activeAt(long gameTime) {
        return gameTime >= occurredAtTick && gameTime < expiresAtTick;
    }
}
