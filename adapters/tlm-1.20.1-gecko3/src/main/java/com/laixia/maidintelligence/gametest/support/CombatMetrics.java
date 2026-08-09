package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;


/**
 * The totals a scenario is judged on, kept apart from the tick-by-tick record.
 *
 * <p>Two jobs that grew together: one writes a line per tick so a person can
 * follow what happened, the other accumulates the handful of numbers an
 * assertion can be written against. They change for different reasons — a new
 * column is a debugging need, a new total is a new requirement — so they are
 * separate here.
 *
 * <p>{@link #stationaryShare()} is the one that matters most. "She stands there
 * and dies" is not a claim about distance covered or damage taken: a maid
 * holding seven blocks and taking nothing is fine, one covering twenty while
 * being run down is not, and the distance reading is higher for the second.
 * It is a claim about whether her feet move while something is trying to kill
 * her, so that is measured directly.
 */
public final class CombatMetrics {
    /** Movement below this in a tick is standing still, allowing for jitter. */
    private static final double STILL = 0.02D;

    /** About how far an ordinary walking hostile can strike from. */
    private static final double MELEE_REACH = 2.5D;


    private double closest = Double.POSITIVE_INFINITY;
    private double closestEngaged = Double.POSITIVE_INFINITY;
    private double farthest;
    private int rooted;
    private int engaged;
    private int sinceEngaged;
    private int withinReach;
    private int withFoes;
    private int stationaryWithFoes;
    private int samples;
    private float damage;
    private float lastHealth = -1.0F;

    public CombatMetrics() {
    }

    /** Fold one tick in. */
    public void record(
            EntityMaid maid,
            LivingEntity foe,
            String intent,
            boolean hasWalkTarget,
            int foes,
            double movedThisTick
    ) {
        samples++;
        double distance = maid.distanceTo(foe);
        closest = Math.min(closest, distance);
        farthest = Math.max(farthest, distance);
        if (intent.startsWith("engage_threat")) {
            engaged++;
        }
        if (engaged > 0) {
            sinceEngaged++;
            closestEngaged = Math.min(closestEngaged, distance);
            if (distance <= MELEE_REACH) {
                withinReach++;
            }
        }
        // Accumulated rather than compared start to end: she regenerates, so a
        // beating she took and healed from would otherwise read as nothing.
        if (lastHealth >= 0.0F && maid.getHealth() < lastHealth) {
            damage += lastHealth - maid.getHealth();
        }
        lastHealth = maid.getHealth();
        if (!hasWalkTarget) {
            rooted++;
        }
        if (foes > 0) {
            withFoes++;
            if (movedThisTick < STILL) {
                stationaryWithFoes++;
            }
        }
    }

    /** The reported defect as a number: motionless while hunted. */
    public double stationaryShare() {
        return withFoes == 0 ? 0.0D : (double) stationaryWithFoes / withFoes;
    }

    /** Share of the engagement spent inside a walking hostile's reach. */
    public double shareWithinReach() {
        return sinceEngaged == 0
                ? 1.0D
                : (double) withinReach / sinceEngaged;
    }

    public double closestApproach() {
        return closest;
    }

    public double closestWhileEngaged() {
        return closestEngaged;
    }

    public double farthestReached() {
        return farthest;
    }

    public int engagedTicks() {
        return engaged;
    }

    /** Ticks with no movement destination at all. */
    public int rootedTicks() {
        return rooted;
    }

    /** How many ticks were recorded. */
    public int samples() {
        return samples;
    }

    public float damageTaken() {
        return damage;
    }


    /** One scannable line, printed whether the scenario passed or failed. */
    public String summary() {
        return "summary: samples=" + samples
                + " closest=" + round(closest)
                + " closestEngaged=" + round(closestEngaged)
                + " farthest=" + round(farthest)
                + " engagedTicks=" + engaged
                + " withinReach=" + Math.round(shareWithinReach() * 100.0D)
                + "% damageTaken=" + round(damage)
                + " stationaryWithFoes=" + stationaryWithFoes + "/" + withFoes
                + " rootedTicks=" + rooted;
    }

    /** Whether she currently holds a movement destination at all. */
    static boolean hasWalkTarget(EntityMaid maid) {
        return maid.getBrain()
                .hasMemoryValue(MemoryModuleType.WALK_TARGET);
    }

    /** Distance between two points, or zero when either is missing. */
    static double moved(Vec3 from, Vec3 to) {
        return from == null || to == null ? 0.0D : from.distanceTo(to);
    }

    private static String round(double value) {
        if (!Double.isFinite(value)) {
            return "inf";
        }
        return String.format("%.1f", value);
    }
}
