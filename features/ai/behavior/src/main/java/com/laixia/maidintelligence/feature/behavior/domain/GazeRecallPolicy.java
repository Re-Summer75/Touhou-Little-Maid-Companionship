package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Platform-neutral eligibility and arrival policy for gaze recall.
 */
public final class GazeRecallPolicy {
    public static final int DEFAULT_HOLD_TICKS = 20;
    public static final int DEFAULT_MINIMUM_FAVORABILITY_LEVEL = 1;
    public static final int DEFAULT_CLOSE_ENOUGH_DISTANCE = 2;

    private final int minimumFavorabilityLevel;
    private final int closeEnoughDistance;

    public GazeRecallPolicy(
            int minimumFavorabilityLevel,
            int closeEnoughDistance
    ) {
        if (minimumFavorabilityLevel < 0) {
            throw new IllegalArgumentException(
                    "minimumFavorabilityLevel must be non-negative"
            );
        }
        if (closeEnoughDistance < 0) {
            throw new IllegalArgumentException(
                    "closeEnoughDistance must be non-negative"
            );
        }
        this.minimumFavorabilityLevel = minimumFavorabilityLevel;
        this.closeEnoughDistance = closeEnoughDistance;
    }

    public static GazeRecallPolicy defaults() {
        return new GazeRecallPolicy(
                DEFAULT_MINIMUM_FAVORABILITY_LEVEL,
                DEFAULT_CLOSE_ENOUGH_DISTANCE
        );
    }

    public boolean eligible(
            int favorabilityLevel,
            boolean followMode,
            boolean canMove
    ) {
        return favorabilityLevel >= minimumFavorabilityLevel
                && followMode
                && canMove;
    }

    public int closeEnoughDistance() {
        return closeEnoughDistance;
    }
}
