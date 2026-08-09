package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Measures what is coming at her, without naming any of it.
 *
 * <p>Sweeps her perception directly rather than reading the host's
 * visible-entity memory. Who is off limits is still settled by
 * {@link ThreatProfile#isHostileTo}, which consults the owner's ignore list
 * without treating it as a hostility test on its own. Every one of them is
 * measured, not just the nearest — a crowd cannot be judged from its closest
 * member.
 *
 * <p>Reach, damage and cadence come from {@link ThreatProfile}, which asks each
 * creature about itself. They were briefly three constants here, which quietly
 * turned every hostile in the game into the same one.
 */
public final class TlmThreatScanner {
    /**
     * How often the world is actually swept, in ticks.
     *
     * <p>Five, against the twenty the host's own sensor runs at. Danger is the
     * one thing where the sampling interval is the reaction time: a zombie that
     * appears just after a sweep is invisible until the next one, and a maid who
     * finds out a second late has already been hit.
     *
     * <p>Not every tick, because this is an AABB sweep and the decision it feeds
     * cannot act faster than she can turn anyway. A quarter second is below the
     * threshold where a player reads it as hesitation, which is the property
     * being bought.
     */
    private static final int SWEEP_INTERVAL_TICKS = 5;

    /** Vertical half-extent of the sweep; she fights up and down stairs. */
    private static final double VERTICAL_REACH = 6.0D;

    /**
     * Results between sweeps, and the tick each was taken on.
     *
     * <p>Weak keys: a maid who unloads must not be kept alive by her own threat
     * list. One entry per maid, replaced whole, so nothing accumulates.
     */
    private final Map<EntityMaid, Sweep> sweeps = new WeakHashMap<>();

    private record Sweep(long tick, List<ScannedThreat> threats) {
    }

    /**
     * Everything she can currently see and is allowed to fight.
     *
     * <p>Swept from the world rather than read out of the host's visible-entity
     * memory. That memory is filled by a sensor running at the vanilla default
     * of twenty ticks, and it feeds both this and the intent's hostile-pressure
     * fact — so a full second of the delay between "a zombie walked up" and "she
     * noticed" came from a number neither this mod nor the player can see. The
     * other two seconds were the intent's own cooldown and evaluation interval.
     *
     * <p>Sight is still required, exactly as the memory required it. Sweeping
     * without that check is faster to write and completely changes what she
     * believes: underground or at night she is nearly always within sixteen
     * blocks of something through a wall, so the crowd she prices a fight
     * against is not the crowd in the room. Measured, that reads as a maid who
     * either backs away from nothing or walks through the three hostiles beside
     * her toward a fourth she cannot reach.
     *
     * <p>Losing sight does not lose the threat immediately: the sweep is only
     * redone every few ticks, and between sweeps the last one is re-measured.
     * Something that steps behind a pillar is still being fought for a moment
     * afterwards, which is the behaviour worth having and costs nothing extra.
     *
     * <p>Distances, damage and cadence are still measured per creature by
     * {@link ThreatProfile}; only where the candidate list comes from changed.
     */
    public List<ScannedThreat> scan(EntityMaid maid) {
        long now = maid.level().getGameTime();
        Sweep cached = sweeps.get(maid);
        if (cached != null && now - cached.tick() < SWEEP_INTERVAL_TICKS) {
            // Re-measured against her current position, because a cached
            // distance is wrong the moment either of them takes a step, and
            // spacing decisions are made from these numbers every tick.
            return resample(maid, cached.threats());
        }
        List<ScannedThreat> threats = sweep(maid);
        sweeps.put(maid, new Sweep(now, threats));
        return threats;
    }

    private List<ScannedThreat> sweep(EntityMaid maid) {
        double reach = PerceptionRange.BLOCKS;
        List<LivingEntity> nearby = maid.level().getEntitiesOfClass(
                LivingEntity.class,
                maid.getBoundingBox().inflate(reach, VERTICAL_REACH, reach),
                candidate -> candidate != maid
                        && candidate.isAlive()
                        && ThreatProfile.isHostileTo(maid, candidate)
                        // Last, because it is a raycast and everything above is
                        // arithmetic.
                        && maid.hasLineOfSight(candidate)
        );
        List<ScannedThreat> threats = new ArrayList<>(nearby.size());
        for (LivingEntity hostile : nearby) {
            if (maid.distanceToSqr(hostile) <= PerceptionRange.SQUARED) {
                threats.add(new ScannedThreat(hostile, sample(maid, hostile)));
            }
        }
        return threats;
    }

    private List<ScannedThreat> resample(
            EntityMaid maid,
            List<ScannedThreat> previous
    ) {
        List<ScannedThreat> threats = new ArrayList<>(previous.size());
        for (ScannedThreat threat : previous) {
            LivingEntity hostile = threat.entity();
            if (hostile.isAlive() && hostile.level() == maid.level()) {
                threats.add(new ScannedThreat(hostile, sample(maid, hostile)));
            }
        }
        return threats;
    }

    /** The samples alone, for the parts of the decision that need no entities. */
    public static List<ThreatSample> samplesOf(List<ScannedThreat> threats) {
        List<ThreatSample> samples = new ArrayList<>(threats.size());
        for (ScannedThreat threat : threats) {
            samples.add(threat.sample());
        }
        return samples;
    }

    private ThreatSample sample(EntityMaid maid, LivingEntity hostile) {
        // Watched here because this is the one place that runs every tick for
        // every hostile she can see. A swing lasts a handful of ticks, so
        // sampling any less often would miss most of them.
        ThreatProfile.observe(hostile, maid.level().getGameTime());
        return new ThreatSample(
                maid.distanceTo(hostile),
                ThreatProfile.strikeDamage(hostile),
                ThreatProfile.reach(hostile, maid),
                ThreatProfile.attackPeriod(hostile),
                hostile.getHealth(),
                airborne(hostile),
                ThreatProfile.closingSpeed(maid, hostile),
                relation(maid, hostile)
        );
    }

    /**
     * Who this one is currently bothering.
     *
     * <p>Read from its own target rather than from damage events, so something
     * that has closed on her owner but not yet landed a hit already counts —
     * waiting for the first hit means she answers it one hit late.
     */
    private ThreatRelation relation(EntityMaid maid, LivingEntity hostile) {
        LivingEntity owner = maid.getOwner();
        LivingEntity itsTarget =
                hostile instanceof Mob mob ? mob.getTarget() : null;
        if (owner != null && itsTarget == owner) {
            return ThreatRelation.ATTACKING_OWNER;
        }
        if (itsTarget == maid) {
            return ThreatRelation.ATTACKING_MAID;
        }
        if (owner != null && owner.getLastHurtMob() == hostile) {
            return ThreatRelation.OWNER_TARGET;
        }
        return ThreatRelation.UNENGAGED;
    }

    /**
     * Whether a swung weapon would miss it entirely.
     *
     * <p>Asked of how it moves rather than of what it is, so a modded flier is
     * covered and a phantom resting on the ground is not treated as unreachable.
     */
    private boolean airborne(LivingEntity hostile) {
        if (hostile.onGround() || hostile.isInWater()) {
            return false;
        }
        if (hostile.isNoGravity()) {
            return true;
        }
        return hostile instanceof Mob mob
                && mob.getNavigation() instanceof
                net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
    }
}
