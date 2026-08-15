package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WithdrawCommitPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.CombatMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.ExchangeAffordability;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RangedDrawCycle;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RetreatSpace;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldGuard;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * Breaking off — but hitting it on the way out when it is standing close enough.
 *
 * <p>Whether to hit on the way out is not a free choice. A landed blow does
 * knock the target back, and where she is trapped that knockback is the only
 * room she will get. But where she can simply leave, stopping to trade is how a
 * lost fight gets fought anyway: something is nearly always within reach when
 * she is losing, so "swing if you can, else retreat" resolves to "never
 * retreat".
 *
 * <p>Away from her owner where there is a choice, since what is beating her
 * follows and leading it to the person she is guarding turns a lost fight into a
 * lost owner. Not at any price, though: past twenty-four blocks the backstop
 * teleports her to his feet, and it brings the pursuit with her. Breaking off
 * toward him deliberately is better than being delivered to him involuntarily,
 * so the leash caps this direction like any other.
 */
public final class Withdrawal {
    /**
     * 她此刻守着的是"走"还是"站住"，以及守了多久。
     *
     * <p>存这一份是因为它派生不出来：判据每 tick 的答案正是那个会抖的符号，而姿态
     * 恰恰是"上一次答案加上守多久"。所有者只有这里一个，弱引用随实体卸载而去。
     */
    private static final java.util.Map<EntityMaid, int[]> STANCE =
            new java.util.WeakHashMap<>();

    private final TlmWeaponScanner weapons;

    public Withdrawal(TlmWeaponScanner weapons) {
        this.weapons = Objects.requireNonNull(weapons, "weapons");
    }

