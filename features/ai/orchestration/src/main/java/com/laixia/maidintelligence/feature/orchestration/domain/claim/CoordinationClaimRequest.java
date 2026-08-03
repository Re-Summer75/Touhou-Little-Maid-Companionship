package com.laixia.maidintelligence.feature.orchestration.domain.claim;

import java.util.Objects;
import java.util.UUID;

public record CoordinationClaimRequest(
        CoordinationResourceKey resource,
        UUID holder,
        UUID operationId,
        int leaseTicks
) {
    public CoordinationClaimRequest {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(operationId, "operationId");
        if (leaseTicks < 1 || leaseTicks > 12_000) {
            throw new IllegalArgumentException(
                    "Claim lease must be in [1, 12000]"
            );
        }
    }
}
