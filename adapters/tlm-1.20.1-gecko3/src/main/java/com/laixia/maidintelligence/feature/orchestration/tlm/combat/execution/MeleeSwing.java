package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

/**
 * Landing a blow: when she may swing, and where she should stand to do it.
 *
 * <p>Both halves of that were previously guessed at, and each guess produced a
 * maid who looked like she was fighting without any of it connecting. The
 * cadence came from a clock rather than her recovery, and the position came
 * from "get as close as possible" rather than from her reach.
 *
 * <p>The blow itself goes out through the mob path, which pays sharpness,
 * knockback and fire aspect but knows nothing about sweeping — that is a player
 * behaviour, and it is rebuilt here so a sword in her hands works like a sword
 * in his.
 */
public final class MeleeSwing {
    /** Ticks in a second — the unit attack speed is quoted in. */
    private static final double TICKS_PER_SECOND = 20.0D;

    /** Fallback cadence when the attribute is missing or nonsense. */
    private static final int DEFAULT_RECOVERY_TICKS = 20;

    /** Floor on recovery, so no attribute value turns her into a blender. */
    private static final int MINIMUM_RECOVERY_TICKS = 6;

    // Vanilla's sweep geometry, kept identical so the arc looks like a player's.
    private static final double SWEEP_BOX_WIDEN = 1.0D;
    private static final double SWEEP_BOX_HEIGHTEN = 0.25D;
    private static final double SWEEP_RANGE_SQR = 9.0D;
    private static final float SWEEP_KNOCKBACK = 0.4F;

    /**
     * Ground behind her that a swing has to be able to retreat into.
     *
     * <p>One block, and it gates <em>going in</em>, not only coming out. That
     * is the difference between hit-and-run and standing in a crowd swinging:
     * closing is only half a tactic, and the half she was doing unconditionally.
     * With six of them around her the body-clearance behind her is routinely
     * under a block, the step-out silently failed, and she spent every cooldown
     * inside their arms — measured at a fifth to a half of the whole fight, for
     * nine to twenty-eight damage a run.
     *
     * <p>Refusing to close without an exit does not make her passive. She backs
     * off instead, and a pack that follows strings out because it paths at
     * uneven speeds; the moment the leader is clear of the rest there is room
     * behind her again and she goes in. Turning six-on-one into six one-on-ones
     * is the tactic, and it falls out of this one condition rather than being
     * scripted.
     */
    private static final double STEP_OUT_GROUND = 1.0D;

    /**
     * How far inside her own maximum she stands to swing.
     *
     * <p>Reach is measured to the target's position, and a target walking
     * away crosses a quarter of a block between the tick she decides and the
     * tick she swings. Standing exactly on the limit turns that into a miss.
     */
    private static final double OWN_REACH_MARGIN = 0.25D;

    /**
     * Clearance the strike stand-off keeps beyond the target's own reach.
     *
     * <p>Small on purpose, and deliberately not {@code safeGap}. That gap is a
     * whole block and it exists for a different question — where to wait out a
     * recovery, where being generous costs nothing. Spent here it closes the
     * window entirely at low favour: a zombie reaches 1.43 and she reaches 2,
     * so demanding 1.43 + 1 leaves nothing between them and she goes back to
     * walking into its arms.
     *
     * <p>A quarter block is what the geometry actually affords untrained. It
     * widens on its own as favour grows, because her reach does.
     */
    private static final double THEIR_REACH_MARGIN = 0.25D;

    /** How many pairs of arms she will stand inside before breaking out. */
    private static final int TOLERATED_ATTACKERS = 1;

    private MeleeSwing() {
    }

    /**
     * Swing when the cooldown is up and the target is reachable — nothing else.
     *
     * <p>This once fired on {@code elapsedTicks % 20 == 0}, which is a clock
     * and not a cooldown: it counted from when the action started rather than
     * from her last swing, so an intent switch reset the phase, and any tick
     * spent out of reach burned that window instead of deferring it. Paired
     * with backing away — which pushes her out of reach precisely while the
     * window is open — she could stand inside her own attack range for a long
     * time and never once connect.
     *
     * @return whether she actually landed a swing this tick
     */
    public static boolean swingIfReady(EntityMaid maid, LivingEntity victim) {
        if (!recovered(maid)) {
            return false;
        }
        if (maid.distanceToSqr(victim)
                > maid.getMeleeAttackRangeSqr(victim)) {
            return false;
        }
        maid.swing(InteractionHand.MAIN_HAND);
        maid.doHurtTarget(victim);
        sweep(maid, victim);
        maid.getBrain().setMemoryWithExpiry(
                MemoryModuleType.ATTACK_COOLING_DOWN,
                true,
                recoveryTicks(maid)
        );
        return true;
    }

