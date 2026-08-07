package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.TaskEquipUtil;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatCapability;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatStance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.EngagementRiskPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.RiskVerdict;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.TargetSelectionPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponKind;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponSelectionPolicy;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import net.minecraft.world.InteractionHand;
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
    private static final float MOVE_SPEED = 0.6F;

    /** How far a losing fight puts between her and it. */
    private static final double WITHDRAW_DISTANCE = 12.0D;

    /** Clearance a break-off needs to begin; it is re-decided every tick. */
    private static final double FLEE_FIRST_STEP = 3.0D;

    /** How far past the preferred range she lets a target drift before closing. */
    private static final double RANGE_TOLERANCE = 2.0D;

    /**
     * The same, for melee, where the whole decision spans about two blocks.
     *
     * <p>Kept well under the clearance she steps out to, or the standoff would
     * be computed and then judged "close enough" without her ever moving.
     */
    private static final double MELEE_TOLERANCE = 0.3D;

    /** Extra ground a ranged retreat takes, so it is not nibbled straight back. */
    private static final double RETREAT_OVERSHOOT = 3.0D;

    private final TlmThreatScanner threats;
    private final TlmWeaponScanner weapons;

    public TlmCombatAction(TlmThreatScanner threats, TlmWeaponScanner weapons) {
        this.threats = Objects.requireNonNull(threats, "threats");
        this.weapons = Objects.requireNonNull(weapons, "weapons");
    }

    public ActionResult execute(EntityMaid maid, int elapsedTicks) {
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
        RiskVerdict verdict = EngagementRiskPolicy.INSTANCE.assess(
                field, capability, CombatReadiness.healthFraction(maid)
        );

        return switch (verdict) {
            case STAND_DOWN -> finish(maid);
            case WITHDRAW -> withdraw(maid, target);
            case ENGAGE, SKIRMISH -> fight(maid, target, arsenal);
        };
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
            List<WeaponCandidate> arsenal
    ) {
        double distance = target.sample().distance();
        CombatStance stance = WeaponSelectionPolicy.INSTANCE.choose(
                arsenal,
                distance,
                weapons.classifyFor(maid.getMainHandItem()) == WeaponKind.MELEE
        );
        if (!stance.engaged()) {
            return withdraw(maid, target);
        }
        equip(maid, stance.weapon());

        // Telling the host who she is fighting keeps its own animations,
        // bauble hooks and target validity in step with this decision.
        maid.setTarget(target.entity());
        maid.getBrain().setMemory(
                MemoryModuleType.ATTACK_TARGET, target.entity()
        );
        CombatMovement.face(maid, target.entity());

        boolean ranged =
                stance.posture() == CombatStance.Posture.RANGED;
        boolean canSee = maid.hasLineOfSight(target.entity());

        if (!canSee && distance <= BLOCKED_GIVE_UP_DISTANCE) {
            // Standing on top of it and still unable to see it means something
            // solid is in between. Closing further will not help, and holding
            // position here is the freeze this used to produce.
            return finish(maid);
        }

        // Blocked, she closes regardless: that usually restores the line of
        // sight, and failing that puts her close enough for melee next tick.
        double desired = ranged
                ? (canSee ? stance.preferredRange() : 0.0D)
                : MeleeSwing.holdDistance(maid, target, MOVE_SPEED);
        keepRange(maid, target, desired, ranged);
        strike(maid, target, ranged);
        return ActionResult.RUNNING;
    }

    private void strike(EntityMaid maid, ScannedThreat target, boolean ranged) {
        LivingEntity victim = target.entity();
        if (ranged) {
            shoot(maid, victim);
            return;
        }
        if (maid.isUsingItem()) {
            // Switched to melee mid-draw; let go of the bow first.
            maid.stopUsingItem();
        }
        MeleeSwing.swingIfReady(maid, victim);
    }

    /**
     * Swing when the cooldown is up and the target is reachable — nothing else.
     *
     * <p>This used to fire on {@code elapsedTicks % 20 == 0}, which is not a
     * cooldown but a clock: it counts from when the action started rather than
     * from her last swing, so an intent switch resets the phase, and any tick
     * she spends out of reach burns that window instead of deferring it.
     * Together with backing away — which pushes her out of reach precisely
     * while the window is open — she could stand inside her own attack range
     * for a long time and never once connect.
     *
     * <p>Asking the brain makes the question the real one, "has she
     * recovered", and lets the answer come from her attack-speed attribute, so
     * gear and effects change her cadence with nothing here rewritten.
     *
     * @return whether she actually landed a swing this tick
     */

    /** Her own swing recovery, read from the attribute rather than assumed. */

    /**
     * Draw, hold, release — the same three steps a player performs.
     *
     * <p>Firing straight from {@code performRangedAttack} does launch an arrow,
     * but it is not shooting: the bow never enters its using state, so it never
     * bends on screen, and the shot carries a made-up power instead of the one
     * her draw earned. Both are visible — arrows leaving a slack bow at
     * uniform speed.
     *
     * <p>The draw itself is entity state, so it survives between ticks without
     * this action having to remember anything: the bow is either being held or
     * it is not, and how long for is {@code getTicksUsingItem}.
     */
    private void shoot(EntityMaid maid, LivingEntity victim) {
        maid.getLookControl().setLookAt(
                victim.getX(), victim.getEyeY(), victim.getZ()
        );
        if (!maid.hasLineOfSight(victim)) {
            // Nothing to aim at any more; relax rather than hold a full draw.
            if (maid.isUsingItem()) {
                maid.stopUsingItem();
            }
            return;
        }
        if (!maid.isUsingItem()) {
            maid.setSwingingArms(true);
            maid.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        ItemStack weapon = maid.getMainHandItem();
        boolean ready = RangedDrawCycle.readyToRelease(
                maid,
                weapon,
                weapons.classifyFor(weapon),
                weapons.externalRecognizer()
        );
        if (!ready) {
            return;
        }
        // Power is read before letting go: releasing clears the draw.
        float power = RangedDrawCycle.releasePower(maid, weapon);
        // releaseUsingItem, not stopUsingItem. Only the former runs the item's
        // own releaseUsing hook, and for a crossbow that hook *is* the loading
        // step — stopping instead threw away the draw and left the weapon
        // empty, so the shot that followed had nothing to fire. A bow does not
        // notice the difference because the host builds its arrow itself.
        maid.releaseUsingItem();
        maid.performRangedAttack(victim, power);
    }

    /**
     * Whether the weapon has been held long enough to fire.
     *
     * <p>Asked of the weapon rather than assumed from a bow's timing. A
     * crossbow reports its own charge duration and already folds Quick Charge
     * into it; a trident needs the ten ticks vanilla requires; a modded weapon
     * with a shorter wind-up answers through the recogniser. Guessing twenty
     * ticks for all of them would make fast weapons feel sluggish and slow ones
     * fire before they are ready.
     */

    /**
     * How hard the shot leaves, on the weapon's own terms.
     *
     * <p>Only a bow turns draw time into power; a crossbow and a trident either
     * fire or do not. Passing a bow's curve to them would be inventing a number
     * they never asked for.
     */

    /** Close, back off, or stand still — whichever the desired range asks for. */

    private void keepRange(
            EntityMaid maid,
            ScannedThreat target,
            double desired,
            boolean ranged
    ) {
        // Melee distances are measured in single blocks, so the tolerance that
        // keeps a bow from twitching at eight blocks would swallow the whole
        // decision here.
        double tolerance = ranged ? RANGE_TOLERANCE : MELEE_TOLERANCE;
        double distance = target.sample().distance();
        if (desired <= 0.0D) {
            // The edge of her reach, not the target's skin: knockback pushes a
            // nose-to-nose target straight out of range, so she spends the next
            // second walking instead of swinging.
            CombatMovement.chase(
                    maid,
                    target.entity(),
                    MeleeSwing.standoff(maid, target.entity()),
                    MOVE_SPEED
            );
            return;
        }
        if (distance < desired - tolerance) {
            // Checked over the distance she actually needs, not a fixed
            // probe: room for one step says nothing about room for the four
            // she is about to ask for, and finding that out halfway leaves her
            // treading against a wall while the target closes anyway.
            if (RetreatSpace.canGiveGround(
                    maid,
                    target.entity(),
                    MOVE_SPEED,
                    SpacingPolicy.INSTANCE.groundToGive(desired, distance)
            )) {
                // Take back more than was lost: a retreat that ends the
                // moment it becomes unnecessary ends exactly when the next
                // step makes it necessary again, and the two of them mark
                // time on the spot.
                CombatMovement.giveGround(
                        maid,
                        target.entity().position(),
                        SpacingPolicy.INSTANCE.groundToGive(
                                desired,
                                distance,
                                ranged ? RETREAT_OVERSHOOT : 0.0D
                        ),
                        MOVE_SPEED
                );
            } else {
                CombatMovement.clear(maid);
            }
            return;
        }
        if (distance > desired + tolerance) {
            CombatMovement.chase(
                    maid, target.entity(), (int) desired, MOVE_SPEED
            );
            return;
        }
        CombatMovement.clear(maid);
    }

    /**
     * Walk at a moving target, and only say so once.
     *
     * <p>An {@link EntityTracker} follows the entity by itself, so re-issuing
     * it every tick buys nothing — and costs everything: movement coordination
     * reads a fresh write of the same intent as a renewal and rolls it back
     * under enforcement, so a target rewritten each tick is a target that never
     * survives its own tick. Writing once and letting the tracker do the
     * following is what the errand skeleton has always done.
     */

    /**
     * Whether she is already walking somewhere close enough to here.
     *
     * <p>Same reason {@link #chase} de-duplicates: movement coordination reads
     * a fresh write of the same intent as a renewal and rolls it back under
     * enforcement. A retreat point recomputed every tick as she moves is a new
     * destination every tick, so without this the retreat cancels itself.
     */

    /** Whether her current walk target is already following this entity. */

    /**
     * Break off, away from whatever is winning.
     *
     * <p>Never toward her owner. What is beating her follows, and leading it to
     * the person she is guarding turns a lost fight into a lost owner — so the
     * retreat is computed from the threat alone, and if that happens to point
     * at her owner she still goes there rather than back into the fight.
     */
    /**
     * Give ground — while still hitting whatever has caught up.
     *
     * <p>Retreating used to mean doing nothing else, which reads as a maid who
     * will not fight: something on her heels takes free swings the whole way
     * out. Deciding the trade is bad is a reason to leave, not a reason to
     * stand there and take it, so anything already inside her reach still gets
     * hit on the way.
     */
    /**
     * Break off — but hit it on the way out when it is standing close enough.
     *
     * <p>Whether to hit on the way out is not a free choice. A landed blow does
     * knock the target back, and where she is trapped that knockback is the
     * only room she will get. But where she can simply leave, stopping to
     * trade is how a lost fight gets fought anyway: something is nearly always
     * within reach when she is losing, so "swing if you can, else retreat"
     * resolves to "never retreat".
     */
    private ActionResult withdraw(EntityMaid maid, ScannedThreat target) {
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.setTarget(null);
        if (maid.isUsingItem()) {
            maid.stopUsingItem();
        }
        // Leaving is the decision; the swing is only what is left when leaving
        // is impossible. Taking the free hit first looked clever — knockback
        // does open distance — but a swing is available almost every time
        // something is standing on her, so the retreat never ran and "she
        // cannot win this" came out as toe-to-toe until she died.
        // Room for the next step, not for the whole twelve blocks. Asking for
        // the full distance means any wall within it answers "no", so indoors —
        // caves, corridors, her own house — she could never break off at all
        // and fell straight back to trading blows. She re-decides every tick;
        // one step is the only commitment being made here.
        boolean roomToLeave = RetreatSpace.canGiveGround(
                maid, target.entity(), MOVE_SPEED, FLEE_FIRST_STEP
        );
        if (roomToLeave) {
            CombatMovement.giveGround(
                    maid,
                    target.entity().position(),
                    WITHDRAW_DISTANCE,
                    MOVE_SPEED
            );
        } else {
            // Cornered. Now the blow is worth taking: knockback is the only
            // room she is going to get.
            MeleeSwing.swingIfReady(maid, target.entity());
        }
        return ActionResult.RUNNING;
    }

    /** Walk directly away from a threat, at the same speed as everything else. */

    private void equip(EntityMaid maid, WeaponCandidate weapon) {
        // Swapping voids the use state, so a swap landing mid-draw restarts
        // the draw — every tick, forever. Melee switching releases the draw
        // deliberately before it gets here.
        if (weapon == null || weapon.inHand() || maid.isUsingItem()) {
            return;
        }
        TaskEquipUtil.tryEquipFromBackpack(
                maid, stack -> weapons.isWeapon(stack)
                        && weapons.classifyFor(stack) == weapon.kind()
        );
    }

    private ActionResult finish(EntityMaid maid) {
        cancel(maid);
        return ActionResult.SUCCEEDED;
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

    /**
     * Health scaled by armour, so gear enters the risk decision.
     *
     * <p>Uses the vanilla mitigation curve rather than a table: twenty armour
     * points roughly halve incoming damage, which is the same as twice the
     * health for the purpose of surviving a fight.
     */

    }
