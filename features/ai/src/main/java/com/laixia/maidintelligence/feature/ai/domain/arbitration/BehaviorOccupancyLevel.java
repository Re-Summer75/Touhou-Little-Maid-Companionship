package com.laixia.maidintelligence.feature.ai.domain.arbitration;

/**
 * Coarse compatibility boundary between native behavior and companion intent.
 */
public enum BehaviorOccupancyLevel {
    IDLE(0),
    SOFT(1),
    HARD(2);

    private final int code;

    BehaviorOccupancyLevel(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
