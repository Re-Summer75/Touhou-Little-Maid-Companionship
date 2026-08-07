package com.laixia.maidintelligence.feature.behavior.domain.combat.threat;

/**
 * One hostile, measured rather than named.
 *
 * <p>Nothing here says what the creature is. A creeper is dangerous because it
 * deals a great deal of damage at once from close range, not because it is a
 * creeper — stated that way, a modded bomber nobody has heard of is handled by
 * the same arithmetic, and a nerfed one stops being over-feared.
 *
 * @param distance     blocks between her and it
 * @param strikeDamage damage one of its attacks would land on her, after her armour
 * @param reach        how far it can hurt her from, in blocks
 * @param attackPeriod ticks between its attacks
 * @param health       what remains to be chewed through
 * @param airborne     whether it is out of reach of a swung sword
 * @param relation     what it is currently doing about her or her owner
 */
public record ThreatSample(
        double distance,
        double strikeDamage,
        double reach,
        int attackPeriod,
        double health,
        boolean airborne,
        ThreatRelation relation
) {
    public ThreatSample {
        if (distance < 0.0D || strikeDamage < 0.0D || reach < 0.0D
                || health < 0.0D || attackPeriod <= 0) {
            throw new IllegalArgumentException(
                    "Threat sample must be non-negative with a positive period"
            );
        }
        if (relation == null) {
            throw new IllegalArgumentException("Threat relation is required");
        }
    }

    /**
     * Damage per second it lands while it is in range of her.
     *
     * <p>Expressed per second rather than per hit so that a slow heavy hitter
     * and a fast weak one can be added together, which is the only way a crowd
     * becomes a single number.
     */
    public double damagePerSecond() {
        return strikeDamage * 20.0D / attackPeriod;
    }

    /**
     * Whether it can hurt her from where it stands.
     *
     * <p>The margin is deliberately absent: something a step outside its reach
     * will simply take that step, and treating it as harmless until it does is
     * how she gets surrounded while the numbers said she was safe.
     */
    public boolean threatensNow() {
        return distance <= reach;
    }

    /** Whether it can hurt her from further than a sword can answer. */
    public boolean outranges(double meleeReach) {
        return reach > meleeReach;
    }

    /**
     * How much of this hostile lands inside the next {@code horizonSeconds}.
     *
     * <p>Distance is not merely "can it touch me". Something five blocks away
     * arrives before she finishes the one in front of her, and treating it as
     * absent until it does is how she commits to a fight that is still growing.
     * Something thirty blocks away will not arrive within any window she is
     * planning over, and counting it would have her flee an empty room.
     *
     * <p>So it decays with arrival time rather than switching at reach: fully
     * present once it can strike, fading to nothing at the horizon.
     *
     * @param horizonSeconds how far ahead the decision is looking
     * @param closingSpeed   blocks per second it closes at
     * @return weight in {@code [0,1]}
     */
    public double convergenceWeight(
            double horizonSeconds,
            double closingSpeed
    ) {
        double gap = distance - reach;
        if (gap <= 0.0D) {
            return 1.0D;
        }
        if (closingSpeed <= 0.0D || horizonSeconds <= 0.0D) {
            return 0.0D;
        }
        double arrivalSeconds = gap / closingSpeed;
        if (arrivalSeconds >= horizonSeconds) {
            return 0.0D;
        }
        return 1.0D - arrivalSeconds / horizonSeconds;
    }
}