    /**
     * Leave, or turn and swing if there is nowhere to leave to.
     *
     * @param field the crowd, for deciding whether a guard is worth raising
     */
    public void run(
            EntityMaid maid,
            ScannedThreat target,
            List<ScannedThreat> pack,
            List<Vec3> crowd,
            ThreatField field
    ) {
        // Spend the draw rather than bin it. A bow needs twenty unbroken ticks
        // to reach full, and this path used to throw whatever had accumulated
        // straight away — so a verdict that flickered between fighting and
        // leaving, which it does whenever a target hovers near the edge of the
        // arithmetic, reset the draw before it could ever finish. What a player
        // sees is a maid holding a bow at full stretch that never once looses
        // an arrow. Firing on the way out costs nothing: the shot is already
        // paid for, and it is the last free hit she will get.
        //
        // Before the target is let go, and that ordering is load-bearing. A bow
        // shoots at whoever is handed to it, so it did not care; the host's
        // crossbow path goes through {@code CrossbowAttackMob}, which re-reads
        // {@code getTarget()} out of the entity rather than taking the victim
        // it was given. Firing after the clear therefore handed it a null and
        // took the server down with it — a crash for the exact case this line
        // exists to serve, breaking off while a shot is still in her hands.
        RangedDrawCycle.releaseOrKeepDraw(maid, target.entity(), weapons);
        boolean stillDrawing = maid.isUsingItem() && !ShieldGuard.raised(maid);
        maid.getBrain().eraseMemory(
                net.minecraft.world.entity.ai.memory.MemoryModuleType
                        .ATTACK_TARGET
        );
        maid.setTarget(null);
        // One question, asked once, of the same crowd and the same distance the
        // escape will actually use.
        //
        // This was two: "is there room for a step" against the single target,
        // then a twelve-block crowd-aware search for somewhere to go. They
        // disagreed constantly — the first said yes, the second found nothing,
        // and the nothing-branch erased her movement. Eleven measured ticks of
        // deciding to withdraw, being able to withdraw, and standing perfectly
        // still while they walked in. Two checks that can disagree about the
        // same fact will, and the disagreement always surfaces as her doing
        // nothing.
        Vec3 escape = RetreatSpace.escapeTo(
                maid, crowd, TlmCombatAction.WITHDRAW_DISTANCE
        );
        // Whether leaving is possible is asked of the measurement, not of the
        // attributes — the same correction the stand-off pricing needed.
        //
        // {@code outpaces} compares MOVEMENT_SPEED with a fifteen percent
        // margin, and against a vindicator she clears it by four percent: 0.42
        // against 0.4025. On paper she escapes. On the coordinates they close
        // from five blocks to one, and she spends the fight walking backwards
        // until a wall stops her — the losing trials in the raid benchmark all
        // end at the far side of the arena with full health right up until the
        // last twenty ticks, which is what "she was never caught, then she was
        // caught with nowhere to go" looks like.
        //
        // {@code soonestContact} is the relative closing rate of both bodies as
        // actually travelled. When it is finite and short, the pursuit is
        // winning whatever the attributes claim, and a retreat is only choosing
        // where she will be cornered. Planting instead is what the surviving
        // trials did by accident — they were the ones where something blocked
        // the retreat and she turned and fought.
        // The measurement to ask is the closing *rate*, not the arrival clock.
        // Those are different questions and the difference is the whole rule:
        // something already touching her reports an arrival of zero whether it
        // is chasing her down or about to be left behind, so gating on arrival
        // planted her in front of a zombie she could plainly outrun — the
        // `losingSheLeavesUnlessPinned` scenario, which is exactly that shape
        // and which caught it.
        //
        // {@code closingSpeed} projects both bodies' actual movement onto the
        // line between them, so it is zero from a standing start (nothing has
        // been demonstrated yet, and she gets to try leaving), negative once she
        // is genuinely pulling away, and positive only while the gap is really
        // shrinking with her already retreating. Positive is the one case where
        // a retreat is not an escape but a choice of where to be cornered.
        boolean gaining = target.sample().closingSpeed() > 0.0D;
        // 被追上不等于该站住——**还要扛得住这一场对砍**。
        //
        // "跑不掉就站住"这条是对的，但它是在她背着盾的时候量出来的，而判据里
        // 没有任何一项知道盾的存在。于是把盾拿走之后她照样站住，只是不再挡得下
        // 任何东西：僵尸局挨打从加盾前的 1.08 涨到 6.5，比从来没有盾时还差几倍。
        //
        // 现在站住要先过 `canAfford`，而那个判据已经把盾折算进去了。带盾时它照旧
        // 判"扛得住"，行为不变；没盾时它判"扛不住"，她继续退——退回加盾之前那条
        // 本来就正确的行为。**一条判据同时服务两种装备，而不是给无盾另开一支。**
        boolean affordable = ExchangeAffordability.canAfford(
                maid, pack, TlmCombatAction.MOVE_SPEED
        );
        boolean rawLeaving = escape != null && (!gaining || !affordable)
                && RetreatSpace.outpaces(
                        maid, target.entity(), TlmCombatAction.MOVE_SPEED
                );
        // 这三道门里只有"有没有地方退"是硬的，另外两道都建立在接近速率的符号上，
        // 而那个符号每几 tick 穿一次零。直接拿它开关腿的后果量过：符号一翻她就不
        // 再写移动目标，而上一个已经被寻路失败抹掉——四把斧头面前站满六 tick，
        // 掉了全部血量的四成半。姿态守十 tick 再允许改主意。
        boolean roomToLeave = commit(maid, rawLeaving, escape != null);
        if (roomToLeave) {
            CombatMovement.giveGround(
                    maid, crowd, TlmCombatAction.WITHDRAW_DISTANCE,
                    TlmCombatAction.MOVE_SPEED
            );
        }
        // Hit it on the way out, and keep leaving while she does.
        //
        // This was an either/or — swing only when cornered — and the either/or
        // was itself a correction: before that it read "swing if you can, else
        // retreat", and something is nearly always in reach when she is losing,
        // so the retreat branch never ran and "she cannot win this" landed as
        // trading blows until she died.
        //
        // Both of those are the same mistake from opposite ends: they let one
        // decision cancel the other. Giving ground is unconditional above, and
        // the swing is added to it rather than chosen instead of it, so the
        // failure the either/or existed to prevent cannot come back — she is
        // walking away in the same tick she connects.
        //
        // Only when it is already inside her reach and the cooldown is already
        // up, so the blow costs her nothing she was going to spend anyway. That
        // is the difference from R-07, which lost measurably: that one switched
        // weapons to shoot while leaving and paid for the swap twice a fight.
        // Nothing here changes what is in her hand.
        //
        // What it buys is knockback, which is distance — the same thing the
        // retreat is trying to produce, taken from the pursuer instead of from
        // her legs. Against something that moves as fast as she does, her legs
        // are not producing any.
        if (MeleeSwing.recovered(maid)
                && MeleeSwing.reach(maid, target.entity())
                        >= target.sample().distance()) {
            ShieldGuard.lowerForSwing(maid);
            MeleeSwing.swingIfReady(maid, target.entity());
        }
        // A retreat is the cheapest guard she ever gets: nothing she does while
        // walking away uses the off hand, and everything chasing her is behind
        // the shield by construction. This is the branch the tactic exists for —
        // the losing exchange, where denying a blow is the only output she has.
        ShieldGuard.consider(maid, target, field, stillDrawing);
        // Only bites when she has planted — a walking maid has her body turned
        // by the host's move control every tick. That is exactly the case this
        // matters in: something she cannot outrun is something she should be
        // blocking rather than showing her back to.
        ShieldGuard.faceThreat(maid, target);
    }

    /**
     * 把这一 tick 的判据折进她守着的那个姿态。
     *
     * <p>状态是 {@code [是否在走, 守了多久]}，两个数一起改，别处不写。
     */
    private static boolean commit(
            EntityMaid maid,
            boolean rawLeaving,
            boolean hasRoom
    ) {
        int[] held = STANCE.get(maid);
        boolean wasLeaving = held != null && held[0] != 0;
        int heldTicks = held == null ? Integer.MAX_VALUE : held[1];
        boolean leaving = WithdrawCommitPolicy.INSTANCE.leaving(
                wasLeaving, heldTicks, rawLeaving, hasRoom
        );
        STANCE.put(maid, new int[]{
                leaving ? 1 : 0,
                WithdrawCommitPolicy.INSTANCE.nextHeld(
                        wasLeaving, leaving, held == null ? 0 : heldTicks
                )
        });
        return leaving;
    }

    /**
     * 忘掉这一仗守着的姿态。
     *
     * <p>由收尾调用。不清的话下一仗开头会继承上一仗最后那半秒的决定，而那半秒
     * 说的是一群已经不在了的敌人——{@code behavior-spec.md} 第四节那条"每一条退出
     * 路径都要清"，这里就是那条退出路径。
     */
    public static void forget(EntityMaid maid) {
        STANCE.remove(maid);
    }
}
