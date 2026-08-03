package com.laixia.maidintelligence.feature.orchestration.api;

import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaim;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoordinationClaimService {
    Optional<CoordinationClaimToken> tryClaim(
            CoordinationClaimRequest request,
            long gameTime
    );

    boolean occupy(
            CoordinationClaimToken token,
            long gameTime,
            int leaseTicks
    );

    boolean renew(
            CoordinationClaimToken token,
            long gameTime,
            int leaseTicks
    );

    boolean owns(CoordinationClaimToken token, long gameTime);

    boolean isClaimed(CoordinationResourceKey resource, long gameTime);

    boolean release(
            CoordinationClaimToken token,
            long gameTime,
            String reason
    );

    int releaseHolder(UUID holder, long gameTime, String reason);

    void invalidateEpoch(long gameTime, String reason);

    long epoch();

    List<CoordinationClaim> activeClaims(long gameTime);
}
