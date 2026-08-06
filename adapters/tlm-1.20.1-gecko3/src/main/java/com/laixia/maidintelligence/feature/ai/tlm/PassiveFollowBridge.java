package com.laixia.maidintelligence.feature.ai.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.domain.arbitration.BehaviorOccupancyLevel;
import com.laixia.maidintelligence.feature.ai.domain.PassiveFollowPolicy;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentLease;
import com.laixia.maidintelligence.feature.ai.domain.MovementIntentSource;

/**
 * Adapts live TLM state to the platform-neutral passive-follow policy.
 */
public final class PassiveFollowBridge {
    private PassiveFollowBridge() {
    }

    public static boolean shouldDefer(EntityMaid maid) {
        MaidMovementCoordinationApi coordination =
                AdapterRuntime.require(MaidMovementCoordinationApi.class);
        if (!coordination.mode().enforcementEnabled()
                || maid.getSwimManager().isGoingToBreath()) {
            return false;
        }

        LivingEntity owner = maid.getOwner();
        if (owner == null
                || owner.isSpectator()
                || owner.isDeadOrDying()
                || owner.level() != maid.level()
                || currentTargetIsOwner(maid, owner)) {
            return false;
        }

        int followDistance = Math.max(
                0,
                (int) maid.getRestrictRadius() - 2
        );
        int teleportDistance = followDistance + 4;
        return PassiveFollowPolicy.INSTANCE.shouldDefer(
                ActivityRadiusBridge.ownerStationary(maid),
                hasProtectedTask(maid),
                maid.distanceToSqr(owner),
                followDistance,
                teleportDistance
        );
    }

    /**
     * Whether she is in the middle of something worth not yanking her out of.
     *
     * <p>Occupancy alone was not enough. A companion errand deliberately reads
     * as idle — its movement lease is passive, so that native behaviour is
     * never wrongly told she is busy — and the consequence was that walking to
     * a cabinet ten blocks away crossed the follow threshold at eight, native
     * follow rewrote her walk target, and she turned round every single time.
     *
     * <p>So a passive companion lease counts here too. It is the narrowest way
     * to say "she is already going somewhere on purpose", and it changes
     * nothing about how she looks to anything else that asks about occupancy.
     */
    private static boolean hasProtectedTask(EntityMaid maid) {
        long gameTime = maid.level().getGameTime();
        if (TlmBehaviorOccupancyClassifier.snapshot(maid, gameTime)
                .level() != BehaviorOccupancyLevel.IDLE) {
            return true;
        }
        return companionErrandUnderWay(maid, gameTime);
    }

    private static boolean companionErrandUnderWay(
            EntityMaid maid,
            long gameTime
    ) {
        if (!(maid instanceof MovementCoordinationAccess access)) {
            return false;
        }
        MovementIntentLease lease =
                access.maidIntelligence$movementIntentLease();
        return lease != null
                && lease.hasActiveLease(gameTime)
                && lease.holder() == MovementIntentSource.COMPANION;
    }

    private static boolean currentTargetIsOwner(
            EntityMaid maid,
            LivingEntity owner
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget())
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(owner::equals)
                .isPresent();
    }
}
