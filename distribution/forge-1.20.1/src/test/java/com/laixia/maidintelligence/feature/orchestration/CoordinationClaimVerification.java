package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.application.claim.DefaultCoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimState;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceKey;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationResourceType;

import java.util.UUID;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;

public final class CoordinationClaimVerification {
    private static final UUID MAID_A = new UUID(1L, 1L);
    private static final UUID MAID_B = new UUID(2L, 2L);
    private static final UUID OPERATION_A = new UUID(3L, 3L);
    private static final UUID OPERATION_B = new UUID(4L, 4L);
    private static final CoordinationResourceKey SLOT =
            new CoordinationResourceKey(
                    "test:dimension",
                    CoordinationResourceType.CONTAINER_SLOT,
                    "1/0"
            );

    private CoordinationClaimVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        claimIsExclusiveIdempotentAndFenced();
        occupationTimeoutHolderReleaseAndEpochAreEnforced();
    }

    private static void claimIsExclusiveIdempotentAndFenced() {
        CoordinationClaimService claims =
                new DefaultCoordinationClaimService();
        CoordinationClaimToken first = claims.tryClaim(
                request(MAID_A, OPERATION_A, 20),
                0L
        ).orElseThrow();
        CoordinationClaimToken duplicate = claims.tryClaim(
                request(MAID_A, OPERATION_A, 20),
                1L
        ).orElseThrow();
        require(first.equals(duplicate),
                "Idempotent claim request changed fencing token");
        require(claims.tryClaim(
                        request(MAID_B, OPERATION_B, 20),
                        1L
                ).isEmpty(),
                "Second maid acquired an occupied resource");
        require(claims.release(first, 2L, "done"),
                "Claim release failed");

        CoordinationClaimToken replacement = claims.tryClaim(
                request(MAID_B, OPERATION_B, 20),
                2L
        ).orElseThrow();
        require(replacement.fencingToken() > first.fencingToken(),
                "Replacement claim did not advance its fence");
        require(!claims.owns(first, 2L),
                "Released stale token still authorizes a commit");
    }

    private static void occupationTimeoutHolderReleaseAndEpochAreEnforced() {
        CoordinationClaimService claims =
                new DefaultCoordinationClaimService();
        CoordinationClaimToken token = claims.tryClaim(
                request(MAID_A, OPERATION_A, 5),
                10L
        ).orElseThrow();
        require(claims.occupy(token, 11L, 5),
                "Claimed resource did not enter occupied state");
        require(claims.activeClaims(11L).get(0).state()
                        == CoordinationClaimState.OCCUPIED,
                "Occupied claim state was not retained");
        require(!claims.owns(token, 16L),
                "Expired occupied claim remained valid");

        CoordinationClaimToken held = claims.tryClaim(
                request(MAID_A, OPERATION_A, 20),
                20L
        ).orElseThrow();
        require(claims.releaseHolder(MAID_A, 21L, "unload") == 1,
                "Holder cleanup did not release its claim");
        require(!claims.owns(held, 21L),
                "Holder cleanup left a valid token");

        CoordinationClaimToken beforeReload = claims.tryClaim(
                request(MAID_B, OPERATION_B, 20),
                30L
        ).orElseThrow();
        long previousEpoch = claims.epoch();
        claims.invalidateEpoch(31L, "reload");
        require(claims.epoch() != previousEpoch,
                "Catalog reload did not advance claim epoch");
        require(!claims.owns(beforeReload, 31L),
                "Pre-reload fencing token remained valid");
    }

    private static CoordinationClaimRequest request(
            UUID holder,
            UUID operation,
            int leaseTicks
    ) {
        return new CoordinationClaimRequest(
                SLOT,
                holder,
                operation,
                leaseTicks
        );
    }
}
