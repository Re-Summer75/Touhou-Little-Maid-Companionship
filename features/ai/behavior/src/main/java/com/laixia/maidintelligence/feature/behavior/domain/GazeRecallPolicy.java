package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Sensor timing defaults; intent eligibility and arrival are data-driven.
 */
public final class GazeRecallPolicy {
    public static final int DEFAULT_HOLD_TICKS = 2;
    public static final int CURRENT_TIMING_REVISION = 2;

    private static final int LEGACY_DEFAULT_HOLD_TICKS = 20;

    private GazeRecallPolicy() {
    }

    public static int migrateHoldTicks(int configured, int timingRevision) {
        if (timingRevision < CURRENT_TIMING_REVISION
                && configured == LEGACY_DEFAULT_HOLD_TICKS) {
            return DEFAULT_HOLD_TICKS;
        }
        return configured;
    }
}
