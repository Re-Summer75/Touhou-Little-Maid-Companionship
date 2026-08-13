package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.WeaponSwap;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.CombatMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpStrike;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RangedDrawCycle;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldGuard;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.MaidEating;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * One tick of a fight she has decided to be in.
 *
 * <p>Split out of {@code TlmCombatAction} so that the action is left holding the
 * lifecycle — start, cancel, let go of her hands, decide the verdict — while
 * this holds what she actually does once the verdict is "fight". They were one
 * file at the source-layout ceiling, which is the point at which the repository
 * asks for a split before the next responsibility lands rather than after.
 *
 * <p>What she does is decided from the weapon in her hand rather than from the
 * weapon the policy picked. The two are not always the same: a swap is deferred
 * while she is drawing, a pack can be full, an item can vanish between the scan
 * and now. Dispatching on the choice instead of on the hand is what produced the
 * maid standing at bow range working an empty bow — the decision was right and
 * the hand was wrong, and nothing checked.
 */
public final class Engagement {
    /**
     * Distance inside which a lost line of sight means solid cover, not range.
     *
     * <p>Roughly arm's length. Anything further and walking closer is worth
     * trying; this close, whatever is between them is a wall, and continuing to
     * approach only presses her into it.
     */
    private static final double BLOCKED_GIVE_UP_DISTANCE = 3.0D;

    /** What the caller should do once this tick is over. */
    public enum Outcome {
        /** Still fighting. */
        RUNNING,
        /** No weapon worth using; break off instead. */
        WITHDRAW,
        /** Something solid is between them and closing will not help. */
        FINISH
    }

    private final TlmWeaponScanner weapons;

    public Engagement(TlmWeaponScanner weapons) {
        this.weapons = Objects.requireNonNull(weapons, "weapons");
    }

    /**
     * Run one tick of the fight.
     *
     * @param target        the one she has chosen to hit
     * @param pressing      whoever is nearest, which spacing answers to
     * @param pack          everything she can see, for the sweep and the leap
     * @param canOpenGround whether backing off is something she could carry out
     */
    public Outcome run(
            EntityMaid maid,
            ScannedThreat target,
            ScannedThreat pressing,
            List<ScannedThreat> pack,
            ThreatField field,
            CombatStance stance,
            boolean canOpenGround,
            List<Vec3> crowd
    ) {
        if (!stance.engaged()) {
            return Outcome.WITHDRAW;
        }
        // The swap already happened, above the verdict: what she holds does not
        // depend on whether she is staying. Only the mouthful still matters
        // here, because it decides whether her hands are free to strike at all.
        boolean chewing = MaidEating.chewing(maid);

        // Re-read rather than trust the choice. Everything below acts on the
        // actual item, so a swap that has not landed yet can cost her a tick
        // but can never put her through the motions of using something she
        // cannot use.
        ItemStack held = maid.getMainHandItem();
        WeaponKind heldKind = weapons.classifyFor(held);
        boolean canStrike = !chewing
                && heldKind != null && weapons.isUsable(maid, held);

        // Telling the host who she is fighting keeps its own animations,
        // bauble hooks and target validity in step with this decision.
        maid.setTarget(target.entity());
        maid.getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET, target.entity()
        );
        CombatMovement.face(maid, target.entity());

        // Where to stand follows the intent, so she keeps walking sensibly
        // through the tick a swap takes; what to do with her hands follows the
        // hand, so she never works a weapon that cannot be used.
        boolean shooting = canStrike
                ? heldKind.isRanged()
                : stance.posture() == CombatStance.Posture.RANGED;
        boolean canSee = maid.hasLineOfSight(target.entity());
        double distance = target.sample().distance();

        if (!canSee && distance <= BLOCKED_GIVE_UP_DISTANCE) {
            // Standing on top of it and still unable to see it means something
            // solid is in between. Closing further will not help, and holding
            // position here is the freeze this used to produce.
            return Outcome.FINISH;
        }

