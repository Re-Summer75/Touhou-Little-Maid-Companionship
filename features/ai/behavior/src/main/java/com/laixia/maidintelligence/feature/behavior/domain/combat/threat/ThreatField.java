package com.laixia.maidintelligence.feature.behavior.domain.combat.threat;

import java.util.Collection;
import java.util.Objects;

/**
 * A crowd, reduced to the few numbers a decision actually needs.
 *
 * <p>Judging hostiles one at a time cannot answer the question being asked. A
 * single zombie is not a threat; nine of them standing where she would stand
 * is, and no amount of looking at one of them says so. So they are aggregated
 * first and decided about second.
 *
 * <p>The aggregation that matters is not how many exist but how many can reach
 * her at once — that is what density means here. Five spread over twenty blocks
 * are fought one at a time; five in a doorway are fought all at once, and only
 * the second is a reason to back out.
 *
 * <p>Both sides of the trade are measured over the same hostiles — the ones
 * that can reach her. Counting damage from those in range but health from
 * every one on the map makes a crowd safely behind a wall look like a fight
 * she is losing, and she flees a room nothing can enter.
 *
 * @param total            hostiles considered, including ones that cannot reach her
 * @param converging       how many could strike her where she stands
 * @param nearestDistance  distance to the closest, or infinity when there are none
 * @param incomingDps      expected damage per second, weighted by arrival time
 * @param incomingHitRate  expected hits per second, weighted the same way
 * @param convergingHealth expected health to remove, weighted by arrival time
 * @param anyAirborne      whether any of them is beyond a swung weapon
 * @param anyOutranging    whether any of them can hurt her from beyond melee
 * @param soonestContact   seconds until the first of them can strike her
 * @param heaviestBlow     the largest single hit any of them lands
 */
public record ThreatField(
        int total,
        int converging,
        double nearestDistance,
        double incomingDps,
        double incomingHitRate,
        double convergingHealth,
        boolean anyAirborne,
        boolean anyOutranging,
        double soonestContact,
        double heaviestBlow
) {
    /** Nothing hostile in sight. */
    public static final ThreatField EMPTY = new ThreatField(
            0, 0, Double.POSITIVE_INFINITY, 0.0D, 0.0D, 0.0D, false, false,
            Double.POSITIVE_INFINITY, 0.0D
    );

    /**
     * How far ahead the aggregation looks.
     *
     * <p>Three seconds is about how long one melee exchange lasts, so it counts
     * what joins before the current one is resolved without pretending to
     * predict a minute out.
     */
    private static final double DEFAULT_HORIZON_SECONDS = 3.0D;

    /**
     * Blocks per second a hostile is assumed to close at.
     *
     * <p>Near a zombie's sprint. Assuming everything moves keeps the estimate
     * on the cautious side, and a stationary hostile simply never arrives to
     * make the caution matter.
     */
    private static final double DEFAULT_CLOSING_SPEED = 4.0D;

    /**
     * Aggregate samples into a field.
     *
     * @param samples    hostiles she can currently perceive
     * @param meleeReach how far her own melee can answer, in blocks
     */
    public static ThreatField of(
            Collection<ThreatSample> samples,
            double meleeReach
    ) {
        return of(
                samples,
                meleeReach,
                DEFAULT_HORIZON_SECONDS,
                DEFAULT_CLOSING_SPEED
        );
    }

    /**
     * Aggregate over an explicit planning window.
     *
     * @param horizonSeconds how far ahead to count arrivals
     * @param closingSpeed   blocks per second hostiles are assumed to close at
     */
    public static ThreatField of(
            Collection<ThreatSample> samples,
            double meleeReach,
            double horizonSeconds,
            double closingSpeed
    ) {
        Objects.requireNonNull(samples, "samples");
        int total = 0;
        int converging = 0;
        double nearest = Double.POSITIVE_INFINITY;
        double incoming = 0.0D;
        double hits = 0.0D;
        double health = 0.0D;
        boolean airborne = false;
        boolean outranging = false;
        double soonest = Double.POSITIVE_INFINITY;
        double heaviest = 0.0D;
        for (ThreatSample sample : samples) {
            if (sample == null) {
                continue;
            }
            total++;
            nearest = Math.min(nearest, sample.distance());
            airborne |= sample.airborne();
            outranging |= sample.outranges(meleeReach);
            // The first arrival, not the nearest. Something further away but
            // sprinting reaches her before something closer and shuffling, and
            // it is arrival that her hands have to be ready for.
            soonest = Math.min(soonest, sample.secondsToContact());
            // The worst single blow, not an average. Averaging is exactly the
            // blindness being corrected: something hitting for thirteen every
            // two seconds and something hitting for three every half second
            // produce the same rate and are not remotely the same risk to a
            // maid with twenty health.
            heaviest = Math.max(heaviest, sample.strikeDamage());
            if (sample.threatensNow()) {
                converging++;
            }
            // Weighted by arrival rather than gated on reach, so one closing in
            // counts for most of itself while one across the map counts for
            // nothing. Damage and health take the same weight: splitting them —
            // damage from those in range, health from everything — is what
            // makes a walled-off horde read as a losing fight.
            double weight =
                    sample.convergenceWeight(horizonSeconds, closingSpeed);
            if (weight > 0.0D) {
                incoming += sample.damagePerSecond() * weight;
                hits += sample.hitsPerSecond() * weight;
                health += sample.health() * weight;
            }
        }
        if (total == 0) {
            return EMPTY;
        }
        return new ThreatField(
                total, converging, nearest, incoming, hits, health,
                airborne, outranging, soonest, heaviest
        );
    }

    public boolean isEmpty() {
        return total == 0;
    }

    /**
     * How long she gets between incoming blows.
     *
     * <p>The gap, not the rate, because that is the form the question takes
     * wherever it is asked: anything she has to hold — a draw, a wind-up —
     * either fits in this gap or is thrown away by the next hit. Infinite when
     * nothing is landing, which keeps a quiet field from reading as a fast one.
     */
    public double secondsBetweenHits() {
        if (incomingHitRate <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0D / incomingHitRate;
    }
}
