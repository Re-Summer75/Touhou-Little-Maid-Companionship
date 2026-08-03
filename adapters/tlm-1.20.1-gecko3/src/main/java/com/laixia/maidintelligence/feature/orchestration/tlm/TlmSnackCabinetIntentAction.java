package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.core.BlockPos;
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

        BlockPos target = snackCabinetMeals.findAvailableMeal(
                maid,
                gameTime
        ).orElse(null);
        if (target == null) {
            cancel(maid);
            return ActionResult.FAILED;
        }

        int closeEnough = intParameter(
                parameters,
                "close_distance",
                2,
                1,
                4
        );
        if (maid.distanceToSqr(
                target.getX() + 0.5D,
                target.getY() + 0.5D,
                target.getZ() + 0.5D
        ) <= (double) closeEnough * closeEnough) {
            cancel(maid);
            return snackCabinetMeals.tryTakeAndStartMeal(maid, target)
                    ? ActionResult.SUCCEEDED
                    : ActionResult.FAILED;
        }
        if (!maid.canBrainMoving()) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        if (targets(maid, target)) {
            return ActionResult.RUNNING;
        }

        float speed = floatParameter(
                parameters,
                "speed",
                0.55F,
                0.1F,
                2.0F
        );
        cancel(maid);
        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        BlockPosTracker tracker = new BlockPosTracker(target);
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
            return ActionResult.FAILED;
        }
        ownedTargets.put(maid, new OwnedTarget(target, written));
        return ActionResult.RUNNING;
    }

    void cancel(EntityMaid maid) {
        OwnedTarget owned = ownedTargets.remove(maid);
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (owned == null) {
            return;
        }
        if (current == null) {
            MovementCoordinationBridge.hardReset(maid);
            return;
        }
        if (current != owned.walkTarget()) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        MovementCoordinationBridge.hardReset(maid);
    }

    private boolean targets(EntityMaid maid, BlockPos target) {
        OwnedTarget owned = ownedTargets.get(maid);
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        return owned != null
                && owned.position().equals(target)
                && owned.walkTarget() == current;
    }

    private static boolean isPassiveTlmSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }

    private static int intParameter(
            Map<String, String> parameters,
            String name,
            int fallback,
            int minimum,
            int maximum
    ) {
        try {
            int value = Integer.parseInt(parameters.getOrDefault(
                    name,
                    Integer.toString(fallback)
            ));
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float floatParameter(
            Map<String, String> parameters,
            String name,
            float fallback,
            float minimum,
            float maximum
    ) {
        try {
            float value = Float.parseFloat(parameters.getOrDefault(
                    name,
                    Float.toString(fallback)
            ));
            if (!Float.isFinite(value)) {
                return fallback;
            }
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record OwnedTarget(
            BlockPos position,
            WalkTarget walkTarget
    ) {
    }
}
