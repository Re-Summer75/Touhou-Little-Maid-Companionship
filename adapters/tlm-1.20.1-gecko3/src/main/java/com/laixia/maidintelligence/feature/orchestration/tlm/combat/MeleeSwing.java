package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

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
     * <p>Conditions follow vanilla's: a real sword, feet on the ground, and a
     * swing that was worth full damage — which here is guaranteed, since this
     * only runs once the cooldown has expired.
     *
     * <p>One deliberate departure: vanilla sweeps everything that is not an
     * ally, while this asks {@link ThreatProfile#isHostileTo} of each bystander.
     * A player accepts the cost of catching their own animals in the arc; a
     * maid doing that is her mod killing her owner's livestock.
     */
    private static void sweep(EntityMaid maid, LivingEntity centre) {
        if (!(maid.getMainHandItem().getItem() instanceof SwordItem)
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
    public static double holdDistance(
            EntityMaid maid,
            ScannedThreat target,
            float speed
    ) {
        if (recovered(maid)) {
            return 0.0D;
        }
        double threatReach = target.sample().reach();
        double clearance =
                SpacingPolicy.INSTANCE.clearanceBeyond(threatReach);
        boolean canStepOut = RetreatSpace.canGiveGround(
                maid,
                target.entity(),
                speed,
                clearance - target.sample().distance()
        );
        return SpacingPolicy.INSTANCE.meleeHold(
                false, threatReach, canStepOut
        );
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
