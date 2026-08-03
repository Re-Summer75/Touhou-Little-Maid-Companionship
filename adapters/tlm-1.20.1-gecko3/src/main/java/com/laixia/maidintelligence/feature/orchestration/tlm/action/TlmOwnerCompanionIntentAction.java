package com.laixia.maidintelligence.feature.orchestration.tlm.action;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentDecision;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.ai.tlm.MovementCoordinationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.NativeBehaviorArbitrationBridge;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Owns owner-facing movement, command-window and hunger-request effects.
 */
@SuppressWarnings("null")
public final class TlmOwnerCompanionIntentAction {
    private static final String AUTHORITY_PARAMETER = "authority";
    private static final String OWNER_COMMAND_AUTHORITY = "owner_command";
    private static final int OWNER_COMMAND_RENEW_TICKS = 20;

    private final Consumer<EntityMaid> hungerRequestAction;
    private final Map<EntityMaid, WalkTarget> ownedOwnerTargets =
            new WeakHashMap<>();

    public TlmOwnerCompanionIntentAction(
            Consumer<EntityMaid> hungerRequestAction
    ) {
        this.hungerRequestAction = Objects.requireNonNull(
                hungerRequestAction,
                "hungerRequestAction"
        );
    }

    public ActionResult approach(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        boolean ownerCommand = ownerCommand(parameters);
        LivingEntity owner = validOwner(maid);
        if (ownerCommand && owner != null) {
            renewOwnedOwnerTarget(maid, owner, gameTime);
        }
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        if (owner == null
                || maid.isHomeModeEnable()
                || !allowed(occupancy, ownerCommand)
                || (!ownerCommand
                && MaidCommandSeatBridge.isSeatProtected(maid))) {
            failMovement(maid, ownerCommand);
            return ActionResult.FAILED;
        }
        if (ownerCommand) {
            NativeBehaviorArbitrationBridge.acquireOwnerCommand(
                    maid,
                    gameTime,
                    OWNER_COMMAND_RENEW_TICKS
            );
            NativeBehaviorArbitrationBridge.quiesceSoftBehavior(
                    maid,
                    occupancy
            );
        }
        if (!maid.canBrainMoving()) {
            failMovement(maid, ownerCommand);
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
                true,
                ownerCommand
                        ? MovementIntentSource.OWNER_COMMAND
                        : MovementIntentSource.COMPANION,
                gameTime
        );
    }

    public ActionResult commandWindow(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    ) {
        LivingEntity owner = validOwner(maid);
        if (owner == null || maid.isHomeModeEnable()) {
            cancelCommandWindow(maid);
            return ActionResult.FAILED;
        }
        // An active command action renews before classification so its own
        // expiring target or command seat is never mistaken for an addon.
        NativeBehaviorArbitrationBridge.acquireOwnerCommand(
                maid,
                gameTime,
                OWNER_COMMAND_RENEW_TICKS
        );
        renewOwnedOwnerTarget(maid, owner, gameTime);
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        if (!occupancy.allowsOwnerCommand()) {
            cancelCommandWindow(maid);
            return ActionResult.FAILED;
        }
        NativeBehaviorArbitrationBridge.quiesceSoftBehavior(maid, occupancy);

        boolean commandSeated =
                MaidCommandSeatBridge.mirrorOwnerSeat(maid, owner);
        if (!commandSeated && !maid.canBrainMoving()) {
            cancelCommandWindow(maid);
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
            cancelCommandWindow(maid);
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
                false,
                MovementIntentSource.OWNER_COMMAND,
                gameTime
        );
    }

    public ActionResult requestHungerAttention(EntityMaid maid) {
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

    public void cancelApproach(
            EntityMaid maid,
            Map<String, String> parameters
    ) {
        clearOwnedOwnerTarget(maid);
        if (ownerCommand(parameters)) {
            NativeBehaviorArbitrationBridge.releaseOwnerCommand(maid);
        }
    }

    public void cancelCommandWindow(EntityMaid maid) {
        clearOwnedOwnerTarget(maid);
        MaidCommandSeatBridge.endCommandWindow(maid);
        NativeBehaviorArbitrationBridge.releaseOwnerCommand(maid);
    }

    public boolean revalidateMovement(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        if (validOwner(maid) == null || maid.isHomeModeEnable()) {
            return false;
        }
        boolean ownerCommand = ownerCommand(parameters);
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        return allowed(occupancy, ownerCommand)
                && (maid.canBrainMoving()
                || (ownerCommand
                && occupancy.level() == BehaviorOccupancyLevel.SOFT));
    }

    public boolean revalidateHungerRequest(EntityMaid maid) {
        LivingEntity owner = validOwner(maid);
        return owner != null && maid.distanceToSqr(owner) <= 64.0D;
    }

    private ActionResult followOwner(
            EntityMaid maid,
            LivingEntity owner,
            float speed,
            int closeEnough,
            boolean succeedWhenClose,
            MovementIntentSource source,
            long gameTime
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
            MovementIntentDecision renewal =
                    MovementCoordinationBridge.renewKnownWrite(
                            maid,
                            source,
                            gameTime
                    );
            return renewal.writeAllowed()
                    ? ActionResult.RUNNING
                    : ActionResult.FAILED;
        }

        WalkTarget previous = MovementCoordinationBridge.capture(
                maid,
                gameTime
        );
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
                source,
                true,
                gameTime
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

    private void renewOwnedOwnerTarget(
            EntityMaid maid,
            LivingEntity owner,
            long gameTime
    ) {
        WalkTarget owned = ownedOwnerTargets.get(maid);
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (owned != null
                && current == owned
                && targetsOwner(maid, owner)) {
            NativeBehaviorArbitrationBridge.acquireOwnerCommand(
                    maid,
                    gameTime,
                    OWNER_COMMAND_RENEW_TICKS
            );
            MovementCoordinationBridge.renewKnownWrite(
                    maid,
                    MovementIntentSource.OWNER_COMMAND,
                    gameTime
            );
        }
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

    private void failMovement(
            EntityMaid maid,
            boolean ownerCommand
    ) {
        clearOwnedOwnerTarget(maid);
        if (ownerCommand) {
            NativeBehaviorArbitrationBridge.releaseOwnerCommand(maid);
        }
    }

    private static boolean allowed(
            BehaviorOccupancySnapshot occupancy,
            boolean ownerCommand
    ) {
        return ownerCommand
                ? occupancy.allowsOwnerCommand()
                : occupancy.allowsPassiveCompanion();
    }

    private static boolean ownerCommand(Map<String, String> parameters) {
        return OWNER_COMMAND_AUTHORITY.equals(
                parameters.get(AUTHORITY_PARAMETER)
        );
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
