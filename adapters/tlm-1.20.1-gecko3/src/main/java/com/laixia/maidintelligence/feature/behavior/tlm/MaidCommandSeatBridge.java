package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.mixin.accessor.EntityAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks seats selected during the short gaze-command window.
 */
@SuppressWarnings("null")
public final class MaidCommandSeatBridge {
    private static final String LOCKED_TAG =
            "tlm_companionship_command_seat_locked";
    private static final String VEHICLE_TAG =
            "tlm_companionship_command_seat_vehicle";
    private static final double SEAT_SEARCH_RANGE = 3.0D;

    private static final Map<EntityMaid, Session> SESSIONS =
            new WeakHashMap<>();

    private MaidCommandSeatBridge() {
    }

    public static boolean mirrorOwnerSeat(
            EntityMaid maid,
            LivingEntity owner
    ) {
        if (maid.level().isClientSide()) {
            return false;
        }
        Session session = SESSIONS.computeIfAbsent(
                maid,
                ignored -> new Session(owner)
        );
        if (session.owner != owner) {
            session = new Session(owner);
            SESSIONS.put(maid, session);
        }

        Entity currentOwnerVehicle = owner.getVehicle();
        Entity previousOwnerVehicle = session.ownerVehicle;
        session.ownerVehicle = currentOwnerVehicle;
        if (session.seatingDisabled) {
            return false;
        }
        if (maid.getVehicle() == session.commandSeat
                && session.commandSeat != null) {
            lockSeat(maid, session.commandSeat);
            return true;
        }
        if (maid.isPassenger()) {
            return false;
        }
        if (!maid.isRideable()) {
            return false;
        }

        Entity candidate = null;
        if (previousOwnerVehicle != null
                && previousOwnerVehicle != currentOwnerVehicle
                && available(previousOwnerVehicle, maid)) {
            candidate = previousOwnerVehicle;
        } else if (currentOwnerVehicle != null) {
            if (available(currentOwnerVehicle, maid)) {
                candidate = currentOwnerVehicle;
            } else if (passiveSeat(currentOwnerVehicle) != null) {
                candidate = nearestEmptySeat(
                        owner,
                        currentOwnerVehicle,
                        maid
                );
            }
        }
        if (candidate == null
                || !maid.closerThan(candidate, SEAT_SEARCH_RANGE)
                || !mount(maid, candidate)) {
            return false;
        }

        session.commandSeat = candidate;
        lockSeat(maid, candidate);
        return true;
    }

    public static void endCommandWindow(EntityMaid maid) {
        SESSIONS.remove(maid);
    }

    public static void releaseByOwner(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }
        Session session = SESSIONS.get(maid);
        if (session != null) {
            session.seatingDisabled = true;
            session.commandSeat = null;
        }
        clearLock(maid);
    }

    public static void releaseForDanger(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }
        SESSIONS.remove(maid);
        clearLock(maid);
    }

    public static boolean isSeatProtected(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return false;
        }
        CompoundTag data = maid.getPersistentData();
        if (!data.getBoolean(LOCKED_TAG)) {
            return false;
        }
        Entity vehicle = maid.getVehicle();
        if (vehicle == null
                || !data.hasUUID(VEHICLE_TAG)
                || !data.getUUID(VEHICLE_TAG).equals(vehicle.getUUID())) {
            clearLock(maid);
            return false;
        }
        return true;
    }

    private static Entity nearestEmptySeat(
            LivingEntity owner,
            Entity occupiedSeat,
            EntityMaid maid
    ) {
        AABB bounds = owner.getBoundingBox().inflate(
                SEAT_SEARCH_RANGE,
                2.0D,
                SEAT_SEARCH_RANGE
        );
        List<Entity> seats = new ArrayList<>();
        seats.addAll(owner.level().getEntitiesOfClass(
                EntityChair.class,
                bounds,
                seat -> seat != occupiedSeat && available(seat, maid)
        ));
        seats.addAll(owner.level().getEntitiesOfClass(
                EntitySit.class,
                bounds,
                seat -> seat != occupiedSeat && available(seat, maid)
        ));
        return seats.stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    private static boolean available(Entity vehicle, EntityMaid maid) {
        if (!vehicle.isAlive()
                || vehicle == maid
                || (vehicle instanceof EntityChair chair
                && !chair.isTameableCanRide())) {
            return false;
        }
        return ((EntityAccessor) vehicle).tlmCanAddPassenger(maid);
    }

    private static boolean mount(EntityMaid maid, Entity vehicle) {
        if (vehicle instanceof EntitySit) {
            return maid.startRiding(vehicle, true);
        }
        return maid.startRiding(vehicle);
    }

    private static Entity passiveSeat(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getType() == EntityChair.TYPE
                || entity.getType() == EntitySit.TYPE
                ? entity
                : null;
    }

    private static void lockSeat(EntityMaid maid, Entity vehicle) {
        CompoundTag data = maid.getPersistentData();
        data.putBoolean(LOCKED_TAG, true);
        data.putUUID(VEHICLE_TAG, vehicle.getUUID());
    }

    private static void clearLock(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(LOCKED_TAG);
        data.remove(VEHICLE_TAG);
    }

    private static final class Session {
        private final LivingEntity owner;
        private Entity ownerVehicle;
        private Entity commandSeat;
        private boolean seatingDisabled;

        private Session(LivingEntity owner) {
            this.owner = owner;
        }
    }
}
