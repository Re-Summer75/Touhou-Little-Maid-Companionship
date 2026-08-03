package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyReason;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancySnapshot;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Encodes live TLM state into one native-behavior occupancy snapshot.
 */
@SuppressWarnings("null")
public final class TlmBehaviorOccupancyClassifier {
    private TlmBehaviorOccupancyClassifier() {
    }

    public static BehaviorOccupancySnapshot snapshot(
            EntityMaid maid,
            long gameTime
    ) {
        MovementCoordinationBridge.reconcile(maid, gameTime);
        boolean ownerCommand =
                NativeBehaviorArbitrationBridge.ownerCommandActive(
                        maid,
                        gameTime
                );
        Entity vehicle = maid.getVehicle();
        boolean passiveSeat = isPassiveSeat(vehicle);
        boolean commandSeat = MaidCommandSeatBridge.isSeatProtected(maid);
        MovementIntentLease lease = movementLease(maid);
        boolean failOpen = lease != null && lease.isFailOpen(gameTime);
        MovementIntentSource source = lease != null
                && lease.hasActiveLease(gameTime)
                ? lease.holder()
                : null;

        if (maid.getSwimManager().isGoingToBreath()) {
            return occupied(BehaviorOccupancyReason.BREATH_AIR,
                    source, failOpen, ownerCommand);
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return occupied(BehaviorOccupancyReason.COMBAT,
                    source, failOpen, ownerCommand);
        }
        if (maid.getBrain().isActive(Activity.PANIC)) {
            return occupied(BehaviorOccupancyReason.PANIC,
                    source, failOpen, ownerCommand);
        }
        if (maid.isHomeModeEnable()) {
            return occupied(BehaviorOccupancyReason.HOME_RETURN,
                    source, failOpen, ownerCommand);
        }
        if (maid.isSleeping()) {
            return occupied(BehaviorOccupancyReason.SLEEPING,
                    source, failOpen, ownerCommand);
        }
        if (maid.isLeashed()) {
            return occupied(BehaviorOccupancyReason.LEASHED,
                    source, failOpen, ownerCommand);
        }
        if (maid.isOrderedToSit()) {
            return occupied(BehaviorOccupancyReason.ORDERED_SIT,
                    source, failOpen, ownerCommand);
        }
        if (maid.isMaidInSittingPose() && !passiveSeat) {
            return occupied(BehaviorOccupancyReason.SITTING_POSE,
                    source, failOpen, ownerCommand);
        }
        if (maid.isUsingItem()) {
            return occupied(BehaviorOccupancyReason.USING_ITEM,
                    source, failOpen, ownerCommand);
        }
        if (failOpen) {
            return occupied(BehaviorOccupancyReason.UNKNOWN_WRITER,
                    source, true, ownerCommand);
        }
        BehaviorOccupancyReason movementReason = reason(source);
        if (movementReason != null
                && movementReason.level() == BehaviorOccupancyLevel.HARD) {
            return occupied(movementReason, source, false, ownerCommand);
        }

        boolean workTarget = maid.getBrain().hasMemoryValue(
                InitEntities.TARGET_POS.get()
        );
        if (workTarget && maid.getBrain().isActive(Activity.WORK)) {
            return occupied(BehaviorOccupancyReason.WORK_TARGET,
                    source, false, ownerCommand);
        }
        if (isSeatedWork(maid)) {
            return occupied(BehaviorOccupancyReason.BUILT_IN_WORK,
                    source, false, ownerCommand);
        }
        if (commandSeat && !ownerCommand) {
            return occupied(BehaviorOccupancyReason.COMMAND_SEAT,
                    source, false, false);
        }
        if (commandSeat) {
            return BehaviorOccupancySnapshot.idle(true);
        }

        if (vehicle != null && !passiveSeat) {
            return occupied(BehaviorOccupancyReason.OTHER_PASSENGER,
                    source, false, ownerCommand);
        }
        if (workTarget) {
            return occupied(BehaviorOccupancyReason.LEISURE,
                    source, false, ownerCommand);
        }
        if (vehicle != null) {
            BehaviorOccupancyReason seatReason =
                    vehicle instanceof EntitySit
                            ? BehaviorOccupancyReason.LEISURE
                            : BehaviorOccupancyReason.PASSIVE_SEAT;
            return occupied(seatReason, source, false, ownerCommand);
        }
        if (movementReason != null) {
            return occupied(movementReason, source, false, ownerCommand);
        }
        if (source == MovementIntentSource.OWNER_COMMAND
                || source == MovementIntentSource.COMPANION) {
            return new BehaviorOccupancySnapshot(
                    BehaviorOccupancyLevel.IDLE,
                    BehaviorOccupancyReason.IDLE,
                    source,
                    false,
                    ownerCommand
            );
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return occupied(BehaviorOccupancyReason.UNKNOWN_WRITER,
                    null, true, ownerCommand);
        }
        return BehaviorOccupancySnapshot.idle(ownerCommand);
    }

    public static boolean isSeatedWork(EntityMaid maid) {
        if (maid.getScheduleDetail() != Activity.WORK) {
            return false;
        }
        Entity vehicle = maid.getVehicle();
        if (vehicle instanceof EntitySit seat) {
            return maid.getTask().canSitInJoy(maid, seat.getJoyType());
        }
        return vehicle != null
                && vehicle.getType() == EntityChair.TYPE
                && maid.getTask().workPointTask(maid);
    }

    public static boolean isPassiveSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }

    private static BehaviorOccupancySnapshot occupied(
            BehaviorOccupancyReason reason,
            MovementIntentSource source,
            boolean failOpen,
            boolean ownerCommand
    ) {
        return new BehaviorOccupancySnapshot(
                reason.level(),
                reason,
                source,
                failOpen,
                ownerCommand
        );
    }

    private static BehaviorOccupancyReason reason(
            MovementIntentSource source
    ) {
        if (source == null
                || source == MovementIntentSource.OWNER_COMMAND
                || source == MovementIntentSource.COMPANION) {
            return null;
        }
        return switch (source) {
            case BREATH_AIR -> BehaviorOccupancyReason.BREATH_AIR;
            case COMBAT -> BehaviorOccupancyReason.COMBAT;
            case HOME_RETURN -> BehaviorOccupancyReason.HOME_RETURN;
            case BUILT_IN_WORK -> BehaviorOccupancyReason.BUILT_IN_WORK;
            case STEAL_EDIBLE -> BehaviorOccupancyReason.STEAL_EDIBLE;
            case PICKUP -> BehaviorOccupancyReason.PICKUP;
            case FOLLOW_OWNER -> BehaviorOccupancyReason.FOLLOW_OWNER;
            case FOLLOW_OWNER_VEHICLE ->
                    BehaviorOccupancyReason.FOLLOW_OWNER_VEHICLE;
            case LEISURE -> BehaviorOccupancyReason.LEISURE;
            case BEG -> BehaviorOccupancyReason.BEG;
            case RANDOM_STROLL -> BehaviorOccupancyReason.RANDOM_STROLL;
            case OWNER_COMMAND, COMPANION -> null;
        };
    }

    private static MovementIntentLease movementLease(EntityMaid maid) {
        if (!(maid instanceof MovementCoordinationAccess access)) {
            return null;
        }
        return access.maidIntelligence$movementIntentLease();
    }
}
