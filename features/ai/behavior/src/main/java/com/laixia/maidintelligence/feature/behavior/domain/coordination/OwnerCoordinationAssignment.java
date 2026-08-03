package com.laixia.maidintelligence.feature.behavior.domain.coordination;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OwnerCoordinationAssignment(
        OwnerCoordinationRequest request,
        List<UUID> responders,
        Map<UUID, Double> scores,
        long decidedAtTick
) {
    public OwnerCoordinationAssignment {
        Objects.requireNonNull(request, "request");
        responders = List.copyOf(responders);
        scores = Map.copyOf(scores);
        if (responders.size() > request.fanOut()
                || responders.stream().distinct().count()
                != responders.size()
                || !scores.keySet().containsAll(responders)) {
            throw new IllegalArgumentException(
                    "Assignment responders must be unique, scored and bounded"
            );
        }
    }

    public boolean assigned(UUID maidId) {
        return responders.contains(maidId);
    }
}
