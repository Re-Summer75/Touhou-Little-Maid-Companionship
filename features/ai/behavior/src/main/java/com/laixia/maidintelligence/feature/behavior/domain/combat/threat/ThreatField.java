package com.laixia.maidintelligence.feature.behavior.domain.combat.threat;

import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;

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
 * @param pressing         how many could strike her after one step. The count
 *                         {@code converging} was meant to be and is not: she
 *                         holds station a little outside a zombie's arms on
 *                         purpose, so "in reach this instant" is zero for a
 *                         crowd that is nonetheless closing on her from three
 *                         sides. Anything asking "am I surrounded" has to ask
 *                         this one.
 * @param crowding         how many of them are effectively in the fight,
 *                         weighted by arrival — the count a sweeping weapon is
 *                         paid by, and not the same as {@code converging},
 *                         which is only those already able to hit her
 * @param nearestDistance  distance to the closest, or infinity when there are none
 * @param incomingDps      expected damage per second, weighted by arrival time
 * @param incomingHitRate  expected hits per second, weighted the same way
 * @param convergingHealth expected health to remove, weighted by arrival time
 * @param standingHealth   health of everything she can see, unweighted. The same
 *                         quantity {@code convergingHealth} measures, asked over
 *                         the fight instead of over the planning window — six
 *                         zombies twelve blocks out arrive at nothing inside
 *                         three seconds and are still six zombies. Only for
 *                         questions whose horizon is the whole engagement, of
 *                         which there is exactly one: whether her ammunition can
 *                         finish it.
 * @param anyAirborne      whether any of them is beyond a swung weapon
 * @param anyOutranging    whether any of them can hurt her from beyond melee
 * @param soonestContact   seconds until the first of them can strike her
 * @param heaviestBlow     the largest single hit any of them lands
 * @param threateningWard  多少只正在打她所守之人、或已经站到所守之处旁边。
 *                         **它不是一个威胁量，是一个"这笔账该按谁的命算"的开关**：
 *                         上面每一个数都在回答"她活不活得下来"，而当怪已经贴到
 *                         主人身上时，那根本不是要保的东西
 */
public record ThreatField(
        int total,
        int converging,
        int pressing,
        double crowding,
        double nearestDistance,
        double incomingDps,
        double incomingHitRate,
        double convergingHealth,
        double standingHealth,
        boolean anyAirborne,
        boolean anyOutranging,
        double soonestContact,
        double heaviestBlow,
        int threateningWard
) {
    /** Nothing hostile in sight. */
    public static final ThreatField EMPTY = new ThreatField(
            0, 0, 0, 0.0D, Double.POSITIVE_INFINITY, 0.0D, 0.0D, 0.0D, 0.0D,
            false, false, Double.POSITIVE_INFINITY, 0.0D, 0
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
        int pressing = 0;
        double nearest = Double.POSITIVE_INFINITY;
        double incoming = 0.0D;
        double hits = 0.0D;
        double health = 0.0D;
        double standing = 0.0D;
        boolean airborne = false;
        boolean outranging = false;
        double soonest = Double.POSITIVE_INFINITY;
        double heaviest = 0.0D;
        double crowd = 0.0D;
        int onWard = 0;
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
            // 只数**可能转向她**的那些。锁着别人的一只不会在这一秒打到她，而
            // 这一列是她的手要为之做好准备的那个时刻。
            boolean atHer = sample.relation().mayTurnOnHer();
            if (atHer) {
                soonest = Math.min(soonest, sample.secondsToContact());
            }
            // The worst single blow, not an average. Averaging is exactly the
            // blindness being corrected: something hitting for thirteen every
            // two seconds and something hitting for three every half second
            // produce the same rate and are not remotely the same risk to a
            // maid with twenty health.
            if (atHer) {
                heaviest = Math.max(heaviest, sample.strikeDamage());
            }
            // Unweighted by distance, and deliberately outside the arrival gate
            // below: distance changes when a fight happens, not whether it
            // happens, and this is read by the one question whose horizon is
            // the whole fight.
            //
            // Gated on attention instead. The question is whether her
            // ammunition covers what she has to get through, and something busy
            // with somebody else — or that has not noticed anyone — is not
            // something she has to get through. Distance is the obvious filter
            // and it is the wrong one: a crowd she is kiting is far away
            // precisely because the kiting is working, and it is still entirely
            // her problem. Without the gate she counted every hostile within
            // sixteen blocks, which in a shared world meant the neighbours.
            if (sample.relation().concernsHer()) {
                standing += sample.health();
            }
            // 已经在打她所守之人，或者已经站到所守之处旁边（还没动手，但下一下
            // 就是）。两者都意味着"她跑掉"救不了任何东西。
            if (sample.relation() == ThreatRelation.ATTACKING_OWNER
                    || sample.relation() == ThreatRelation.NEAR_WARD) {
                onWard++;
            }
            if (atHer && sample.threatensNow()) {
                converging++;
            }
            // The margin is the one her feet already use, so "she thinks she is
            // surrounded" and "she behaves as though she is" cannot drift apart.
            if (atHer
                    && sample.threatensWithin(
                            SpacingPolicy.instance().safeGap())) {
                pressing++;
            }
            // Weighted by arrival rather than gated on reach, so one closing in
            // counts for most of itself while one across the map counts for
            // nothing. Damage and health take the same weight: splitting them —
            // damage from those in range, health from everything — is what
            // makes a walled-off horde read as a losing fight.
            double weight =
                    sample.convergenceWeight(horizonSeconds, closingSpeed);
            if (weight > 0.0D) {
                // 承伤只收可能转向她的那些；要清掉的血量与横扫的人头照收——
                // 她**可以**选择去打一只在射牛的骷髅，那时它自然会转过来。
                if (atHer) {
                    incoming += sample.damagePerSecond() * weight;
                    hits += sample.hitsPerSecond() * weight;
                }
                health += sample.health() * weight;
                // The same weight, counted rather than multiplied by anything.
                // A sweeping weapon is paid once per body in the arc, so what
                // it needs to know is how many bodies there are going to be —
                // a question neither the incoming rate nor {@code converging}
                // answers. The latter counts only those already able to hit
                // her, which against a pack still walking in is zero, and zero
                // is what silently switched the sword's arc off in exactly the
                // fight it exists for.
                crowd += weight;
            }
        }
        if (total == 0) {
            return EMPTY;
        }
        return new ThreatField(
                total, converging, pressing, crowd, nearest, incoming, hits,
                health, standing, airborne, outranging, soonest, heaviest,
                onWard
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
