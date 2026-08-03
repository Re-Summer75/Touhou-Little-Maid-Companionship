package com.laixia.maidintelligence.feature.behavior.domain.coordination;

import java.util.Objects;
import java.util.UUID;

/**
 * A loaded maid's read-only response estimate for one shared request.
 */
public record OwnerCoordinationBid(
        UUID maidId,
        boolean eligible,
        double utility,
        double distance,
        int load
) {
    public OwnerCoordinationBid {
        Objects.requireNonNull(maidId, "maidId");
        if (!Double.isFinite(utility)
                || !Double.isFinite(distance)
                || distance < 0.0D
                || load < 0
                || load > 100) {
            throw new IllegalArgumentException(
                    "Bid values must be finite and bounded"
            );
        }
    }
}
