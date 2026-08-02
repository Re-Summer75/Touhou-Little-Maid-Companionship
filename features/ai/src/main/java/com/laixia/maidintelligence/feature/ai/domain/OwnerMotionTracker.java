package com.laixia.maidintelligence.feature.ai.domain;

/**
 * Per-maid owner-motion hysteresis sampled at most once per game tick.
 */
public final class OwnerMotionTracker {
    private static final long NO_OWNER = Long.MIN_VALUE;
    private static final long NO_SAMPLE = Long.MIN_VALUE;

    private long ownerIdentity = NO_OWNER;
    private long lastSampleTick = NO_SAMPLE;
    private int stationarySamples;
    private int movingSamples;
    private boolean stationary;

    public boolean observe(
            long gameTick,
            long observedOwnerIdentity,
            boolean moving,
            int stationaryConfirmTicks,
            int movingConfirmTicks
    ) {
        if (observedOwnerIdentity == NO_OWNER
                || gameTick < lastSampleTick
                || ownerIdentity != observedOwnerIdentity) {
            reset(observedOwnerIdentity);
        }
        if (lastSampleTick == gameTick) {
            return stationary;
        }
        lastSampleTick = gameTick;

        if (moving) {
            stationarySamples = 0;
            movingSamples = saturatingIncrement(movingSamples);
            if (movingSamples >= Math.max(1, movingConfirmTicks)) {
                stationary = false;
            }
        } else {
            movingSamples = 0;
            stationarySamples = saturatingIncrement(stationarySamples);
            if (stationarySamples >= Math.max(1, stationaryConfirmTicks)) {
                stationary = true;
            }
        }
        return stationary;
    }

    public boolean stationary() {
        return stationary;
    }

    public void reset() {
        reset(NO_OWNER);
    }

    private void reset(long observedOwnerIdentity) {
        ownerIdentity = observedOwnerIdentity;
        lastSampleTick = NO_SAMPLE;
        stationarySamples = 0;
        movingSamples = 0;
        stationary = false;
    }

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}
