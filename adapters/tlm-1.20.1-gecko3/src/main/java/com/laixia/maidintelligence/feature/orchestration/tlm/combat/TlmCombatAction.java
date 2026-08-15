package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.MaidEating;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.TargetSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import net.minecraft.world.entity.item.ItemEntity;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.GroundWeapon;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.WeaponSwap;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.CombatMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RetreatSpace;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatSurvey;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.CombatAppetite;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * Runs a fight, one tick at a time.
 *
 * <p>Every tick re-asks all three questions — who to hit, whether to be
 * fighting, what to fight with — because all three change while she fights. A
 * plan made when the first zombie appeared is the wrong plan once four more
 * arrive, and the cost of re-deciding is three comparisons over a handful of
 * entities.
 *
 * <p>What is left here is the lifecycle and the verdict: scan, pick a target,
 * price the fight, and route. What she does once the verdict is in belongs to
 * {@link Engagement} and {@link Withdrawal} — they were methods on this class
 * until it reached the source-layout ceiling, which is where the repository asks
 * for a split before the next responsibility arrives rather than after.
 *
 * <p>This writes {@code WALK_TARGET} directly rather than going through the
 * errand skeleton. An errand walks somewhere and commits; a fight has no
 * destination and never commits, so the skeleton's claim-and-arrive shape does
 * not fit. It still finishes through the same movement bridge, so pickup
 * protection, hard states and fail-open behave identically.
 */
public final class TlmCombatAction {
    /**
     * How fast she moves in a fight — the same as everywhere else.
     *
     * <p>Every plan in this mod walks her at 0.5–0.6, so a fight running on its
     * own faster number made her visibly superhuman the moment one began.
     * Backing away was faster still, on the reasoning that a retreat has to
     * outrun what chases it; but a maid who reverses faster than a player can
     * sprint is not a tactic, it is a bug that happens to work. Whether she can
     * retreat at all is now settled by comparing speeds rather than by quietly
     * handing her extra ones.
     */
    public static final float MOVE_SPEED = 0.6F;

    /**
     * How far a losing fight puts between her and it.
     *
     * <p>Her whole perception, not less. Breaking off to twelve blocks left her
     * inside the sixteen she can see — and inside the range most things can see
     * her back — so the retreat ended while the fight did not.
     */
    public static final double WITHDRAW_DISTANCE = PerceptionRange.BLOCKS;

    private final TlmThreatScanner threats;
    private final TlmWeaponScanner weapons;
    private final CombatAppetite appetite;
    /** 掉落物广告索引，用来在她空手时找地上的武器；没有则退化成从前。 */
    private final TlmAffordancePerceptionService perception;
    private final WeaponSwap swap;
    private final Engagement engagement;
    private final Withdrawal withdrawal;

    /**
     * A fight that can also feed her.
     *
     * <p>Hunger arrives as a collaborator rather than being read off the entity
     * because this mod keeps its own hunger, and it is the one the food errands
     * already act on. Two hunger numbers would drift, and the first sign of it
     * would be a maid who walks to a snack cabinet while believing she is full.
     */
    public TlmCombatAction(
            TlmThreatScanner threats,
            TlmWeaponScanner weapons,
            MaidStatusApi<EntityMaid> status
    ) {
        this(threats, weapons, status, null);
    }

    /** 同一场仗，外加一份掉落物广告索引。 */
    public TlmCombatAction(
            TlmThreatScanner threats,
            TlmWeaponScanner weapons,
            MaidStatusApi<EntityMaid> status,
            TlmAffordancePerceptionService perception
    ) {
        this.perception = perception;
        this.threats = Objects.requireNonNull(threats, "threats");
        this.weapons = Objects.requireNonNull(weapons, "weapons");
        this.appetite = new CombatAppetite(status);
        this.swap = new WeaponSwap(this.weapons);
        this.engagement = new Engagement(this.weapons);
        this.withdrawal = new Withdrawal(this.weapons);
    }

    /**
     * The same fight, for callers with no status to hand.
     *
     * <p>Only the scenarios that drive this class directly. Hunger reads as
     * full, which costs exactly one of the four eating rules — the one that
     * spends a lull on a mouthful. Being about to die, needing a mouthful to
     * win, and refusing to spend stores on a fight already won are all about the
     * fight and go on working.
     */
    public TlmCombatAction(TlmThreatScanner threats, TlmWeaponScanner weapons) {
        this(threats, weapons, null);
    }

