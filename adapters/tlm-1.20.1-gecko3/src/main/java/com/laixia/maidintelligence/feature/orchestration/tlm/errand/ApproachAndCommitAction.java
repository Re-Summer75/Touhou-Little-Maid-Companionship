package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMovement;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmActionParameters;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Go there, hold it, do it.
 *
 * <p>Every errand a maid runs has the same shape — check she is free, pick
 * something, reserve it, walk over, and only then do the one thing that makes
 * this errand different from the others. Written once per errand, the first
 * four steps came to roughly two hundred and fifty duplicated lines apiece,
 * and the duplication was the dangerous part rather than the wasteful one:
 * three copies of "release the walk target only if it is still ours" agree
 * until somebody improves one of them.
 *
 * <p>So this is the only place in the mod that writes a maid's walk target for
 * a companion errand, and the only place that reserves what she is heading
 * toward. An {@link Errand} supplies the two genuinely varying pieces.
 *
 * <p>One instance per errand, holding that errand's own progress, so two kinds
 * of errand cannot be confused for one another mid-walk.
 */
public final class ApproachAndCommitAction {
    private static final int CLAIM_LEASE_TICKS = 100;
    private static final int COMMIT_LEASE_TICKS = 20;

    private final Errand errand;
    private final Map<EntityMaid, Progress> progress = new WeakHashMap<>();

    public ApproachAndCommitAction(Errand errand) {
        this.errand = Objects.requireNonNull(errand, "errand");
    }

    public ActionResult execute(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        errand.prepare(maid);
        if (!eligible(maid)) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        ApproachTarget target = errand.find(maid, gameTime);
        if (target == null || !target.valid()) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        Progress held = claim(maid, target, gameTime);
        if (held == null) {
            /*
             * Somebody else holds it. Failing rather than waiting hands the
             * decision back to the engine, which may well rank a different
             * errand highest now that this one is spoken for.
             */
            return ActionResult.FAILED;
        }

        int closeEnough = TlmActionParameters.integer(
                parameters,
                "close_distance",
                2,
                1,
                4
        );
        if (target.distanceToSqr(maid) <= (double) closeEnough * closeEnough) {
            return commit(maid, held, target, gameTime);
        }
        if (!maid.canBrainMoving()) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        float speed = TlmActionParameters.number(
                parameters,
                "speed",
                0.55F,
                0.1F,
                2.0F
        );
        return walk(maid, held, target, speed, closeEnough);
    }

    /**
     * Whether the step may continue. Called between ticks, when the world has
     * had a chance to move on without telling anyone.
     */
    public boolean revalidate(EntityMaid maid, long gameTime) {
        Progress held = progress.get(maid);
        if (held == null) {
            return false;
        }
        if (!eligible(maid)
                || !errand.stillWorthwhile(maid, held.target(), gameTime)
                || (held.claim() != null
                        && !held.claims().owns(held.claim(), gameTime))) {
            cancel(maid);
            return false;
        }
        return true;
    }

    public void cancel(EntityMaid maid) {
        release(maid, progress.remove(maid), "cancelled");
    }

    private ActionResult commit(
            EntityMaid maid,
            Progress held,
            ApproachTarget target,
            long gameTime
    ) {
        clearMovement(maid, held);
        // Upgrading the reservation before touching anything is what stops two
        // maids who both arrived from both succeeding.
        boolean occupied = held.claim() == null
                || (held.claims().occupy(
                        held.claim(),
                        gameTime,
                        COMMIT_LEASE_TICKS
                ) && held.claims().owns(held.claim(), gameTime));
        boolean done = occupied && errand.commit(maid, target, gameTime);
        if (progress.get(maid) == held) {
            progress.remove(maid);
        }
        release(maid, held, done ? errand.name() : "commit_failed");
        return done ? ActionResult.SUCCEEDED : ActionResult.FAILED;
    }

