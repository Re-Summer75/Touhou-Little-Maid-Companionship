package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The sole adapter allowed to apply data-driven companion side effects.
 */
@SuppressWarnings("null")
public final class TlmMaidIntentActions
        implements IntentActionPort<EntityMaid> {
    private final Consumer<EntityMaid> hungerRequestAction;
    private final TlmSnackCabinetIntentAction snackCabinetAction;
    private final Map<EntityMaid, WalkTarget> ownedOwnerTargets =
            new WeakHashMap<>();

    public TlmMaidIntentActions(
            Consumer<EntityMaid> hungerRequestAction
    ) {
        this(
                hungerRequestAction,
                new MaidSnackCabinetMealSource(new MaidMealAccess())
        );
    }

    public TlmMaidIntentActions(
            Consumer<EntityMaid> hungerRequestAction,
            MaidSnackCabinetMealSource snackCabinetMeals
    ) {
        this.hungerRequestAction = Objects.requireNonNull(
                hungerRequestAction,
                "hungerRequestAction"
        );
        this.snackCabinetAction = new TlmSnackCabinetIntentAction(
                snackCabinetMeals
        );
    }

    @Override
    public ActionResult execute(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    ) {
        if (action.equals(CompanionIntentIds.APPROACH_OWNER)) {
            return approachOwner(maid, parameters);
        }
        if (action.equals(
                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
        )) {
            return snackCabinetAction.execute(
                    maid,
                    parameters,
                    gameTime
            );
        }
        if (action.equals(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW
        )) {
            return commandWindow(maid, parameters, elapsedTicks);
        }
        if (action.equals(
                CompanionIntentIds.REQUEST_HUNGER_ATTENTION
        )) {
            return requestHungerAttention(maid);
        }
        return ActionResult.FAILED;
    }

    @Override
    public void cancel(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters
    ) {
        if (action.equals(CompanionIntentIds.APPROACH_OWNER)) {
            clearOwnedOwnerTarget(maid);
        }
        if (action.equals(
                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
        )) {
            snackCabinetAction.cancel(maid);
        }
        if (action.equals(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW
        )) {
            clearOwnedOwnerTarget(maid);
            MaidCommandSeatBridge.endCommandWindow(maid);
        }
    }

    private ActionResult approachOwner(
            EntityMaid maid,
            Map<String, String> parameters
    ) {
        LivingEntity owner = validOwner(maid);
        if (owner == null
                || maid.isHomeModeEnable()
                || maid.isOrderedToSit()
                || maid.isMaidInSittingPose()
                || maid.isSleeping()
                || maid.isLeashed()
                || maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        )
                || maid.getBrain().isActive(Activity.PANIC)) {
            clearOwnedOwnerTarget(maid);
            return ActionResult.FAILED;
        }
        if (MaidCommandSeatBridge.isSeatProtected(maid)) {
            clearOwnedOwnerTarget(maid);
            return ActionResult.FAILED;
        }
        if (isPassiveTlmSeat(maid.getVehicle())) {
            maid.stopRiding();
        }
        if (!maid.canBrainMoving()) {
            clearOwnedOwnerTarget(maid);
            return ActionResult.FAILED;
        }

        int closeEnough = intParameter(
                parameters,
                "close_distance",
                2,
                1,
                16
        );
        float speed = floatParameter(
                parameters,
                "speed",
                0.55F,
                0.1F,
                2.0F
        );
        return followOwner(
                maid,
                owner,
                speed,
                closeEnough,
                true
        );
    }

    private ActionResult commandWindow(
            EntityMaid maid,
            Map<String, String> parameters,
            int elapsedTicks
    ) {
        LivingEntity owner = validOwner(maid);
        if (owner == null
                || maid.isHomeModeEnable()
                || maid.isOrderedToSit()
                || maid.isMaidInSittingPose()
                || maid.isSleeping()
                || maid.isLeashed()
                || maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        )
                || maid.getBrain().isActive(Activity.PANIC)) {
            clearOwnedOwnerTarget(maid);
            MaidCommandSeatBridge.endCommandWindow(maid);
            return ActionResult.FAILED;
        }

        boolean commandSeated =
                MaidCommandSeatBridge.mirrorOwnerSeat(maid, owner);
        if (!commandSeated && !maid.canBrainMoving()) {
            clearOwnedOwnerTarget(maid);
            MaidCommandSeatBridge.endCommandWindow(maid);
            return ActionResult.FAILED;
        }

        int durationTicks = intParameter(
                parameters,
                "duration_ticks",
                60,
                1,
                1200
        );
        if (elapsedTicks >= durationTicks) {
            clearOwnedOwnerTarget(maid);
            MaidCommandSeatBridge.endCommandWindow(maid);
            return ActionResult.SUCCEEDED;
        }

        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET,
                new EntityTracker(owner, true)
        );
        if (commandSeated) {
            clearOwnedOwnerTarget(maid);
            return ActionResult.RUNNING;
        }

        int closeEnough = intParameter(
                parameters,
                "close_distance",
                2,
                1,
                16
        );
        float speed = floatParameter(
                parameters,
                "speed",
                0.55F,
                0.1F,
                2.0F
        );
        return followOwner(
                maid,
                owner,
                speed,
                closeEnough,
                false
        );
    }

    private ActionResult followOwner(
            EntityMaid maid,
            LivingEntity owner,
            float speed,
            int closeEnough,
            boolean succeedWhenClose
    ) {
        if (maid.distanceToSqr(owner)
                <= (double) closeEnough * closeEnough) {
            clearOwnedOwnerTarget(maid);
            maid.getBrain().setMemory(
                    MemoryModuleType.LOOK_TARGET,
                    new EntityTracker(owner, true)
            );
            return succeedWhenClose
                    ? ActionResult.SUCCEEDED
                    : ActionResult.RUNNING;
        }
        if (targetsOwner(maid, owner)) {
            return ActionResult.RUNNING;
        }

        WalkTarget previous = MovementCoordinationBridge.capture(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
        BehaviorUtils.setWalkAndLookTargetMemories(
                maid,
                owner,
                speed,
                closeEnough
        );
        MovementCoordinationBridge.finishKnownWrite(
                maid,
                previous,
                MovementIntentSource.COMPANION,
                true
        );
        if (!targetsOwner(maid, owner)) {
            clearOwnedOwnerTarget(maid);
            return ActionResult.FAILED;
        }
        WalkTarget written = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (written != null) {
            ownedOwnerTargets.put(maid, written);
        }
        return ActionResult.RUNNING;
    }

    private ActionResult requestHungerAttention(EntityMaid maid) {
        LivingEntity owner = validOwner(maid);
        if (owner == null || maid.distanceToSqr(owner) > 64.0D) {
            return ActionResult.FAILED;
        }
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET,
                new EntityTracker(owner, true)
        );
        hungerRequestAction.accept(maid);
        return ActionResult.SUCCEEDED;
    }

    private void clearOwnedOwnerTarget(EntityMaid maid) {
        WalkTarget owned = ownedOwnerTargets.remove(maid);
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
        if (current != owned) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        MovementCoordinationBridge.hardReset(maid);
    }

    private static boolean targetsOwner(
            EntityMaid maid,
            LivingEntity owner
    ) {
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        return current != null
                && current.getTarget() instanceof EntityTracker tracker
                && tracker.getEntity() == owner;
    }

    private static LivingEntity validOwner(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || !maid.isTame()
                || !owner.isAlive()
                || owner.isSpectator()
                || owner.level() != maid.level()) {
            return null;
        }
        return owner;
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

}
