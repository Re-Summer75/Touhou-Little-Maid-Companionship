package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.OwnerFollowBridge;
import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * Where a fight puts her feet.
 *
 * <p>Every write here goes through the movement bridge, so hard states and
 * fail-open behave as they do for errands. What is different is the shape and
 * the rank: an errand walks somewhere and commits, while a fight has no
 * destination and never commits, so this writes {@code WALK_TARGET} directly
 * rather than borrowing the errand skeleton — and it claims at emergency
 * authority, so unlike an errand it does take precedence over a pickup already
 * in progress. A dropped carrot can wait; the zombie cannot.
 *
 * <p>Every method de-duplicates. That is not an optimisation — coordination
 * reads a fresh write of the same intent as a renewal and rolls it back under
 * enforcement, so a target recomputed every tick is a target that never
 * survives its own tick. This is the single most repeated mistake in this area,
 * which is why the de-duplication lives with the writes rather than in the
 * callers.
 */
public final class CombatMovement {
    /**
     * Combat writes at emergency authority, because a fight is one.
     *
     * <p>This used to claim as {@code COMPANION}, which is
     * {@code PASSIVE_COMPANION} — the <em>weakest</em> rank there is, below
     * {@code NATIVE_SOFT}. Free mode deliberately leaves the host's idle
     * wandering switched on, and wandering claims as {@code RANDOM_STROLL},
     * which is {@code NATIVE_SOFT}. So a maid deciding to back away from a
     * zombie was outranked by her own decision to stroll: the retreat was
     * written, the arbiter suppressed it, and she stood there. Both halves of
     * the complaint — ranged never opening distance and melee never stepping
     * out of reach — go through this class, which is why both showed it.
     *
     * <p>A live server reported 98 suppressed writes against 0 preemptions,
     * which is what "never wins a contest" looks like from the outside.
     *
     * <p>{@code COMBAT} already existed for this and sits just under
     * {@code BREATH_AIR} on priority, so drowning still outranks fighting.
     */

    /** How far a fixed destination may drift before it counts as somewhere new. */
    private static final double DESTINATION_SLACK = 2.0D;

    /** How far past the preferred range she lets a target drift before closing. */
    private static final double RANGE_TOLERANCE = 2.0D;

    /**
     * The same, for melee, where the whole decision spans about two blocks.
     *
     * <p>Kept well under the clearance she steps out to, or the standoff would
     * be computed and then judged "close enough" without her ever moving.
     */
    private static final double MELEE_TOLERANCE = 0.3D;

    /**
     * Clearance a step back needs before it is worth starting.
     *
     * <p>The next step, not the whole retreat. Asking whether all eleven blocks
     * of a bow's fallback are clear means any wall inside them answers "no", and
     * the answer to "no" here was to stand perfectly still — so in a cave, a
     * corridor or her own house she held her ground at knife range and was
     * beaten to death without moving. Breaking off already learned this and
     * probes one stride; holding a range is the same question and was still
     * asking for the whole distance.
     */
    private static final double RETREAT_FIRST_STEP = 3.0D;

    /**
     * Ground a stand-off has to actually gain to be worth starting.
     *
     * <p>Only for a ranged stand-off. A maid who shuffles one block of the eight
     * she wants, arrives, and immediately qualifies to shuffle again spends the
     * whole fight turning around; in a small room that came out as a crossbow
     * that never fired, because every tick went into walking.
     *
     * <p>Melee is the opposite case and must not be held to this. Stepping out
     * of a target's reach during her swing recovery only ever asks for about a
     * block, so a two-block minimum silently cancels the entire hit-and-run —
     * she stands inside its arms through every cooldown and takes the free hits.
     * That is precisely what applying this to both did.
     */
    private static final double WORTHWHILE_GROUND = 2.0D;

