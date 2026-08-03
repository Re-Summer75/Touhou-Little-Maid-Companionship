package com.laixia.maidintelligence.feature.behavior.domain.perception;

import java.util.Objects;

public record AffordanceCandidate(
        AffordanceAdvertisement advertisement,
        double distanceSquared,
        double coarseUtility
) {
    public AffordanceCandidate {
        Objects.requireNonNull(advertisement, "advertisement");
        if (!Double.isFinite(distanceSquared)
                || distanceSquared < 0.0D
                || !Double.isFinite(coarseUtility)) {
            throw new IllegalArgumentException(
                    "Invalid affordance candidate score"
            );
        }
    }
}
