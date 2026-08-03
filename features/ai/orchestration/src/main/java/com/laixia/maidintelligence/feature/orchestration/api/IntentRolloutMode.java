package com.laixia.maidintelligence.feature.orchestration.api;

/**
 * Transitional rollout policy while the companion pipeline is migrated.
 */
public enum IntentRolloutMode {
    LIVE_ONLY(false),
    SHADOW_COMPARE(true);

    private final boolean shadowEnabled;

    IntentRolloutMode(boolean shadowEnabled) {
        this.shadowEnabled = shadowEnabled;
    }

    public boolean shadowEnabled() {
        return shadowEnabled;
    }
}
