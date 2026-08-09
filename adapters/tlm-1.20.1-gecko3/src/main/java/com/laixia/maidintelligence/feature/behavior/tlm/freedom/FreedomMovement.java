package com.laixia.maidintelligence.feature.behavior.tlm.freedom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

/**
 * Where a free-mode maid is told to walk, and the one thing that outranks it.
 *
 * <p>This replaces a lease system — claims, renewals, pre-emption, suppression,
 * fail-open — that existed because a dozen host behaviours wrote {@code
 * WALK_TARGET} at each other every tick and our own writes kept losing. Free
 * mode does not register those behaviours any more, so the contest is over:
 * the writers left are this mod's plans and the host's breathing reflex.
 *
 * <p>Two writers need a rule, not a protocol. Drowning outranks everything,
 * which is one comparison; the arbiter it replaces was six files and a
 * configuration section, and every bug this area ever had came from something
 * inside it. Deleting it is the fix, not a simplification of the fix.
 *
 * <p>Nothing here is mode-guarded, because nothing calls it outside free mode:
 * the orchestrator that drives every caller starts only there. That is the
 * general shape of the isolation — other modes are untouched because the code
 * that touches them no longer exists, not because it checks first.
 */
public final class FreedomMovement {
    private FreedomMovement() {
    }

    /**
     * Send her somewhere, unless she is drowning.
     *
     * <p>The stale path and the unreachable-since stamp go with the old target.
     * They outlive it otherwise, and she keeps walking the route computed for
     * somewhere she is no longer going.
     *
     * @return whether the target was written
     */
    public static boolean write(EntityMaid maid, WalkTarget target) {
        if (surfacing(maid)) {
            return false;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, target);
        return true;
    }

    /** Stop, unless the host is currently steering her to air. */
    public static void clear(EntityMaid maid) {
        if (surfacing(maid)) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    /**
     * Whether the host is currently walking her to air.
     *
     * <p>The one case where a host behaviour still owns her feet in free mode,
     * and the reason breathing stayed on the keep-list: a plan that overwrites
     * the route to the surface drowns her while executing correctly.
     */
    public static boolean surfacing(EntityMaid maid) {
        return maid.getSwimManager().isGoingToBreath();
    }
}
