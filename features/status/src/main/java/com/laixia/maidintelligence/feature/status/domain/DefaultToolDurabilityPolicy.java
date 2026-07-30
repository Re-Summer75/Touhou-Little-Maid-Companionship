package com.laixia.maidintelligence.feature.status.domain;

public final class DefaultToolDurabilityPolicy {
    public static final DefaultToolDurabilityPolicy INSTANCE =
            new DefaultToolDurabilityPolicy();

    public static final int MINIMUM_WARNING_DURABILITY = 10;
    public static final int WARNING_PERCENT_DIVISOR = 10;

    private DefaultToolDurabilityPolicy() {
    }

    public int remainingDurability(int maxDamage, int damageValue) {
        return Math.max(0, maxDamage - damageValue);
    }

    public int warningThreshold(int maxDamage) {
        int tenPercentRoundedUp =
                (Math.max(0, maxDamage) + WARNING_PERCENT_DIVISOR - 1)
                        / WARNING_PERCENT_DIVISOR;
        return Math.max(MINIMUM_WARNING_DURABILITY, tenPercentRoundedUp);
    }

    public boolean isLowDurability(int maxDamage, int damageValue) {
        return maxDamage > 0
                && remainingDurability(maxDamage, damageValue)
                <= warningThreshold(maxDamage);
    }
}
