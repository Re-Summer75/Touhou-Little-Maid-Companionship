package com.laixia.maidintelligence.feature.ai.api;

/**
 * Runtime policy for movement intent coordination.
 */
public enum MovementCoordinationMode {
    OFF(false, false),
    OBSERVE(true, false),
    CONSERVATIVE(true, true);

    private final boolean trackingEnabled;
    private final boolean enforcementEnabled;

    MovementCoordinationMode(
            boolean trackingEnabled,
            boolean enforcementEnabled
    ) {
        this.trackingEnabled = trackingEnabled;
        this.enforcementEnabled = enforcementEnabled;
    }

    public boolean trackingEnabled() {
        return trackingEnabled;
    }

    public boolean enforcementEnabled() {
        return enforcementEnabled;
    }
}