    /**
     * The sword arc that catches whatever else is standing there.
     *
     * <p>{@code doHurtTarget} is how a mob hits: it pays out attack damage,
     * sharpness, knockback and fire aspect, and stops there. Sweeping lives in
     * {@code Player.attack} and nowhere else, so a maid swinging a sword was
     * hitting one target at a time no matter how many were pressed against her —
     * and the enchantment slot for it did nothing at all.
     *
     * <p>Conditions follow vanilla's: a weapon that sweeps, feet on the ground,
     * and a swing that was worth full damage — which here is guaranteed, since
     * this only runs once the cooldown has expired.
     *
     * <p>"A weapon that sweeps" is asked of the item rather than of its class.
     * {@code instanceof SwordItem} is the vanilla test and it answers no for
     * every modded blade that does not happen to extend that class — which is
     * most of them, and which is invisible: she swings, it lands, and the arc
     * silently is not there. The tool action is the question actually being
     * asked, and any mod that wants its weapon to sweep already declares it.
     *
     * <p>One deliberate departure: vanilla sweeps everything that is not an
     * ally, while this asks {@link ThreatProfile#isHostileTo} of each bystander.
     * A player accepts the cost of catching their own animals in the arc; a
     * maid doing that is her mod killing her owner's livestock.
     */
    private static void sweep(EntityMaid maid, LivingEntity centre) {
        if (!maid.getMainHandItem().canPerformAction(ToolActions.SWORD_SWEEP)
                || !maid.onGround()) {
            return;
        }
        float base = (float) maid.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float arc = 1.0F
                + EnchantmentHelper.getSweepingDamageRatio(maid) * base;
        double facing = maid.getYRot() * ((float) Math.PI / 180F);
        boolean caught = false;
        for (LivingEntity bystander : maid.level().getEntitiesOfClass(
                LivingEntity.class,
                centre.getBoundingBox().inflate(
                        SWEEP_BOX_WIDEN, SWEEP_BOX_HEIGHTEN, SWEEP_BOX_WIDEN
                )
        )) {
            if (bystander == maid
                    || bystander == centre
                    || maid.isAlliedTo(bystander)
                    || !ThreatProfile.isHostileTo(maid, bystander)
                    || maid.distanceToSqr(bystander) >= SWEEP_RANGE_SQR) {
                continue;
            }
            bystander.knockback(
                    SWEEP_KNOCKBACK,
                    Mth.sin((float) facing),
                    -Mth.cos((float) facing)
            );
            bystander.hurt(maid.damageSources().mobAttack(maid), arc);
            caught = true;
        }
        if (caught) {
            maid.level().playSound(
                    null,
                    maid.getX(),
                    maid.getY(),
                    maid.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    maid.getSoundSource(),
                    1.0F,
                    1.0F
            );
        }
    }