    /**
     * What a melee step-out asks for: anything at all.
     *
     * <p>It asked for a whole block, and a whole block is more than a crowd
     * leaves. With six of them around her the body-clearance behind her reads
     * a fraction most ticks, the step-out failed its own threshold, and the
     * branch below fell through to standing still — inside their arms, through
     * every cooldown. Measured, that was a fifth to a half of the fight spent
     * in reach and every point of damage she took.
     *
     * <p>Half a block out of a zombie's arc is half a block it has to walk back
     * before it can swing, and she re-decides next tick anyway. Demanding a
     * tidy full block is how the whole tactic got cancelled for tidiness.
     * Ranged keeps its two-block minimum: a bow shuffling a few centimetres
     * really is just twitching.
     */
    private static final double WORTHWHILE_MELEE_GROUND = 0.01D;

    private CombatMovement() {
    }

    /**
     * Close, back off, or stand still — whichever the desired range asks for.
     *
     * <p>This is the whole of "where should she be standing", which is why it
     * lives here rather than beside the striking. It reads the same spacing
     * policy the domain layer states and turns it into the one write that
     * matters.
     *
     * <p>Two threats, not one, and they are usually the same object. The
     * <em>quarry</em> is who she is trying to hit; the <em>pressing</em> one is
     * whoever is closest to hurting her. Spacing has to answer to the second.
     * Reading the quarry's distance for both is right until the moment they
     * differ — and the whole point of choosing a target on anything other than
     * distance is that they differ. She would then judge herself comfortable
     * because the one she is aiming at is six blocks away, while another stands
     * at her elbow hitting her, and nothing in the arithmetic would notice.
     *
     * @param quarry   who she is trying to hit
     * @param pressing whoever is nearest to hurting her, spacing answers to it
     * @param desired  the distance she is trying to hold, zero meaning close in
     * @param ranged   whether she is shooting, which widens every tolerance
     */
    public static void keepRange(
            EntityMaid maid,
            ScannedThreat quarry,
            ScannedThreat pressing,
            java.util.List<Vec3> crowd,
            double desired,
            boolean ranged,
            float speed
    ) {
        // Melee distances are measured in single blocks, so the tolerance that
        // keeps a bow from twitching at eight blocks would swallow the whole
        // decision here.
        // Asymmetric on purpose. The tolerance exists so a target drifting
        // around the range she is holding does not make her twitch; it is not
        // an allowance for the target to walk two blocks closer for free.
        // Applied to both sides it was exactly that: she began giving ground
        // only at six blocks, and a zombie covers six to arm's length in about
        // a second — faster than she turns, accelerates and paths. The measured
        // result was a fight that oscillated between ten blocks and two, which
        // reads as "she never keeps her distance" because the part a player
        // watches is the trough.
        double tolerance = ranged ? RANGE_TOLERANCE : MELEE_TOLERANCE;
        double distance = pressing.sample().distance();
        if (desired <= 0.0D) {
            // The edge of her reach, not the target's skin: knockback pushes a
            // nose-to-nose target straight out of range, so she spends the next
            // second walking instead of swinging.
            chase(
                    maid,
                    quarry.entity(),
                    MeleeSwing.standoff(maid, quarry.entity()),
                    speed
            );
            return;
        }
        if (distance < desired) {
            // Only the next stride has to be clear. She re-decides every tick,
            // so a retreat that turns out to be short is corrected next tick,
            // whereas demanding the whole distance up front turns every indoor
            // fight into standing still.
            if (RetreatSpace.canGiveGround(
                    maid, pressing.entity(), speed, RETREAT_FIRST_STEP
            ) && RetreatSpace.escapeReach(
                    maid, pressing.entity().position(), RETREAT_FIRST_STEP
            ) >= (ranged
                    ? WORTHWHILE_GROUND
                    : WORTHWHILE_MELEE_GROUND)) {
                // Take back more than was lost: a retreat that ends the moment
                // it becomes unnecessary ends exactly when the next step makes
                // it necessary again, and the two of them mark time on the spot.
                giveGround(
                        maid,
                        crowd,
                        SpacingPolicy.instance().groundToGive(
                                desired,
                                distance,
                                ranged ? SpacingPolicy.instance().retreatOvershoot() : 0.0D
                        ),
                        speed
                );
            } else {
                // Nowhere to give. Hold what she has rather than walk anywhere:
                // the distance being held is her swing-recovery step-out, and
                // closing it on purpose walks her back inside the reach of
                // everything that cornered her, to be hit by all of it in turn.
                // Standing is not a tactic, but it is not that either, and the
                // decision to close belongs to the tick where her swing is
                // ready — which arrives on its own.
                clear(maid);
            }
            return;
        }
        if (distance > desired + tolerance) {
            chase(maid, quarry.entity(), (int) desired, speed);
            return;
        }
        // Comfortable — but a retreat already under way is not finished just
        // because it has become unnecessary. Clearing here unconditionally is
        // what made giving ground a one-tick event: she starts backing off at
        // six blocks, crosses six on the very next tick, lands in this branch,
        // and has the target erased before she has taken a step. The overshoot
        // {@link SpacingPolicy} computes was therefore never once walked, and
        // the whole cycle repeated until a tick came where she did not re-issue
        // in time — which is why she kept distance sometimes and was eaten
        // others, from identical code.
        if (givingGround(maid, pressing.entity())) {
            return;
        }
        clear(maid);
    }

