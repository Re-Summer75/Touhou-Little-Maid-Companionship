package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * What she can actually bring to the fight, right now.
 *
 * <p>Stated as effective health and damage per second rather than as gear, so
 * the same maid in diamond and the same maid empty-handed produce different
 * decisions from one rule. Nothing here needs to know what a diamond is.
 *
 * @param effectiveHealth health after armour is accounted for, in raw damage she can absorb
 * @param meleeDps        damage per second she lands in melee, zero without a melee weapon
 * @param rangedDps       damage per second she lands at range, zero without a usable one
 * @param meleeReach      how far her melee actually reaches, in blocks
 */
public record CombatCapability(
        double effectiveHealth,
        double meleeDps,
        double rangedDps,
        double meleeReach
) {
    public CombatCapability {
        if (effectiveHealth < 0.0D || meleeDps < 0.0D || rangedDps < 0.0D
                || meleeReach < 0.0D) {
            throw new IllegalArgumentException(
                    "Combat capability must be non-negative"
            );
        }
    }

    /** Best damage she can put out by any means. */
    public double bestDps() {
        return Math.max(meleeDps, rangedDps);
    }

    /** Whether she can hurt anything at all. */
    public boolean armed() {
        return bestDps() > 0.0D;
    }
}
