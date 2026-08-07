package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * Where a fight puts her feet.
 *
 * <p>Every write here goes through the movement bridge, so pickup protection,
 * hard states and fail-open behave exactly as they do for errands. What is
 * different is the shape: an errand walks somewhere and commits, while a fight
 * has no destination and never commits, so this writes {@code WALK_TARGET}
 * directly rather than borrowing the errand skeleton.
 *
 * <p>Every method de-duplicates. That is not an optimisation — coordination
 * reads a fresh write of the same intent as a renewal and rolls it back under
 * enforcement, so a target recomputed every tick is a target that never
 * survives its own tick. This is the single most repeated mistake in this area,
 * which is why the de-duplication lives with the writes rather than in the
 * callers.
 */
public final class CombatMovement {
    /** How close a tracked target may drift before the chase is re-issued. */
    private static final double CHASE_SLACK = 1.0D;

    /** How far a fixed destination may drift before it counts as somewhere new. */
    private static final double DESTINATION_SLACK = 4.0D;

    private CombatMovement() {
    }

    /**
     * Walk at a moving target, and only say so once.
     *
     * <p>An {@link EntityTracker} follows the entity by itself, so re-issuing
     * it every tick buys nothing and costs the write, as above.
     */
    public static void chase(
            EntityMaid maid,
            LivingEntity victim,
            int closeEnough,
            float speed
    ) {
        if (tracking(maid, victim.position(), CHASE_SLACK)) {
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
            Vec3 threat,
            double distance,
            float speed
    ) {
        walkTo(
                maid,
                RetreatSpace.awayFrom(maid.position(), threat, distance),
                1,
                speed
        );
    }

    /** Keep her eyes on it, which the host's animations read from. */
    public static void face(EntityMaid maid, LivingEntity victim) {
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET, new EntityTracker(victim, true)
        );
    }

    /** Release her feet, leaving whatever runs next free to steer. */
    public static void clear(EntityMaid maid) {
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        MovementCoordinationBridge.finishKnownWrite(
                maid, previous, MovementIntentSource.COMPANION, true
        );
    }

    private static void write(EntityMaid maid, WalkTarget target) {
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        // A stale path or unreachable-since stamp outlives the target it was
        // computed for, and she will keep following the old one.
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, target);
        MovementCoordinationBridge.finishKnownWrite(
                maid, previous, MovementIntentSource.COMPANION, true
        );
    }

    private static boolean tracking(
            EntityMaid maid,
            Vec3 destination,
            double slack
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .map(tracker -> tracker.currentPosition()
                        .distanceToSqr(destination) < slack)
                .orElse(false);
    }
}