    /**
     * Whether her current destination is one she is retreating to.
     *
     * <p>A fixed point further from the threat than she is standing. An entity
     * tracker is excluded on purpose: chasing something is not retreating from
     * it, however the distances happen to compare on a given tick.
     */
    private static boolean givingGround(
            EntityMaid maid,
            LivingEntity threat
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(tracker -> !(tracker instanceof EntityTracker))
                .map(tracker -> tracker.currentPosition()
                        .distanceToSqr(threat.position())
                        > maid.position().distanceToSqr(threat.position()))
                .orElse(false);
    }

    /**
     * Walk at a moving target, and only say so once.
     *
     * <p>An {@link EntityTracker} follows the entity by itself, so re-issuing
     * it every tick buys nothing and costs the write, as above.
     *
     * <p>How close is part of what is being said, though. This once compared
     * only positions, and an entity tracker reports the entity's own position —
     * so the comparison was zero against zero and always matched. A chase
     * issued at bow range then survived the switch to melee: she was still
     * walking at the same target, so nothing was rewritten, and the stopping
     * distance stayed at eight blocks while her sword reached one and a half.
     * She closed to eight, stopped, and stood there swinging at nothing for as
     * long as the fight lasted.
     */
    public static void chase(
            EntityMaid maid,
            LivingEntity victim,
            int closeEnough,
            float speed
    ) {
        if (alreadyChasing(maid, victim, closeEnough)) {
            return;
        }
        write(
                maid,
                new WalkTarget(
                        new EntityTracker(victim, false), speed, closeEnough
                )
        );
    }

    /** Walk to a fixed point, unless she is already headed somewhere like it. */
    public static void walkTo(
            EntityMaid maid,
            Vec3 destination,
            int closeEnough,
            float speed
    ) {
        if (tracking(maid, destination, DESTINATION_SLACK)) {
            return;
        }
        write(maid, new WalkTarget(destination, speed, closeEnough));
    }

    /**
     * Walk directly away from a threat.
     *
     * <p>Away from the threat, never towards her owner: leading a pack to the
     * person she is protecting is worse than the hit she is avoiding.
     */
    public static void giveGround(
            EntityMaid maid,
            java.util.List<Vec3> crowd,
            double distance,
            float speed
    ) {
        // A retreat already under way and still improving is left alone.
        //
        // Every write erases the PATH memory, so a destination that moves is a
        // navigation that restarts. Against one hostile the escape point is
        // stable and this never mattered; against a crowd the centroid shifts
        // every tick, the recomputed point clears the two-block slack, and she
        // re-paths continuously. Measured in a soak she was correctly deciding
        // to withdraw, correctly holding the lease, correctly being sent eleven
        // blocks away — and covering 0.3 blocks per twenty ticks while her
        // health went from 18 to 6. All the decisions were right and she was
        // standing still inside them.
        if (retreatStillImproving(maid, crowd)) {
            return;
        }
        // The best line available, which is straight back whenever that works
        // and the nearest open angle when it does not. Walking into a wall and
        // calling it a retreat is the same as not retreating.
        Vec3 escape = RetreatSpace.escapeTo(maid, crowd, distance);
        if (escape == null) {
            // Nothing to write — and emphatically not a reason to erase what she
            // already has. Clearing here is an active instruction to stand
            // still, and it was reached whenever the caller's "can I retreat"
            // question and this one disagreed: measured, eleven ticks of
            // `WITHDRAW` with `canOpen=y`, no walk target, idle navigation and
            // nobody holding the lease, while hostiles closed from 5.3 to 4.8.
            // She had decided to leave, was able to leave, and had been told to
            // stop.
            return;
        }
        walkTo(maid, escape, 1, speed);
    }

