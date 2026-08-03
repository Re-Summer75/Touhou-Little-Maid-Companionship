package com.laixia.maidintelligence.feature.behavior.api;

import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationAssignment;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationBid;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationGroupId;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationRequest;

import java.util.List;
import java.util.UUID;

public interface OwnerCoordinationService {
    OwnerCoordinationAssignment assign(
            OwnerCoordinationRequest request,
            List<OwnerCoordinationBid> bids,
            long gameTime
    );

    List<OwnerCoordinationAssignment> activeAssignments(
            OwnerCoordinationGroupId group,
            long gameTime
    );

    void releaseCandidate(UUID maidId, long gameTime);

    void clear();
}
