package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.mixin.accessor.EntityAccessor;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
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
    private static final int SEAT_CLAIM_TICKS = 100;

    private static final Map<EntityMaid, Session> SESSIONS =
            new WeakHashMap<>();
    private static final Map<EntityMaid, CoordinationClaimToken>
            OCCUPIED_CLAIMS = new WeakHashMap<>();
    private static TlmAffordancePerceptionService perception;

    private MaidCommandSeatBridge() {
    }

    public static synchronized void bindPerception(
            TlmAffordancePerceptionService service
    ) {
        perception = java.util.Objects.requireNonNull(
                service,
                "service"
        );
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
            renewOccupiedClaim(maid);
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
                && available(previousOwnerVehicle, maid, owner)) {
            candidate = previousOwnerVehicle;
        } else if (currentOwnerVehicle != null) {
            if (available(currentOwnerVehicle, maid, owner)) {
                candidate = currentOwnerVehicle;
            } else {
                candidate = nearestCompatibleSeat(
                        owner,
                        currentOwnerVehicle,
                        maid
                );
            }
        }
        if (candidate == null
                || !maid.closerThan(candidate, SEAT_SEARCH_RANGE)
                || !available(candidate, maid, owner)
                || !tryMountClaimed(maid, candidate)) {
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
        releaseClaim(maid, "owner_release");
        clearLock(maid);
    }

    public static void releaseForDanger(EntityMaid maid) {
        releaseTerminal(maid);
    }

    /**
     * Releases a passive command seat before TLM performs owner teleport.
     */
    public static void releaseForTeleport(EntityMaid maid) {
        releaseTerminal(maid);
    }

    private static void releaseTerminal(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }
        SESSIONS.remove(maid);
        releaseClaim(maid, "terminal_release");
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
            releaseClaim(maid, "seat_lost");
            clearLock(maid);
            return false;
        }
        renewOccupiedClaim(maid);
        return true;
    }

    private static Entity nearestCompatibleSeat(
            LivingEntity owner,
            Entity occupiedSeat,
            EntityMaid maid
    ) {
        TlmAffordancePerceptionService current = perception;
        if (current == null) {
            return null;
        }
        return current.queryCompatibleSeats(
                        maid,
                        32,
                        SEAT_SEARCH_RANGE,
                        maid.level().getGameTime()
                ).stream()
                .filter(seat -> seat != occupiedSeat)
                .filter(seat -> compatibleSeatType(occupiedSeat, seat))
                .filter(seat -> available(seat, maid, owner))
                .filter(seat -> claimAvailable(maid, seat))
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    private static boolean tryMountClaimed(
            EntityMaid maid,
            Entity candidate
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        releaseClaim(maid, "retarget");
        int seatIndex = candidate.getPassengers().size();
        CoordinationClaimService claims =
                TlmCoordinationClaims.service(level);
        CoordinationClaimToken token = claims.tryClaim(
                new CoordinationClaimRequest(
                        TlmCoordinationClaims.seat(
                                level,
                                candidate,
                                seatIndex
                        ),
                        maid.getUUID(),
                        TlmCoordinationClaims.operation(
                                maid,
                                "seat/" + candidate.getUUID()
                                        + "/" + seatIndex
                        ),
                        SEAT_CLAIM_TICKS
                ),
                level.getGameTime()
        ).orElse(null);
        if (token == null) {
            return false;
        }
        if (!maid.startRiding(candidate, true)
                || !claims.occupy(
                token,
                level.getGameTime(),
                SEAT_CLAIM_TICKS
        )) {
            maid.stopRiding();
            claims.release(
                    token,
                    level.getGameTime(),
                    "mount_failed"
            );
            return false;
        }
        OCCUPIED_CLAIMS.put(maid, token);
        return true;
    }

    private static boolean claimAvailable(
            EntityMaid maid,
            Entity candidate
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        return !TlmCoordinationClaims.service(level).isClaimed(
                TlmCoordinationClaims.seat(
                        level,
                        candidate,
                        candidate.getPassengers().size()
                ),
                level.getGameTime()
        );
    }

    private static void renewOccupiedClaim(EntityMaid maid) {
        CoordinationClaimToken token = OCCUPIED_CLAIMS.get(maid);
        if (token == null
                || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (!TlmCoordinationClaims.service(level).renew(
                token,
                level.getGameTime(),
                SEAT_CLAIM_TICKS
        )) {
            OCCUPIED_CLAIMS.remove(maid);
        }
    }

    private static void releaseClaim(EntityMaid maid, String reason) {
        CoordinationClaimToken token = OCCUPIED_CLAIMS.remove(maid);
        if (token == null
                || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        TlmCoordinationClaims.service(level).release(
                token,
                level.getGameTime(),
                reason
        );
    }

    private static boolean available(
            Entity vehicle,
            EntityMaid maid,
            LivingEntity owner
    ) {
        if (!vehicle.isAlive()
                || vehicle == maid) {
            return false;
        }
        EntityAccessor access = (EntityAccessor) vehicle;
        return access.tlmCanAddPassenger(maid)
                || access.tlmCanAddPassenger(owner);
    }

    private static boolean compatibleSeatType(
            Entity occupiedSeat,
            Entity candidate
    ) {
        if (candidate.getType() == occupiedSeat.getType()) {
            return true;
        }
        return isTlmPassiveSeat(occupiedSeat)
                && isTlmPassiveSeat(candidate);
    }

    private static boolean isTlmPassiveSeat(Entity entity) {
        return entity.getType() == EntityChair.TYPE
                || entity.getType() == EntitySit.TYPE;
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
