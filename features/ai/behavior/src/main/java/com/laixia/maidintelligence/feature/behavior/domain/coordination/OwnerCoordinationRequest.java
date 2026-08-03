package com.laixia.maidintelligence.feature.behavior.domain.coordination;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Objects;
import java.util.UUID;

public record OwnerCoordinationRequest(
        UUID requestId,
        OwnerCoordinationGroupId group,
        OrchestrationId purpose,
        int priority,
        int fanOut,
        long createdAtTick,
        long expiresAtTick
) {
    public static final int MAX_FAN_OUT = 8;
    public static final long MAX_LIFETIME_TICKS = 12_000L;

    public OwnerCoordinationRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(purpose, "purpose");
        if (priority < 0 || priority > 1_000) {
            throw new IllegalArgumentException(
                    "Priority must be in [0, 1000]"
            );
        }
        if (fanOut < 1 || fanOut > MAX_FAN_OUT) {
            throw new IllegalArgumentException(
                    "fanOut must be in [1, " + MAX_FAN_OUT + "]"
            );
        }
        if (expiresAtTick < createdAtTick
                || expiresAtTick - createdAtTick > MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException(
                    "Request lifetime must be in [0, 12000]"
            );
        }
    }

    public boolean active(long gameTime) {
        return gameTime >= createdAtTick && gameTime <= expiresAtTick;
    }
}
