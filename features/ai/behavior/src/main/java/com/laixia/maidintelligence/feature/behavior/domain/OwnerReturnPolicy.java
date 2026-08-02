package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Platform-neutral timing, eligibility and probability for returning to owner.
 */
public final class OwnerReturnPolicy {
    public static final OwnerReturnPolicy INSTANCE = new OwnerReturnPolicy();

    public static final int DEFAULT_POST_TASK_SETTLE_TICKS = 20;
    public static final int DEFAULT_POST_TASK_TIMEOUT_TICKS = 200;
    public static final int DEFAULT_POST_TASK_COOLDOWN_TICKS = 0;
    public static final double DEFAULT_WANDER_RETURN_CHANCE = 0.10D;
    public static final int DEFAULT_WANDER_COOLDOWN_TICKS = 400;
    public static final int DEFAULT_CLOSE_ENOUGH_DISTANCE = 2;

    private OwnerReturnPolicy() {
    }

    public boolean eligible(
            boolean followMode,
            boolean canMove,
            boolean combatActive,
            boolean protectedMovement
    ) {
        return followMode
                && canMove
                && !combatActive
                && !protectedMovement;
    }

    public boolean postTaskReady(
            long releasedAtTick,
            long gameTime,
            int settleTicks,
            long cooldownUntilTick
    ) {
        return releasedAtTick >= 0L
                && gameTime >= releasedAtTick
                && gameTime - releasedAtTick >= Math.max(0, settleTicks)
                && cooldownElapsed(gameTime, cooldownUntilTick);
    }

    public boolean postTaskExpired(
            long releasedAtTick,
            long gameTime,
            int timeoutTicks
    ) {
        return releasedAtTick < 0L
                || gameTime < releasedAtTick
                || gameTime - releasedAtTick
                >= Math.max(1, timeoutTicks);
    }

    public boolean cooldownElapsed(long gameTime, long cooldownUntilTick) {
        return cooldownUntilTick == 0L || gameTime >= cooldownUntilTick;
    }

    public boolean chancePassed(double randomSample, double chance) {
        if (!Double.isFinite(randomSample)
                || !Double.isFinite(chance)
                || chance <= 0.0D) {
            return false;
        }
        return randomSample >= 0.0D
                && randomSample < Math.min(1.0D, chance);
    }

    public boolean closeEnough(
            double distanceSquared,
            int closeEnoughDistance
    ) {
        int distance = Math.max(0, closeEnoughDistance);
        return Double.isFinite(distanceSquared)
                && distanceSquared <= (double) distance * distance;
    }
}
