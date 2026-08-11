package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementContext;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.TargetSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.CombatAppetite;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.MaidEating;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.WeaponSwap;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.CombatMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.JumpStrike;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RangedDrawCycle;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RetreatSpace;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatReadiness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.CombatSurvey;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
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
 * <p>What she does is decided from the weapon in her hand rather than from the
 * weapon the policy picked. The two are not always the same: a swap is deferred
 * while she is drawing, a pack can be full, an item can vanish between the scan
 * and now. Dispatching on the choice instead of on the hand is what produced
 * the maid standing at bow range working an empty bow — the decision was right
 * and the hand was wrong, and nothing checked.
 *
 * <p>This writes {@code WALK_TARGET} directly rather than going through the
 * errand skeleton. An errand walks somewhere and commits; a fight has no
 * destination and never commits, so the skeleton's claim-and-arrive shape does
 * not fit. It still finishes through the same movement bridge, so pickup
 * protection, hard states and fail-open behave identically.
 */
public final class TlmCombatAction {

    /**
     * Distance inside which a lost line of sight means solid cover, not range.
     *
     * <p>Roughly arm's length. Anything further and walking closer is worth
     * trying; this close, whatever is between them is a wall, and continuing to
     * approach only presses her into it.
     */
    private static final double BLOCKED_GIVE_UP_DISTANCE = 3.0D;

    /**
     * How fast she moves in a fight — the same as everywhere else.
     *
     * <p>Every plan in this mod walks her at 0.5–0.6, so a fight running on
     * its own faster number made her visibly superhuman the moment one began.
     * Backing away was faster still, on the reasoning that a retreat has to
     * outrun what chases it; but a maid who reverses faster than a player can
     * sprint is not a tactic, it is a bug that happens to work. Whether she
     * can retreat at all is now settled by comparing speeds rather than by
     * quietly handing her extra ones.
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

    /** Clearance a break-off needs to begin; it is re-decided every tick. */
    private static final double FLEE_FIRST_STEP = 3.0D;


