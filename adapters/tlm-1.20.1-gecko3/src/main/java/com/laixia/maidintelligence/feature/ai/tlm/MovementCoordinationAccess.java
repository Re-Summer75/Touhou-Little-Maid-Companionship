package com.laixia.maidintelligence.feature.ai.tlm;

import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;

/**
 * Mixin-backed per-maid movement coordination state.
 */
public interface MovementCoordinationAccess {
    MovementIntentLease maidIntelligence$movementIntentLease();
}