    private ActionResult walk(
            EntityMaid maid,
            Progress held,
            ApproachTarget target,
            float speed,
            int closeEnough
    ) {
        if (alreadyHeading(maid, target)) {
            return ActionResult.RUNNING;
        }
        clearMovement(maid, held);
        PositionTracker tracker = target.tracker();
        WalkTarget written = new WalkTarget(tracker, speed, closeEnough);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        // Refused only while the host is walking her to air. Taking a refusal
        // as success would leave her standing still with a plan that believes
        // it is under way; the check used to guard against a whole arbiter and
        // now guards against the one writer that still outranks us.
        if (!FreedomMovement.write(maid, written)) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        progress.put(maid, held.withWalkTarget(written));
        return ActionResult.RUNNING;
    }

    private Progress claim(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return null;
        }
        if (!errand.requiresClaim()) {
            // Nothing to contend for, so progress is only about which walk
            // target is ours to withdraw.
            Progress current = progress.get(maid);
            if (current != null
                    && current.target().identity().equals(target.identity())) {
                return current;
            }
            cancel(maid);
            Progress unclaimed = new Progress(target, null, null, null);
            progress.put(maid, unclaimed);
            return unclaimed;
        }
        Progress current = progress.get(maid);
        if (current != null
                && current.target().identity().equals(target.identity())) {
            if (current.claims().renew(
                    current.claim(),
                    gameTime,
                    CLAIM_LEASE_TICKS
            )) {
                // Same errand, refreshed target: keep the walk already written.
                Progress refreshed = current.withTarget(target);
                progress.put(maid, refreshed);
                return refreshed;
            }
            cancel(maid);
        } else if (current != null) {
            cancel(maid);
        }

        CoordinationClaimService claims =
                TlmCoordinationClaims.service(level);
        CoordinationClaimToken token = claims.tryClaim(
                new CoordinationClaimRequest(
                        target.claimKey(level),
                        maid.getUUID(),
                        TlmCoordinationClaims.operation(
                                maid,
                                errand.name() + "/" + target.identity()
                        ),
                        CLAIM_LEASE_TICKS
                ),
                gameTime
        ).orElse(null);
        if (token == null) {
            return null;
        }
        Progress claimed = new Progress(target, null, token, claims);
        progress.put(maid, claimed);
        return claimed;
    }

    private static boolean eligible(EntityMaid maid) {
        return !maid.isOrderedToSit()
                && !maid.isMaidInSittingPose()
                && !maid.isSleeping()
                && !maid.isLeashed()
                && !maid.isUsingItem()
                && !maid.isBegging()
                && !maid.isPassenger()
                && !maid.getBrain().hasMemoryValue(
                        MemoryModuleType.ATTACK_TARGET
                )
                && !maid.getBrain().isActive(Activity.PANIC)
                && !MaidCommandSeatBridge.isSeatProtected(maid);
    }

    private static boolean alreadyHeading(
            EntityMaid maid,
            ApproachTarget target
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(target::matches)
                .isPresent();
    }

    private void release(EntityMaid maid, Progress held, String reason) {
        if (held == null) {
            return;
        }
        clearMovement(maid, held);
        if (held.claim() == null) {
            return;
        }
        held.claims().release(
                held.claim(),
                maid.level().getGameTime(),
                reason
        );
    }

    /**
     * Erases only our own walk target. Something else may have taken the wheel
     * since it was written, and clearing that would be a silent override.
     */
    private static void clearMovement(EntityMaid maid, Progress held) {
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (current == null) {
            return;
        }
        if (held.walkTarget() == null || current != held.walkTarget()) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    private record Progress(
            ApproachTarget target,
            WalkTarget walkTarget,
            CoordinationClaimToken claim,
            CoordinationClaimService claims
    ) {
        Progress withWalkTarget(WalkTarget written) {
            return new Progress(target, written, claim, claims);
        }

        Progress withTarget(ApproachTarget refreshed) {
            return new Progress(refreshed, walkTarget, claim, claims);
        }
    }
}