    private final TlmThreatScanner threats;
    private final TlmWeaponScanner weapons;
    private final CombatAppetite appetite;
    private final WeaponSwap swap;

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
        this.threats = Objects.requireNonNull(threats, "threats");
        this.weapons = Objects.requireNonNull(weapons, "weapons");
        this.appetite = new CombatAppetite(status);
        this.swap = new WeaponSwap(this.weapons);
    }

    /**
     * The same fight, for callers with no status to hand.
     *
     * <p>Only the scenarios that drive this class directly. Hunger reads as
     * full, which costs exactly one of the four eating rules — the one that
     * spends a lull on a mouthful. Being about to die, needing a mouthful to
     * win, and refusing to spend stores on a fight already won are all about
     * the fight and go on working.
     */
    public TlmCombatAction(TlmThreatScanner threats, TlmWeaponScanner weapons) {
        this(threats, weapons, null);
    }

    /**
     * Run the fight.
     *
     * <p>This used to stand down when the maid was on one of the host's own
     * attack tasks, because the intent that runs it did not test work mode and
     * both systems would otherwise drive the same fight — two target choices
     * and {@code doHurtTarget} called once by each. That check is gone with the
     * condition that made it necessary: the orchestrator now starts only in
     * free mode, so the task here is always this mod's own and the host has no
     * attack behaviour registered to collide with.
     */
    public ActionResult execute(EntityMaid maid) {
        List<ScannedThreat> scanned = threats.scan(maid);
        if (scanned.isEmpty()) {
            return finish(maid);
        }
        List<ThreatSample> samples = TlmThreatScanner.samplesOf(scanned);
        List<WeaponCandidate> arsenal = weapons.scan(maid);
        CombatCapability capability = CombatReadiness.of(maid, arsenal);

        ThreatSample chosen = TargetSelectionPolicy.INSTANCE.select(samples);
        ScannedThreat target = CombatSurvey.locate(scanned, chosen);
        if (target == null) {
            return finish(maid);
        }
        ThreatField field =
                ThreatField.of(samples, capability.meleeReach());
        // Answered once and handed to both decisions. Whether she can give
        // ground settles "is keeping my distance a plan" and "is a bow the
        // right thing to hold", and the two must not disagree about it.
        List<Vec3> crowd = CombatSurvey.crowdOf(scanned);
        boolean canOpenGround = canOpenGround(maid, target, crowd);
        double healthFraction = CombatReadiness.healthFraction(maid);
        // Asked before the verdict, because eating is a thing she does *about*
        // the fight rather than instead of it: a mouthful taken while backing
        // off is the best moment in the whole engagement, and one taken before
        // closing is what makes an unwinnable fight winnable. Her feet go on
        // doing whatever the verdict says either way — only her hands are busy.
        appetite.consider(
                maid, field, capability, healthFraction, canOpenGround
        );

        RiskVerdict verdict = EngagementRiskPolicy.instance().assess(
                field,
                capability,
                healthFraction,
                canOpenGround
        );

        return switch (verdict) {
            case STAND_DOWN -> finish(maid);
            case WITHDRAW -> withdraw(maid, target, crowd);
            case ENGAGE, SKIRMISH -> fight(
                    maid, target, CombatSurvey.nearest(scanned), scanned, field, arsenal,
                    canOpenGround, crowd
            );
        };
    }

    /**
     * The distance this stance asks her to hold, with a floor.
     *
     * <p>A stance that reports nothing (an unmeasured weapon, or a posture
     * chosen before the weapon was known) falls back to the configured
     * ceiling rather than to zero — zero reads as "close in", which is the
     * opposite of what a ranged posture wants.
     */
    private static double standoff(CombatStance stance) {
        double declared = stance.preferredRange();
        return declared > 0.0D
                ? declared
                : WeaponSelectionPolicy.instance().preferredRange();
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
            List<Vec3> crowd
    ) {
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
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        releaseHands(maid);
        CombatMovement.clear(maid);
    }

    /**
     * Let go of whatever her hands are holding open, and nothing else.
     *
     * <p>Split from {@link #cancel} because the two are asked at different
     * moments. The fight calls {@code cancel} when it is genuinely over and can
     * safely tear down everything; the orchestrator calls this when the step it
     * is running has merely timed out, which during a long fight is a clock
     * expiring rather than a fight ending.
     *
     * <p>Tearing down movement there is measurably wrong: the retreat she is
     * halfway through is erased, re-issued, erased again, and she spends half
     * the fight rooted — measured at ninety-two motionless ticks out of two
     * hundred with three hostiles on her.
     *
     * <p>A draw is different, and it is the reason this exists at all. Nothing
     * in the world clears a use state; only letting go does. So an archer whose
     * fight ended between one tick and the next stayed at full draw for the rest
     * of her life, aiming at nothing.
     */
    public void releaseHands(EntityMaid maid) {
        if (maid.isUsingItem()) {
            maid.stopUsingItem();
        }
        maid.setSwingingArms(false);
    }

    private ActionResult fight(
            EntityMaid maid,
            ScannedThreat target,
            ScannedThreat pressing,
            List<ScannedThreat> pack,
            ThreatField field,
            List<WeaponCandidate> arsenal,
            boolean canOpenGround,
            List<Vec3> crowd
    ) {
        CombatStance stance = WeaponSelectionPolicy.instance().choose(
                arsenal, situation(maid, target, field, canOpenGround)
        );
        if (!stance.engaged()) {
            return withdraw(maid, target, crowd);
        }
        // Her hands are the one resource eating and fighting both want. While
        // she is mid-mouthful the weapon stays in the pack: swapping it back
        // would cancel the food, spend the seconds and buy nothing, which is
        // the worst of the three possible outcomes.
        boolean chewing = MaidEating.chewing(maid);
        if (!chewing) {
            swap.equip(
                    maid,
                    stance.weapon(),
                    weapons.isUsable(maid, maid.getMainHandItem())
            );
        }

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
            return finish(maid);
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
                        maid, pressing, crowd, pack, MOVE_SPEED
                );
        CombatMovement.keepRange(
                maid, target, pressing, crowd, desired, shooting, MOVE_SPEED
        );
        // 两个离地的理由，都不是脚走得到的。高处那个是竖直方向够不着，走路只
        // 收得回水平那一段；另一个是节奏——冷却剩下的 tick 数正好够她升上去再
        // 落下来时，这一刀就是暴击。顺序如此：先问够不够得着，再问疼不疼。放在
        // 走位之后，因为跳只值得在她已经站到最近处时才跳。
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
        return ActionResult.RUNNING;
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
            boolean canOpenGround
    ) {
        return EngagementContext.of(
                target.sample(),
                field,
                CombatReadiness.swingsPerSecond(maid),
                CombatReadiness.SHOTS_PER_SECOND,
                canOpenGround,
                weapons.classifyFor(maid.getMainHandItem()) == WeaponKind.MELEE
        );
    }

    private void strike(EntityMaid maid, ScannedThreat target, boolean ranged) {
        LivingEntity victim = target.entity();
        if (ranged) {
            RangedDrawCycle.shoot(maid, victim, weapons);
            return;
        }
        if (maid.isUsingItem()) {
            // Switched to melee mid-draw; let go of the bow first.
            maid.stopUsingItem();
        }
        MeleeSwing.swingIfReady(maid, victim);
    }

    /**
     * Break off — but hit it on the way out when it is standing close enough.
     *
     * <p>Whether to hit on the way out is not a free choice. A landed blow does
     * knock the target back, and where she is trapped that knockback is the
     * only room she will get. But where she can simply leave, stopping to
     * trade is how a lost fight gets fought anyway: something is nearly always
     * within reach when she is losing, so "swing if you can, else retreat"
     * resolves to "never retreat".
     *
     * <p>Away from her owner where there is a choice, since what is beating her
     * follows and leading it to the person she is guarding turns a lost fight
     * into a lost owner. Not at any price, though: past twenty-four blocks the
     * backstop teleports her to his feet, and it brings the pursuit with her.
     * Breaking off toward him deliberately is better than being delivered to
     * him involuntarily, so the leash caps this direction like any other.
     */
    private ActionResult withdraw(
            EntityMaid maid,
            ScannedThreat target,
            List<Vec3> crowd
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
        RangedDrawCycle.releaseOrKeepDraw(
                maid, target.entity(), weapons
        );
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
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
                maid, crowd, WITHDRAW_DISTANCE
        );
        boolean roomToLeave = escape != null
                && RetreatSpace.outpaces(maid, target.entity(), MOVE_SPEED);
        if (roomToLeave) {
            CombatMovement.giveGround(
                    maid, crowd, WITHDRAW_DISTANCE, MOVE_SPEED
            );
        } else {
            // Cornered. Now the blow is worth taking: knockback is the only
            // room she is going to get.
            //
            // Measured against vindicators — which move about as fast as she
            // retreats — this reads badly: she flees correctly, is run down
            // anyway, and never strikes back across two hundred ticks. Also
            // striking while leaving looks like the obvious answer and is not
            // mine to take: `losingSheLeavesUnlessPinned` pins the either/or
            // deliberately, and removing it makes that test fail by design.
            // Whether a maid who cannot outrun her pursuer should turn and
            // fight is a change in what she is, not a bug fix.
            MeleeSwing.swingIfReady(maid, target.entity());
        }
        return ActionResult.RUNNING;
    }
    private ActionResult finish(EntityMaid maid) {
        cancel(maid);
        return ActionResult.SUCCEEDED;
    }
}
