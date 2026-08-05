package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Timing for the two-stage gaze gesture.
 *
 * @param acknowledgeTicks how long the owner must hold their gaze on one maid
 *                         before she looks back
 * @param windowTicks      how long, after the owner looks away, the gesture
 *                         stays open for a destination to be indicated
 * @param settleTicks      how long the owner's aim must rest on one spot for it
 *                         to count as pointing rather than panning past
 */
public record GazeGestureTiming(
        int acknowledgeTicks,
        int windowTicks,
        int settleTicks
) {
    public GazeGestureTiming {
        requirePositive(acknowledgeTicks, "acknowledgeTicks");
        requirePositive(windowTicks, "windowTicks");
        requirePositive(settleTicks, "settleTicks");
    }

    public static GazeGestureTiming defaults() {
        return new GazeGestureTiming(
                GazeRecallPolicy.DEFAULT_HOLD_TICKS,
                GazeRecallPolicy.DEFAULT_WINDOW_TICKS,
                GazeRecallPolicy.DEFAULT_SETTLE_TICKS
        );
    }

    public GazeGestureTiming withAcknowledgeTicks(int ticks) {
        return new GazeGestureTiming(ticks, windowTicks, settleTicks);
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1");
        }
    }
}
