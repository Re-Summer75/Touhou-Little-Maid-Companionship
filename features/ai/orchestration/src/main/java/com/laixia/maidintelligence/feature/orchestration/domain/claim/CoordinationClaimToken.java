package com.laixia.maidintelligence.feature.orchestration.domain.claim;

import java.util.Objects;
import java.util.UUID;

/**
 * Fencing token that must be rechecked immediately before world commit.
 */
public record CoordinationClaimToken(
        UUID claimId,
        CoordinationResourceKey resource,
        UUID holder,
        UUID operationId,
        long fencingToken,
        long epoch
) {
    public CoordinationClaimToken {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(operationId, "operationId");
        if (fencingToken < 1L || epoch < 1L) {
            throw new IllegalArgumentException("Invalid claim fencing data");
        }
    }
}
