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
 * @param closingSpeed blocks a second it is eating the gap at, negative if it
 *                     is opening one
 * @param relation     what it is currently doing about her or her owner
 */
public record ThreatSample(
        double distance,
        double strikeDamage,
        double reach,
        int attackPeriod,
        double health,
        boolean airborne,
        double closingSpeed,
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
        return strikeDamage * hitsPerSecond();
    }

    /**
     * How often it connects, separately from how hard.
     *
     * <p>Damage per second cannot answer everything the fight needs to know:
     * one heavy blow every three seconds and three light ones a second can add
     * up to the same rate, and they are not the same to a maid drawing a bow.
     * A draw survives the gap between hits or it does not, and that depends on
     * the cadence alone.
     */
    public double hitsPerSecond() {
        return 20.0D / attackPeriod;
    }

    /**
     * Seconds before it can hit her, at the speed it is actually moving.
     *
     * <p>The difference between knowing where something is and knowing where it
     * is going. "Is it in reach" can only ever be answered after the fact, so a
     * decision built on it is always one exchange late: she draws a bow at a
     * zombie six blocks away, and by the time the answer flips to yes the
     * zombie is already hitting her and the draw is wasted. Asking when it
     * arrives instead lets her draw the sword before it gets there.
     *
     * <p>Measured, not assumed. A creature's movement-speed attribute says what
     * it could do, not what it is doing — something walking away, stuck on a
     * fence or wading through water is not arriving on schedule, and the
     * attribute cannot tell. Infinite when it is not closing at all, which is
     * the honest answer and keeps every comparison below well defined.
     */
    public double secondsToContact() {
        double gap = distance - reach;
        if (gap <= 0.0D) {
            return 0.0D;
        }
        if (closingSpeed <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return gap / closingSpeed;
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

    /**
     * Whether it could hurt her after closing {@code margin} blocks.
     *
     * <p>{@link #threatensNow()} asks the same question with no margin, and that
     * turns out to be the wrong question almost everywhere it matters. A zombie
     * reaches about a block and a half; she stands a little further out than
     * that on purpose, so "can it hit me right now" is false for every hostile
     * in a crowd that is nonetheless about to hit her. Anything deciding whether
     * she is surrounded has to ask about the step, not the instant.
     */
    public boolean threatensWithin(double margin) {
        return distance <= reach + Math.max(0.0D, margin);
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
