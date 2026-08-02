package com.laixia.maidintelligence.feature.ai.domain;

/**
 * Result of evaluating one known movement writer.
 */
public enum MovementIntentDecision {
    PASS_THROUGH(true),
    ACQUIRED(true),
    RENEWED(true),
    RETARGETED(true),
    PREEMPTED(true),
    SUPPRESSED(false),
    OBSERVED_CONFLICT(true);

    private final boolean writeAllowed;

    MovementIntentDecision(boolean writeAllowed) {
        this.writeAllowed = writeAllowed;
    }

    public boolean writeAllowed() {
        return writeAllowed;
    }
}