    /**
     * Whether her swing has recovered.
     *
     * <p>Exposed because where she stands depends on it: inside a target's reach
     * she can be hit, and during recovery she cannot hit back, so the two
     * questions are one decision rather than two.
     */
    public static boolean recovered(EntityMaid maid) {
        return !maid.getBrain()
                .hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN);
    }

    /** How far she can strike this target, in blocks. */
    public static double reach(EntityMaid maid, LivingEntity victim) {
        return Math.sqrt(maid.getMeleeAttackRangeSqr(victim));
    }

    /**
     * Her swing recovery in ticks, from the weapon she is holding.
     *
     * <p>{@code ATTACK_SPEED} counts swings per second — 4.0 bare-handed, 1.6
     * with most swords, 1.0 with an axe — so the interval is ticks divided by
     * it. Scaling it against a baseline instead, as this first did, inverted
     * the whole point: a sword reports a lower number than bare hands because
     * each hit is worth more, and treating lower as slower made every weapon
     * she picked up reduce her damage rather than raise it.
     */
    public static int recoveryTicks(EntityMaid maid) {
        double perSecond = maid.getAttributeValue(Attributes.ATTACK_SPEED);
        if (perSecond <= 0.0D) {
            return DEFAULT_RECOVERY_TICKS;
        }
        return (int) Math.max(
                MINIMUM_RECOVERY_TICKS,
                Math.round(TICKS_PER_SECOND / perSecond)
        );
    }

    /**
     * How far to stand off right now, which is not always zero.
     *
     * <p>Recovered, she closes: the blow is the point. Recovering, she steps
     * outside the target's reach if she can get out and back — the gain is the
     * time it spends walking in, during which it cannot hit her either, and
     * that gain does not depend on out-reaching it. Where she cannot disengage
     * — no ground, or it is as quick as she is — trading toe to toe is the
     * better of two bad options, since backing off would cost her the swing
     * and spare it nothing.
     */
    /**
     * Whether at most one of them could touch her where she is standing.
     *
     * <p>This is the whole tactic against a crowd, stated as one condition.
     * Her reach and a zombie's are both about 1.4 blocks — the same formula
     * decides both — so she has no range advantage to exploit and no timing
     * window she can walk into and out of before six independent cooldowns
     * come up. Against one attacker hit-and-run works and is already tested;
     * against six simultaneously it cannot, at any spacing.
     *
     * <p>So she refuses to be in more than one pair of arms at a time. Backing
     * off instead is not passivity: a pack that follows strings out, because
     * they path at uneven speeds, and the moment the leader is clear of the
     * rest this answers true again and she goes in. Six-on-one becomes six
     * one-on-ones, which is the only shape of this fight she can win unhurt.
     *
     * <p>Counting rather than timing, deliberately. Whether each of them has
     * spent its blow was tried first and measured worse: it relaxes the
     * condition instead of tightening it, so she closes on a momentary lull and
     * arrives after it has passed.
     */
    private static boolean facingOneAtATime(
            java.util.List<ScannedThreat> pack
    ) {
        int able = 0;
        for (ScannedThreat threat : pack) {
            double clearance = SpacingPolicy.instance()
                    .clearanceBeyond(threat.sample().reach());
            if (threat.sample().distance() <= clearance
                    && ++able > TOLERATED_ATTACKERS) {
                return false;
            }
        }
        return true;
    }

    public static double holdDistance(
            EntityMaid maid,
            ScannedThreat target,
            java.util.List<Vec3> crowd,
            java.util.List<ScannedThreat> pack,
            float speed
    ) {
        double threatReach = target.sample().reach();
        double clearance =
                SpacingPolicy.instance().clearanceBeyond(threatReach);
        boolean canStepOut = RetreatSpace.canGiveGround(
                maid,
                target.entity(),
                speed,
                clearance - target.sample().distance()
        ) && RetreatSpace.escapeReach(maid, crowd, clearance)
                >= STEP_OUT_GROUND;
        if (!facingOneAtATime(pack)) {
            // Already in more than one pair of arms. Refusing to close is not
            // enough here — she is past that — so ask for a distance that
            // actually breaks contact instead of the single block a melee hold
            // shuffles. Standing in the huddle taking turns is where every
            // point of damage in this fight comes from.
            return clearance + SpacingPolicy.instance().retreatOvershoot();
        }
        if (recovered(maid)) {
            // Her swing is ready. Where to take it from is the whole question,
            // and it has an answer better than "as close as possible".
            double window = strikeWindow(maid, target);
            if (window > 0.0D) {
                return window;
            }
            if (canStepOut) {
                return 0.0D;
            }
        }
        return SpacingPolicy.instance().meleeHold(
                false, threatReach, canStepOut
        );
    }

    /**
     * A distance from which she can hit it and it cannot hit her.
     *
     * <p>Zero when no such distance exists, which is the caller's signal to
     * close the usual way.
     *
     * <p>That such a gap exists at all was missed for a long time, and the
     * miss is why she kept walking into arm's length. The host does not decide
     * her reach by the vanilla body-width formula it uses for mobs — it
     * overrides that with an attribute plus a favourability bonus, so she
     * reaches two blocks untrained and up to seven at full favour, against a
     * zombie's one and a half. She outranges most of what she fights, by a
     * little at first and by a great deal later, and none of that was being
     * spent: on a ready swing the spacing asked for zero, meaning "walk in".
     *
     * <p>Because the reach is an attribute, a weapon that grants entity reach
     * widens this window by itself. Long weapons need no special case.
     *
     * <p>The margins are deliberate and asymmetric. She stands a little inside
     * her own maximum, because a target drifting outward at the instant she
     * swings is a wasted swing; and well outside theirs, because a target
     * drifting inward is a hit taken. Missing costs a cooldown, being hit
     * costs health.
     */
    private static double strikeWindow(EntityMaid maid, ScannedThreat target) {
        double hers = reach(maid, target.entity()) - OWN_REACH_MARGIN;
        double theirs = target.sample().reach() + THEIR_REACH_MARGIN;
        return hers > theirs ? hers : 0.0D;
    }

    /**
     * How close she should get: the edge of her reach, not the target's skin.
     *
     * <p>Closing all the way in buys nothing — the blow lands the same from the
     * edge — and costs her the one thing that matters after it lands, which is
     * still being in range. A knocked-back target steps out of a nose-to-nose
     * stance immediately, so she spends the next second walking rather than
     * swinging. Standing at the edge means the knockback moves it *within* her
     * reach instead of out of it.
     */
    public static int standoff(EntityMaid maid, LivingEntity victim) {
        return (int) Math.max(1.0D, Math.floor(reach(maid, victim)));
    }
}
