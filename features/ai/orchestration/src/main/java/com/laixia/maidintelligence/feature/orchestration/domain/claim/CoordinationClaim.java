package com.laixia.maidintelligence.feature.orchestration.domain.claim;

import java.util.Objects;

public record CoordinationClaim(
        CoordinationClaimToken token,
        CoordinationClaimState state,
        long claimedAtTick,
        long expiresAtTick,
        String releaseReason
) {
    public CoordinationClaim {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(releaseReason, "releaseReason");
        if (expiresAtTick <= claimedAtTick) {
            throw new IllegalArgumentException(
                    "Claim expiry must follow acquisition"
            );
        }
    }

    public boolean activeAt(long gameTime) {
        return state != CoordinationClaimState.RELEASED
                && gameTime >= claimedAtTick
                && gameTime < expiresAtTick;
    }
}