        // Blocked, she closes regardless: that usually restores the line of
        // sight, and failing that puts her close enough for melee next tick.
        //
        // The stance carries the distance, not the policy: it was chosen for
        // this weapon and already capped, so a bow holds fifteen where a
        // crossbow holds eight. Reading the global ceiling here instead is what
        // made every ranged weapon fight at the same range.
        //
        // Spacing answers to whoever is nearest, never to the one she picked.
        // The two are the same object most of the time and differ exactly when
        // target choice stops being "the closest" — which is the whole point of
        // finishing a hurt one. Judged off the quarry, she would call six
        // blocks comfortable while a second zombie stood at her elbow.
        double desired = shooting
                ? (canSee ? standoff(stance) : 0.0D)
                : MeleeSwing.holdDistance(
                        maid, pressing, crowd, pack, TlmCombatAction.MOVE_SPEED
                );
        CombatMovement.keepRange(
                maid, target, pressing, crowd, desired, shooting,
                TlmCombatAction.MOVE_SPEED
        );
        // 两个离地的理由，都不是脚走得到的：够不着的高处是竖直方向的问题，走路只
        // 收得回水平那一段；冷却剩下的 tick 数正好够她升起再落下时，这一刀就是暴击。
        // 起跳之后还要有人推她——腾空期间 keepRange 要求的是保持退让间距，而落刀的
        // 几何指望的是她朝目标收进去，两者不接上，这一跳就永远差最后一截。
        if (!shooting && (
                JumpStrike.worthLeavingTheGround(maid, target.entity())
                        || JumpStrike.worthCrittingNow(maid, target, pack))) {
            JumpStrike.leap(maid);
        }
        JumpStrike.rideTheLeap(maid, target.entity());
        if (canStrike) {
            strike(maid, target, shooting);
        }
        // Last, and only with whatever the strike left free. Raising the shield
        // is what she does with the part of the exchange she cannot swing in,
        // so it has to see the swing's outcome — asked first, it would put the
        // shield up on the same tick the sword comes down and cancel it.
        ShieldGuard.consider(maid, target, field, shooting || chewing);
        // Facing last, after the guard is up and after movement has been
        // written. Blocking only answers what arrives from in front, and her
        // body points wherever she is walking — so this is the difference
        // between a raised shield and a shield that stops anything.
        ShieldGuard.faceThreat(maid, target);
        return Outcome.RUNNING;
    }

    /**
     * The distance this stance asks her to hold, with a floor.
     *
     * <p>A stance that reports nothing (an unmeasured weapon, or a posture
     * chosen before the weapon was known) falls back to the configured ceiling
     * rather than to zero — zero reads as "close in", which is the opposite of
     * what a ranged posture wants.
     */
    private static double standoff(CombatStance stance) {
        double declared = stance.preferredRange();
        return declared > 0.0D
                ? declared
                : WeaponSelectionPolicy.instance().preferredRange();
    }


    private void strike(EntityMaid maid, ScannedThreat target, boolean ranged) {
        LivingEntity victim = target.entity();
        if (ranged) {
            RangedDrawCycle.shoot(maid, victim, weapons);
            return;
        }
        if (maid.isUsingItem() && !ShieldGuard.raised(maid)) {
            // Switched to melee mid-draw; let go of the bow first. A raised
            // shield is excluded because it is not a draw being abandoned — it
            // is the guard she puts up between swings, and dropping it here
            // would cancel it on every single blow.
            maid.stopUsingItem();
        }
        // The shield comes down for the swing itself. Vanilla lets a blocking
        // player attack, and the host's melee path does not go through that
        // code, so leaving it raised produces a swing that lands while she is
        // nominally guarding — which reads as a maid who blocks and hits at the
        // same time. She pays the beat, and gets it back on the next tick.
        ShieldGuard.lowerForSwing(maid);
        MeleeSwing.swingIfReady(maid, victim);
    }
}