    /**
     * Run the fight.
     *
     * <p>This used to stand down when the maid was on one of the host's own
     * attack tasks, because the intent that runs it did not test work mode and
     * both systems would otherwise drive the same fight — two target choices and
     * {@code doHurtTarget} called once by each. That check is gone with the
     * condition that made it necessary: the orchestrator now starts only in free
     * mode, so the task here is always this mod's own and the host has no attack
     * behaviour registered to collide with.
     */
    public ActionResult execute(EntityMaid maid) {
        List<ScannedThreat> scanned = threats.scan(maid);
        if (scanned.isEmpty()) {
            return finish(maid);
        }
        List<ThreatSample> samples = TlmThreatScanner.samplesOf(scanned);
        // 扫描之前先腾副手：军械表不看副手，所以插在那里的武器要先回背包，才能
        // 在**这一 tick** 就成为一个选项，而不是等到下一 tick。
        swap.unpark(maid);
        List<WeaponCandidate> arsenal = weapons.scan(maid);
        CombatCapability capability =
                CombatReadiness.of(maid, arsenal, scanned);

        ThreatSample chosen = TargetSelectionPolicy.INSTANCE.select(samples);
        ScannedThreat target = CombatSurvey.locate(scanned, chosen);
        if (target == null) {
            return finish(maid);
        }
        ThreatField field = ThreatField.of(samples, capability.meleeReach());
        // Answered once and handed to both decisions. Whether she can give
        // ground settles "is keeping my distance a plan" and "is a bow the
        // right thing to hold", and the two must not disagree about it.
        List<Vec3> crowd = CombatSurvey.crowdOf(scanned);
        boolean canOpenGround = canOpenGround(maid, target, crowd, field);
        double healthFraction = CombatReadiness.healthFraction(maid);
        // Asked before the verdict, because eating is a thing she does *about*
        // the fight rather than instead of it: a mouthful taken while backing
        // off is the best moment in the whole engagement, and one taken before
        // closing is what makes an unwinnable fight winnable. Her feet go on
        // doing whatever the verdict says either way — only her hands are busy.
        appetite.consider(
                maid, field, capability, healthFraction, canOpenGround
        );

        // 空着手挨追时，去捡地上那件能打的。
        //
        // 玩家报告的处境：武器打没了，怪在后面追，她只会一直跑——脚边躺着一把
        // 剑也不去捡。而"空手"在风险裁决里是直接判撤离的，所以那条路上她永远
        // 不会重新武装起来。
        //
        // 不为此另扫一遍世界：掉落物本来就在广告索引里，此前只登记了"能不能
        // 吃"，现在同一条广告也登记"能不能打"。登记一次、各取所需。
        //
        // 只改脚步，不改裁决：她仍然被判为打不过（她确实打不过，手里什么都没
        // 有），只是撤退的方向变成"那把剑在的地方"。捡起来之后武器扫描下一
        // tick 就看得到它，裁决自己会翻面。
        if (!capability.armed()) {
            ItemEntity weapon = GroundWeapon.nearestFor(maid, perception);
            if (weapon != null) {
                // 走到它身上，让宿主自己的拾取把它收进去——捡起来这件事归
                // 本体，我们只负责把她带过去。
                CombatMovement.walkTo(
                        maid, weapon.position(), 0, MOVE_SPEED
                );
                return ActionResult.RUNNING;
            }
        }

        RiskVerdict verdict = EngagementRiskPolicy.instance().assess(
                field, capability, healthFraction, canOpenGround
        );

        // What to hold is decided here, above the verdict, because it does not
        // depend on the verdict. Leaving is still done with something in her
        // hands: a parting blow is knockback, and knockback is the distance the
        // retreat is trying to buy.
        //
        // It used to live inside the engaged branch only, and that is the whole
        // of why she fought vindicators bare-handed. A mouthful moves her weapon
        // into the pack and leaves the emptied stack in her hand; if the very
        // next verdict is WITHDRAW — which it is, in the fight she is losing —
        // nothing ever put the weapon back. Measured: main hand empty for 520 of
        // 700 ticks, one swing in the whole fight, and the damage log shows
        // `empty[dealt=1.0]`, which is a bare fist.
        CombatStance stance = WeaponSelectionPolicy.instance().choose(
                arsenal, situation(maid, target, field, canOpenGround, capability)
        );
        // Her hands are the one resource eating and fighting both want. While
        // she is mid-mouthful the weapon stays in the pack: swapping it back
        // would cancel the food, spend the seconds and buy nothing.
        if (!MaidEating.chewing(maid) && stance.engaged()) {
            swap.equip(
                    maid,
                    stance.weapon(),
                    weapons.isUsable(maid, maid.getMainHandItem())
            );
        }

        return switch (verdict) {
            case STAND_DOWN -> finish(maid);
            case WITHDRAW -> {
                withdrawal.run(maid, target, scanned, crowd, field);
                yield ActionResult.RUNNING;
            }
            case ENGAGE, SKIRMISH -> engage(
                    maid, target, scanned, field, stance, canOpenGround, crowd
            );
        };
    }

