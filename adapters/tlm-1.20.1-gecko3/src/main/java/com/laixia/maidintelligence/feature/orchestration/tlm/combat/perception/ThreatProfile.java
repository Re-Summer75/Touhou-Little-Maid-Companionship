package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

/**
 * Asks a hostile about itself, instead of assuming.
 *
 * <p>Reach, damage and cadence used to be three constants applied to
 * everything: 2.5 blocks, one hit a second, and a flat guess when no damage was
 * declared. That is not a rounding error in the risk assessment — it *is* the
 * risk assessment's input. An enderman's reach, a ghast's range and a spider's
 * cadence were all flattened into the same numbers, so "can she win this" was
 * being answered about a creature that does not exist.
 *
 * <p>Everything here comes from the entity's own attributes and geometry, so a
 * modded hostile is measured on the same footing as a zombie without being
 * named anywhere. Fallbacks exist only where vanilla genuinely publishes
 * nothing, and each one errs towards caution.
 */
public final class ThreatProfile {
    /**
     * When each of them was last seen swinging.
     *
     * <p>Weak keys, one entry each, overwritten rather than accumulated: a
     * creature that unloads must not be kept alive by our memory of its last
     * punch.
     */
    private static final java.util.Map<LivingEntity, Long> LAST_SWING =
            new java.util.WeakHashMap<>();

    /**
     * Ticks of a spent cooldown we refuse to count on.
     *
     * <p>The period is an estimate and she needs time to get out again, not
     * only to get in. Six ticks off the end is the difference between a tactic
     * and a coin flip.
     */
    private static final int SWING_WINDOW_SAFETY = 6;

    /**
     * Damage assumed when the creature declares none.
     *
     * <p>A creeper has no attack-damage attribute at all; its harm comes from
     * exploding. Rather than name creepers — which would break for every modded
     * bomber — anything hostile that declares no damage is treated as hitting
     * harder than a zombie. Guessing high costs her some caution; guessing low
     * costs her the maid.
     */
    private static final double UNDECLARED_DAMAGE = 9.0D;

    /**
     * Cadence used when the creature publishes no attack speed.
     *
     * <p>Most vanilla hostiles do not: their melee interval lives in a goal,
     * not an attribute, and that interval is one second. Anything that does
     * publish one is believed instead.
     */
    private static final int UNDECLARED_ATTACK_PERIOD = 20;

    /** Ticks in a second, the unit attack speed is quoted in. */
    private static final double TICKS_PER_SECOND = 20.0D;

    /**
     * Reach floor for anything that fights by touching.
     *
     * <p>Geometry can report a very short reach for a small creature, but every
     * hostile can still hit something it is standing against, and treating a
     * reach as near-zero would tell her a silverfish cannot touch her.
     */
    private static final double MINIMUM_MELEE_REACH = 1.0D;

    private ThreatProfile() {
    }

    /**
     * Whether this is something she should be fighting at all.
     *
     * <p>{@code EntityMaid.canAttack} sounds like it answers this and does not:
     * it checks the owner's ignore list and whose maid something is, and returns
     * true for a cow. Filtering threats on it alone put every animal in range
     * into the risk assessment, and — once swords started sweeping — into the
     * arc as well.
     *
     * <p>So hostility is asked twice over. {@link Enemy} covers the hostile
     * faction, including modded mobs, without naming any of them. Anything else
     * qualifies only by its own behaviour: if it has decided to attack her or
     * her owner, what species it is stops being interesting.
     */
    public static boolean isHostileTo(EntityMaid maid, LivingEntity candidate) {
        if (candidate == maid || !maid.canAttack(candidate)) {
            return false;
        }
        if (candidate instanceof Enemy) {
            return true;
        }
        if (!(candidate instanceof Mob mob)) {
            return false;
        }
        LivingEntity itsTarget = mob.getTarget();
        return itsTarget == maid
                || (itsTarget != null && itsTarget == maid.getOwner());
    }