    /**
     * Whether the retreat she is already running is still worth finishing.
     *
     * <p>Judged on where it takes her rather than on when it was decided: a
     * destination that still puts more room between her and the nearest hostile
     * than she has now is a destination worth arriving at, even if a freshly
     * computed one would be marginally better. Recomputing "marginally better"
     * every tick is how she ends up re-pathing instead of travelling.
     */
    private static boolean retreatStillImproving(
            EntityMaid maid,
            java.util.List<Vec3> crowd
    ) {
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (current == null || current.getTarget() instanceof EntityTracker) {
            return false;
        }
        Vec3 destination = current.getTarget().currentPosition();
        if (destination.distanceToSqr(maid.position()) < 1.0D) {
            // Arrived, or as good as. Time for a new decision.
            return false;
        }
        if (!OwnerFollowBridge.withinLeash(maid, destination)) {
            // It was somewhere she could go when it was chosen. Her owner has
            // been walking since, and a retreat that now ends outside the leash
            // ends with her being dropped back into the fight she left. Better
            // to spend the tick finding one she is allowed to finish.
            return false;
        }
        return nearestOf(destination, crowd) > nearestOf(maid.position(), crowd);
    }

    /** Distance from a point to the closest member of the crowd. */
    private static double nearestOf(Vec3 point, java.util.List<Vec3> crowd) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Vec3 threat : crowd) {
            nearest = Math.min(nearest, point.distanceTo(threat));
        }
        return nearest;
    }

    /** Keep her eyes on it, which the host's animations read from. */
    public static void face(EntityMaid maid, LivingEntity victim) {
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET, new EntityTracker(victim, true)
        );
    }

    /** Release her feet, leaving whatever runs next free to steer. */
    public static void clear(EntityMaid maid) {
        FreedomMovement.clear(maid);
    }

    private static void write(EntityMaid maid, WalkTarget target) {
        FreedomMovement.write(maid, target);
    }

    /**
     * Whether she is already walking at this entity, stopping where asked.
     *
     * <p>Both halves matter. The entity, because a tracker aimed at something
     * else is not this chase; the stopping distance, because it is the only
     * part of a chase that changes when her stance does.
     */
    private static boolean alreadyChasing(
            EntityMaid maid,
            LivingEntity victim,
            int closeEnough
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .filter(target -> target.getCloseEnoughDist() == closeEnough)
                .map(WalkTarget::getTarget)
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(victim::equals)
                .isPresent();
    }

    /**
     * Whether a fixed destination is close enough to the one she already has.
     *
     * <p>Compared as squares on both sides. The slack was stated in blocks and
     * tested against {@code distanceToSqr}, so a four-block tolerance behaved
     * as two.
     *
     * <p>A chase is never the destination in question, however near it passes.
     * An entity tracker reports the entity's own position, so "walk to a point
     * two blocks from that zombie" and "walk at that zombie" compared as the
     * same instruction — and the point she is sent to while giving ground is,
     * by construction, roughly her own distance from it. Measured: the melee
     * step-out asked for a spot 1.98 blocks from the target, the slack was 2,
     * and the write was skipped as redundant against a chase issued the tick
     * before. She kept the chase and stood inside its reach for the whole
     * recovery. {@link #givingGround} excludes trackers for the same reason;
     * this is the other half of it.
     */
    private static boolean tracking(
            EntityMaid maid,
            Vec3 destination,
            double slack
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(target -> !(target instanceof EntityTracker))
                .map(tracker -> tracker.currentPosition()
                        .distanceToSqr(destination) < slack * slack)
                .orElse(false);
    }
}
