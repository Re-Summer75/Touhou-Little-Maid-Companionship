package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AbilityGrantSet(
        long revision,
        Map<OrchestrationId, AbilityGrant> grants
) {
    public static final int MAX_GRANTS = 64;

    public AbilityGrantSet {
        grants = Map.copyOf(new LinkedHashMap<>(grants));
        if (revision < 0L || grants.size() > MAX_GRANTS) {
            throw new IllegalArgumentException("Invalid ability grant set");
        }
        grants.forEach((id, grant) -> {
            Objects.requireNonNull(id, "ability id");
            Objects.requireNonNull(grant, "grant");
            if (!id.equals(grant.ability())) {
                throw new IllegalArgumentException(
                        "Ability grant key does not match its value"
                );
            }
        });
    }

    public static AbilityGrantSet empty() {
        return new AbilityGrantSet(0L, Map.of());
    }

    public boolean contains(OrchestrationId ability) {
        return grants.containsKey(ability);
    }

    public AbilityGrantSet grant(AbilityGrant grant) {
        Objects.requireNonNull(grant, "grant");
        Map<OrchestrationId, AbilityGrant> updated =
                new LinkedHashMap<>(grants);
        if (!updated.containsKey(grant.ability())
                && updated.size() >= MAX_GRANTS) {
            throw new IllegalStateException("Ability grant limit reached");
        }
        updated.put(grant.ability(), grant);
        return new AbilityGrantSet(nextRevision(), updated);
    }

    public AbilityGrantSet revoke(OrchestrationId ability) {
        if (!grants.containsKey(ability)) {
            return this;
        }
        Map<OrchestrationId, AbilityGrant> updated =
                new LinkedHashMap<>(grants);
        updated.remove(ability);
        return new AbilityGrantSet(nextRevision(), updated);
    }

    private long nextRevision() {
        return revision == Long.MAX_VALUE ? 1L : revision + 1L;
    }
}