    /**
     * The fight as the weapon choice needs to see it.
     *
     * <p>Assembled here because only the adapter can answer half of it: whether
     * there is ground behind her, how fast she swings, how far the thing in
     * front of her reaches. The policy receives numbers and returns a stance,
     * and never learns what a zombie is.
     */
    private EngagementContext situation(
            EntityMaid maid,
            ScannedThreat target,
            ThreatField field,
            boolean canOpenGround,
            CombatCapability capability
    ) {
        return EngagementContext.of(
                target.sample(),
                field,
                CombatReadiness.swingsPerSecond(maid),
                CombatReadiness.SHOTS_PER_SECOND,
                canOpenGround,
                weapons.classifyFor(maid.getMainHandItem()) == WeaponKind.MELEE,
                capability.guardedShare()
        );
    }

    private ActionResult engage(
            EntityMaid maid,
            ScannedThreat target,
            List<ScannedThreat> scanned,
            ThreatField field,
            CombatStance stance,
            boolean canOpenGround,
            List<Vec3> crowd
    ) {
        Engagement.Outcome outcome = engagement.run(
                maid,
                target,
                CombatSurvey.nearest(scanned),
                scanned,
                field,
                stance,
                canOpenGround,
                crowd
        );
        return switch (outcome) {
            case RUNNING -> ActionResult.RUNNING;
            case FINISH -> finish(maid);
            case WITHDRAW -> {
                withdrawal.run(maid, target, scanned, crowd, field);
                yield ActionResult.RUNNING;
            }
        };
    }

    /**
     * Whether backing off to a shooting distance is something she could do.
     *
     * <p>Asked over the ground a stand-off would actually need, not a fixed
     * probe: room for one step says nothing about room for the walk back to
     * shooting distance, and that walk is what both callers are pricing.
     */
    private boolean canOpenGround(
            EntityMaid maid,
            ScannedThreat target,
            List<Vec3> crowd,
            ThreatField field
    ) {
        // Measured arrival, before anything about the ground.
        //
        // The terrain half of this question was always honest; the speed half
        // was not. It compared MOVEMENT_SPEED attributes with a fifteen percent
        // margin, and against a vindicator that comes out at 0.42 against
        // 0.4025 — she is judged able to kite by four percent, on paper. The
        // coordinates say otherwise: they close from five blocks to one over
        // the fight and kill her against the far wall.
        //
        // This file's own weapon pricing settled the same argument long ago —
        // "the attribute says how fast it *can* run, not how fast it *is*
        // running" — and then this predicate went on reading the attribute. So
        // ask the measurement instead: {@code soonestContact} comes from the
        // relative closing speed of both bodies, so a pursuer that is actually
        // gaining reports a finite arrival however fast either of them is on
        // paper, and one that is genuinely being left behind reports infinity.
        //
        // One draw is the unit because that is the smallest thing standing off
        // has to buy. If the first of them arrives before she can complete a
        // single shot, "keep your distance" is not a plan she can execute — it
        // is a description of walking backwards until a wall stops her.
        double untilContact = field.soonestContact();
        if (Double.isFinite(untilContact)
                && untilContact < 1.0D / CombatReadiness.SHOTS_PER_SECOND) {
            return false;
        }
        // Asked of the crowd, because the retreat is taken from the crowd. This
        // was left on the single target when giving ground became crowd-aware,
        // and the two then answered different questions: measured, "can I keep
        // my distance" flipped between yes and no on alternating ticks while an
        // escape of nine to twelve blocks was sitting there the whole time. A
        // verdict and the movement it authorises must be reasoning about the
        // same fight.
        double groundWanted = Math.max(
                1.0D,
                WeaponSelectionPolicy.instance().preferredRange()
                        - target.sample().distance()
        );
        return RetreatSpace.escapeReach(maid, crowd, groundWanted)
                >= Math.max(1.0D, Math.min(
                        RetreatSpace.MINIMUM_RETREAT, Math.floor(groundWanted)))
                && RetreatSpace.outpaces(
                        maid, target.entity(), MOVE_SPEED
                );
    }

