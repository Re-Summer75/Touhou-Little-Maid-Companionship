package com.laixia.maidintelligence.feature.behavior.domain.ability;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;
import java.util.UUID;

public record AbilityActivationRequest(
        UUID requestId,
        OrchestrationId ability,
        AbilityActivationSource source,
        long createdAtTick,
        long expiresAtTick
) {
    public AbilityActivationRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(source, "source");
        if (expiresAtTick < createdAtTick
                || expiresAtTick - createdAtTick > 12_000L) {
            throw new IllegalArgumentException(
                    "Ability request lifetime must be in [0, 12000]"
            );
        }
    }

    public boolean active(long gameTime) {
        return gameTime >= createdAtTick && gameTime <= expiresAtTick;
    }
}