    /**
     * How far it can hurt her from.
     *
     * <p>For anything that closes, this is its own melee geometry — the same
     * calculation the host uses to decide whether its attack connects, so it
     * accounts for both bodies rather than for an average one. For anything
     * that shoots, follow range is what governs when it opens fire, and it is
     * capped at her perception because a threat she cannot see is not one she
     * can position against.
     */
    public static double reach(LivingEntity hostile, EntityMaid against) {
        if (hostile instanceof RangedAttackMob) {
            return Math.min(
                    PerceptionRange.BLOCKS, followRange(hostile)
            );
        }
        if (hostile instanceof Mob mob) {
            // Its own geometry, which is the same calculation the host uses to
            // decide whether its attack connects — so it accounts for both
            // bodies. A constant here is what made a ravager and a silverfish
            // equally dangerous to stand next to.
            return Math.max(
                    MINIMUM_MELEE_REACH,
                    Math.sqrt(mob.getMeleeAttackRangeSqr(against))
            );
        }
        // Not a Mob and not a shooter: fall back on its body, which is the only
        // thing it has told us about itself.
        return Math.max(
                MINIMUM_MELEE_REACH,
                hostile.getBbWidth() + against.getBbWidth()
        );
    }

    /**
     * How fast the gap between the two of them is actually shutting.
     *
     * <p>Both bodies count: something walking at her while she backs away at the
     * same speed is not arriving, and neither entity's movement-speed attribute
     * can express that. Reading real motion is also what stops a creature slowed
     * by water, cobwebs, an effect or plain bad pathing from being treated as
     * though it were sprinting — an attribute says what it could do, not what it
     * is doing.
     *
     * <p>Blocks a second, negative when the gap is opening.
     */
    public static double closingSpeed(EntityMaid maid, LivingEntity hostile) {
        Vec3 gap = maid.position().subtract(hostile.position());
        if (gap.lengthSqr() < 1.0E-4D) {
            return 0.0D;
        }
        return hostile.getDeltaMovement()
                .subtract(maid.getDeltaMovement())
                .dot(gap.normalize()) * 20.0D;
    }

    /**
     * Seconds before it could strike her, at the speed it is really moving.
     *
     * <p>Infinite when it is not closing, which is the honest answer rather than
     * a very large number, and keeps every comparison against it well defined.
     */
    public static double secondsToContact(
            EntityMaid maid,
            LivingEntity hostile
    ) {
        double gap = maid.distanceTo(hostile) - reach(hostile, maid);
        if (gap <= 0.0D) {
            return 0.0D;
        }
        double closing = closingSpeed(maid, hostile);
        return closing <= 0.0D ? Double.POSITIVE_INFINITY : gap / closing;
    }

    /** What one of its hits costs her, from its own attribute. */
    public static double strikeDamage(LivingEntity hostile) {
        double declared = value(hostile, Attributes.ATTACK_DAMAGE);
        return declared > 0.0D ? declared : UNDECLARED_DAMAGE;
    }

    /**
     * Note what it is doing this tick. Called once per measurement.
     *
     * <p>{@code swinging} is set on the server for the few ticks a mob's attack
     * animation runs, and a mob only swings when it attacks — so watching it is
     * how she learns, with no private field, that this one has spent its blow.
     */
    public static void observe(LivingEntity hostile, long tick) {
        if (hostile.swinging) {
            LAST_SWING.put(hostile, tick);
        }
    }

    /**
     * Whether its blow is spent and cannot land again for a moment.
     *
     * <p>The other half of hit-and-run, and the half she did not have. Reach
     * and cadence are both asymmetries she can exploit, but only reach was
     * modelled: she knew how far each of them could hit from and nothing about
     * <em>when</em>. Every approach was therefore priced as though the thing in
     * front of her were always about to swing — true on average, and wrong in
     * the only moment that matters.
     *
     * <p>Against a crowd this has to be asked of all of them, not of the one
     * she is aiming at. Asking only the target is a mistake with a measurable
     * cost: she closes because that one is spent, and the other five are not.
     */
    public static boolean swingSpent(LivingEntity hostile, long tick) {
        Long last = LAST_SWING.get(hostile);
        if (last == null) {
            return false;
        }
        long elapsed = tick - last;
        return elapsed >= 0
                && elapsed < attackPeriod(hostile) - SWING_WINDOW_SAFETY;
    }

    /** Ticks between its attacks, from its own attribute where it has one. */
    public static int attackPeriod(LivingEntity hostile) {
        double perSecond = value(hostile, Attributes.ATTACK_SPEED);
        if (perSecond <= 0.0D) {
            return UNDECLARED_ATTACK_PERIOD;
        }
        return (int) Math.max(1L, Math.round(TICKS_PER_SECOND / perSecond));
    }

    private static double followRange(LivingEntity hostile) {
        double range = value(hostile, Attributes.FOLLOW_RANGE);
        return range > 0.0D ? range : PerceptionRange.BLOCKS;
    }

    private static double value(
            LivingEntity hostile,
            net.minecraft.world.entity.ai.attributes.Attribute attribute
    ) {
        AttributeInstance instance = hostile.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }
}