    /** Drop everything this action owns; the orchestrator may resume later. */
    public void cancel(EntityMaid maid) {
        releaseHands(maid);
        CombatMovement.clear(maid);
    }

    /**
     * Let go of everything the world will not let go of for her.
     *
     * <p>Split from {@link #cancel} because the two are asked at different
     * moments. The fight calls {@code cancel} when it is genuinely over and can
     * safely tear down everything, movement included; the orchestrator calls
     * this when the step it is running has merely timed out, which during a
     * long fight is a clock expiring rather than a fight ending. What is left
     * here is exactly the state that survives on its own and would otherwise
     * survive forever.
     *
     * <p>Tearing down movement there is measurably wrong: the retreat she is
     * halfway through is erased, re-issued, erased again, and she spends half
     * the fight rooted — measured at ninety-two motionless ticks out of two
     * hundred with three hostiles on her.
     *
     * <p>A draw is different, and it is the reason this exists at all. Nothing
     * in the world clears a use state; only letting go does. So an archer whose
     * fight ended between one tick and the next stayed at full draw for the rest
     * of her life, aiming at nothing. A raised shield is let go here for the
     * same reason and by the same call.
     */
    public void releaseHands(EntityMaid maid) {
        if (maid.isUsingItem()) {
            maid.stopUsingItem();
        }
        maid.setSwingingArms(false);
        forgetADeadTarget(maid);
        // 走还是站住那个姿态也是这一仗的状态，且没有别处会清。留着它，下一仗开头
        // 的半秒会照着一群已经不在的敌人走。
        Withdrawal.forget(maid);
    }

    /**
     * 指着一具尸体的攻击目标，扔掉。
     *
     * <p>自由模式没有宿主行为，**没有任何东西会替她清 {@code ATTACK_TARGET}**——
     * 原版那个"目标无效就停手"的行为不在 {@code FreedomBrain} 的保留清单里。而每一件
     * 差事的资格判据都要求这条记忆是空的（见 {@code ApproachAndCommitAction.eligible}），
     * 于是她把东西打死、意图随后被取消之后，那条记忆永远留着，她此后什么都做不了：
     * 不跟随、不吃饭、不回家、不落座，站到主人右键她为止。玩家报的"战斗完有概率
     * 什么任务都无法执行"就是这个。
     *
     * <p><b>只扔死的那一个。</b>第一版在这里无条件清目标，两个基准同时变差：僵尸局
     * 从"六只全杀"退到剩一两只，卫道士局从 2/4 存活退到 0/4。原因是这条路径在编排器
     * 每次取消时都会走到——一场仗里的步骤超时是钟走完了，不是仗打完了——而清掉活着
     * 的目标会打断本体的挥击路由。**活着的目标是这一仗的一部分，不归收尾管。**
     *
     * <p>而死锁那一侧不需要它：报出来的处境是"打完之后"，那时目标已经是尸体。仗还
     * 没完时目标活着，威胁压力也还在，战斗意图会照旧被选中——那不是"她什么都做不了"，
     * 那就是她在打。
     */
    private static void forgetADeadTarget(EntityMaid maid) {
        LivingEntity target = maid.getTarget();
        if (target != null && target.isAlive() && !target.isRemoved()) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.setTarget(null);
    }

    private ActionResult finish(EntityMaid maid) {
        cancel(maid);
        return ActionResult.SUCCEEDED;
    }
}
