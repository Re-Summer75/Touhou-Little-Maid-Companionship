package com.laixia.maidintelligence.feature.ai.api;

import com.laixia.maidintelligence.feature.ai.domain.MovementIntentDecision;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.domain.MovementTargetKind;

/**
 * Runtime policy and diagnostics for compatibility-first movement coordination.
 */
public interface MaidMovementCoordinationApi {
    MovementCoordinationMode mode();

    int leaseTicks();

    int pickupCommitmentTicks();

    int failOpenTicks();

    MovementIntentDecision claim(
            MovementIntentLease lease,
            long gameTime,
            MovementIntentSource source,
            MovementTargetKind targetKind,
            long targetIdentity
    );

    boolean reconcile(
            MovementIntentLease lease,
            long gameTime,
            MovementTargetKind observedTargetKind,
            long observedTargetIdentity
    );

    void hardReset(MovementIntentLease lease);

    MovementCoordinationSnapshot snapshot();

    void resetMetrics();
}
