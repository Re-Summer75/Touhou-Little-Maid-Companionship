package com.laixia.maidintelligence.feature.behavior.domain.perception;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One object's advertised interactions and motive effects.
 */
public record AffordanceAdvertisement(
        AffordanceTargetId target,
        Set<OrchestrationId> affordances,
        Map<OrchestrationId, Double> commodities,
        AffordancePosition position,
        long revision,
        long observedAtTick,
        long expiresAtTick,
        Map<String, String> attributes
) {
    public AffordanceAdvertisement {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(affordances, "affordances");
        Objects.requireNonNull(commodities, "commodities");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(attributes, "attributes");
        if (revision < 0L || expiresAtTick <= observedAtTick) {
            throw new IllegalArgumentException(
                    "Invalid affordance revision or expiry"
            );
        }
        if (affordances.isEmpty() || attributes.size() > 16) {
            throw new IllegalArgumentException(
                    "Invalid affordance advertisement contents"
            );
        }
        for (double value : commodities.values()) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Commodity value must be finite"
                );
            }
        }
        affordances = Set.copyOf(affordances);
        commodities = Map.copyOf(commodities);
        attributes = Map.copyOf(attributes);
    }

    public boolean activeAt(long gameTime) {
        return gameTime >= observedAtTick && gameTime < expiresAtTick;
    }
}
