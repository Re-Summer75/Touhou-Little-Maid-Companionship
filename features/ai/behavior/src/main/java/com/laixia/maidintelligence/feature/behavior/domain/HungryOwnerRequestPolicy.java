package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * Platform-neutral eligibility, cadence and probability for requesting food.
 */
public final class HungryOwnerRequestPolicy {
    public static final HungryOwnerRequestPolicy INSTANCE =
            new HungryOwnerRequestPolicy();

    public static final int DEFAULT_STANDARD_HUNGER_THRESHOLD = 40;
    public static final int DEFAULT_STANDARD_MINIMUM_FAVORABILITY_LEVEL = 0;
    public static final double DEFAULT_STANDARD_REQUEST_CHANCE = 0.10D;
    public static final int DEFAULT_HIGH_TRUST_HUNGER_THRESHOLD = 20;
    public static final int
            DEFAULT_HIGH_TRUST_MINIMUM_FAVORABILITY_LEVEL = 3;
    public static final double DEFAULT_HIGH_TRUST_REQUEST_CHANCE = 0.25D;
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 100;
    public static final int DEFAULT_CLOSE_ENOUGH_DISTANCE = 2;

    private HungryOwnerRequestPolicy() {
    }

    public RequestTier selectTier(
            int hunger,
            int favorabilityLevel,
            int standardHungerThreshold,
            int standardMinimumFavorabilityLevel,
            int highTrustHungerThreshold,
            int highTrustMinimumFavorabilityLevel
    ) {
        if (hunger <= highTrustHungerThreshold
                && favorabilityLevel
                >= highTrustMinimumFavorabilityLevel) {
            return RequestTier.HIGH_TRUST;
        }
        if (hunger <= standardHungerThreshold
                && favorabilityLevel
                >= standardMinimumFavorabilityLevel) {
            return RequestTier.STANDARD;
        }
        return RequestTier.NONE;
    }

    public boolean eligible(
            RequestTier tier,
            boolean followMode,
            boolean canMove,
            boolean combatActive
    ) {
        return tier != RequestTier.NONE
                && followMode
                && canMove
                && !combatActive;
    }

    public boolean shouldEvaluate(
            long gameTime,
            int maidIdentity,
            int checkIntervalTicks
    ) {
        int interval = Math.max(1, checkIntervalTicks);
        return Math.floorMod(gameTime + maidIdentity, interval) == 0L;
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

    public enum RequestTier {
        NONE,
        STANDARD,
        HIGH_TRUST
    }
}
