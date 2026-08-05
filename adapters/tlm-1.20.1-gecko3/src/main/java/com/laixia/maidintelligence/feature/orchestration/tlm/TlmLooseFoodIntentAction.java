package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Walks to a dropped item and eats it.
 *
 * <p>The counterpart to fetching from a cabinet, and deliberately a separate
 * action rather than a branch inside it: what is claimed is the item itself,
 * not a slot inside something, and the two race differently when several maids
 * are hungry at once.
 *
 * <p>This is also what makes throwing food at a maid mean something. Nothing
 * here knows about being thrown — the item is simply the nearest good thing to
 * eat, and it wins on the same ranking a cabinet is scored by.
 */
@SuppressWarnings("null")
final class TlmLooseFoodIntentAction {
    private static final int CLAIM_LEASE_TICKS = 100;
    private static final int COMMIT_LEASE_TICKS = 20;
    private static final int CANDIDATES = 4;

    private final TlmAffordancePerceptionService perception;
    private final MaidMealAccess mealAccess;
    private final Map<EntityMaid, Owned> owned = new WeakHashMap<>();

    TlmLooseFoodIntentAction(
            TlmAffordancePerceptionService perception,
            MaidMealAccess mealAccess
    ) {
        this.perception = Objects.requireNonNull(perception, "perception");
        this.mealAccess = Objects.requireNonNull(mealAccess, "mealAccess");
    }

