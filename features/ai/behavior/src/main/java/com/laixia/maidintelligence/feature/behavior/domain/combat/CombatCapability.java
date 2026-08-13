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
 * @param guardedShare    the share of incoming damage a raised shield denies over
 *                        an exchange, in {@code [0,1]}. Zero without one, and
 *                        zero again when she is carrying one that cannot help —
 *                        an axe takes it away, and a crowd hits her from angles
 *                        a shield does not cover. A blocked hit is denied
 *                        outright rather than softened, so this is a share of
 *                        the blows and not a reduction applied to each
 */
public record CombatCapability(
        double effectiveHealth,
        double meleeDps,
        double rangedDps,
        double meleeReach,
        double guardedShare
) {
    public CombatCapability {
        if (effectiveHealth < 0.0D || meleeDps < 0.0D || rangedDps < 0.0D
                || meleeReach < 0.0D) {
            throw new IllegalArgumentException(
                    "Combat capability must be non-negative"
            );
        }
        if (guardedShare < 0.0D || guardedShare > 1.0D) {
            throw new IllegalArgumentException(
                    "Guarded share must be a share in [0, 1], was " + guardedShare
            );
        }
    }

    /**
     * The same maid with nothing in her off hand.
     *
     * <p>Kept so that every decision written before there was a shield goes on
     * reading the way it did. An unguarded maid is the case those were stated
     * against, and zero is exactly that case rather than a placeholder.
     */
    public CombatCapability(
            double effectiveHealth,
            double meleeDps,
            double rangedDps,
            double meleeReach
    ) {
        this(effectiveHealth, meleeDps, rangedDps, meleeReach, 0.0D);
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
