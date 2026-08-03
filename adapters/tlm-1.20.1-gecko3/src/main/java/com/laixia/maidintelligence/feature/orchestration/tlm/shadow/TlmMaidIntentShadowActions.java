package com.laixia.maidintelligence.feature.orchestration.tlm.shadow;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.tlm.TlmBehaviorOccupancyClassifier;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.Map;
import java.util.Objects;

/**
 * Predicts adapter outcomes without writing Brain, entities, inventories or
 * shared resources.
 */
@SuppressWarnings("null")
public final class TlmMaidIntentShadowActions
        implements IntentActionPort<EntityMaid> {
    private final MaidSnackCabinetMealSource snackCabinetMeals;

    public TlmMaidIntentShadowActions(
            MaidSnackCabinetMealSource snackCabinetMeals
    ) {
        this.snackCabinetMeals = Objects.requireNonNull(
                snackCabinetMeals,
                "snackCabinetMeals"
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
            return approachOwner(maid, parameters, gameTime);
        }
        if (action.equals(
                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
        )) {
            return fetchSnackCabinetMeal(maid, parameters, gameTime);
        }
        if (action.equals(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW
        )) {
            return commandWindow(maid, parameters, gameTime, elapsedTicks);
        }
        if (action.equals(
                CompanionIntentIds.REQUEST_HUNGER_ATTENTION
        )) {
            LivingEntity owner = validOwner(maid);
            return owner != null && maid.distanceToSqr(owner) <= 64.0D
                    ? ActionResult.SUCCEEDED
                    : ActionResult.FAILED;
        }
        if (action.equals(CompanionIntentIds.DEPLOY_BOAT)) {
            return canDeployBoat(maid)
                    ? ActionResult.SUCCEEDED
                    : ActionResult.FAILED;
        }
        return ActionResult.FAILED;
    }

    @Override
    public void cancel(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters
    ) {
        // Shadow cancellation intentionally owns no external state.
    }

    private static ActionResult approachOwner(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        LivingEntity owner = validOwner(maid);
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        boolean ownerCommand = ownerCommand(parameters);
        if (owner == null
                || maid.isHomeModeEnable()
                || !allowed(occupancy, ownerCommand)) {
            return ActionResult.FAILED;
        }
        boolean movable = maid.canBrainMoving()
                || (ownerCommand
                && occupancy.level() == BehaviorOccupancyLevel.SOFT
                && TlmBehaviorOccupancyClassifier.isPassiveSeat(
                maid.getVehicle()
        ));
        if (!movable) {
            return ActionResult.FAILED;
        }
        int closeEnough = intParameter(
                parameters,
                "close_distance",
                2,
                1,
                16
        );
        return maid.distanceToSqr(owner)
                <= (double) closeEnough * closeEnough
                ? ActionResult.SUCCEEDED
                : ActionResult.RUNNING;
    }

    private ActionResult fetchSnackCabinetMeal(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        BlockPos target = snackCabinetMeals
                .findAvailableMeal(maid, gameTime)
                .orElse(null);
        if (target == null
                || !maid.canBrainMoving()
                || !occupancy.allowsPassiveCompanion()) {
            return ActionResult.FAILED;
        }
        int closeEnough = intParameter(
                parameters,
                "close_distance",
                2,
                1,
                8
        );
        double targetX = target.getX() + 0.5D;
        double targetY = target.getY() + 0.5D;
        double targetZ = target.getZ() + 0.5D;
        return maid.distanceToSqr(targetX, targetY, targetZ)
                <= (double) closeEnough * closeEnough
                ? ActionResult.SUCCEEDED
                : ActionResult.RUNNING;
    }

    private static ActionResult commandWindow(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    ) {
        LivingEntity owner = validOwner(maid);
        BehaviorOccupancySnapshot occupancy =
                TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime);
        if (owner == null
                || maid.isHomeModeEnable()
                || !occupancy.allowsOwnerCommand()) {
            return ActionResult.FAILED;
        }
        int durationTicks = intParameter(
                parameters,
                "duration_ticks",
                60,
                1,
                1200
        );
        return elapsedTicks >= durationTicks
                ? ActionResult.SUCCEEDED
                : ActionResult.RUNNING;
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
        return "owner_command".equals(parameters.get("authority"));
    }

    private static boolean canDeployBoat(EntityMaid maid) {
        if (maid.isPassenger()) {
            return false;
        }
        if (!maid.level().getEntitiesOfClass(
                Boat.class,
                maid.getBoundingBox().inflate(8.0D),
                boat -> boat.isAlive() && boat.getPassengers().isEmpty()
        ).isEmpty()) {
            return true;
        }
        var inventory = maid.getAvailableInv(false);
        boolean hasBoat = false;
        for (int slot = 0; slot < inventory.getSlots() && !hasBoat; slot++) {
            String itemPath = BuiltInRegistries.ITEM
                    .getKey(inventory.getStackInSlot(slot).getItem())
                    .getPath();
            for (Boat.Type variant : Boat.Type.values()) {
                String material = variant.name()
                        .toLowerCase(java.util.Locale.ROOT);
                if (itemPath.equals(material + "_boat")
                        || itemPath.equals(material + "_raft")) {
                    hasBoat = true;
                    break;
                }
            }
        }
        if (!hasBoat) {
            return false;
        }
        BlockPos origin = maid.blockPosition();
        for (BlockPos position : BlockPos.betweenClosed(
                origin.offset(-4, -2, -4),
                origin.offset(4, 1, 4)
        )) {
            if (maid.level().hasChunkAt(position)
                    && maid.level().getFluidState(position)
                    .is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
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
}