    ActionResult execute(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        if (!eligible(maid)) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        ItemEntity target = bestTarget(maid, gameTime);
        if (target == null) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        Owned claim = claim(maid, target, gameTime);
        if (claim == null) {
            // Another maid holds it. Failing rather than waiting lets the
            // engine re-rank, which may well send her to a cabinet instead.
            return ActionResult.FAILED;
        }

        int closeEnough = TlmActionParameters.integer(
                parameters,
                "close_distance",
                2,
                1,
                4
        );
        if (maid.distanceToSqr(target) <= (double) closeEnough * closeEnough) {
            clearMovement(maid, claim);
            boolean committed = claim.claims().occupy(
                    claim.claim(),
                    gameTime,
                    COMMIT_LEASE_TICKS
            ) && claim.claims().owns(claim.claim(), gameTime);
            boolean eaten = committed && consume(maid, target);
            finish(maid, claim, eaten ? "eaten" : "commit_failed");
            return eaten ? ActionResult.SUCCEEDED : ActionResult.FAILED;
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
        return walk(maid, claim, target, speed, closeEnough);
    }

    /**
     * Takes one item and starts the meal, putting it back if the meal will not
     * start. The check runs before the item leaves the world so the usual
     * outcome needs no restoring at all.
     */
    private boolean consume(EntityMaid maid, ItemEntity target) {
        ItemStack stack = target.getItem();
        if (stack.isEmpty()
                || maid.isUsingItem()
                || !maid.getTask().enableEating(maid)
                || !maid.getHideInv().getStackInSlot(0).isEmpty()
                || mealAccess.hasLocalHungerMeal(maid)
                || !mealAccess.canStartExternalHungerMeal(maid, stack)) {
            return false;
        }
        ItemStack meal = stack.copy();
        meal.setCount(1);
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        if (remainder.isEmpty()) {
            target.discard();
        } else {
            target.setItem(remainder);
        }
        if (mealAccess.tryStartExternalHungerMeal(maid, meal)) {
            return true;
        }
        restore(maid, target, meal, remainder);
        return false;
    }

    private static void restore(
            EntityMaid maid,
            ItemEntity target,
            ItemStack meal,
            ItemStack remainder
    ) {
        if (!remainder.isEmpty() && target.isAlive()) {
            ItemStack merged = remainder.copy();
            merged.grow(1);
            target.setItem(merged);
            return;
        }
        // The entity is gone, so the item comes back as a fresh drop rather
        // than vanishing because a meal declined to start.
        ItemEntity replacement = new ItemEntity(
                maid.level(),
                target.getX(),
                target.getY(),
                target.getZ(),
                meal
        );
        replacement.setNoPickUpDelay();
        maid.level().addFreshEntity(replacement);
    }

    private ActionResult walk(
            EntityMaid maid,
            Owned claim,
            ItemEntity target,
            float speed,
            int closeEnough
    ) {
        if (targets(maid, target)) {
            return ActionResult.RUNNING;
        }
        clearMovement(maid, claim);
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        EntityTracker tracker = new EntityTracker(target, false);
        WalkTarget written = new WalkTarget(tracker, speed, closeEnough);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, written);
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                previous,
                MovementIntentSource.COMPANION,
                true
        );
        if (maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null) != written) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        owned.put(maid, claim.withWalkTarget(written));
        return ActionResult.RUNNING;
    }

    /**
     * Whether the step may keep running. An item can be picked up by anyone at
     * any moment, so the target being gone is the ordinary case here rather
     * than an error.
     */
    boolean revalidate(EntityMaid maid, long gameTime) {
        Owned current = owned.get(maid);
        if (current == null) {
            return false;
        }
        if (!eligible(maid)
                || !current.target().isAlive()
                || current.target().getItem().isEmpty()
                || !current.claims().owns(current.claim(), gameTime)) {
            cancel(maid);
            return false;
        }
        return true;
    }

    void cancel(EntityMaid maid) {
        Owned held = owned.remove(maid);
        if (held == null) {
            return;
        }
        clearMovement(maid, held);
        held.claims().release(
                held.claim(),
                maid.level().getGameTime(),
                "cancelled"
        );
    }

    private void finish(EntityMaid maid, Owned held, String reason) {
        if (owned.get(maid) == held) {
            owned.remove(maid);
        }
        clearMovement(maid, held);
        held.claims().release(
                held.claim(),
                maid.level().getGameTime(),
                reason
        );
    }

    private ItemEntity bestTarget(EntityMaid maid, long gameTime) {
        List<ItemEntity> candidates = perception.queryLooseFood(
                maid,
                CANDIDATES,
                gameTime
        );
        for (ItemEntity candidate : candidates) {
            if (candidate.isAlive()
                    && !candidate.hasPickUpDelay()
                    && mealAccess.canStartExternalHungerMeal(
                            maid,
                            candidate.getItem()
                    )) {
                return candidate;
            }
        }
        return null;
    }

    private Owned claim(
            EntityMaid maid,
            ItemEntity target,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return null;
        }
        Owned current = owned.get(maid);
        if (current != null && current.target() == target) {
            if (current.claims().renew(
                    current.claim(),
                    gameTime,
                    CLAIM_LEASE_TICKS
            )) {
                return current;
            }
            cancel(maid);
        } else if (current != null) {
            cancel(maid);
        }

        CoordinationClaimService claims =
                TlmCoordinationClaims.service(level);
        CoordinationClaimToken token = claims.tryClaim(
                new CoordinationClaimRequest(
                        TlmCoordinationClaims.itemEntity(level, target),
                        maid.getUUID(),
                        TlmCoordinationClaims.operation(
                                maid,
                                "loose_food/" + target.getUUID()
                        ),
                        CLAIM_LEASE_TICKS
                ),
                gameTime
        ).orElse(null);
        if (token == null) {
            return null;
        }
        Owned claimed = new Owned(target, null, token, claims);
        owned.put(maid, claimed);
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

    private static boolean targets(EntityMaid maid, ItemEntity target) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(target::equals)
                .isPresent();
    }

    private static void clearMovement(EntityMaid maid, Owned held) {
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (current == null) {
            MovementCoordinationBridge.hardReset(maid);
            return;
        }
        // Only ours is erased: something else may have taken the wheel since,
        // and clearing its target would be a silent override.
        if (held.walkTarget() == null || current != held.walkTarget()) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        MovementCoordinationBridge.hardReset(maid);
    }

    private record Owned(
            ItemEntity target,
            WalkTarget walkTarget,
            CoordinationClaimToken claim,
            CoordinationClaimService claims
    ) {
        Owned withWalkTarget(WalkTarget written) {
            return new Owned(target, written, claim, claims);
        }
    }
}
