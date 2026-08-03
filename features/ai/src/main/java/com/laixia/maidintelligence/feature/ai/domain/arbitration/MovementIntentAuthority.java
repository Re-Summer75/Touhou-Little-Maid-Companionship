package com.laixia.maidintelligence.feature.ai.domain.arbitration;

/**
 * Cross-system authority used before native Brain priority tie-breaking.
 */
public enum MovementIntentAuthority {
    EMERGENCY(0),
    NATIVE_COMMITMENT(1),
    OWNER_COMMAND(2),
    NATIVE_SOFT(3),
    PASSIVE_COMPANION(4);

    private final int rank;

    MovementIntentAuthority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
