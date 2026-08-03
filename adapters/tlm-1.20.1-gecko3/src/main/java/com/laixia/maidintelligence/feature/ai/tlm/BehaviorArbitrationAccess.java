package com.laixia.maidintelligence.feature.ai.tlm;

import com.laixia.maidintelligence.feature.ai.domain.arbitration.OwnerCommandOverrideLease;

/**
 * Mixin-backed transient arbitration state for one maid.
 */
public interface BehaviorArbitrationAccess {
    OwnerCommandOverrideLease maidIntelligence$ownerCommandOverrideLease();
}
