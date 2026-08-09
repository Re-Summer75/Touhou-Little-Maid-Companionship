package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomOccupancy;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerFollowPolicy;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Lets built-in movement needs release TLM passive seats and ordinary boats.
 * Commanded sitting and unrelated vehicle integrations remain untouched.
 */
@SuppressWarnings("null")
public final class MaidSeatAutonomyBridge {

    private MaidSeatAutonomyBridge() {
    }

    public static void leaveOrdinaryBoatWhenOwnerLeaves(EntityMaid maid) {
        if (!canLeaveSeatState(maid)) {
            return;
        }
        Entity vehicle = maid.getVehicle();
        if (!isOrdinaryBoat(vehicle)) {
            return;
        }
        boolean commandProtected =
                MaidCommandSeatBridge.isSeatProtected(maid);
        if (commandProtected && maid.isHomeModeEnable()) {
            return;
        }

        LivingEntity owner = maid.getOwner();
        boolean ownerStillAboard = owner != null
                && owner.isAlive()
                && owner.level() == maid.level()
                && owner.getVehicle() == vehicle;
        if (!ownerStillAboard) {
            // A command seat must not strand a following maid after the
            // owner leaves their shared boat.
            if (commandProtected) {
                MaidCommandSeatBridge.releaseForTeleport(maid);
            }
            maid.stopRiding();
        }
    }

    public static void leaveSeatForFollow(EntityMaid maid) {
        if (!canLeaveSeatState(maid)
                || maid.isHomeModeEnable()) {
            return;
        }
        Entity vehicle = maid.getVehicle();
        boolean commandProtected =
                MaidCommandSeatBridge.isSeatProtected(maid);
        if (!isPassiveSeat(vehicle)
                && !isOrdinaryBoat(vehicle)
                && !commandProtected) {
            return;
        }
        if (commandProtected) {
            if (!requiresEmergencyOwnerFollow(maid)) {
                return;
            }
            MaidCommandSeatBridge.releaseForTeleport(maid);
            maid.stopRiding();
            return;
        }

        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || maid.level() != owner.level()) {
            return;
        }
        int followDistance = (int) maid.getRestrictRadius() - 2;
        if (!maid.closerThan(owner, followDistance)) {
            maid.stopRiding();
        }
    }

    public static boolean requiresEmergencyOwnerFollow(EntityMaid maid) {
        if (!canLeaveSeatState(maid)
                || maid.isHomeModeEnable()) {
            return false;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || maid.level() != owner.level()) {
            return false;
        }
        return OwnerFollowPolicy.INSTANCE.shouldTeleport(
                maid.distanceToSqr(owner)
        );
    }

    /**
     * Every autonomous "get up and go" starts here, so the mode check does too.
     *
     * <p>Deciding to leave a chair — to follow, to fetch, to fight — is a
     * decision, and in the host's own modes the host makes it. A maid sitting
     * in a boat under a work mode was being made to stand up by us, on reasons
     * that mode never asked about.
     */
    private static boolean canLeaveSeatState(EntityMaid maid) {
        return FreedomMode.isActive(maid)
                && !maid.level().isClientSide()
                && !maid.isMaidInSittingPose()
                && !maid.isOrderedToSit()
                && !maid.isSleeping()
                && !maid.isLeashed();
    }

    private static boolean isPassiveSeat(Entity vehicle) {
        return FreedomOccupancy.isPassiveSeat(vehicle);
    }

    private static boolean isOrdinaryBoat(Entity vehicle) {
        return vehicle instanceof Boat;
    }

}
