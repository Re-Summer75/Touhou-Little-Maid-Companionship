package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource.MealTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Owns snack-cabinet movement and extraction side effects for one intent.
 */
@SuppressWarnings("null")
final class TlmSnackCabinetIntentAction {
    private static final int CLAIM_LEASE_TICKS = 100;
    private static final int COMMIT_LEASE_TICKS = 20;
    private final MaidSnackCabinetMealSource snackCabinetMeals;
    private final Map<EntityMaid, OwnedTarget> ownedTargets =
            new WeakHashMap<>();

    TlmSnackCabinetIntentAction(
            MaidSnackCabinetMealSource snackCabinetMeals
    ) {
        this.snackCabinetMeals = Objects.requireNonNull(
                snackCabinetMeals,
                "snackCabinetMeals"
        );
    }

    ActionResult execute(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        if (maid.isOrderedToSit()
                || maid.isMaidInSittingPose()
                || maid.isSleeping()
                || maid.isLeashed()
                || maid.isUsingItem()
                || maid.isBegging()
                || maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        )
                || maid.getBrain().isActive(Activity.PANIC)
                || MaidCommandSeatBridge.isSeatProtected(maid)) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        if (isPassiveTlmSeat(maid.getVehicle())) {
            maid.stopRiding();
        }
        if (maid.isPassenger()) {
            cancel(maid);
            return ActionResult.FAILED;
        }

        MealTarget target = snackCabinetMeals.findAvailableMealTarget(
                maid,
                gameTime
        ).orElse(null);
        if (target == null) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        OwnedTarget owned = claim(maid, target, gameTime);
        if (owned == null) {
            return ActionResult.FAILED;
        }

        int closeEnough = TlmActionParameters.integer(
                parameters,
                "close_distance",
                2,
                1,
                4
        );
        if (maid.distanceToSqr(
                target.position().getX() + 0.5D,
                target.position().getY() + 0.5D,
                target.position().getZ() + 0.5D
        ) <= (double) closeEnough * closeEnough) {
            clearMovement(maid, owned);
            boolean committed = owned.claims().occupy(
                    owned.claim(),
                    gameTime,
                    COMMIT_LEASE_TICKS
            ) && owned.claims().owns(owned.claim(), gameTime);
            boolean succeeded = committed
                    && snackCabinetMeals.tryTakeAndStartMeal(maid, target);
            finish(maid, owned, succeeded ? "consumed" : "commit_failed");
            return succeeded
                    ? ActionResult.SUCCEEDED
                    : ActionResult.FAILED;
        }
        if (!maid.canBrainMoving()) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        if (targets(maid, target.position())) {
            return ActionResult.RUNNING;
        }

        float speed = TlmActionParameters.number(
                parameters,
                "speed",
                0.55F,
                0.1F,
                2.0F
        );
        clearMovement(maid, owned);
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        BlockPosTracker tracker = new BlockPosTracker(target.position());
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
        ownedTargets.put(maid, owned.withWalkTarget(written));
        return ActionResult.RUNNING;
    }

    void cancel(EntityMaid maid) {
        OwnedTarget owned = ownedTargets.remove(maid);
        if (owned == null) {
            return;
        }
        clearMovement(maid, owned);
        owned.claims().release(
                owned.claim(),
                maid.level().getGameTime(),
                "cancelled"
        );
    }

    private static void clearMovement(
            EntityMaid maid,
            OwnedTarget owned
    ) {
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (current == null) {
            MovementCoordinationBridge.hardReset(maid);
            return;
        }
        if (owned.walkTarget() == null
                || current != owned.walkTarget()) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        MovementCoordinationBridge.hardReset(maid);
    }

    boolean revalidate(EntityMaid maid, long gameTime) {
        return !maid.isOrderedToSit()
                && !maid.isMaidInSittingPose()
                && !maid.isSleeping()
                && !maid.isLeashed()
                && !maid.isUsingItem()
                && !maid.isBegging()
                && !maid.isPassenger()
                && maid.canBrainMoving()
                && !maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        )
                && !maid.getBrain().isActive(Activity.PANIC)
                && !MaidCommandSeatBridge.isSeatProtected(maid)
                && snackCabinetMeals.findAvailableMealTarget(
                maid,
                gameTime
        ).isPresent();
    }

    private OwnedTarget claim(
            EntityMaid maid,
            MealTarget target,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return null;
        }
        OwnedTarget current = ownedTargets.get(maid);
        if (current != null
                && current.target().position().equals(target.position())
                && current.target().slot() == target.slot()) {
            if (current.claims().renew(
                    current.claim(),
                    gameTime,
                    CLAIM_LEASE_TICKS
            )) {
                OwnedTarget refreshed = current.withTarget(target);
                ownedTargets.put(maid, refreshed);
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
                        TlmCoordinationClaims.containerSlot(
                                level,
                                target.position(),
                                target.slot()
                        ),
                        maid.getUUID(),
                        TlmCoordinationClaims.operation(
                                maid,
                                "snack/" + target.position().asLong()
                                        + "/" + target.slot()
                        ),
                        CLAIM_LEASE_TICKS
                ),
                gameTime
        ).orElse(null);
        if (token == null) {
            return null;
        }
        OwnedTarget claimed = new OwnedTarget(
                target,
                null,
                token,
                claims
        );
        ownedTargets.put(maid, claimed);
        return claimed;
    }

    private void finish(
            EntityMaid maid,
            OwnedTarget owned,
            String reason
    ) {
        if (ownedTargets.get(maid) == owned) {
            ownedTargets.remove(maid);
        }
        clearMovement(maid, owned);
        owned.claims().release(
                owned.claim(),
                maid.level().getGameTime(),
                reason
        );
    }

    private boolean targets(EntityMaid maid, BlockPos target) {
        OwnedTarget owned = ownedTargets.get(maid);
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        return owned != null
                && owned.target().position().equals(target)
                && owned.walkTarget() != null
                && owned.walkTarget() == current;
    }

    private static boolean isPassiveTlmSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }



    private record OwnedTarget(
            MealTarget target,
            WalkTarget walkTarget,
            CoordinationClaimToken claim,
            CoordinationClaimService claims
    ) {
        private OwnedTarget withWalkTarget(WalkTarget target) {
            return new OwnedTarget(this.target, target, claim, claims);
        }

        private OwnedTarget withTarget(MealTarget target) {
            return new OwnedTarget(target, walkTarget, claim, claims);
        }
    }
}
