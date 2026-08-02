package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Allocation-free, edge-triggered hold gesture state.
 */
public final class ContinuousLookTracker {
    private static final int NO_TARGET = Integer.MIN_VALUE;

    private int targetId = NO_TARGET;
    private int heldTicks;
    private boolean triggered;

    public boolean observe(int observedTargetId, int requiredTicks) {
        int threshold = Math.max(1, requiredTicks);
        if (observedTargetId < 0) {
            reset();
            return false;
        }
        if (targetId != observedTargetId) {
            targetId = observedTargetId;
            heldTicks = 1;
            triggered = false;
        } else if (!triggered && heldTicks < threshold) {
            heldTicks++;
        }
        if (!triggered && heldTicks >= threshold) {
            triggered = true;
            return true;
        }
        return false;
    }

    public void reset() {
        targetId = NO_TARGET;
        heldTicks = 0;
        triggered = false;
    }

    public int targetId() {
        return targetId;
    }

    public int heldTicks() {
        return heldTicks;
    }

    public boolean triggered() {
        return triggered;
    }
}
