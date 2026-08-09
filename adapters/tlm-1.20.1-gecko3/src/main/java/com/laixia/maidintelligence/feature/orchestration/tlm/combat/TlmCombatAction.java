package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.TaskEquipUtil;
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
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
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

    /** How far a losing fight puts between her and it. */
    public static final double WITHDRAW_DISTANCE = 12.0D;

    /** Clearance a break-off needs to begin; it is re-decided every tick. */
    private static final double FLEE_FIRST_STEP = 3.0D;

    /**
     * Slack when matching a chosen weapon back to a stack in her pack.
     *
     * <p>Power is derived from the item, so the same sword scores the same
     * twice; the tolerance only absorbs floating point, not genuine difference.
     */
    private static final double POWER_MATCH_SLACK = 1.0E-6D;

    private final TlmThreatScanner threats;
    private final TlmWeaponScanner weapons;

    public TlmCombatAction(TlmThreatScanner threats, TlmWeaponScanner weapons) {
        this.threats = Objects.requireNonNull(threats, "threats");
        this.weapons = Objects.requireNonNull(weapons, "weapons");
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
        ScannedThreat target = locate(scanned, chosen);
        if (target == null) {
            return finish(maid);
        }
        ThreatField field =
                ThreatField.of(samples, capability.meleeReach());
        // Answered once and handed to both decisions. Whether she can give
        // ground settles "is keeping my distance a plan" and "is a bow the
        // right thing to hold", and the two must not disagree about it.
        List<Vec3> crowd = crowdOf(scanned);
        boolean canOpenGround = canOpenGround(maid, target, crowd);
        RiskVerdict verdict = EngagementRiskPolicy.instance().assess(
                field,
                capability,
                CombatReadiness.healthFraction(maid),
                canOpenGround
        );

        return switch (verdict) {
            case STAND_DOWN -> finish(maid);
            case WITHDRAW -> withdraw(maid, target, crowd);
            case ENGAGE, SKIRMISH -> fight(
                    maid, target, field, arsenal, canOpenGround, crowd
            );
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
        if (maid.isUsingItem()) {
            // Otherwise she walks away still holding the bow at full draw.
            maid.stopUsingItem();
        }
        maid.setSwingingArms(false);
        CombatMovement.clear(maid);
    }

    private ActionResult fight(
            EntityMaid maid,
            ScannedThreat target,
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
        equip(
                maid,
                stance.weapon(),
                weapons.isUsable(maid, maid.getMainHandItem())
        );

        // Re-read rather than trust the choice. Everything below acts on the
        // actual item, so a swap that has not landed yet can cost her a tick
        // but can never put her through the motions of using something she
        // cannot use.
        ItemStack held = maid.getMainHandItem();
        WeaponKind heldKind = weapons.classifyFor(held);
        boolean canStrike =
                heldKind != null && weapons.isUsable(maid, held);

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
        double desired = shooting
                ? (canSee ? WeaponSelectionPolicy.instance().preferredRange()
                        : 0.0D)
                : MeleeSwing.holdDistance(maid, target, MOVE_SPEED);
        CombatMovement.keepRange(
                maid, target, crowd, desired, shooting, MOVE_SPEED
        );
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
     * <p>Never toward her owner. What is beating her follows, and leading it to
     * the person she is guarding turns a lost fight into a lost owner.
     */
    private ActionResult withdraw(
            EntityMaid maid,
            ScannedThreat target,
            List<Vec3> crowd
    ) {
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.setTarget(null);
        // Spend the draw rather than bin it. A bow needs twenty unbroken ticks
        // to reach full, and this path used to throw whatever had accumulated
        // straight away — so a verdict that flickered between fighting and
        // leaving, which it does whenever a target hovers near the edge of the
        // arithmetic, reset the draw before it could ever finish. What a player
        // sees is a maid holding a bow at full stretch that never once looses
        // an arrow. Firing on the way out costs nothing: the shot is already
        // paid for, and it is the last free hit she will get.
        RangedDrawCycle.releaseOrKeepDraw(
                maid, target.entity(), weapons
        );
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

    /**
     * Put the chosen weapon in her hand.
     *
     * <p>The predicate has to describe the weapon well enough that no worse
     * stack satisfies it. Matching on kind alone — which is all this used to
     * ask — meant the host's search took the first melee item in the pack, so a
     * decision to draw the netherite sword could equip a wooden hoe, and an
     * unusable weapon already in her hand short-circuited the search entirely
     * because an empty bow is still, by kind, a bow.
     */
    private void equip(
            EntityMaid maid,
            WeaponCandidate weapon,
            boolean heldUsable
    ) {
        if (weapon == null || weapon.inHand()) {
            return;
        }
        if (maid.isUsingItem()) {
            // A draw in progress is the only progress a ranged weapon ever
            // makes, so a usable one finishes its shot before anything is
            // swapped. This is the rule tool replacement already follows, and
            // combat was the one place missing it.
            //
            // Deferring the swap instead — let go now, swap next tick — reads
            // as the careful option and is the bug players reported as "she
            // charges and never fires": letting go clears the use state, the
            // shooting step further down the same tick sees no draw in
            // progress and starts a fresh one, and the next tick lets go of
            // that one too. She winds up once a tick forever, never reaches
            // the release, and stands at bow range being eaten while she does
            // it. Two steps that were each reasonable alone.
            if (heldUsable) {
                return;
            }
            // Nothing to protect: whatever is in her hand cannot be used, so
            // the draw was never going to produce a shot. Dropping it here
            // rather than returning is what lets the swap land on this tick
            // instead of never.
            maid.stopUsingItem();
        }
        TaskEquipUtil.tryEquipFromBackpack(
                maid, stack -> matches(maid, stack, weapon)
        );
    }

    /** Whether this stack is the weapon that was chosen, or better of its kind. */
    private boolean matches(
            EntityMaid maid,
            ItemStack stack,
            WeaponCandidate weapon
    ) {
        return weapons.classifyFor(stack) == weapon.kind()
                && weapons.isUsable(maid, stack)
                && weapons.powerOf(stack)
                        >= weapon.power() - POWER_MATCH_SLACK;
    }

    private ActionResult finish(EntityMaid maid) {
        cancel(maid);
        return ActionResult.SUCCEEDED;
    }

    /** Every hostile she can see, as positions — the crowd to escape, not one of it. */
    private static List<Vec3> crowdOf(List<ScannedThreat> scanned) {
        List<Vec3> positions = new java.util.ArrayList<>(scanned.size());
        for (ScannedThreat threat : scanned) {
            positions.add(threat.entity().position());
        }
        return positions;
    }

    private ScannedThreat locate(
            List<ScannedThreat> scanned,
            ThreatSample sample
    ) {
        if (sample == null) {
            return null;
        }
        for (ScannedThreat threat : scanned) {
            if (threat.sample() == sample) {
                return threat;
            }
        }
        return null;
    }
}
