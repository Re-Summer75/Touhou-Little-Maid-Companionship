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
        /*
         * One ruler for every advertiser. Ranking weighs a commodity against a
         * distance penalty that is itself a fraction of the search radius, so
         * an advertiser answering 100 where its neighbours answer 1 would not
         * be a hundred times more attractive — it would silently switch off
         * distance for every query it appeared in.
         */
        for (double value : commodities.values()) {
            if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
                throw new IllegalArgumentException(
                        "Commodity value must be within [0, 1]"
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
